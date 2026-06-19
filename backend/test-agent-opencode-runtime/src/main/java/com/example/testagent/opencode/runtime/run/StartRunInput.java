package com.example.testagent.opencode.runtime.run;

import com.example.testagent.domain.session.SessionId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Run 启动应用层输入，隔离 HTTP DTO，并保留 Phase 11 prompt parts 与运行态选择。
 */
public record StartRunInput(
        SessionId sessionId,
        String prompt,
        List<PromptPart> parts,
        String messageId,
        String agent,
        String model,
        String variant,
        String mode) {

    public StartRunInput {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        prompt = optionalText(prompt);
        parts = parts == null ? List.of() : List.copyOf(parts);
        messageId = optionalText(messageId);
        agent = optionalText(agent);
        model = optionalText(model);
        variant = optionalText(variant);
        mode = optionalText(mode);
    }

    public static StartRunInput ofPrompt(SessionId sessionId, String prompt) {
        return new StartRunInput(sessionId, prompt, List.of(PromptPart.text(prompt)), null, null, null, null, null);
    }

    public String effectivePrompt() {
        if (prompt != null && !prompt.isBlank()) {
            return prompt;
        }
        return parts.stream()
                .filter(part -> "text".equals(part.type()))
                .map(PromptPart::text)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    public record PromptPart(
            String type,
            String text,
            String path,
            String name,
            String mimeType,
            String content,
            String url,
            String agentId,
            String id,
            String label,
            String uri,
            Map<String, Object> source,
            Map<String, Object> metadata) {

        public PromptPart {
            type = optionalText(type);
            text = optionalText(text);
            path = optionalText(path);
            name = optionalText(name);
            mimeType = optionalText(mimeType);
            content = optionalText(content);
            url = optionalText(url);
            agentId = optionalText(agentId);
            id = optionalText(id);
            label = optionalText(label);
            uri = optionalText(uri);
            source = immutableCopy(source);
            metadata = immutableCopy(metadata);
        }

        public static PromptPart text(String text) {
            return new PromptPart("text", text, null, null, null, null, null, null, null, null, null, null, null);
        }

        public static PromptPart file(
                String path,
                String name,
                String mimeType,
                String url,
                String content,
                Map<String, Object> metadata) {
            return new PromptPart("file", null, path, name, mimeType, content, url, null, null, null, null, null, metadata);
        }

        public static PromptPart agent(String agentId, String name) {
            return new PromptPart("agent", null, null, name, null, null, null, agentId, null, null, null, null, null);
        }

        public static PromptPart reference(String id, String label, String uri, Map<String, Object> metadata) {
            return new PromptPart("reference", null, null, null, null, null, null, null, id, label, uri, null, metadata);
        }
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(value));
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
