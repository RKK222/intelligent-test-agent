package com.enterprise.testagent.opencode.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 V2 Form.Info 投影为平台沿用的 Question 请求，并把旧 answers 列表还原为字段 key 的回复。
 * 业务层只接触平台 Map，不依赖 V2 generated DTO。
 */
public final class OpencodeV2FormAdapter {

    private OpencodeV2FormAdapter() {}

    /** V2 字段 key 是回复的权威身份，questionId 只作为平台兼容别名。 */
    public static Map<String, Object> toQuestion(Map<?, ?> form) {
        LinkedHashMap<String, Object> question = new LinkedHashMap<>();
        String id = text(form.get("id"));
        if (id != null) {
            question.put("id", id);
            question.put("requestId", id);
        }
        String sessionId = text(form.get("sessionID"));
        if (sessionId != null) question.put("sessionID", sessionId);
        List<Map<String, Object>> fields = fields(form);
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            String key = text(field.get("key"));
            if (key == null) continue;
            item.put("questionId", key);
            String title = text(field.get("title"));
            String description = text(field.get("description"));
            item.put("text", title == null ? (description == null ? key : description) : title);
            if (description != null && title != null) item.put("description", description);
            String formTitle = text(form.get("title"));
            if (formTitle != null) item.put("header", formTitle);
            String type = text(field.get("type"));
            Object options = field.get("options");
            item.put("kind", "multiselect".equals(type) ? "multiple"
                    : options instanceof List<?> list && !list.isEmpty() ? "single" : "text");
            if (options instanceof List<?>) item.put("options", options);
            if (field.get("custom") instanceof Boolean custom) item.put("custom", custom);
            if (field.get("required") instanceof Boolean required) item.put("required", required);
            questions.add(item);
        }
        question.put("questions", questions);
        return question;
    }

    /** 原生 answer map 直接透传；旧 answers 数组按 Form.Fields 的 key 和类型逐项转换。 */
    public static Map<String, Object> toReply(Map<String, Object> body, Map<?, ?> form) {
        Object direct = body.get("answer");
        if (direct instanceof Map<?, ?>) return Map.of("answer", direct);
        LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
        if (!(body.get("answers") instanceof List<?> answers)) return Map.of("answer", answer);
        List<Map<String, Object>> fields = fields(form);
        if (fields.isEmpty()) {
            // 兼容历史模拟数据；真实 V2 Form.Info 总包含至少一个有 key 的字段。
            for (int i = 0; i < answers.size(); i++) answer.put(Integer.toString(i), answers.get(i));
            return Map.of("answer", answer);
        }
        boolean oneField = fields.size() == 1;
        for (int i = 0; i < fields.size() && (oneField || i < answers.size()); i++) {
            Map<String, Object> field = fields.get(i);
            String key = text(field.get("key"));
            if (key == null) continue;
            Object raw = oneField && answers.size() > 1 ? answers : answers.isEmpty() ? null : answers.get(i);
            Object value = replyValue(raw, text(field.get("type")));
            if (value != null) answer.put(key, value);
        }
        return Map.of("answer", answer);
    }

    private static Object replyValue(Object raw, String type) {
        if (raw == null) return null;
        if ("multiselect".equals(type)) {
            return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(raw));
        }
        Object value = raw instanceof List<?> list ? (list.isEmpty() ? null : list.getFirst()) : raw;
        if (value == null) return null;
        if ("boolean".equals(type)) return value instanceof Boolean ? value : Boolean.parseBoolean(String.valueOf(value));
        if ("integer".equals(type)) {
            try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ignored) { return value; }
        }
        if ("number".equals(type)) {
            try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ignored) { return value; }
        }
        return String.valueOf(value);
    }

    private static List<Map<String, Object>> fields(Map<?, ?> form) {
        if (!(form.get("fields") instanceof List<?> rawFields)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : rawFields) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            LinkedHashMap<String, Object> field = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key instanceof String name && value != null) field.put(name, value);
            });
            result.add(field);
        }
        return result;
    }

    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }
}
