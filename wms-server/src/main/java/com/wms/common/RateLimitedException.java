package com.wms.common;

/** 登录失败限速异常（HTTP 429）。 */
public class RateLimitedException extends RuntimeException {
    public RateLimitedException(String message) { super(message); }
}
