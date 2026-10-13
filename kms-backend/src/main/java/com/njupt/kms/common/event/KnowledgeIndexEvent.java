package com.njupt.kms.common.event;

/**
 * 知识条目变更事件，驱动向量索引重建。
 *
 * <p>刻意定义在 M0 而非 M5：生产者是 M1（知识资产域），消费者是 M5（智能问答域），
 * 放在任何一方的包里都会让另一方反向依赖。M0 是两个模块的共同依赖。
 *
 * @param itemId 条目 id
 * @param action UPSERT 表示新增/编辑/发布，REMOVE 表示删除/归档/退回草稿
 */
public record KnowledgeIndexEvent(Long itemId, Action action) {

    public enum Action {
        UPSERT, REMOVE
    }

    public static KnowledgeIndexEvent upsert(Long itemId) {
        return new KnowledgeIndexEvent(itemId, Action.UPSERT);
    }

    public static KnowledgeIndexEvent remove(Long itemId) {
        return new KnowledgeIndexEvent(itemId, Action.REMOVE);
    }
}
