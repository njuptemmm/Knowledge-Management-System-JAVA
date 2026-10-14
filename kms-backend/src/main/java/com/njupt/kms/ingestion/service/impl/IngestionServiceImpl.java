package com.njupt.kms.ingestion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.ingestion.dto.AttachmentVO;
import com.njupt.kms.ingestion.entity.Attachment;
import com.njupt.kms.ingestion.event.AttachmentUploadedEvent;
import com.njupt.kms.ingestion.mapper.AttachmentMapper;
import com.njupt.kms.ingestion.service.IngestionService;
import com.njupt.kms.ingestion.storage.FileStorage;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionServiceImpl implements IngestionService {

    private final AttachmentMapper attachmentMapper;
    private final FileStorage fileStorage;
    private final KnowledgeService knowledgeService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AttachmentVO upload(Long itemId, MultipartFile file) {
        requireWritableItem(itemId);

        String storedPath = fileStorage.store(file);

        Attachment attachment = new Attachment();
        attachment.setItemId(itemId);
        attachment.setOriginalName(sanitizeName(file.getOriginalFilename()));
        attachment.setStoredPath(storedPath);
        attachment.setMimeType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setParseStatus(Attachment.STATUS_PENDING);
        attachmentMapper.insert(attachment);

        // 事务提交后才触发解析，避免解析线程读到未提交的记录
        eventPublisher.publishEvent(new AttachmentUploadedEvent(attachment.getId()));
        return AttachmentVO.from(attachment);
    }

    @Override
    public List<AttachmentVO> listByItem(Long itemId) {
        return attachmentMapper.selectList(
                        Wrappers.<Attachment>lambdaQuery()
                                .eq(Attachment::getItemId, itemId)
                                .orderByDesc(Attachment::getId))
                .stream()
                .map(AttachmentVO::from)
                .toList();
    }

    @Override
    public Attachment get(Long id) {
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        return attachment;
    }

    @Override
    public Resource download(Long id) {
        return fileStorage.load(get(id).getStoredPath());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Attachment attachment = get(id);
        requireWritableItem(attachment.getItemId());
        attachmentMapper.deleteById(id);
        fileStorage.delete(attachment.getStoredPath());
    }

    /** 只有条目的作者或管理员能挂附件。 */
    private void requireWritableItem(Long itemId) {
        KnowledgeItem item = knowledgeService.getEntity(itemId);
        User current = CurrentUser.getRequired();
        if (!current.isAdmin() && !item.getAuthorId().equals(current.getId())) {
            throw new BusinessException("无权操作他人的知识条目");
        }
    }

    /**
     * 清洗原始文件名，只保留用于展示的部分。
     *
     * <p>存储路径完全由服务端生成（UUID + 扩展名），本字段不参与任何路径拼接，
     * 因此这里的清洗是纵深防御：去掉路径分隔符、上级目录记号与控制字符，
     * 避免它在日志、下载响应头或前端渲染时被误用。
     */
    private String sanitizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "未命名文件";
        }
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        // 去掉 ".."（含 URL 编码形式的 %2e）与控制字符
        name = name.replace("..", "_")
                .replaceAll("(?i)%2e", "_")
                .replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank() || name.equals(".")) {
            return "未命名文件";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
