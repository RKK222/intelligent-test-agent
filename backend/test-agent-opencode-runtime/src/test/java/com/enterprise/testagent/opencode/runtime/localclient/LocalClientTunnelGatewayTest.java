package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

/** 验证反向隧道的取消、超时、generation fencing 与有界背压。 */
class LocalClientTunnelGatewayTest {

    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();
    private final LocalClientInstanceId clientInstanceId = new LocalClientInstanceId("lci_tunnel_gateway_test");

    @Test
    void downstreamCancellationSendsRemoteCancelFrame() {
        List<LocalClientFrame> sent = new CopyOnWriteArrayList<>();
        LocalClientTunnelGateway gateway = gateway(sent);

        Disposable subscription = gateway.request(
                        clientInstanceId,
                        7,
                        LocalClientFrameType.HTTP_REQUEST,
                        requestPayload(),
                        "trace_cancel",
                        Duration.ofMinutes(1))
                .subscribe();
        assertThat(sent).singleElement().satisfies(frame ->
                assertThat(frame.type()).isEqualTo(LocalClientFrameType.HTTP_REQUEST));

        String targetRequestId = sent.getFirst().requestId();
        subscription.dispose();

        assertThat(sent).hasSize(2);
        LocalClientFrame cancel = sent.get(1);
        assertThat(cancel.type()).isEqualTo(LocalClientFrameType.CANCEL);
        assertThat(codec.payload(cancel, LocalClientPayloads.Cancel.class).targetRequestId())
                .isEqualTo(targetRequestId);
    }

    @Test
    void requestTimeoutSendsRemoteCancelFrame() {
        List<LocalClientFrame> sent = new CopyOnWriteArrayList<>();
        LocalClientTunnelGateway gateway = gateway(sent);

        StepVerifier.create(gateway.request(
                        clientInstanceId,
                        7,
                        LocalClientFrameType.HTTP_REQUEST,
                        requestPayload(),
                        "trace_timeout",
                        Duration.ofMillis(20)))
                .expectError()
                .verify(Duration.ofSeconds(2));

        assertThat(sent).extracting(LocalClientFrame::type)
                .containsExactly(LocalClientFrameType.HTTP_REQUEST, LocalClientFrameType.CANCEL);
    }

    @Test
    void pendingRegistryRejectsWrongGenerationAndOverflowsAtBoundedCapacity() {
        LocalClientPendingRequestRegistry registry = new LocalClientPendingRequestRegistry();
        String requestId = "lcr_backpressure";
        registry.open(requestId, clientInstanceId, 7);

        assertThat(registry.accept(clientInstanceId, chunk(requestId, 6, 0))).isFalse();
        for (int sequence = 0; sequence < 64; sequence++) {
            assertThat(registry.accept(clientInstanceId, chunk(requestId, 7, sequence))).isTrue();
        }
        assertThat(registry.accept(clientInstanceId, chunk(requestId, 7, 64))).isFalse();
    }

    @Test
    void pendingRegistryBoundsGlobalOutstandingRequestsAndReleasesSlots() {
        LocalClientPendingRequestRegistry registry = new LocalClientPendingRequestRegistry();
        for (int index = 0; index < 1024; index++) {
            registry.open("lcr_capacity_" + index, clientInstanceId, 7);
        }
        assertThatThrownBy(() -> registry.open("lcr_capacity_overflow", clientInstanceId, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too many pending");

        registry.cancel("lcr_capacity_0");
        registry.open("lcr_capacity_reused", clientInstanceId, 7);
    }

    private LocalClientTunnelGateway gateway(List<LocalClientFrame> sent) {
        LocalClientConnectionRegistry connections = new LocalClientConnectionRegistry();
        connections.register(
                clientInstanceId,
                new UserId("usr_tunnel_gateway_test"),
                7,
                "grant-fingerprint",
                new LocalClientConnectionSender() {
                    @Override
                    public void send(LocalClientFrame frame) {
                        sent.add(frame);
                    }

                    @Override
                    public void close(String reason) {
                        // 当前测试只验证发送路径。
                    }
                });
        return new LocalClientTunnelGateway(connections, new LocalClientPendingRequestRegistry());
    }

    private LocalClientPayloads.HttpRequest requestPayload() {
        return new LocalClientPayloads.HttpRequest("GET", "/api/info", java.util.Map.of(), null, false);
    }

    private LocalClientFrame chunk(String requestId, long generation, long sequence) {
        return new LocalClientFrame(
                "local-opencode-client.v1",
                LocalClientFrameType.STREAM_CHUNK,
                requestId,
                "trace_backpressure",
                generation,
                codec.payload(new LocalClientPayloads.BinaryChunk(sequence, "AA==", false)));
    }
}
