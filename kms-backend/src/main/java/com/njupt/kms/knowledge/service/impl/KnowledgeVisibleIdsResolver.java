package com.njupt.kms.knowledge.service.impl;

import com.njupt.kms.knowledge.mapper.KnowledgeSearchMapper;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * 由 M1 实现的「可见性解析器」，供 M5 问答域在向量召回后二次过滤。
 *
 * <p>与 {@code KnowledgeSearchPort} 同一模式：针对 {@code knowledge_item} 的 SQL
 * 仍写在 M1 包内，M5 只注入一个 {@code Function}，不接触 Mapper。
 *
 * <p>当前用户从 {@link CurrentUser} 读取——与列表、检索接口用的是同一个来源。
 * 召回发生在请求线程上（流式响应开始之前），因此 ThreadLocal 可用。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeVisibleIdsResolver implements Function<Collection<Long>, Set<Long>> {

    private final KnowledgeSearchMapper searchMapper;

    @Override
    public Set<Long> apply(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        User current = CurrentUser.get();
        Long currentUserId = current == null ? null : current.getId();
        return new HashSet<>(searchMapper.selectVisibleIds(itemIds, currentUserId));
    }
}
