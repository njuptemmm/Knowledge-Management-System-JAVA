package com.njupt.kms.knowledge.service.impl;

import com.njupt.kms.knowledge.mapper.KnowledgeSearchMapper;
import com.njupt.kms.qa.index.ItemContentLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 把 M1 的知识条目内容暴露给 M5 索引服务。
 *
 * <p>与 {@code KnowledgeSearchPort} 同一模式：针对 {@code knowledge_item} 的 SQL
 * 仍写在 M1 包内，M5 只见接口。
 *
 * <p>只暴露已发布条目——草稿与归档内容不进索引，
 * 从根源杜绝通过问答泄露未发布内容。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeItemContentLoader implements ItemContentLoader {

    private final KnowledgeSearchMapper searchMapper;

    @Override
    public ItemContent load(Long itemId) {
        if (itemId == null) {
            return null;
        }
        Map<String, Object> row = searchMapper.selectPublishedById(itemId);
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new ItemContent(
                row.get("title") == null ? "" : String.valueOf(row.get("title")),
                row.get("body") == null ? "" : String.valueOf(row.get("body")));
    }

    @Override
    public Set<Long> allIndexableItemIds() {
        return new HashSet<>(searchMapper.selectPublishedIds());
    }
}
