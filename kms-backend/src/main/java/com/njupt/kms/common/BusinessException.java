package com.njupt.kms.common;

import lombok.Getter;

/** 业务失败。message 会原样展示给用户，必须写成人话。 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ResultCode.BUSINESS_ERROR, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
