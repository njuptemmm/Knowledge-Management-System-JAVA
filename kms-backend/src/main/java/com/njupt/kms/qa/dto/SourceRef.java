package com.njupt.kms.qa.dto;

/** 返回给前端的引用来源。前端据此渲染可点击的引用卡片，跳转到知识详情页。 */
public record SourceRef(Long itemId, String title, String headingPath, double score) {
}
