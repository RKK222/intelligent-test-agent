package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import java.util.Objects;

/**
 * Run 编排使用的用户专属 opencode 进程运行目标。
 */
public record UserOpencodeProcessAssignment(
        ExecutionNode node,
        String linuxServerId,
        OpencodeServerProcess processSnapshot) {

    public UserOpencodeProcessAssignment(ExecutionNode node) {
        this(node, null, null);
    }

    public UserOpencodeProcessAssignment(ExecutionNode node, String linuxServerId) {
        this(node, linuxServerId, null);
    }

    public UserOpencodeProcessAssignment {
        Objects.requireNonNull(node, "node must not be null");
        linuxServerId = linuxServerId == null || linuxServerId.isBlank() ? null : linuxServerId.trim();
        if (processSnapshot != null) {
            if (!node.executionNodeId().value().equals("node_" + processSnapshot.processId().value())) {
                throw new IllegalArgumentException("node and process snapshot must match");
            }
            if (linuxServerId == null
                    || !linuxServerId.equals(processSnapshot.linuxServerId().value())) {
                throw new IllegalArgumentException("linux server and process snapshot must match");
            }
        }
    }
}
