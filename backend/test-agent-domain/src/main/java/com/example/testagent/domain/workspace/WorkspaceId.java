package com.example.testagent.domain.workspace;

import com.example.testagent.domain.support.DomainValidation;

/**
 * Workspace 领域 ID，使用 wrk_ 前缀与前端和事件契约保持一致。
 */
public record WorkspaceId(String value) {

    public WorkspaceId {
        value = DomainValidation.requirePrefixedId(value, "wrk_", "workspaceId");
    }

    @Override
    public String toString() {
        return value;
    }
}
