package com.example.testagent.api.config;

import com.example.testagent.api.web.platform.TerminalWebSocketHandler;
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

    /**
     * 注册 ticket 保护的 terminal WebSocket 路径，兼容旧 URL 和 internal platform URL。
     */
    @Bean
    HandlerMapping terminalWebSocketHandlerMapping(TerminalWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(
                "/api/sessions/*/terminal/ws", handler,
                "/api/internal/platform/opencode-runtime/sessions/*/terminal/ws", handler));
        mapping.setOrder(-1);
        return mapping;
    }

    /**
     * 提供 WebFlux WebSocket handler adapter。
     */
    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
