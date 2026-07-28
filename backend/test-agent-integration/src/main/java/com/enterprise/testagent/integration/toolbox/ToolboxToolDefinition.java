package com.enterprise.testagent.integration.toolbox;

import java.util.List;
import java.util.Objects;

/** 版本化离线目录中的不可变工具定义。 */
public record ToolboxToolDefinition(
        String toolId,
        ToolboxSource source,
        String sourceVersion,
        String nameZh,
        String nameEn,
        String descriptionZh,
        ToolboxCategory category,
        List<String> keywords,
        String launchPath,
        int catalogOrder) {

    /** 校验目录条目必须可以离线展示并只能启动同源工具路径。 */
    public ToolboxToolDefinition {
        requireText(toolId, "toolId");
        Objects.requireNonNull(source, "source must not be null");
        requireText(sourceVersion, "sourceVersion");
        requireText(nameZh, "nameZh");
        requireText(nameEn, "nameEn");
        requireText(descriptionZh, "descriptionZh");
        Objects.requireNonNull(category, "category must not be null");
        keywords = keywords == null ? List.of() : keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .distinct()
                .toList();
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("keywords must not be empty");
        }
        requireText(launchPath, "launchPath");
        if (!launchPath.startsWith("/toolbox/apps/")) {
            throw new IllegalArgumentException("launchPath must use toolbox same-origin prefix");
        }
        if (catalogOrder < 0) {
            throw new IllegalArgumentException("catalogOrder must not be negative");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
