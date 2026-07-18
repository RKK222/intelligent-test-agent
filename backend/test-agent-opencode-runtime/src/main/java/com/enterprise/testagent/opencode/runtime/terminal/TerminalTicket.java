package com.enterprise.testagent.opencode.runtime.terminal;

import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 已签发的 PTY ticket 内部状态。Workspace 与服务器 root 终端复用同一种一次性票据。
 */
public record TerminalTicket(
        String ticket,
        String targetType,
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
        String traceId,
        Instant expiresAt) {

    public static final String TARGET_WORKSPACE = "workspace";
    public static final String TARGET_SERVER_ROOT = "server-root";

    /** 保留原 workspace ticket 构造方式，避免既有调用方感知目标泛化。 */
    public TerminalTicket(
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
        this(ticket, TARGET_WORKSPACE, sessionId, workspaceId, executionNodeId, null, null,
                workspaceRoot, cwd, shell, cols, rows, traceId, expiresAt);
    }

    /** 判断是否为高危服务器 root 终端。 */
    public boolean serverRoot() {
        return TARGET_SERVER_ROOT.equals(targetType);
    }

    /** 生成 JVM 内 active 租约键，隔离 workspace session 与服务器 root 终端。 */
    public String activeKey() {
        return serverRoot()
                ? "server-root:" + linuxServerId.value() + ":" + userId.value()
                : "workspace:" + sessionId.value();
    }

    /** 返回不会包含命令或输出正文的审计目标 ID。 */
    public String auditTargetId() {
        return serverRoot() ? linuxServerId.value() : sessionId.value();
    }
}
