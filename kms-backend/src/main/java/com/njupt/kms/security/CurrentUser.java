package com.njupt.kms.security;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.ResultCode;

/** 当前请求的登录用户上下文。由 AuthInterceptor 写入与清理。 */
public final class CurrentUser {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(User user) {
        HOLDER.set(user);
    }

    /** 未登录返回 null。 */
    public static User get() {
        return HOLDER.get();
    }

    /** 未登录抛异常。业务方法应使用此方法强制登录。 */
    public static User getRequired() {
        User user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static Long getId() {
        return getRequired().getId();
    }

    /** 必须在请求结束时调用，否则线程池复用会造成身份串号。 */
    public static void clear() {
        HOLDER.remove();
    }
}
