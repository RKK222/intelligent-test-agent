package com.example.testagent.domain.support;

import java.time.Instant;
import java.util.Objects;

/**
 * 领域模型的轻量校验工具，只依赖 JDK，避免把 Web 或持久化校验注解带进 domain。
 */
public final class DomainValidation {

    /**
     * 工具类不允许实例化，领域校验统一通过静态方法使用。
     */
    private DomainValidation() {
    }

    /**
     * 校验领域 ID 的前缀和非空语义；返回原始值，避免值对象构造器重复拼接或修改 ID。
     */
    public static String requirePrefixedId(String value, String prefix, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank() || !value.startsWith(prefix)) {
            throw new IllegalArgumentException(fieldName + " must start with " + prefix);
        }
        return value;
    }

    /**
     * 校验必填文本字段；允许保留调用方传入的首尾空白，只拒绝 null 和纯空白值。
     */
    public static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * 校验领域时间字段存在；时间顺序由各聚合根根据业务语义继续判断。
     */
    public static Instant requireInstant(Instant value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }
}
