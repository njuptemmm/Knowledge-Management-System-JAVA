package com.njupt.kms.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {

    public static final String TOKEN_HEADER = "Authorization";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(TOKEN_HEADER);
        // **无论有没有 token 都要赋值。**
        // CurrentUser 是 ThreadLocal，Tomcat 线程会被复用。若只在带 token 时赋值，
        // 上一个请求残留在这条线程上的身份就会作用到本次匿名请求上——
        // 实测出现过「未登录调用 /api/qa/reindex 返回成功」的身份串号。
        // 显式置 null 让残留值不可能存活。
        CurrentUser.set(StringUtils.hasText(token) ? authService.resolve(token) : null);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }

    /**
     * 异步请求（如 SSE 问答）开始并发处理时，Servlet 容器会把当前线程交还给线程池。
     *
     * <p>此时必须清理，否则这条线程带着本次请求的身份去服务下一个请求。
     * 本项目的异步接口（问答）在返回 Flux 之前已完成取数，因此这里清理不影响它。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request,
                                               HttpServletResponse response, Object handler) {
        CurrentUser.clear();
    }
}
