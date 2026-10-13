package com.njupt.kms.common;

/** 统一响应码。0 表示成功，其余为业务失败。 */
public final class ResultCode {

    private ResultCode() {
    }

    public static final int SUCCESS = 0;
    /** 参数校验失败 */
    public static final int PARAM_ERROR = 1001;
    /** 未登录或登录已过期 */
    public static final int UNAUTHORIZED = 1002;
    /** 已登录但无权操作 */
    public static final int FORBIDDEN = 1003;
    /** 资源不存在 */
    public static final int NOT_FOUND = 1004;
    /** 一般业务失败 */
    public static final int BUSINESS_ERROR = 2000;
    /** 系统异常（原因不外泄） */
    public static final int SYSTEM_ERROR = 5000;
}
