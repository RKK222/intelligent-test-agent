package com.example.testagent.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.testagent.app.config.TestAgentRuntimeProperties;
import com.example.testagent.app.terminal.TerminalActiveSessionRegistry;
import com.example.testagent.app.terminal.TerminalApplicationService;
import com.example.testagent.app.terminal.TerminalMessageCodec;
import com.example.testagent.app.terminal.TerminalProcessFactory;
import com.example.testagent.app.terminal.TerminalTicket;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

class TerminalWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsSecondWebSocketForAlreadyActiveSessionWithoutStartingProcess() throws Exception {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        registry.reserve(ticket);
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        FakeWebSocketSession session = FakeWebSocketSession.allowed("/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, registry).handle(session).block();

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asText()).isEqualTo("CONFLICT");
        assertThat(session.closed()).isTrue();
        verify(processFactory, never()).start(ticket);
    }

    @Test
    void rejectsDisallowedOriginBeforeConsumingTicket() throws Exception {
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        FakeWebSocketSession session = FakeWebSocketSession.disallowed("/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, new TerminalActiveSessionRegistry()).handle(session).block();

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("code").asText()).isEqualTo("PTY_ORIGIN_DENIED");
        verify(terminalService, never()).consumeTicket(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void releasesActiveReservationWhenProcessStartFails() throws Exception {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        when(processFactory.start(ticket))
                .thenThrow(new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "PTY 后端不可用"));
        FakeWebSocketSession session = FakeWebSocketSession.allowed("/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, registry).handle(session).block();

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("code").asText()).isEqualTo("OPENCODE_UNAVAILABLE");
        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
    }

    private TerminalWebSocketHandler handler(
            TerminalApplicationService terminalService,
            TerminalProcessFactory processFactory,
            TerminalActiveSessionRegistry registry) {
        return new TerminalWebSocketHandler(
                terminalService,
                processFactory,
                new TerminalMessageCodec(objectMapper),
                new TestAgentRuntimeProperties(),
                registry);
    }

    private static TerminalTicket ticket(String sessionId) {
        return new TerminalTicket(
                "pty_1234567890abcdef",
                new SessionId(sessionId),
                new WorkspaceId("wrk_1234567890abcdef"),
                new ExecutionNodeId("node_1234567890abcdef"),
                Path.of("/tmp/demo"),
                Path.of("/tmp/demo"),
                "/bin/sh",
                80,
                24,
                "trace_1234567890abcdef",
                Instant.parse("2026-06-19T00:01:00Z"));
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private final HandshakeInfo handshakeInfo;
        private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();
        private boolean closed;

        private FakeWebSocketSession(String path, String origin) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin(origin);
            headers.set("X-Trace-Id", "trace_1234567890abcdef");
            this.handshakeInfo = new HandshakeInfo(URI.create("ws://127.0.0.1:8080" + path), headers, Mono.<Principal>empty(), null);
        }

        static FakeWebSocketSession allowed(String path) {
            return new FakeWebSocketSession(path, "http://localhost:3000");
        }

        static FakeWebSocketSession disallowed(String path) {
            return new FakeWebSocketSession(path, "http://evil.test");
        }

        List<String> sentText() {
            return sentText;
        }

        boolean closed() {
            return closed;
        }

        @Override
        public String getId() {
            return "ws_test";
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
            return Flux.empty();
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
