package com.enterprise.testagent.integration.toolbox;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 锁定上游版本后生成的完整离线工具目录。 */
public record ToolboxCatalog(String catalogVersion, List<ToolboxToolDefinition> tools) {

    /** 校验目录版本、稳定 ID 和启动路径唯一性。 */
    public ToolboxCatalog {
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new IllegalArgumentException("catalogVersion must not be blank");
        }
        tools = List.copyOf(Objects.requireNonNull(tools, "tools must not be null"));
        Set<String> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (ToolboxToolDefinition tool : tools) {
            if (!ids.add(tool.toolId())) {
                throw new IllegalArgumentException("duplicate toolId: " + tool.toolId());
            }
            if (!paths.add(tool.launchPath())) {
                throw new IllegalArgumentException("duplicate launchPath: " + tool.launchPath());
            }
        }
    }
}
