package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerConnectionRegistry;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlApplicationService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlMessage;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlMessageCodec;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerPendingCommandRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ManagerControlWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ManagerControlMessageCodec codec = new ManagerControlMessageCodec(objectMapper);

    @Test
    void rejectsMissingManagerTokenAndClosesSession() throws Exception {
        ManagerControlApplicationService controlService = Mockito.mock(ManagerControlApplicationService.class);
        FakeWebSocketSession session = FakeWebSocketSession.withToken(null, List.of());

        handler(controlService, new ManagerPendingCommandRegistry()).handle(session).block(Duration.ofSeconds(1));

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("errorCode").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(session.closed()).isTrue();
        verify(controlService, never()).register(Mockito.any());
    }

    @Test
    void handlesRegisterHeartbeatCommandResultAndDisconnect() {
        ManagerControlApplicationService controlService = Mockito.mock(ManagerControlApplicationService.class);
        ManagerPendingCommandRegistry pendingCommands = new ManagerPendingCommandRegistry();
        CompletableFuture<ManagerControlMessage> pending = pendingCommands.create("mcmd_1234567890abcdef");
        ManagerControlMessage register = ManagerControlMessage.register(
                "mgr_1234567890abcdef",
                "ctr_01",
                "10.8.0.12",
                "opencode-a",
                4096,
                4100,
                4,
                1,
                Map.of("start", true),
                "trace_1234567890abcdef");
        ManagerControlMessage heartbeat = ManagerControlMessage.register(
                "mgr_1234567890abcdef",
                "ctr_01",
                "10.8.0.12",
                "opencode-a",
                4096,
                4100,
                4,
                1,
                Map.of("health", true),
                "trace_1234567890abcdef");
        heartbeat = new ManagerControlMessage(
                "heartbeat",
                heartbeat.protocolVersion(),
                heartbeat.traceId(),
                heartbeat.managerId(),
                heartbeat.containerId(),
                heartbeat.linuxServerId(),
                heartbeat.containerName(),
                heartbeat.portStart(),
                heartbeat.portEnd(),
                heartbeat.maxProcesses(),
                heartbeat.currentProcesses(),
                heartbeat.capabilities(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        ManagerControlMessage result = ManagerControlMessage.commandResult(
                "mcmd_1234567890abcdef",
                "health",
                "HEALTHY",
                4096,
                12345L,
                "http://10.8.0.12:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                true,
                "ok",
                "trace_1234567890abcdef");
        when(controlService.register(register)).thenReturn(ManagerControlMessage.registered(
                "bjp_1234567890abcdef",
                "trace_1234567890abcdef"));

        FakeWebSocketSession session = FakeWebSocketSession.withToken(
                "secret-token",
                List.of(codec.encode(register), codec.encode(heartbeat), codec.encode(result)));

        handler(controlService, pendingCommands).handle(session).block(Duration.ofSeconds(1));

        assertThat(codec.decode(session.sentText().getFirst()).type()).isEqualTo("registered");
        assertThat(pendingCommands.await("mcmd_1234567890abcdef", pending, Duration.ofSeconds(1)).status())
                .isEqualTo("HEALTHY");
        verify(controlService).register(register);
        verify(controlService).heartbeat(heartbeat);
        verify(controlService).disconnect(new ContainerManagerId("mgr_1234567890abcdef"), "trace_1234567890abcdef");
    }

    private ManagerControlWebSocketHandler handler(
            ManagerControlApplicationService controlService,
            ManagerPendingCommandRegistry pendingCommands) {
        BackendJavaProcessLifecycleService backendLifecycle = Mockito.mock(BackendJavaProcessLifecycleService.class);
        when(backendLifecycle.backendProcessId()).thenReturn(new BackendProcessId("bjp_1234567890abcdef"));
        return new ManagerControlWebSocketHandler(
                settings(),
                codec,
                controlService,
                backendLifecycle,
                new ManagerConnectionRegistry(),
                pendingCommands);
    }

    private static ManagerControlSettings settings() {
        return new ManagerControlSettings(
                "secret-token",
                "http://10.8.0.21:8080",
                new LinuxServerId("10.8.0.21"),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                100);
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private final HandshakeInfo handshakeInfo;
        private final List<String> incoming;
        private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();
        private boolean closed;

        private FakeWebSocketSession(String token, List<String> incoming) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Trace-Id", "trace_1234567890abcdef");
            if (token != null) {
                headers.setBearerAuth(token);
            }
            this.handshakeInfo = new HandshakeInfo(
                    URI.create("ws://127.0.0.1:8080/api/internal/platform/opencode-runtime/manager/ws"),
                    headers,
                    Mono.<Principal>empty(),
                    null);
            this.incoming = List.copyOf(incoming);
        }

        static FakeWebSocketSession withToken(String token, List<String> incoming) {
            return new FakeWebSocketSession(token, incoming);
        }

        List<String> sentText() {
            return sentText;
        }

        boolean closed() {
            return closed;
        }

        @Override
        public String getId() {
            return "ws_manager_test";
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshakeInfo;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return bufferFactory;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            return Flux.fromIterable(incoming).map(this::textMessage);
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages)
                    .doOnNext(message -> sentText.add(message.getPayloadAsText()))
                    .then();
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public Mono<Void> close(CloseStatus status) {
            closed = true;
            return Mono.empty();
        }

        @Override
        public Mono<CloseStatus> closeStatus() {
            return Mono.just(CloseStatus.NORMAL);
        }

        @Override
        public WebSocketMessage textMessage(String payload) {
            return new WebSocketMessage(
                    WebSocketMessage.Type.TEXT,
                    bufferFactory.wrap(payload.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public WebSocketMessage binaryMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pingMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pongMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(bufferFactory));
        }
    }
}
