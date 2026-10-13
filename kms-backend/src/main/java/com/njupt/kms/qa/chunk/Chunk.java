package com.njupt.kms.qa.chunk;

/**
 * 索引的最小单元。
 *
 * @param itemId      所属知识条目 id
 * @param index       该条目内的块序号，从 0 起
 * @param text        块正文
 * @param headingPath 标题路径，如 {@code Java 基础 > 集合框架}，用于在引用中定位出处
 */
public record Chunk(Long itemId, int index, String text, String headingPath) {

    /** 向量库文档 id。带 itemId 前缀，便于按条目识别与排查。 */
    public String docId() {
        return "item-" + itemId + "-chunk-" + index;
    }
}
