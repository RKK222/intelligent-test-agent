package com.enterprise.testagent.opencode.runtime.runtime;

import java.util.List;
import java.util.Map;

/**
 * 从 opencode 同步消息响应或 projected messages 中提取可展示的旁路问答自然语言答案。
 */
public final class SideQuestionAnswerExtractor {

    /**
     * 提取最终答案；纯工具协议、空响应或没有 assistant 文本时返回 {@code null}。
     */
    public String extract(Object response) {
        if (response instanceof Iterable<?> messages) {
            return extractLastAssistantText(messages);
        }
        if (!(response instanceof Map<?, ?> map)) {
            return sanitize(text(response));
        }
        String partsText = extractPartsText(map.get("parts"));
        if (partsText != null) {
            return sanitize(partsText);
        }
        for (String key : List.of("answer", "content", "text")) {
            String value = text(map.get(key));
            if (value != null) {
                return sanitize(value);
            }
        }
        for (String key : List.of("data", "messages", "info")) {
            String nested = extract(map.get(key));
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * projected messages 可能同时包含 user、reasoning 和多轮 assistant，只采用最后一条 assistant 的 text part。
     */
    private String extractLastAssistantText(Iterable<?> messages) {
        String answer = null;
        for (Object item : messages) {
            if (!(item instanceof Map<?, ?> message)) {
                continue;
            }
            Map<?, ?> info = message.get("info") instanceof Map<?, ?> value ? value : message;
            if (!"assistant".equals(text(info.get("role")))) {
                continue;
            }
            String candidate = sanitize(extractPartsText(message.get("parts")));
            // fork 会带入历史 assistant；最后一条若没有自然语言，必须保持空而不能回退到历史答案。
            answer = candidate;
        }
        return answer;
    }

    /**
     * 过滤模型把工具调用协议伪装成普通文本的情况；只有协议之外的自然语言可以进入宠物气泡。
     */
    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String remaining = value;
        StringBuilder result = new StringBuilder();
        while (true) {
            int start = remaining.indexOf("<tool_calls");
            if (start < 0) {
                result.append(remaining);
                break;
            }
            result.append(remaining, 0, start);
            int closingStart = remaining.indexOf("</tool_calls", start);
            if (closingStart < 0) {
                break;
            }
            int closingEnd = remaining.indexOf('>', closingStart);
            if (closingEnd < 0) {
                break;
            }
            remaining = remaining.substring(closingEnd + 1);
        }
        String normalized = result.toString().trim();
        return normalized.isEmpty() || normalized.contains("<tool_call") ? null : normalized;
    }

    private String extractPartsText(Object value) {
        if (!(value instanceof Iterable<?> parts)) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Object item : parts) {
            if (!(item instanceof Map<?, ?> part) || !"text".equals(text(part.get("type")))) {
                continue;
            }
            String valueText = text(part.get("text"));
            if (valueText == null) {
                valueText = text(part.get("content"));
            }
            if (valueText != null) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(valueText);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
