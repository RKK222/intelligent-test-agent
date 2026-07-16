package com.enterprise.testagent.domain.managedworkspace;

import java.util.Objects;

/**
 * 应用版本工作区在某台 Linux 服务器上的副本业务 ID。
 */
public record ApplicationWorkspaceVersionReplicaId(String value) {

    public ApplicationWorkspaceVersionReplicaId {
        Objects.requireNonNull(value, "replicaId must not be null");
        if (!value.startsWith("awr_")) {
            throw new IllegalArgumentException("replicaId must start with awr_");
        }
    }
}
