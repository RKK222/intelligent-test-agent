package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.node.ExecutionNode;
import java.util.Objects;

/**
 * Run 编排使用的用户专属 opencode 进程运行目标。
 */
public record UserOpencodeProcessAssignment(ExecutionNode node, String linuxServerId) {

    public UserOpencodeProcessAssignment(ExecutionNode node) {
        this(node, null);
    }

    public UserOpencodeProcessAssignment {
        Objects.requireNonNull(node, "node must not be null");
        linuxServerId = linuxServerId == null || linuxServerId.isBlank() ? null : linuxServerId.trim();
    }
}
