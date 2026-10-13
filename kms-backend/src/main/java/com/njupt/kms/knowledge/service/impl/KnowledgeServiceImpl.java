package com.njupt.kms.knowledge.service.impl;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final KnowledgeItemMapper itemMapper;
    private final TagService tagService;

    @Override
    @Transactional
    public KnowledgeDetailVO create(KnowledgeSaveRequest request) {
        User current = CurrentUser.getRequired();

        KnowledgeItem item = new KnowledgeItem();
        item.setTitle(request.getTitle().trim());
        item.setSummary(request.getSummary());
        item.setContent(request.getContent());
        item.setContentType(request.getContentType() == null ? "MARKDOWN" : request.getContentType());
        item.setStatus(STATUS_DRAFT);
        item.setCategoryId(request.getCategoryId());
        item.setAuthorId(current.getId());
        item.setViewCount(0);
        itemMapper.insert(item);

        tagService.replaceTags(item.getId(), request.getTagIds());
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public KnowledgeDetailVO update(Long id, KnowledgeSaveRequest request) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);

        item.setTitle(request.getTitle().trim());
        item.setSummary(request.getSummary());
        item.setContent(request.getContent());
        if (request.getContentType() != null) {
            item.setContentType(request.getContentType());
        }
        item.setCategoryId(request.getCategoryId());
        itemMapper.updateById(item);

        tagService.replaceTags(id, request.getTagIds());
        return toDetailVO(item);
    }

    @Override
    public KnowledgeDetailVO detail(Long id) {
        KnowledgeItem item = requireExisting(id);
        itemMapper.incrementViewCount(id);
        item.setViewCount(item.getViewCount() == null ? 1 : item.getViewCount() + 1);
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        tagService.replaceTags(id, List.of());
        itemMapper.deleteById(id);
    }

    @Override
    public KnowledgeItem getEntity(Long id) {
        return requireExisting(id);
    }

    private KnowledgeItem requireExisting(Long id) {
        KnowledgeItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("知识条目不存在");
        }
        return item;
    }

    /** 仅作者本人或管理员可写。 */
    private void assertWritable(KnowledgeItem item) {
        User current = CurrentUser.getRequired();
        if (!current.isAdmin() && !item.getAuthorId().equals(current.getId())) {
            throw new BusinessException("无权操作他人的知识条目");
        }
    }

    private KnowledgeDetailVO toDetailVO(KnowledgeItem item) {
        return KnowledgeDetailVO.from(item);
    }
}
