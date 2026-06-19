package com.example.testagent.opencode.runtime.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * PTY WebSocket JSON envelope 编解码，避免 handler 中散落字符串拼接。
 */
@Component
public class TerminalMessageCodec {

    private final ObjectMapper objectMapper;

    public TerminalMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TerminalClientMessage decode(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            return new TerminalClientMessage(
                    text(node, "type"),
                    text(node, "data"),
                    number(node, "cols"),
                    number(node, "rows"),
                    text(node, "reason"));
        } catch (Exception exception) {
            return new TerminalClientMessage("error", null, null, null, "invalid-json");
        }
    }

    public String encode(TerminalServerMessage message) {
        try {
            var node = objectMapper.createObjectNode();
            node.put("type", message.type());
            if (message.data() != null) {
                node.put("data", message.data());
            }
            if (message.seq() != null) {
                node.put("seq", message.seq());
            }
            if (Boolean.TRUE.equals(message.truncated())) {
                node.put("truncated", true);
            }
            if ("exit".equals(message.type()) && message.exitCode() != null) {
                node.put("code", message.exitCode());
            }
            if ("error".equals(message.type()) || "warning".equals(message.type())) {
                node.put("code", message.errorCode());
                node.put("message", message.message());
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{\"type\":\"error\",\"code\":\"PTY_ENCODE_FAILED\",\"message\":\"encode failed\"}";
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private Integer number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isInt() ? value.asInt() : null;
    }
}
