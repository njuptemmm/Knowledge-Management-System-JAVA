package com.njupt.kms.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySaveRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过 50 字")
    private String name;

    /** 0 或 null 表示根分类 */
    private Long parentId;

    private Integer sort;

    @Size(max = 255, message = "描述不能超过 255 字")
    private String description;
}
