package com.njupt.kms.search.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SearchHit {

    private Long id;
    private String title;
    /** 纯文本命中片段，不含 HTML。高亮由前端完成，避免后端拼 HTML 引入 XSS。 */
    private String snippet;
    private Long categoryId;
    private String categoryName;
    private List<String> tags = new ArrayList<>();
    private String status;
    private LocalDateTime updatedAt;
    private Double score;
}
