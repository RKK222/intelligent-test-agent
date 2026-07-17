package com.enterprise.testagent.common.api;

import java.util.Objects;

/**
 * 平台统一成功响应体，所有对外 API 成功返回时都带上 traceId，便于前后端和日志串联。
 */
public record ApiResponse<T>(boolean success, T data, String traceId) {

    /**
     * 校验成功响应的不变量，禁止通过构造器伪造 success=false 的成功响应。
     */
    public ApiResponse {
        if (!success) {
            throw new IllegalArgumentException("success response must keep success=true");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * 构造统一成功响应，data 可为空，traceId 必须由入口层生成或透传。
     */
    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, traceId);
    }
}
