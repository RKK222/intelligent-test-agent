package com.enterprise.testagent.opencode.client;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * opencode prompt part 的稳定 facade 模型，避免 generated DTO 暴露到 app/domain 层。
 */
public record OpencodePromptPart(
        String type,
        String text,
        String url,
        String mime,
        String filename,
        String name,
        Map<String, Object> source,
        Map<String, Object> metadata) {

    /**
     * 校验 prompt part 类型专属字段，并把 source/metadata 固化为不可变 Map。
     */
    public OpencodePromptPart {
        type = DomainValidation.requireText(type, "type");
        source = immutableCopy(source);
        metadata = immutableCopy(metadata);
        if ("text".equals(type)) {
            text = DomainValidation.requireText(text, "text");
            url = null;
            mime = null;
            filename = optionalText(filename);
            name = null;
        } else if ("file".equals(type)) {
            url = DomainValidation.requireText(url, "url");
            mime = DomainValidation.requireText(mime, "mime");
            text = null;
            filename = optionalText(filename);
            name = null;
        } else if ("agent".equals(type)) {
            name = DomainValidation.requireText(name, "name");
            text = null;
            url = null;
            mime = null;
            filename = null;
        } else {
            throw new IllegalArgumentException("unsupported opencode prompt part type: " + type);
        }
    }

    /**
     * 创建文本 part，适用于普通用户 prompt。
     */
    public static OpencodePromptPart text(String text) {
        return new OpencodePromptPart("text", text, null, null, null, null, null, null);
    }

    /**
     * 创建文件 part，url/mime 必填，filename 和 source 用于前端选区上下文。
     */
    public static OpencodePromptPart file(String url, String mime, String filename, Map<String, Object> source) {
        return new OpencodePromptPart("file", null, url, mime, filename, null, source, null);
    }

    /**
     * 创建 agent 引用 part，用于 slash command 或 @agent 上下文。
     */
    public static OpencodePromptPart agent(String name, Map<String, Object> source) {
        return new OpencodePromptPart("agent", null, null, null, null, name, source, null);
    }

    /**
     * 转换为 prompt_async 请求体 Map，只输出当前 part 类型允许的字段。
     */
    Map<String, Object> toRequestBody() {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        if (text != null) {
            body.put("text", text);
        }
        if (mime != null) {
            body.put("mime", mime);
        }
        if (filename != null) {
            body.put("filename", filename);
        }
        if (url != null) {
            body.put("url", url);
        }
        if (name != null) {
            body.put("name", name);
        }
        if (!source.isEmpty()) {
            body.put("source", source);
        }
        if (!metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        return Map.copyOf(body);
    }

    /**
     * 固化调用方传入的上下文 Map，null 或空 Map 按空上下文处理。
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
