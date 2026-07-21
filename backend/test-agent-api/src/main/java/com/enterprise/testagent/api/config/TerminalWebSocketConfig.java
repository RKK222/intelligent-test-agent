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
            @Value("${test-agent.files.max-preview-bytes:${test-agent.files.max-file-bytes:5242880}}") long maxPreviewBytes,
            @Value("${test-agent.files.upload-chunk-bytes:262144}") int uploadChunkBytes) {
        if (maxPreviewBytes < 1) {
            throw new IllegalArgumentException("maxPreviewBytes must be positive");
        }
        if (uploadChunkBytes < 1 || uploadChunkBytes > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("uploadChunkBytes must be between 1 and 4194304");
        }
        // 文本写入经过 JSON.stringify 后，单个控制字符最坏会转义为 6 字节的 Unicode 转义形式；
        // 渐进预览单段固定小于该阈值；分片上传只需容纳一个 Base64 分片。
        // 取两者较大值并附加 RPC envelope 余量。传输帧上限不是文件总大小或最终预览总量上限。
        long envelopeBytes = 64L * 1024L;
        long maxScalablePreviewBytes = (Integer.MAX_VALUE - envelopeBytes) / 6L;
        long escapedPreviewBytes = maxPreviewBytes > maxScalablePreviewBytes
                ? Integer.MAX_VALUE
                : maxPreviewBytes * 6L;
        long encodedChunkBytes = 4L * ((uploadChunkBytes + 2L) / 3L);
        long requiredPayloadBytes = Math.max(escapedPreviewBytes, encodedChunkBytes);
        int maxFramePayloadLength = requiredPayloadBytes >= Integer.MAX_VALUE - envelopeBytes
                ? Integer.MAX_VALUE
                : Math.toIntExact(requiredPayloadBytes + envelopeBytes);
        ReactorNettyRequestUpgradeStrategy strategy = new ReactorNettyRequestUpgradeStrategy(
                () -> WebsocketServerSpec.builder().maxFramePayloadLength(maxFramePayloadLength));
        return new WebSocketHandlerAdapter(new HandshakeWebSocketService(strategy));
    }
}
