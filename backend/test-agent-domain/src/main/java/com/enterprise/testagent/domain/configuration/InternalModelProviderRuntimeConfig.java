package com.enterprise.testagent.domain.configuration;

import java.util.Objects;

/**
 * 内部模型供应商运行配置。Token 只允许进入 JVM 运行快照，不参与 API 序列化或日志输出。
 */
public final class InternalModelProviderRuntimeConfig {

    private final InternalModelProvider provider;
    private final String authToken;

    public InternalModelProviderRuntimeConfig(InternalModelProvider provider, String authToken) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.authToken = authToken;
    }

    public InternalModelProvider provider() {
        return provider;
    }

    public String authToken() {
        return authToken;
    }

    @Override
    public String toString() {
        return "InternalModelProviderRuntimeConfig[providerId=" + provider.providerId() + ", authToken=***]";
    }
}
