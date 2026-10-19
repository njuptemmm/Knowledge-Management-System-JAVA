package com.njupt.kms.qa.service;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.ResultCode;
import com.njupt.kms.qa.dto.QaSessionVO;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 问答会话的元数据与归属，存 Redis。
 *
 * <p>消息正文归 {@link QaHistoryService}（List），这里只管「会话是谁的、叫什么、什么时候活跃」：
 * <ul>
 *   <li>{@code kms:qa:session:{chatId}} —— Hash：ownerId / title / createdAt / updatedAt</li>
 *   <li>{@code kms:qa:sessions:{ownerId}} —— ZSet，score = 最后活跃时间戳，用于按活跃度倒序列出</li>
 * </ul>
 *
 * <p>不把会话元数据放进消息 List：两者生命周期不同，列表页只需要元数据，
 * 每次列会话都去反序列化全部消息既慢又没必要。
 *
 * <p>匿名用户仍可提问，但不建立会话记录（无法归属，也没有「我的会话」可列）。
 */
@Slf4j
@Service
public class QaSessionService {

    private static final String SESSION_KEY_PREFIX = "kms:qa:session:";
    private static final String INDEX_KEY_PREFIX = "kms:qa:sessions:";
    /** 与消息历史一致：7 天不使用即过期 */
    private static final Duration TTL = Duration.ofDays(7);
    /** 单用户最多保留的会话数。超出后最久未活跃的被裁剪 */
    private static final int MAX_SESSIONS = 50;
    private static final String DEFAULT_TITLE = "新对话";
    private static final int TITLE_MAX_CHARS = 24;
    /** 匿名会话的 ownerId。0 表示不属于任何账号，任何拿到 id 的人都能续聊 */
    private static final long ANONYMOUS_OWNER = 0L;

    private final StringRedisTemplate redisTemplate;
    private final QaHistoryService historyService;

    public QaSessionService(StringRedisTemplate redisTemplate, QaHistoryService historyService) {
        this.redisTemplate = redisTemplate;
        this.historyService = historyService;
    }

    /**
     * 记录一次提问，必要时创建会话。
     *
     * <p>标题只在第一句提问时写入：后续问题不该把标题改掉，否则列表里的名字
     * 会随着对话内容不断变化，用户找不到之前那段对话。
     *
     * @return 会话归属者；匿名会话返回 null
     */
    public Long touch(String chatId, String question) {
        if (!StringUtils.hasText(chatId)) {
            return null;
        }
        User current = CurrentUser.get();
        long ownerId = current == null ? ANONYMOUS_OWNER : current.getId();
        LocalDateTime now = LocalDateTime.now();

        Map<Object, Object> state = readState(chatId);
        if (state == null) {
            writeState(chatId, ownerId, titleOf(question), now, now);
        } else {
            String title = str(state.get("title"));
            if (!StringUtils.hasText(title) || DEFAULT_TITLE.equals(title)) {
                redisTemplate.opsForHash().put(sessionKey(chatId), "title", titleOf(question));
            }
            redisTemplate.opsForHash().put(sessionKey(chatId), "updatedAt", String.valueOf(millis(now)));
            redisTemplate.expire(sessionKey(chatId), TTL);
        }

        if (ownerId != ANONYMOUS_OWNER) {
            index(chatId, ownerId, now);
        }
        return ownerId == ANONYMOUS_OWNER ? null : ownerId;
    }

