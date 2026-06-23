package com.icbc.testagent.domain.configuration;

import java.time.Instant;
import java.util.Objects;

/**
 * 外部系统同步的应用定义，本期仅只读消费。
 */
public record ApplicationDefinition(
        ApplicationId appId,
        String appName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public ApplicationDefinition {
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(appName, "appName must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (appName.isBlank()) {
            throw new IllegalArgumentException("appName must not be blank");
        }
    }
}
