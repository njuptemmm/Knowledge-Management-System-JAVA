package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.event.KnowledgeIndexEvent;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeListVO;
import com.njupt.kms.knowledge.dto.KnowledgeQuery;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import com.njupt.kms.security.UserQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private static final int MAX_PAGE_SIZE = 100;

    private final KnowledgeItemMapper itemMapper;
    private final TagService tagService;
    private final CategoryService categoryService;
    private final UserQueryPort userQueryPort;
    private final ApplicationEventPublisher eventPublisher;

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

        applyTags(item.getId(), request);
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

        applyTags(id, request);
        // 只有已发布的条目在索引里，草稿的编辑不必触发重建
        if (STATUS_PUBLISHED.equals(item.getStatus())) {
            eventPublisher.publishEvent(KnowledgeIndexEvent.upsert(id));
        }
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
    public Page<KnowledgeListVO> page(KnowledgeQuery query) {
        User current = CurrentUser.get();

        // mine=true 时由 SQL 的 visibleAuthorId 分支放行自己的全部状态；
        // 否则只返回 PUBLISHED。
        Long visibleAuthorId = Boolean.TRUE.equals(query.getMine()) && current != null
                ? current.getId()
                : null;

        // 点击父分类要能看到下级条目，因此取其全部后代 id
        Collection<Long> categoryIds = query.getCategoryId() == null
                ? null
                : categoryService.descendantIds(query.getCategoryId());

        Page<KnowledgeListVO> page = new Page<>(clampPage(query), clampSize(query));
        return (Page<KnowledgeListVO>) itemMapper.selectPageVO(
                page, categoryIds, query.getStatus(), query.getTagId(), visibleAuthorId);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        tagService.replaceTags(id, List.of());
        itemMapper.deleteById(id);
        eventPublisher.publishEvent(KnowledgeIndexEvent.remove(id));
    }

    @Override
    @Transactional
    public KnowledgeDetailVO publish(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        if (!STATUS_DRAFT.equals(item.getStatus())) {
            throw new BusinessException("当前状态不允许发布");
        }
        item.setStatus(STATUS_PUBLISHED);
        item.setPublishedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        // 事务提交后由 M5 重建该条目的向量索引（未启用 RAG 时无监听者，事件被丢弃）
        eventPublisher.publishEvent(KnowledgeIndexEvent.upsert(id));
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public KnowledgeDetailVO archive(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        if (STATUS_ARCHIVED.equals(item.getStatus())) {
            throw new BusinessException("当前状态不允许归档");
        }
        item.setStatus(STATUS_ARCHIVED);
        item.setArchivedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        // 归档内容不该留在索引里，否则问答仍可能引用它
        eventPublisher.publishEvent(KnowledgeIndexEvent.remove(id));
        return toDetailVO(item);
    }

    @Override
    @Transactional
    public KnowledgeDetailVO restore(Long id) {
        KnowledgeItem item = requireExisting(id);
        assertWritable(item);
        if (!STATUS_ARCHIVED.equals(item.getStatus())) {
            throw new BusinessException("当前状态不允许恢复");
        }
        item.setStatus(STATUS_DRAFT);
        item.setArchivedAt(null);
        itemMapper.updateById(item);
        // 回到草稿即退出可见范围，索引里也不该留着
        eventPublisher.publishEvent(KnowledgeIndexEvent.remove(id));
        return toDetailVO(item);
    }

    @Override
    public KnowledgeItem getEntity(Long id) {
        return requireExisting(id);
    }

    /**
     * 写入标签。tagNames 优先于 tagIds —— 编辑器提交的是名称（新标签没有 id），
     * 其余调用方提交 id。两者都为空时表示清空标签。
     */
    private void applyTags(Long itemId, KnowledgeSaveRequest request) {
        if (request.getTagNames() != null) {
            tagService.replaceTagsByNames(itemId, request.getTagNames());
        } else {
            tagService.replaceTags(itemId, request.getTagIds());
        }
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

    private int clampPage(KnowledgeQuery query) {
        Integer page = query.getPage();
        return (page == null || page < 1) ? 1 : page;
    }

    private int clampSize(KnowledgeQuery query) {
        Integer size = query.getSize();
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 详情 VO 需要分类名、作者昵称与标签，这些都不在 knowledge_item 表里。
     * 分类与标签归本模块所有，直接查；作者昵称归 security 模块，走 UserQueryPort。
     */
    private KnowledgeDetailVO toDetailVO(KnowledgeItem item) {
        KnowledgeDetailVO vo = KnowledgeDetailVO.from(item);
        vo.setCategoryName(categoryService.nameOf(item.getCategoryId()));
        vo.setTags(tagService.namesOf(item.getId()));
        if (item.getAuthorId() != null) {
            vo.setAuthorName(userQueryPort.nicknamesOf(List.of(item.getAuthorId()))
                    .get(item.getAuthorId()));
        }
        return vo;
    }
}
