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

    /**
     * 按 id 指定标签。
     *
     * <p>{@code tagNames} 非 null 时本字段被忽略——两者只取其一，
     * 避免"提交了 id 又提交了名称"时语义不明。
     */
    private List<Long> tagIds;

    /**
     * 按名称指定标签，不存在的会自动创建。
     *
     * <p>面向编辑器：用户在输入框里敲一个新标签名时，前端拿不到它的 id
     * （标签没有独立的新增接口），只能按名称提交。没有这个字段，
     * 用户就只能从已有标签里挑，无法建立新标签。
     */
    @Size(max = 10, message = "单个条目最多 10 个标签")
    private List<@NotBlank(message = "标签名不能为空") @Size(max = 50, message = "标签名不能超过 50 字") String> tagNames;
}
