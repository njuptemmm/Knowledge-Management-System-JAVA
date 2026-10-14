package com.njupt.kms.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.njupt.kms.common.BusinessException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private AuthService authService;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        interceptor = new AuthInterceptor(authService);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    private User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setRole("USER");
        u.setStatus(1);
        return u;
    }

    @Test
    void sets_current_user_from_a_valid_token() {
        User zhangsan = user(2L, "zhangsan");
        when(authService.resolve("tok")).thenReturn(zhangsan);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "tok");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(CurrentUser.get()).isNotNull();
        assertThat(CurrentUser.get().getUsername()).isEqualTo("zhangsan");
    }

    @Test
    void request_without_token_clears_any_identity_left_on_the_thread() {
        // 回归测试：ThreadLocal 残留会造成身份串号。
        // 实测出现过「上一个请求的身份被匿名请求继承」，导致未登录也能调用
        // 需要登录的接口。preHandle 必须无条件赋值，而不是「有 token 才赋值」。
        CurrentUser.set(user(2L, "zhangsan"));

        MockHttpServletRequest request = new MockHttpServletRequest();   // 不带 Authorization

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(CurrentUser.get()).isNull();
        // 需要登录的接口此时必须拒绝
        assertThatThrownBy(CurrentUser::getRequired)
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }

    @Test
    void invalid_token_clears_identity_instead_of_leaving_the_previous_one() {
        when(authService.resolve("expired")).thenReturn(null);
        CurrentUser.set(user(2L, "zhangsan"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "expired");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(CurrentUser.get()).isNull();
    }

    @Test
    void after_completion_clears_the_context() {
        CurrentUser.set(user(2L, "zhangsan"));

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object(), null);

        assertThat(CurrentUser.get()).isNull();
    }

    @Test
    void async_handling_start_releases_the_threads_identity() {
        // SSE 问答会把线程交还给线程池，此时不清理就会串号
        CurrentUser.set(user(2L, "zhangsan"));

        interceptor.afterConcurrentHandlingStarted(new MockHttpServletRequest(),
                new MockHttpServletResponse(), new Object());

        assertThat(CurrentUser.get()).isNull();
    }
}