    /** 当前登录用户的会话，按最后活跃时间倒序。未登录返回空列表。 */
    public List<QaSessionVO> list() {
        User current = CurrentUser.get();
        if (current == null) {
            return List.of();
        }
        String indexKey = indexKey(current.getId());
        Set<String> ids = redisTemplate.opsForZSet().reverseRange(indexKey, 0, MAX_SESSIONS - 1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<QaSessionVO> sessions = new ArrayList<>(ids.size());
        for (String chatId : ids) {
            Map<Object, Object> state = readState(chatId);
            if (state == null) {
                // 元数据已过期但索引还在：顺手清掉，下次列表更干净
                redisTemplate.opsForZSet().remove(indexKey, chatId);
                continue;
            }
            sessions.add(new QaSessionVO(
                    chatId,
                    str(state.get("title")),
                    historyService.count(chatId),
                    toLocalDateTime(state.get("createdAt")),
                    toLocalDateTime(state.get("updatedAt"))));
        }
        return sessions;
    }

    /**
     * 会话归属校验。消息历史、续聊、删除都必须先过这一关。
     *
     * <p>没有元数据的会话（旧版本遗留、或匿名创建）直接放行：
     * id 是 32 位随机串，猜不到；而拒绝它们会让升级前的历史全部失效。
     */
    public void checkAccess(String chatId) {
        Map<Object, Object> state = readState(chatId);
        if (state == null) {
            return;
        }
        String rawOwner = str(state.get("ownerId"));
        if (!StringUtils.hasText(rawOwner)) {
            return;
        }
        long ownerId = Long.parseLong(rawOwner);
        if (ownerId == ANONYMOUS_OWNER) {
            return;
        }

        User current = CurrentUser.get();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "该会话属于登录用户，请先登录");
        }
        if (!Objects.equals(current.getId(), ownerId) && !current.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
    }

    /** 删除会话元数据与消息。调用方需先做 {@link #checkAccess}。 */
    public void delete(String chatId) {
        Map<Object, Object> state = readState(chatId);
        if (state != null) {
            String rawOwner = str(state.get("ownerId"));
            if (StringUtils.hasText(rawOwner)) {
                redisTemplate.opsForZSet().remove(indexKey(Long.parseLong(rawOwner)), chatId);
            }
        }
        redisTemplate.delete(sessionKey(chatId));
        historyService.clear(chatId);
    }

    public static String newChatId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ---------- 内部 ----------

    private void writeState(String chatId, long ownerId, String title,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        String key = sessionKey(chatId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "ownerId", String.valueOf(ownerId),
                "title", title,
                "createdAt", String.valueOf(millis(createdAt)),
                "updatedAt", String.valueOf(millis(updatedAt))));
        redisTemplate.expire(key, TTL);
    }

    private void index(String chatId, long ownerId, LocalDateTime activeAt) {
        String indexKey = indexKey(ownerId);
        redisTemplate.opsForZSet().add(indexKey, chatId, millis(activeAt));
        redisTemplate.expire(indexKey, TTL);
        // 只保留最近 MAX_SESSIONS 个：按 score 升序取最旧的一段删掉
        Long size = redisTemplate.opsForZSet().zCard(indexKey);
        if (size != null && size > MAX_SESSIONS) {
            Set<String> stale = redisTemplate.opsForZSet()
                    .range(indexKey, 0, size - MAX_SESSIONS - 1);
            if (stale != null && !stale.isEmpty()) {
                redisTemplate.opsForZSet().remove(indexKey, stale.toArray());
                stale.forEach(redisTemplate::delete);
            }
        }
    }

    private Map<Object, Object> readState(String chatId) {
        Map<Object, Object> state = redisTemplate.opsForHash().entries(sessionKey(chatId));
        // entries() 的契约允许返回 null（键不存在时不同客户端行为不一），统一收敛成「无会话」
        return state == null || state.isEmpty() ? null : state;
    }

    private static String sessionKey(String chatId) {
        return SESSION_KEY_PREFIX + chatId;
    }

    private static String indexKey(long ownerId) {
        return INDEX_KEY_PREFIX + ownerId;
    }

    private static String titleOf(String question) {
        if (!StringUtils.hasText(question)) {
            return DEFAULT_TITLE;
        }
        String trimmed = question.strip();
        return trimmed.length() <= TITLE_MAX_CHARS
                ? trimmed
                : trimmed.substring(0, TITLE_MAX_CHARS) + "…";
    }

    private static long millis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime toLocalDateTime(Object raw) {
        String text = str(raw);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(text)), ZoneId.systemDefault());
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
