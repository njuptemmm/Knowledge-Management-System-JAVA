package com.njupt.kms.qa.retrieve;

/**
 * 召回的一个块。
 *
 * @param itemId      所属知识条目 id
 * @param title       条目标题
 * @param headingPath 标题路径，如 {@code Java 基础 > 集合框架}，用于在引用中定位出处
 * @param text        块正文
 * @param score       相似度
 */
public record RetrievedChunk(Long itemId, String title, String headingPath, String text, double score) {
}
