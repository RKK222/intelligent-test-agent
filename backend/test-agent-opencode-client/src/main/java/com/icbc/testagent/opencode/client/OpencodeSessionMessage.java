package com.icbc.testagent.opencode.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * opencode session message 的平台侧投影 DTO，避免 generated SDK DTO 穿透到业务模块。
 */
public record OpencodeSessionMessage(
        Map<String, Object> message,
        List<Map<String, Object>> parts) {

    /**
     * 固化 message 与 part 投影，过滤 generated DTO 转换过程中产生的 null 字段。
     */
    public OpencodeSessionMessage {
        message = immutableWithoutNulls(message);
        parts = parts == null ? List.of() : parts.stream()
                .map(OpencodeSessionMessage::immutableWithoutNulls)
                .toList();
    }

    /**
     * 复制 Map 并过滤 null 键值，防止空字段进入上层消息恢复逻辑。
     */
    private static Map<String, Object> immutableWithoutNulls(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
    }
}
