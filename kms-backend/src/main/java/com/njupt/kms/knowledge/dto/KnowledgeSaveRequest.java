package com.njupt.kms.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @Size(max = 500, message = "摘要不能超过 500 字")
    private String summary;

    private String content;

    private String contentType;

    private Long categoryId;

    private List<Long> tagIds;
}
