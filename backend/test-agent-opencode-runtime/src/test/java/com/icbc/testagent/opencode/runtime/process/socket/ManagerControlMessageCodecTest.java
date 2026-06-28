package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagerControlMessageCodecTest {

    @Test
    void encodesAndDecodesRegisterMessage() {
        ManagerControlMessageCodec codec = new ManagerControlMessageCodec(new ObjectMapper());
        ManagerControlMessage message = ManagerControlMessage.register(
                "mgr_1234567890abcdef",
                "ctr_01",
                "10.8.0.12",
                "ctr_01",
                4096,
                4100,
                5,
                2,
                Map.of("commands", "start,health"),
                "trace_1234567890abcdef");

        ManagerControlMessage decoded = codec.decode(codec.encode(message));

        assertThat(decoded.type()).isEqualTo("register");
        assertThat(decoded.protocolVersion()).isEqualTo(ManagerControlProtocol.VERSION);
        assertThat(decoded.managerId()).isEqualTo("mgr_1234567890abcdef");
        assertThat(decoded.containerId()).isEqualTo("ctr_01");
        assertThat(decoded.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(decoded.currentProcesses()).isEqualTo(2);
        assertThat(decoded.capabilities()).containsEntry("commands", "start,health");
    }

    @Test
    void preservesUnknownTypeForHandlerValidation() {
        ManagerControlMessageCodec codec = new ManagerControlMessageCodec(new ObjectMapper());

        ManagerControlMessage decoded = codec.decode("""
                {"type":"mystery","protocolVersion":"opencode-manager.v1","traceId":"trace_1234567890abcdef"}
                """);

        assertThat(decoded.type()).isEqualTo("mystery");
        assertThat(decoded.traceId()).isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void encodesManagerHeartbeatWithConnectedBackendIds() {
        ManagerControlMessageCodec codec = new ManagerControlMessageCodec(new ObjectMapper());
        ManagerControlMessage heartbeat = ManagerControlMessage.managerHeartbeat(
                "mgr_1234567890abcdef",
                "ctr_01",
                "10.8.0.12",
                "opencode-a",
                4096,
                4100,
                5,
                2,
                Map.of("commands", List.of("start", "health")),
                List.of("bjp_1234567890abcdef", "bjp_2234567890abcdef"),
                "trace_1234567890abcdef");

        ManagerControlMessage decoded = codec.decode(codec.encode(heartbeat));

        assertThat(decoded.type()).isEqualTo(ManagerControlProtocol.TYPE_MANAGER_HEARTBEAT);
        assertThat(decoded.connectedBackendProcessIds()).containsExactly(
                "bjp_1234567890abcdef",
                "bjp_2234567890abcdef");
        assertThat(decoded.currentProcesses()).isEqualTo(2);
    }

    @Test
    void encodesBackendListResponseEndpoints() {
        ManagerControlMessageCodec codec = new ManagerControlMessageCodec(new ObjectMapper());
        ManagerControlMessage response = ManagerControlMessage.backendListResponse(
                List.of(new ManagerBackendEndpoint(
                        "bjp_1234567890abcdef",
                        "10.8.0.12",
                        "http://10.8.0.12:8080",
                        "ws://10.8.0.12:8080/api/internal/platform/opencode-runtime/manager/ws",
                        Instant.parse("2026-06-24T00:00:00Z"))),
                "trace_1234567890abcdef");

        String encoded = codec.encode(response);
        ManagerControlMessage decoded = codec.decode(encoded);

        assertThat(decoded.type()).isEqualTo(ManagerControlProtocol.TYPE_BACKEND_LIST_RESPONSE);
        assertThat(decoded.backendEndpoints()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.backendProcessId()).isEqualTo("bjp_1234567890abcdef");
            assertThat(endpoint.webSocketUrl()).isEqualTo("ws://10.8.0.12:8080/api/internal/platform/opencode-runtime/manager/ws");
        });
        assertThat(readLastHeartbeatAt(encoded).isTextual()).isTrue();
        assertThat(readLastHeartbeatAt(encoded).asText()).isEqualTo("2026-06-24T00:00:00Z");
    }

    /**
     * Go manager 使用 time.Time 解码后端列表，控制面时间字段必须保持 RFC3339 字符串。
     */
    private JsonNode readLastHeartbeatAt(String encoded) {
        try {
            return new ObjectMapper()
                    .readTree(encoded)
                    .path("backendEndpoints")
                    .get(0)
                    .path("lastHeartbeatAt");
        } catch (Exception exception) {
            throw new AssertionError("后端列表响应 JSON 解析失败", exception);
        }
    }
}
