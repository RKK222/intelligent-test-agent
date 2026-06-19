package com.example.testagent.opencode.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * opencode runtime JSON projection，调用方必须在 app 层再包装为平台 ApiResponse。
 */
public record OpencodeRuntimeResult(JsonNode body) {

    public OpencodeRuntimeResult {
        Objects.requireNonNull(body, "body must not be null");
    }
}
