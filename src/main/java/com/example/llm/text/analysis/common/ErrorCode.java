package com.example.llm.text.analysis.common;

/**
 * 业务错误码
 */
public class ErrorCode {

    /** 成功 */
    public static final int SUCCESS = 200;
    /** 参数错误 */
    public static final int PARAM_ERROR = 400;
    /** 大模型限流 */
    public static final int LLM_RATE_LIMITED = 429;
    /** 大模型服务不可用 */
    public static final int LLM_UNAVAILABLE = 503;
    /** 熔断打开（快速失败） */
    public static final int CIRCUIT_OPEN = 504;
    /** 服务器内部错误 */
    public static final int INTERNAL_ERROR = 500;

    private ErrorCode() {
    }
}
