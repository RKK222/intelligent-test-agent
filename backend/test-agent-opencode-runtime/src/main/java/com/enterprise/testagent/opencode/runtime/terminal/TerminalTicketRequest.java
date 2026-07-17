package com.enterprise.testagent.opencode.runtime.terminal;

/**
 * PTY ticket 创建请求。workspaceId/cwd/shell 来自前端运行态，不进入 settings。
 */
public record TerminalTicketRequest(
        String workspaceId,
        String cwd,
        String shell,
        Integer cols,
        Integer rows) {
}
