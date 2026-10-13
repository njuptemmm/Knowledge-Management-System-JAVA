package com.njupt.kms.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M1 导出的**只读**端口，供 M3 检索域跨模块取数。
 *
 * <p>{@code knowledge_item} 表由 M1 拥有，因此针对它的 SQL 只能写在 M1 包内
 * （设计文档 §3.3）。M3 通过本接口取数，依赖方向单向：search → knowledge。
 *
 * <p>本接口不含任何写操作，这是「受控例外」得以成立的前提。
 */
public interface KnowledgeSearchPort {

    IPage<KnowledgeSearchRow> search(KnowledgeSearchCondition condition, long page, long size);

    /** 批量取条目标签名，避免 M3 逐条查库。返回 itemId → 标签名列表。 */
    Map<Long, List<String>> tagNamesOf(Collection<Long> itemIds);

    /**
     * 给定一批条目 id，返回**当前真正可见**的子集。
     *
     * <p>供 M5 问答域在向量召回后二次过滤：索引与状态变更之间存在窗口期，
     * 只信索引里的 metadata 会在窗口期内漏出已归档内容。
     *
     * <p>规则与列表、检索接口完全一致：{@code currentUserId} 为 null（未登录）
     * 时只返回 PUBLISHED 的条目；登录用户额外可见自己的条目。
     */
    Set<Long> visibleItemIds(Collection<Long> itemIds, Long currentUserId);
}
