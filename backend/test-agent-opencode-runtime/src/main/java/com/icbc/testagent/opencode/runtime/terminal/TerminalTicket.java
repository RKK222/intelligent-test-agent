package com.icbc.testagent.opencode.runtime.terminal;

import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 已签发的 PTY ticket 内部状态，绑定平台 session/workspace、目录和 traceId。
 */
public record TerminalTicket(
        String ticket,
        SessionId sessionId,
        WorkspaceId workspaceId,
        ExecutionNodeId executionNodeId,
        Path workspaceRoot,
        Path cwd,
        String shell,
        int cols,
        int rows,
        String traceId,
        Instant expiresAt) {
}
