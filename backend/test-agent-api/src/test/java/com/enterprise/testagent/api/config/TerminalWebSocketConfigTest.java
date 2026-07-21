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

        int configuredFrameBytes = configuredFrameBytes(maxFileBytes);

        assertThat(configuredFrameBytes)
                .isGreaterThan(request.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void fileFrameLimitAllowsLineDenseTextWithinBusinessLimit() throws Exception {
        int maxFileBytes = 128 * 1024;
        String request = writeRequest("\n".repeat(maxFileBytes));

        int configuredFrameBytes = configuredFrameBytes(maxFileBytes);

        assertThat(configuredFrameBytes)
                .isGreaterThan(request.getBytes(StandardCharsets.UTF_8).length);
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

    private int configuredFrameBytes(long maxFileBytes) {
        WebSocketHandlerAdapter adapter = new TerminalWebSocketConfig().webSocketHandlerAdapter(maxFileBytes);
        HandshakeWebSocketService service = (HandshakeWebSocketService) adapter.getWebSocketService();
        ReactorNettyRequestUpgradeStrategy strategy =
                (ReactorNettyRequestUpgradeStrategy) service.getUpgradeStrategy();
        return strategy.getWebsocketServerSpec().maxFramePayloadLength();
    }
}
