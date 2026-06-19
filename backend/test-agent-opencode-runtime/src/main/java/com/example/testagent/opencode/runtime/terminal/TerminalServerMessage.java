package com.example.testagent.opencode.runtime.terminal;

public record TerminalServerMessage(
        String type,
        String data,
        Integer seq,
        Integer exitCode,
        String errorCode,
        String message,
        Boolean truncated) {

    public static TerminalServerMessage output(String data, int seq) {
        return output(data, seq, false);
    }

    public static TerminalServerMessage output(String data, int seq, boolean truncated) {
        return new TerminalServerMessage("output", data, seq, null, null, null, truncated);
    }

    public static TerminalServerMessage exit(int code, int seq) {
        return new TerminalServerMessage("exit", null, seq, code, null, null, false);
    }

    public static TerminalServerMessage error(String code, String message) {
        return new TerminalServerMessage("error", null, null, null, code, message == null ? code : message, false);
    }

    public static TerminalServerMessage warning(String code, String message) {
        return new TerminalServerMessage("warning", null, null, null, code, message == null ? code : message, false);
    }
}
