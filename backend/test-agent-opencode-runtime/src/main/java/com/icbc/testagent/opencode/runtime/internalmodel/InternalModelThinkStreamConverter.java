package com.icbc.testagent.opencode.runtime.internalmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/**
 * 企业内部模型把思考内容混在 delta.content 中，本转换器按 SSE chunk 顺序迁移到 reasoning_content。
 */
public class InternalModelThinkStreamConverter {

    private static final String SSE_DATA_PREFIX = "data:";
    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";

    private final ObjectMapper objectMapper;
    private boolean inThink;
    private String pendingTagPrefix = "";

    public InternalModelThinkStreamConverter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 转换已经由 SSE 解码器提取出的 data 内容；无法解析为 OpenAI chunk 时保持原样。
     */
    public String convertData(String payload) {
        if (payload == null || payload.isBlank() || "[DONE]".equals(payload.trim())) {
            return payload;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!(root instanceof ObjectNode objectRoot) || !root.path("choices").isArray()) {
                return payload;
            }
            for (JsonNode choice : root.path("choices")) {
                JsonNode delta = choice.path("delta");
                if (!(delta instanceof ObjectNode deltaObject)) {
                    continue;
                }
                JsonNode content = deltaObject.get("content");
                if (content == null || !content.isTextual()) {
                    continue;
                }
                ConvertedText converted = convertText(content.asText());
                if (converted.content().isEmpty()) {
                    deltaObject.remove("content");
                } else {
                    deltaObject.put("content", converted.content());
                }
                if (!converted.reasoningContent().isEmpty()
                        && (deltaObject.get("reasoning_content") == null
                        || !deltaObject.get("reasoning_content").isTextual())) {
                    deltaObject.put("reasoning_content", converted.reasoningContent());
                }
            }
            return objectMapper.writeValueAsString(objectRoot);
        } catch (Exception exception) {
            return payload;
        }
    }

    /**
     * 兼容仍以原始 SSE data 行调用的测试和旧内部调用方。
     */
    public String convertLine(String line) {
        if (line == null || !line.startsWith(SSE_DATA_PREFIX)) {
            return line;
        }
        String payload = line.substring(SSE_DATA_PREFIX.length());
        return SSE_DATA_PREFIX + convertData(payload);
    }

    private ConvertedText convertText(String text) {
        String input = pendingTagPrefix + text;
        pendingTagPrefix = "";
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        int index = 0;
        while (index < input.length()) {
            String tag = inThink ? THINK_CLOSE : THINK_OPEN;
            int tagIndex = input.indexOf(tag, index);
            if (tagIndex >= 0) {
                append(input, index, tagIndex, content, reasoning);
                index = tagIndex + tag.length();
                inThink = !inThink;
                continue;
            }
            int safeEnd = safeEndBeforePossibleTag(input, index, tag);
            append(input, index, safeEnd, content, reasoning);
            pendingTagPrefix = input.substring(safeEnd);
            index = input.length();
        }
        return new ConvertedText(content.toString(), reasoning.toString());
    }

    private void append(String input, int from, int to, StringBuilder content, StringBuilder reasoning) {
        if (to <= from) {
            return;
        }
        if (inThink) {
            reasoning.append(input, from, to);
        } else {
            content.append(input, from, to);
        }
    }

    /**
     * 结尾可能是下一行 tag 的前缀时暂存，避免跨 chunk 标签被当成普通文本输出。
     */
    private int safeEndBeforePossibleTag(String input, int index, String tag) {
        int earliest = input.length();
        int maxPrefixLength = Math.min(tag.length() - 1, input.length() - index);
        for (int length = 1; length <= maxPrefixLength; length++) {
            int start = input.length() - length;
            if (start >= index && tag.startsWith(input.substring(start))) {
                earliest = Math.min(earliest, start);
            }
        }
        return earliest;
    }

    private record ConvertedText(String content, String reasoningContent) {
    }
}
