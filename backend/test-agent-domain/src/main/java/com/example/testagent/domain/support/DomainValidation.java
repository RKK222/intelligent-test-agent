package com.example.testagent.domain.support;

import java.time.Instant;
import java.util.Objects;

/**
 * 领域模型的轻量校验工具，只依赖 JDK，避免把 Web 或持久化校验注解带进 domain。
 */
public final class DomainValidation {

    private DomainValidation() {
    }

    public static String requirePrefixedId(String value, String prefix, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank() || !value.startsWith(prefix)) {
            throw new IllegalArgumentException(fieldName + " must start with " + prefix);
        }
        return value;
    }

    public static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public static Instant requireInstant(Instant value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }
}
