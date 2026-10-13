package com.njupt.kms.security;

/** 令牌存储抽象。当前实现为 Redis，便于后续替换。 */
public interface TokenStore {

    void save(String token, Long userId);

    Long findUserId(String token);

    void remove(String token);
}
