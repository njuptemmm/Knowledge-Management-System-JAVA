package com.njupt.kms.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeQuery {

    private Long categoryId;

    private Long tagId;

    /** DRAFT / PUBLISHED / ARCHIVED；为空时按 mine 决定可见范围 */
    private String status;

    /** true 表示只看当前用户的条目（含全部状态） */
    private Boolean mine = false;

    private Integer page = 1;

    private Integer size = 10;
}
