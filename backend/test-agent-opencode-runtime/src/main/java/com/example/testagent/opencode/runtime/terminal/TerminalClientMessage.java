package com.example.testagent.opencode.runtime.terminal;

public record TerminalClientMessage(
        String type,
        String data,
        Integer cols,
        Integer rows,
        String reason) {
}
