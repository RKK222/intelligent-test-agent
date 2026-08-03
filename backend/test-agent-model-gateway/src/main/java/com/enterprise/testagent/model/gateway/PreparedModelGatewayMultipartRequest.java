package com.enterprise.testagent.model.gateway;

import java.util.Objects;
import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;

/** 音频转写 multipart 请求的供应商快照及已重写表单。 */
public record PreparedModelGatewayMultipartRequest(
        String endpoint,
        String publicModelId,
        ResolvedModel resolvedModel,
        MultiValueMap<String, HttpEntity<?>> upstreamParts) {

    public PreparedModelGatewayMultipartRequest {
        endpoint = requireText(endpoint, "endpoint");
        publicModelId = requireText(publicModelId, "publicModelId");
        resolvedModel = Objects.requireNonNull(resolvedModel, "resolvedModel must not be null");
        upstreamParts = Objects.requireNonNull(upstreamParts, "upstreamParts must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
