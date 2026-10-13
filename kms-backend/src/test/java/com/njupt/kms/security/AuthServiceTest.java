package com.njupt.kms.security;

import com.njupt.kms.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private UserMapper userMapper;
    private TokenStore tokenStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        tokenStore = mock(TokenStore.class);
        authService = new AuthService(userMapper, tokenStore, ENCODER);
    }

    private User user(String rawPassword) {
        User u = new User();
        u.setId(2L);
        u.setUsername("zhangsan");
        u.setPasswordHash(ENCODER.encode(rawPassword));
        u.setRole("USER");
        u.setStatus(1);
        return u;
    }

    @Test
    void login_returns_token_on_correct_password() {
        when(userMapper.selectOne(any())).thenReturn(user("user123"));

        String token = authService.login("zhangsan", "user123");

        assertThat(token).isNotBlank();
        verify(tokenStore).save(eq(token), eq(2L));
    }

    @Test
    void login_rejects_wrong_password() {
        when(userMapper.selectOne(any())).thenReturn(user("user123"));

        assertThatThrownBy(() -> authService.login("zhangsan", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void login_rejects_unknown_user_with_same_message() {
        when(userMapper.selectOne(any())).thenReturn(null);

        // 不区分「用户不存在」与「密码错误」，避免用户名枚举
        assertThatThrownBy(() -> authService.login("nobody", "whatever"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void login_rejects_disabled_user() {
        User disabled = user("user123");
        disabled.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(disabled);

        assertThatThrownBy(() -> authService.login("zhangsan", "user123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已被禁用");
    }

    @Test
    void resolve_returns_null_for_blank_token() {
        assertThat(authService.resolve(null)).isNull();
        assertThat(authService.resolve("")).isNull();
    }

    @Test
    void resolve_returns_null_for_unknown_token() {
        when(tokenStore.findUserId("bad")).thenReturn(null);

        assertThat(authService.resolve("bad")).isNull();
    }

    @Test
    void resolve_returns_user_for_valid_token() {
        when(tokenStore.findUserId("tok")).thenReturn(2L);
        when(userMapper.selectById(2L)).thenReturn(user("user123"));

        User resolved = authService.resolve("tok");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getUsername()).isEqualTo("zhangsan");
    }

    @Test
    void logout_removes_token() {
        authService.logout("tok");

        verify(tokenStore).remove("tok");
    }
}
