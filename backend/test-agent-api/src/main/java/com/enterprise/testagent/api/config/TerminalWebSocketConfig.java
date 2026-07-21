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
        mapping.setUrlMap(Map.of(
                "/api/internal/platform/opencode-runtime/sessions/*/terminal/ws", handler,
                "/api/internal/platform/opencode-runtime/management/linux-servers/*/terminal/ws", handler));
        mapping.setOrder(-1);
        return mapping;
    }

    /**
     * 提供 WebFlux WebSocket handler adapter。
     */
    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter(
            @Value("${test-agent.files.max-file-bytes:1048576}") long maxFileBytes) {
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        // 文本写入经过 JSON.stringify 后，单个控制字符最坏会转义为 6 字节的 Unicode 转义形式；
        // 因此按 6 倍业务文件上限预留，比 Base64 的 4/3 膨胀更严格，再附加 RPC envelope 余量。
        // 业务处理层仍按 max-file-bytes 校验 UTF-8/解码后大小，传输层只负责让合法请求到达统一校验。
        long envelopeBytes = 64L * 1024L;
        long maxScalableFileBytes = (Integer.MAX_VALUE - envelopeBytes) / 6L;
        int maxFramePayloadLength = maxFileBytes > maxScalableFileBytes
                ? Integer.MAX_VALUE
                : Math.toIntExact(maxFileBytes * 6L + envelopeBytes);
        ReactorNettyRequestUpgradeStrategy strategy = new ReactorNettyRequestUpgradeStrategy(
                () -> WebsocketServerSpec.builder().maxFramePayloadLength(maxFramePayloadLength));
        return new WebSocketHandlerAdapter(new HandshakeWebSocketService(strategy));
    }
}
