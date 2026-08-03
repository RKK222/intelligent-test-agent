package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.domain.configuration.ModelCapability;
import java.util.Objects;
import java.util.Set;

/** 请求体校验及模型解析后的不可变快照，确保读取和转发期间不会切换供应商或密钥。 */
public record PreparedModelGatewayRequest(
        String endpoint,
        String publicModelId,
        Set<ModelCapability> requiredCapabilities,
        ResolvedModel resolvedModel,
        byte[] upstreamBody) {

    public PreparedModelGatewayRequest {
        endpoint = requireText(endpoint, "endpoint");
        publicModelId = requireText(publicModelId, "publicModelId");
        requiredCapabilities = Set.copyOf(Objects.requireNonNull(requiredCapabilities));
        resolvedModel = Objects.requireNonNull(resolvedModel, "resolvedModel must not be null");
        upstreamBody = Objects.requireNonNull(upstreamBody, "upstreamBody must not be null").clone();
    }

    @Override
    public byte[] upstreamBody() {
        return upstreamBody.clone();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
