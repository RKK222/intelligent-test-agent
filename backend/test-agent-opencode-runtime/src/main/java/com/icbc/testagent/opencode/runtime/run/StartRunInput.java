package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.session.SessionId;
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
        String mode,
        String command,
        String arguments) {

    /**
     * 规范化 Run 启动输入，保留非空可选字段并固化 prompt parts。
     */
    public StartRunInput {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        prompt = optionalText(prompt);
        parts = parts == null ? List.of() : List.copyOf(parts);
        messageId = optionalText(messageId);
        agent = optionalText(agent);
        model = optionalText(model);
        variant = optionalText(variant);
        mode = optionalText(mode);
        command = optionalText(command);
        arguments = optionalText(arguments);
    }

    /**
     * 保留旧调用方的八参数构造方式，新增命令字段默认缺失。
     */
    public StartRunInput(
            SessionId sessionId,
            String prompt,
            List<PromptPart> parts,
            String messageId,
            String agent,
            String model,
            String variant,
            String mode) {
        this(sessionId, prompt, parts, messageId, agent, model, variant, mode, null, null);
    }

    /**
     * 创建兼容旧 API 的纯文本启动输入。
     */
    public static StartRunInput ofPrompt(SessionId sessionId, String prompt) {
        return new StartRunInput(sessionId, prompt, List.of(PromptPart.text(prompt)), null, null, null, null, null, null, null);
    }

    /**
     * 返回发送给 opencode 的有效 prompt；缺少顶层 prompt 时拼接所有 text part。
     */
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

        /**
         * 规范化 prompt part 字段，source/metadata 固化为不可变 Map。
         */
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

        /**
         * 创建文本 prompt part。
         */
        public static PromptPart text(String text) {
            return new PromptPart("text", text, null, null, null, null, null, null, null, null, null, null, null);
        }

        /**
         * 创建文件 prompt part，可携带路径、URL、内联内容和 UI metadata。
         */
        public static PromptPart file(
                String path,
                String name,
                String mimeType,
                String url,
                String content,
                Map<String, Object> metadata) {
            return new PromptPart("file", null, path, name, mimeType, content, url, null, null, null, null, null, metadata);
        }

        /**
         * 创建 agent prompt part，agentId 用于运行态选择，name 用于展示。
         */
        public static PromptPart agent(String agentId, String name) {
            return new PromptPart("agent", null, null, name, null, null, null, agentId, null, null, null, null, null);
        }

        /**
         * 创建 reference prompt part，runtime 层会降级为文本提示。
         */
        public static PromptPart reference(String id, String label, String uri, Map<String, Object> metadata) {
            return new PromptPart("reference", null, null, null, null, null, null, null, id, label, uri, null, metadata);
        }
    }

    /**
     * 固化 Map 字段，null 或空 Map 按空上下文处理。
     */
    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(value));
    }

    /**
     * 规范化可选文本，空白字符串按缺失处理。
     */
    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
