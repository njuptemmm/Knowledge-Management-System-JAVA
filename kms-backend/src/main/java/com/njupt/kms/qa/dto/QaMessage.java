package com.njupt.kms.qa.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一条问答记录，存 Redis 供历史回看。
 *
 * @param role    {@code user} 或 {@code assistant}
 * @param content 问题或答案正文
 * @param sources 答案的引用来源；用户消息为空列表
 * @param at      产生时间
 */
public record QaMessage(String role, String content, List<SourceRef> sources, LocalDateTime at) {

    public static QaMessage user(String question) {
        return new QaMessage("user", question, List.of(), LocalDateTime.now());
    }

    public static QaMessage assistant(String answer, List<SourceRef> sources) {
        return new QaMessage("assistant", answer, sources == null ? List.of() : sources, LocalDateTime.now());
    }
}
