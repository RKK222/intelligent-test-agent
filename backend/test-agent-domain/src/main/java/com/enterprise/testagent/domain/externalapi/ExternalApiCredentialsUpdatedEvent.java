package com.enterprise.testagent.domain.externalapi;

/** 数据库事务提交后触发本机与跨 Java 凭据快照刷新。 */
public record ExternalApiCredentialsUpdatedEvent(String traceId) {
    public ExternalApiCredentialsUpdatedEvent {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }
}
