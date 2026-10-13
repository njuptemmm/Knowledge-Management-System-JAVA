package com.njupt.kms.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
}
