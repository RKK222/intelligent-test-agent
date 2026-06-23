package com.icbc.testagent.domain.configuration;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 应用工作空间配置 ID，使用 awp_ 前缀，与运行态 Workspace 区分。
 */
public record ApplicationWorkspaceId(String value) {

    public ApplicationWorkspaceId {
        value = DomainValidation.requirePrefixedId(value, "awp_", "applicationWorkspaceId");
    }

    @Override
    public String toString() {
        return value;
    }
}
