package com.enterprise.testagent.opencode.runtime.terminal;

import java.time.Instant;

/**
 * PTY ticket 创建响应。ticket 只短期存在，前端不得持久化。
 */
public record TerminalTicketResponse(
        String ticket,
        Instant expiresAt,
        String webSocketUrl) {
}
