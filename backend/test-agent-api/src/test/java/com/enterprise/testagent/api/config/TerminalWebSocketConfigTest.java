package com.enterprise.testagent.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

class TerminalWebSocketConfigTest {

    @Test
    void fileFrameLimitAllowsWorstCaseJsonEscapingWithinBusinessLimit() throws Exception {
        int maxFileBytes = 64 * 1024;
        String request = writeRequest("\u0000".repeat(maxFileBytes));

        int configuredFrameBytes = configuredFrameBytes(maxFileBytes, 16 * 1024);

        assertThat(configuredFrameBytes)
                .isGreaterThan(request.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void fileFrameLimitAllowsLineDenseTextWithinBusinessLimit() throws Exception {
        int maxFileBytes = 128 * 1024;
        String request = writeRequest("\n".repeat(maxFileBytes));

        int configuredFrameBytes = configuredFrameBytes(maxFileBytes, 16 * 1024);

        assertThat(configuredFrameBytes)
                .isGreaterThan(request.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void fileFrameLimitAllowsConfiguredBase64UploadChunk() throws Exception {
        int uploadChunkBytes = 1024 * 1024;
        String request = new ObjectMapper().writeValueAsString(Map.of(
                "id", "wfr_chunk",
                "op", "workspace.upload.chunk",
                "params", Map.of(
                        "workspaceId", "wrk_1234567890abcdef",
                        "uploadId", "upl_123",
                        "index", 0,
                        "contentBase64", java.util.Base64.getEncoder().encodeToString(new byte[uploadChunkBytes]))));

        int configuredFrameBytes = configuredFrameBytes(1024, uploadChunkBytes);

        assertThat(configuredFrameBytes).isGreaterThan(request.getBytes(StandardCharsets.UTF_8).length);
    }

    private String writeRequest(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "id", "wfr_large",
                "op", "workspace.write",
                "params", Map.of(
                        "workspaceId", "wrk_1234567890abcdef",
                        "path", "large.txt",
                        "content", content)));
    }

    private int configuredFrameBytes(long maxPreviewBytes, int uploadChunkBytes) {
        WebSocketHandlerAdapter adapter = new TerminalWebSocketConfig()
                .webSocketHandlerAdapter(maxPreviewBytes, uploadChunkBytes);
        HandshakeWebSocketService service = (HandshakeWebSocketService) adapter.getWebSocketService();
        ReactorNettyRequestUpgradeStrategy strategy =
                (ReactorNettyRequestUpgradeStrategy) service.getUpgradeStrategy();
        return strategy.getWebsocketServerSpec().maxFramePayloadLength();
    }
}
