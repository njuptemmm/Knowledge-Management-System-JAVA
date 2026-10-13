package com.njupt.kms.qa.index;

import java.util.Set;

/**
 * 按条目 id 取标题与正文。
 *
 * <p>抽成接口是为了让索引服务不直接依赖取数实现：{@code knowledge_item} 表归 M1 所有，
 * 由 M1 实现本接口（与 {@code KnowledgeSearchPort} 同一模式），
 * M5 只见接口，SQL 仍写在 M1 包内。
 */
@FunctionalInterface
public interface ItemContentLoader {

    /** 返回 null 表示条目不存在或当前不可索引（如未发布）。 */
    ItemContent load(Long itemId);

    /** 全部可索引条目的 id。默认空集合，便于测试只实现 {@link #load}。 */
    default Set<Long> allIndexableItemIds() {
        return Set.of();
    }

    record ItemContent(String title, String content) {
    }
}
