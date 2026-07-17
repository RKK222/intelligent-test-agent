package com.enterprise.testagent.api.config;

import com.enterprise.testagent.api.web.platform.TerminalWebSocketHandler;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import reactor.netty.http.server.WebsocketServerSpec;

/**
 * Phase 11 P2 受控 PTY WebSocket 入口，只暴露 ticket 保护的 terminal/ws 路径。
 */
@Configuration
public class TerminalWebSocketConfig {

    /**
     * 注册 ticket 保护的 terminal WebSocket 路径，只保留 internal platform URL。
     */
    @Bean
    HandlerMapping terminalWebSocketHandlerMapping(TerminalWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/api/internal/platform/opencode-runtime/sessions/*/terminal/ws", handler));
        mapping.setOrder(-1);
        return mapping;
    }

    /**
     * 提供 WebFlux WebSocket handler adapter。
     */
    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter(
            @Value("${test-agent.files.max-file-bytes:1048576}") long maxFileBytes) {
        // 文件上传使用 Base64，单帧需容纳 4/3 膨胀后的文件内容和 RPC envelope；
        // 业务处理层仍按 max-file-bytes 校验解码后大小，避免仅依赖传输层上限。
        long expectedFrameBytes = 4L * ((maxFileBytes + 2L) / 3L) + 64L * 1024L;
        int maxFramePayloadLength = (int) Math.min(Integer.MAX_VALUE, expectedFrameBytes);
        ReactorNettyRequestUpgradeStrategy strategy = new ReactorNettyRequestUpgradeStrategy(
                () -> WebsocketServerSpec.builder().maxFramePayloadLength(maxFramePayloadLength));
        return new WebSocketHandlerAdapter(new HandshakeWebSocketService(strategy));
    }
}
