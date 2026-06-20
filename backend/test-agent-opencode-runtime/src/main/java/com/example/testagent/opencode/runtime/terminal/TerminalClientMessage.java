package com.example.testagent.opencode.runtime.terminal;

/**
 * 浏览器发往 PTY WebSocket 的客户端消息 envelope。
 */
public record TerminalClientMessage(
        String type,
        String data,
        Integer cols,
        Integer rows,
        String reason) {
}
