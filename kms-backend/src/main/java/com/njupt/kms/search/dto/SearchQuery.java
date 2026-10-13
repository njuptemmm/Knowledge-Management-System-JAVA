package com.njupt.kms.search.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SearchQuery {

    private String keyword;

    private Long categoryId;

    private Long tagId;

    private String contentType;

    /** DRAFT / PUBLISHED / ARCHIVED；为空时按 mine 决定 */
    private String status;

    private LocalDate from;

    private LocalDate to;

    /** relevance（默认） / latest / hottest */
    private String sort;

    /** true 表示只搜索当前用户的条目（含全部状态） */
    private Boolean mine = false;

    private Integer page = 1;

    private Integer size = 10;
}
