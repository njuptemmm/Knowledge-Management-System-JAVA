package com.njupt.kms.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final TokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;

    public String login(String username, String rawPassword) {
        User user = StringUtils.hasText(username)
                ? userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username))
                : null;

        // 用户不存在与密码错误返回同一提示，避免用户名枚举
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.save(token, user.getId());
        return token;
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            tokenStore.remove(token);
        }
    }

    /** 令牌无效或用户已被删除/禁用时返回 null。 */
    public User resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Long userId = tokenStore.findUserId(token);
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        return user;
    }

    public User requireUser(String token) {
        User user = resolve(token);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }
}
