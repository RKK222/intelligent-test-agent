package com.example.testagent.app.terminal;

public record TerminalServerMessage(
        String type,
        String data,
        Integer seq,
        Integer exitCode,
        String errorCode,
        String message) {

    public static TerminalServerMessage output(String data, int seq) {
        return new TerminalServerMessage("output", data, seq, null, null, null);
    }

    public static TerminalServerMessage exit(int code, int seq) {
        return new TerminalServerMessage("exit", null, seq, code, null, null);
    }

    public static TerminalServerMessage error(String code, String message) {
        return new TerminalServerMessage("error", null, null, null, code, message == null ? code : message);
    }
}
