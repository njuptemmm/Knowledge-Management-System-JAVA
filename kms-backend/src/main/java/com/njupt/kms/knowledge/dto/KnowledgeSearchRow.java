package com.njupt.kms.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 检索命中的知识条目原始行。只有 M1 生产它。 */
@Data
public class KnowledgeSearchRow {

    private Long id;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private String status;
    private LocalDateTime updatedAt;
    /** 加权相关度；无关键词时为 0 */
    private Double score;
}
