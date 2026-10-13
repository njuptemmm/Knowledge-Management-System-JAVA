package com.njupt.kms.qa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.kms.qa.dto.QaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 问答会话历史，存 Redis。
 *
 * <p>沿用 TokenStore 的做法：只依赖模板，不引额外抽象。
 * 会话保留 7 天，与登录令牌一致；单会话最多保留 40 条消息，防止无限增长。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaHistoryService {

    private static final String KEY_PREFIX = "kms:qa:chat:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_MESSAGES = 40;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 追加一条消息。写失败只记日志——会话历史不该影响本次问答。 */
    public void append(String chatId, QaMessage message) {
        if (!StringUtils.hasText(chatId) || message == null) {
            return;
        }
        try {
            String key = KEY_PREFIX + chatId;
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
            redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("[RAG] 会话历史写入失败 chatId={}: {}", chatId, e.getMessage());
        }
    }

    public List<QaMessage> history(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return List.of();
        }
        List<String> raw = redisTemplate.opsForList().range(KEY_PREFIX + chatId, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<QaMessage> messages = new ArrayList<>(raw.size());
        for (String json : raw) {
            try {
                messages.add(objectMapper.readValue(json, QaMessage.class));
            } catch (Exception e) {
                // 单条坏数据不该让整个历史不可读
                log.warn("[RAG] 会话消息反序列化失败，已跳过");
            }
        }
        return messages;
    }

    public void clear(String chatId) {
        if (StringUtils.hasText(chatId)) {
            redisTemplate.delete(KEY_PREFIX + chatId);
        }
    }
}
