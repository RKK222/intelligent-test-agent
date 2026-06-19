package com.example.testagent.common.api;

import java.util.Objects;

/**
 * 平台统一成功响应体，所有对外 API 成功返回时都带上 traceId，便于前后端和日志串联。
 */
public record ApiResponse<T>(boolean success, T data, String traceId) {

    public ApiResponse {
        if (!success) {
            throw new IllegalArgumentException("success response must keep success=true");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, traceId);
    }
}
