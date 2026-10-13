package com.njupt.kms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    private static final String KEY_PREFIX = "kms:token:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, Long userId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), TTL);
    }

    @Override
    public Long findUserId(String token) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        return value == null ? null : Long.valueOf(value);
    }

    @Override
    public void remove(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
