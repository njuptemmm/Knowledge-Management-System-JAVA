package com.njupt.kms.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提问请求体。
 *
 * <p>用请求体而非查询参数：问题可以很长，中文经 URL 编码后长度会膨胀数倍，
 * 放在 URL 里会撞上长度上限并返回 400。实测 1500 字的问题（编码后约 1.3 万字符）
 * 就会触发。
 */
@Data
public class QaAskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过 2000 字")
    private String question;

    /** 续接已有会话时传入；不传则新建一个并回传 id。 */
    private String chatId;
}
