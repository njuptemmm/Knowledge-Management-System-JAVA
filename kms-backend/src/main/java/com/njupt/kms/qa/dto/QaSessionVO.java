package com.njupt.kms.qa.dto;

import java.time.LocalDateTime;

/**
 * 会话列表项。
 *
 * <p>列表只返回摘要信息，不返回消息正文——会话多时正文会让响应体膨胀几十倍，
 * 而列表页根本不用它。点开某个会话时再走 {@code /api/qa/history/{chatId}} 取正文。
 *
 * @param chatId       会话 id
 * @param title        标题，默认取第一句提问的前 24 字
 * @param messageCount 消息条数（一问一答算 2 条）
 * @param createdAt    创建时间
 * @param updatedAt    最后活跃时间，列表按它倒序
 */
public record QaSessionVO(String chatId, String title, int messageCount,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
}
