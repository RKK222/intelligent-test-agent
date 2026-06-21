package com.icbc.testagent.opencode.runtime.terminal;

/**
 * 服务端发往浏览器的 PTY WebSocket 消息 envelope。
 */
public record TerminalServerMessage(
        String type,
        String data,
        Integer seq,
        Integer exitCode,
        String errorCode,
        String message,
        Boolean truncated) {

    /**
     * 构造未截断输出帧。
     */
    public static TerminalServerMessage output(String data, int seq) {
        return output(data, seq, false);
    }

    /**
     * 构造输出帧，truncated 表示本帧内容已按预算截断。
     */
    public static TerminalServerMessage output(String data, int seq, boolean truncated) {
        return new TerminalServerMessage("output", data, seq, null, null, null, truncated);
    }

    /**
     * 构造进程退出帧。
     */
    public static TerminalServerMessage exit(int code, int seq) {
        return new TerminalServerMessage("exit", null, seq, code, null, null, false);
    }

    /**
     * 构造错误帧，message 缺失时使用 code 作为安全说明。
     */
    public static TerminalServerMessage error(String code, String message) {
        return new TerminalServerMessage("error", null, null, null, code, message == null ? code : message, false);
    }

    /**
     * 构造告警帧，通常用于输出截断等非致命状态。
     */
    public static TerminalServerMessage warning(String code, String message) {
        return new TerminalServerMessage("warning", null, null, null, code, message == null ? code : message, false);
    }
}
