package com.enterprise.testagent.opencode.runtime.support;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 递归投影体验工作区的物理根路径，避免 OpenCode 响应、消息快照和错误详情泄露服务器目录。
 */
public final class ExperienceWorkspacePathRedactor {

    public static final String LOGICAL_ROOT = "<experience-workspace>";

    private ExperienceWorkspacePathRedactor() {
    }

    /** Map 的 key/value、集合与数组都必须处理，文件路径可能出现在任意 JSON 位置。 */
    public static Object redact(Object value, String directory) {
        if (value instanceof String text) {
            return redactText(text, directory);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> redacted = new LinkedHashMap<>();
            map.forEach((key, item) -> redacted.put(
                    key instanceof String text ? redactText(text, directory) : key,
                    redact(item, directory)));
            return redacted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> redact(item, directory)).toList();
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> redacted = new ArrayList<>(Array.getLength(value));
            for (int index = 0; index < Array.getLength(value); index++) {
                redacted.add(redact(Array.get(value, index), directory));
            }
            return redacted;
        }
        return value;
    }

    /** 同时兼容 Unix 与 Windows 分隔符投影，响应中只保留统一逻辑占位。 */
    public static String redactText(String text, String directory) {
        if (text == null || directory == null || directory.isBlank()) {
            return text;
        }
        String redacted = text.replace(directory, LOGICAL_ROOT);
        String slashDirectory = directory.replace('\\', '/');
        if (!slashDirectory.equals(directory)) {
            redacted = redacted.replace(slashDirectory, LOGICAL_ROOT);
        }
        String backslashDirectory = directory.replace('/', '\\');
        if (!backslashDirectory.equals(directory)) {
            redacted = redacted.replace(backslashDirectory, LOGICAL_ROOT);
        }
        return redacted;
    }
}
