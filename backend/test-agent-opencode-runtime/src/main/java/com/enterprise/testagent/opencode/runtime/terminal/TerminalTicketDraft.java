package com.enterprise.testagent.opencode.runtime.terminal;

import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;

/**
 * ticket 签发前的内部草稿，包含已校验的 session/workspace/cwd/shell 信息。
 */
record TerminalTicketDraft(
        SessionId sessionId,
        WorkspaceId workspaceId,
        ExecutionNodeId executionNodeId,
        LinuxServerId linuxServerId,
        UserId userId,
        Path workspaceRoot,
        Path cwd,
        String shell,
        int cols,
        int rows,
        String traceId) {

    TerminalTicketDraft(
            SessionId sessionId,
            WorkspaceId workspaceId,
            ExecutionNodeId executionNodeId,
            Path workspaceRoot,
            Path cwd,
            String shell,
            int cols,
            int rows,
            String traceId) {
        this(sessionId, workspaceId, executionNodeId, null, null, workspaceRoot, cwd, shell, cols, rows, traceId);
    }

    static TerminalTicketDraft serverShell(
            LinuxServerId linuxServerId,
            UserId userId,
            Path cwd,
            int cols,
            int rows,
            String traceId) {
        return new TerminalTicketDraft(null, null, null, linuxServerId, userId, cwd, cwd,
                "/bin/bash", cols, rows, traceId);
    }
}
