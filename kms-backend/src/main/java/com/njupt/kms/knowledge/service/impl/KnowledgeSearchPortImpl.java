package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;
import com.njupt.kms.knowledge.mapper.KnowledgeSearchMapper;
import com.njupt.kms.knowledge.service.KnowledgeSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchPortImpl implements KnowledgeSearchPort {

    private final KnowledgeSearchMapper searchMapper;

    @Override
    public IPage<KnowledgeSearchRow> search(KnowledgeSearchCondition condition, long page, long size) {
        Page<KnowledgeSearchRow> pager = new Page<>(page, size);
        return searchMapper.search(
                pager,
                condition.getKeyword(),
                condition.getWeightTitle(),
                condition.getWeightSummary(),
                condition.getWeightContent(),
                condition.getCategoryIds(),
                condition.getTagId(),
                condition.getContentType(),
                condition.getStatus(),
                condition.getFrom(),
                condition.getTo(),
                condition.getSort(),
                condition.getVisibleAuthorId(),
                condition.isUseFullText());
    }

    @Override
    public Map<Long, List<String>> tagNamesOf(Collection<Long> itemIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : searchMapper.selectTagsOf(itemIds)) {
            Long itemId = ((Number) row.get("itemId")).longValue();
            result.computeIfAbsent(itemId, k -> new ArrayList<>())
                    .add(String.valueOf(row.get("name")));
        }
        return result;
    }
}
