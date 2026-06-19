package com.example.testagent.app.terminal;

public record TerminalClientMessage(
        String type,
        String data,
        Integer cols,
        Integer rows,
        String reason) {
}
