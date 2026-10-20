package com.example.llm.text.analysis.common;

import lombok.Getter;

/**
 * 业务异常：携带业务错误码，由全局异常处理器统一响应
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
