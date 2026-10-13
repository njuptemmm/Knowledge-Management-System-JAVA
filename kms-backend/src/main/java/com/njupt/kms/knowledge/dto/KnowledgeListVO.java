package com.njupt.kms.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeListVO {

    private Long id;
    private String title;
    private String summary;
    private String status;
    private Long categoryId;
    private String categoryName;
    private String authorName;
    private Integer viewCount;
    private LocalDateTime updatedAt;
}
