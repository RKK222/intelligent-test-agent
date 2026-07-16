package com.enterprise.testagent.domain.workspace;

import java.util.Objects;

/**
 * 当前节点解析出的可信 Workspace，以及本次解析是否主动修正了持久化 server/root 绑定。
 */
public record TrustedWorkspaceResolution(Workspace workspace, boolean bindingChanged) {

    public TrustedWorkspaceResolution {
        Objects.requireNonNull(workspace, "workspace must not be null");
    }
}
