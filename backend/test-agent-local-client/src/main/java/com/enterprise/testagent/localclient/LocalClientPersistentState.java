package com.enterprise.testagent.localclient;

import java.time.Instant;
import java.util.Map;

/** 客户端原子持久化状态，不包含 client key 或模型 grant。 */
record LocalClientPersistentState(
        String clientInstanceId,
        ProcessState process,
        Map<String, WorkspaceRoot> workspaces) {

    LocalClientPersistentState {
        workspaces = workspaces == null ? Map.of() : Map.copyOf(workspaces);
    }

    record ProcessState(long processId, Instant startedAt, String executable, int port) {
    }

    record WorkspaceRoot(String normalizedRootPath, String rootDigest, String fileSystemIdentity) {
    }
}
