package com.njupt.kms.knowledge.dto;

import com.njupt.kms.knowledge.entity.KnowledgeItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeDetailVO {

    private Long id;
    private String title;
    private String summary;
    private String content;
    private String contentType;
    private String status;
    private Long categoryId;
    private String categoryName;
    private Long authorId;
    private String authorName;
    private Integer viewCount;
    private List<String> tags = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeDetailVO from(KnowledgeItem item) {
        KnowledgeDetailVO vo = new KnowledgeDetailVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setSummary(item.getSummary());
        vo.setContent(item.getContent());
        vo.setContentType(item.getContentType());
        vo.setStatus(item.getStatus());
        vo.setCategoryId(item.getCategoryId());
        vo.setAuthorId(item.getAuthorId());
        vo.setViewCount(item.getViewCount());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }
}
