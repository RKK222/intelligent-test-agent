package com.example.testagent.domain.workspace;

import com.example.testagent.domain.support.DomainValidation;

/**
 * Workspace 领域 ID，使用 wrk_ 前缀与前端和事件契约保持一致。
 */
public record WorkspaceId(String value) {

    /**
     * 校验工作区 ID 前缀。
     */
    public WorkspaceId {
        value = DomainValidation.requirePrefixedId(value, "wrk_", "workspaceId");
    }

    /**
     * 返回原始 workspaceId 字符串，便于日志、事件和持久化参数使用。
     */
    @Override
    public String toString() {
        return value;
    }
}
