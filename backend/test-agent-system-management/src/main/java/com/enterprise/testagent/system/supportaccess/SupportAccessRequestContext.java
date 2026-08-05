package com.enterprise.testagent.system.supportaccess;

/** 排查访问请求元数据；User-Agent 进入服务后只保存摘要。 */
public record SupportAccessRequestContext(
        String traceId,
        String ipAddress,
        String userAgent) {

    public SupportAccessRequestContext {
        traceId = require(traceId, "traceId");
        ipAddress = normalize(ipAddress);
        userAgent = normalize(userAgent);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
