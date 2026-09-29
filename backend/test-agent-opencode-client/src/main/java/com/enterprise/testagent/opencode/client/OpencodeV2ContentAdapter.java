package com.enterprise.testagent.opencode.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 V2 assistant.content 项投影成平台既有 Part DTO；历史读取和实时事件共用同一身份规则。
 */
final class OpencodeV2ContentAdapter {

    private OpencodeV2ContentAdapter() {}

    static Map<String, Object> project(Map<?, ?> source, String sessionId, String messageId, int ordinal) {
        LinkedHashMap<String, Object> part = copy(source);
        String id = text(part.get("id"));
        if (id == null) id = "part_" + (messageId == null ? "unknown" : messageId) + "_" + ordinal;
        part.put("id", id);
        part.put("partID", id);
        part.put("partId", id);
        if (sessionId != null) {
            part.put("sessionID", sessionId);
            part.put("sessionId", sessionId);
        }
        if (messageId != null) {
            part.put("messageID", messageId);
            part.put("messageId", messageId);
        }
        String type = text(part.get("type"));
        if ("reasoning".equals(type) && part.get("time") instanceof Map<?, ?> time) {
            LinkedHashMap<String, Object> normalizedTime = copy(time);
            if (time.containsKey("created")) normalizedTime.putIfAbsent("start", time.get("created"));
            if (time.containsKey("completed")) normalizedTime.putIfAbsent("end", time.get("completed"));
            part.put("time", normalizedTime);
        }
        if ("tool".equals(type)) projectTool(part, id);
        return Map.copyOf(part);
    }

    private static void projectTool(LinkedHashMap<String, Object> part, String id) {
        part.putIfAbsent("callID", id);
        part.putIfAbsent("callId", id);
        String name = text(part.get("name"));
        if (name != null) {
            part.putIfAbsent("tool", name);
            part.putIfAbsent("toolName", name);
        }
        if (!(part.get("state") instanceof Map<?, ?> sourceState)) return;
        LinkedHashMap<String, Object> state = copy(sourceState);
        if ("streaming".equals(state.get("status"))) state.put("status", "pending");
        Object content = state.get("content");
        if (content instanceof List<?> values) {
            List<String> lines = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Map<?, ?> item && "text".equals(item.get("type"))
                        && item.get("text") instanceof String line) lines.add(line);
            }
            if (!lines.isEmpty()) {
                String output = String.join("\n", lines);
                state.putIfAbsent("output", output);
                part.putIfAbsent("output", output);
            }
        }
        if (part.get("time") instanceof Map<?, ?> rawTime) {
            LinkedHashMap<String, Object> time = new LinkedHashMap<>();
            if (rawTime.containsKey("created")) time.put("start", rawTime.get("created"));
            if (rawTime.containsKey("completed")) time.put("end", rawTime.get("completed"));
            if (!time.isEmpty()) state.putIfAbsent("time", time);
        }
        part.put("state", state);
    }

    private static LinkedHashMap<String, Object> copy(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String field && value != null) result.put(field, value);
        });
        return result;
    }

    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }
}
