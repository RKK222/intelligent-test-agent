package com.example.testagent.app.config;

import com.example.testagent.app.web.TerminalWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

/**
 * Phase 11 P2 受控 PTY WebSocket 入口，只暴露 ticket 保护的 terminal/ws 路径。
 */
@Configuration
public class TerminalWebSocketConfig {

    @Bean
    HandlerMapping terminalWebSocketHandlerMapping(TerminalWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/api/sessions/*/terminal/ws", handler));
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
