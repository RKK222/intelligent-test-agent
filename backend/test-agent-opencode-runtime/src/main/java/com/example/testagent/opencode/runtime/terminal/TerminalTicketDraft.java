package com.example.testagent.opencode.runtime.terminal;

import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;

record TerminalTicketDraft(
        SessionId sessionId,
        WorkspaceId workspaceId,
        ExecutionNodeId executionNodeId,
        Path workspaceRoot,
        Path cwd,
        String shell,
        int cols,
        int rows,
        String traceId) {
}
