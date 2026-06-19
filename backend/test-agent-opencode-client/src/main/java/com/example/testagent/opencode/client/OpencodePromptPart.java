package com.example.testagent.opencode.client;

import com.example.testagent.domain.support.DomainValidation;
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

    public static OpencodePromptPart text(String text) {
        return new OpencodePromptPart("text", text, null, null, null, null, null, null);
    }

    public static OpencodePromptPart file(String url, String mime, String filename, Map<String, Object> source) {
        return new OpencodePromptPart("file", null, url, mime, filename, null, source, null);
    }

    public static OpencodePromptPart agent(String name, Map<String, Object> source) {
        return new OpencodePromptPart("agent", null, null, null, null, name, source, null);
    }

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
