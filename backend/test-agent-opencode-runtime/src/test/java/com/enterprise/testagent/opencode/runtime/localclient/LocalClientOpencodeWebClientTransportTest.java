package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.opencode.client.GeneratedOpencodeSdkGateway;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 验证本地隧道仍使用 generated SDK 所需的 Jackson 2 JSON 类型。 */
class LocalClientOpencodeWebClientTransportTest {

    private static final LocalClientInstanceId INSTANCE_ID =
            new LocalClientInstanceId("lci_transport_codec_test");
    private static final long GENERATION = 7L;

    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    @Test
    void decodesJackson2JsonNodeFromHttpResponse() {
        LocalClientOpencodeWebClientTransport transport = transportReturning(
                "application/json", "{\"models\":[\"local-model\"]}", false);

        JsonNode response = transport.create(node(), "trace_transport_json", 1024 * 1024)
                .get()
                .uri("http://local-opencode-client.invalid/provider")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(2));

        assertThat(response).isNotNull();
        assertThat(response.path("models").get(0).asText()).isEqualTo("local-model");
    }

    @Test
    void decodesJackson2JsonNodeFromEventStream() {
        LocalClientOpencodeWebClientTransport transport = transportReturning(
                "text/event-stream", "data: {\"type\":\"server.connected\"}\n\n", true);

        List<JsonNode> events = transport.create(node(), "trace_transport_sse", 1024 * 1024)
                .get()
                .uri("http://local-opencode-client.invalid/event")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.path("type").asText()).isEqualTo("server.connected"));
    }

    @Test
    void gatewayReadsSessionMessageSnapshotThroughLocalTransport() {
        LocalClientOpencodeWebClientTransport transport = transportReturning(
                "application/json",
                """
                [
                  {
                    "info": {
                      "id": "msg_local_transport_assistant",
                      "sessionID": "ses_local_transport",
                      "role": "assistant",
                      "time": {"created": 1787414400000}
                    },
                    "parts": [
                      {
                        "id": "part_local_transport_text",
                        "messageID": "msg_local_transport_assistant",
                        "type": "text",
                        "text": "LOCAL_CHAT_OK"
                      }
                    ]
                  }
                ]
                """,
                false);

        OpencodeSessionMessagesResult result = new GeneratedOpencodeSdkGateway(List.of(transport))
                .sessionMessages(node(), "ses_local_transport", 100, "asc", null, "trace_local_snapshot")
                .block(Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.messages()).singleElement().satisfies(message -> {
            assertThat(message.message()).containsEntry("role", "assistant");
            assertThat(message.parts()).singleElement().satisfies(part ->
                    assertThat(part).containsEntry("text", "LOCAL_CHAT_OK"));
        });
    }

    private LocalClientOpencodeWebClientTransport transportReturning(
            String contentType,
            String body,
            boolean streaming) {
        LocalClientConnectionRegistry connections = new LocalClientConnectionRegistry();
        LocalClientPendingRequestRegistry pending = new LocalClientPendingRequestRegistry();
        AtomicReference<LocalClientTunnelGateway> gateway = new AtomicReference<>();
        connections.register(
                INSTANCE_ID,
                new UserId("usr_transport_codec_test"),
                GENERATION,
                "grant-fingerprint",
                new LocalClientConnectionSender() {
                    @Override
                    public void send(LocalClientFrame requestFrame) {
                        if (requestFrame.type() != LocalClientFrameType.HTTP_REQUEST) {
                            return;
                        }
                        LocalClientPayloads.HttpRequest request =
                                codec.payload(requestFrame, LocalClientPayloads.HttpRequest.class);
                        assertThat(request.streaming()).isEqualTo(streaming);
                        if (streaming) {
                            // 模拟客户端异步回帧，确保服务端已完成 stream subscriber 建立。
                            CompletableFuture.delayedExecutor(10, TimeUnit.MILLISECONDS).execute(() -> {
                                accept(gateway.get(), requestFrame, LocalClientFrameType.STREAM_OPEN,
                                        new LocalClientPayloads.StreamOpen(
                                                200, Map.of("content-type", List.of(contentType))));
                                accept(gateway.get(), requestFrame, LocalClientFrameType.STREAM_CHUNK,
                                        new LocalClientPayloads.BinaryChunk(0, base64(body), false));
                                accept(gateway.get(), requestFrame, LocalClientFrameType.STREAM_END, Map.of());
                            });
                        } else {
                            accept(gateway.get(), requestFrame, LocalClientFrameType.HTTP_RESPONSE,
                                    new LocalClientPayloads.HttpResponse(
                                            200, Map.of("content-type", List.of(contentType)), base64(body)));
                        }
                    }

                    @Override
                    public void close(String reason) {
                        // 测试连接在用例结束时随对象释放，无需额外处理。
                    }
                });
        LocalClientTunnelGateway tunnel = new LocalClientTunnelGateway(connections, pending);
        gateway.set(tunnel);
        return new LocalClientOpencodeWebClientTransport(tunnel);
    }

    private void accept(
            LocalClientTunnelGateway gateway,
            LocalClientFrame request,
            LocalClientFrameType type,
            Object payload) {
        assertThat(gateway.accept(INSTANCE_ID, new LocalClientFrame(
                LocalClientProtocol.VERSION,
                type,
                request.requestId(),
                request.traceId(),
                GENERATION,
                codec.payload(payload)))).isTrue();
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private ExecutionNode node() {
        Instant now = Instant.parse("2026-08-22T12:00:00Z");
        return new ExecutionNode(
                new ExecutionNodeId("node_local_transport_codec"),
                "http://local-opencode-client.invalid",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                now,
                Set.of("chat"),
                now,
                now,
                "trace_transport_node",
                RuntimeKind.LOCAL_CLIENT,
                INSTANCE_ID.value(),
                GENERATION);
    }
}
