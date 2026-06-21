package com.icbc.testagent.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * agent runtime JSON projection。
 */
public record AgentRuntimeResult(JsonNode body) {

    /**
     * body 不能为空，调用方会再包装为平台 ApiResponse。
     */
    public AgentRuntimeResult {
        Objects.requireNonNull(body, "body must not be null");
    }
}
