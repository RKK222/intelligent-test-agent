package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.opencode.runtime.terminal.TerminalActiveSessionRegistry;
import com.icbc.testagent.opencode.runtime.terminal.TerminalAuditLogger;
import com.icbc.testagent.opencode.runtime.terminal.TerminalApplicationService;
import com.icbc.testagent.opencode.runtime.terminal.TerminalMessageCodec;
import com.icbc.testagent.opencode.runtime.terminal.TerminalProcessFactory;
import com.icbc.testagent.opencode.runtime.terminal.TerminalProcessSession;
import com.icbc.testagent.opencode.runtime.terminal.TerminalServerMessage;
import com.icbc.testagent.opencode.runtime.terminal.TerminalTicket;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Duration;
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
import reactor.core.publisher.Sinks;

class TerminalWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsSecondWebSocketForAlreadyActiveSessionWithoutStartingProcess() throws Exception {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        registry.reserve(ticket);
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        TerminalAuditLogger auditLogger = Mockito.mock(TerminalAuditLogger.class);
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        FakeWebSocketSession session = FakeWebSocketSession.allowed("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, registry, defaultOptions(), auditLogger).handle(session).block();

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asText()).isEqualTo("CONFLICT");
        assertThat(session.closed()).isTrue();
        verify(processFactory, never()).start(ticket);
        verify(auditLogger).upgradeRejected(ticket, "CONFLICT");
    }

    @Test
    void rejectsDisallowedOriginBeforeConsumingTicket() throws Exception {
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        FakeWebSocketSession session = FakeWebSocketSession.disallowed("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

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
        FakeWebSocketSession session = FakeWebSocketSession.allowed("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, registry).handle(session).block();

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("code").asText()).isEqualTo("OPENCODE_UNAVAILABLE");
        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
    }

    @Test
    void rejectsInputRateViolationBeforeWritingToTerminal() throws Exception {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        TerminalProcessSession terminal = Mockito.mock(TerminalProcessSession.class);
        TerminalAuditLogger auditLogger = Mockito.mock(TerminalAuditLogger.class);
        HandlerOptions options = defaultOptions().withMaxInputBytes(4);
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        when(processFactory.start(ticket)).thenReturn(terminal);
        when(terminal.output()).thenReturn(Flux.empty());
        when(terminal.close()).thenReturn(Mono.empty());
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef",
                List.of("""
                        {"type":"input","data":"12345"}
                        """));

        handler(terminalService, processFactory, registry, options, auditLogger).handle(session).block(Duration.ofSeconds(1));

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asText()).isEqualTo("RATE_LIMITED");
        verify(terminal, never()).input(Mockito.any());
        verify(terminal).close();
        verify(auditLogger).inputRejected(ticket, "RATE_LIMITED", 5);
        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
    }

    @Test
    void forwardsInputResizeAndCloseThenReleasesActiveReservation() {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        TerminalProcessSession terminal = Mockito.mock(TerminalProcessSession.class);
        TerminalAuditLogger auditLogger = Mockito.mock(TerminalAuditLogger.class);
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        when(processFactory.start(ticket)).thenReturn(terminal);
        when(terminal.output()).thenReturn(Flux.empty());
        when(terminal.input("echo hi\n")).thenReturn(Mono.empty());
        when(terminal.resize(120, 32)).thenReturn(Mono.empty());
        when(terminal.close()).thenReturn(Mono.empty());
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef",
                List.of(
                        """
                        {"type":"input","data":"echo hi\\n"}
                        """,
                        """
                        {"type":"resize","cols":120,"rows":32}
                        """,
                        """
                        {"type":"close","reason":"user"}
                        """));

        handler(terminalService, processFactory, registry, defaultOptions(), auditLogger).handle(session).block(Duration.ofSeconds(1));

        verify(terminal).input("echo hi\n");
        verify(terminal).resize(120, 32);
        verify(terminal).close();
        verify(auditLogger).upgradeAccepted(ticket);
        verify(auditLogger).input(ticket, 8);
        verify(auditLogger).resize(ticket, 120, 32);
        verify(auditLogger).close(ticket, "user");
        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
    }

    @Test
    void rejectsInvalidClientMessageAndClosesTerminal() throws Exception {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        TerminalProcessSession terminal = Mockito.mock(TerminalProcessSession.class);
        Sinks.Many<TerminalServerMessage> output = Sinks.many().unicast().onBackpressureBuffer();
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        when(processFactory.start(ticket)).thenReturn(terminal);
        when(terminal.output()).thenReturn(output.asFlux());
        when(terminal.close()).thenAnswer(invocation -> {
            output.tryEmitComplete();
            return Mono.empty();
        });
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef",
                List.of("not-json"));

        handler(terminalService, processFactory, registry).handle(session).block(Duration.ofSeconds(1));

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("message").asText()).isEqualTo("invalid terminal message");
        verify(terminal, never()).input(Mockito.any());
        verify(terminal, never()).resize(Mockito.any(), Mockito.any());
        verify(terminal).close();
        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
    }

    @Test
    void auditsTerminalExitEnvelope() {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        TerminalProcessSession terminal = Mockito.mock(TerminalProcessSession.class);
        TerminalAuditLogger auditLogger = Mockito.mock(TerminalAuditLogger.class);
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        when(processFactory.start(ticket)).thenReturn(terminal);
        when(terminal.output()).thenReturn(Flux.just(TerminalServerMessage.exit(0, 1)));
        when(terminal.close()).thenReturn(Mono.empty());
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, registry, defaultOptions(), auditLogger)
                .handle(session)
                .block(Duration.ofSeconds(1));

        verify(auditLogger).exit(ticket, 0);
    }

    @Test
    void closesIdleTerminalAfterConfiguredTimeout() throws Exception {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        TerminalProcessSession terminal = Mockito.mock(TerminalProcessSession.class);
        TerminalAuditLogger auditLogger = Mockito.mock(TerminalAuditLogger.class);
        Sinks.Many<TerminalServerMessage> output = Sinks.many().unicast().onBackpressureBuffer();
        HandlerOptions options = defaultOptions()
                .withIdleTimeout(Duration.ofMillis(10))
                .withHardTimeout(Duration.ofSeconds(1));
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        when(processFactory.start(ticket)).thenReturn(terminal);
        when(terminal.output()).thenReturn(output.asFlux());
        when(terminal.close()).thenAnswer(invocation -> {
            output.tryEmitComplete();
            return Mono.empty();
        });
        FakeWebSocketSession session = FakeWebSocketSession.allowedUntilClose(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, registry, options, auditLogger).handle(session).block(Duration.ofSeconds(1));

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("code").asText()).isEqualTo("PTY_TIMEOUT");
        assertThat(error.get("message").asText()).isEqualTo("terminal idle timeout");
        assertThat(session.closed()).isTrue();
        verify(terminal).close();
        verify(auditLogger).timeout(ticket, "terminal idle timeout");
        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
    }

    @Test
    void closesTerminalAfterConfiguredHardTimeout() throws Exception {
        TerminalTicket ticket = ticket("ses_1234567890abcdef");
        TerminalActiveSessionRegistry registry = new TerminalActiveSessionRegistry();
        TerminalApplicationService terminalService = Mockito.mock(TerminalApplicationService.class);
        TerminalProcessFactory processFactory = Mockito.mock(TerminalProcessFactory.class);
        TerminalProcessSession terminal = Mockito.mock(TerminalProcessSession.class);
        TerminalAuditLogger auditLogger = Mockito.mock(TerminalAuditLogger.class);
        Sinks.Many<TerminalServerMessage> output = Sinks.many().unicast().onBackpressureBuffer();
        HandlerOptions options = defaultOptions()
                .withIdleTimeout(Duration.ofSeconds(1))
                .withHardTimeout(Duration.ofMillis(10));
        when(terminalService.consumeTicket(
                        new SessionId("ses_1234567890abcdef"),
                        "pty_1234567890abcdef",
                        "http://localhost:3000",
                        "trace_1234567890abcdef"))
                .thenReturn(ticket);
        when(processFactory.start(ticket)).thenReturn(terminal);
        when(terminal.output()).thenReturn(output.asFlux());
        when(terminal.close()).thenAnswer(invocation -> {
            output.tryEmitComplete();
            return Mono.empty();
        });
        FakeWebSocketSession session = FakeWebSocketSession.allowedUntilClose(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_1234567890abcdef");

        handler(terminalService, processFactory, registry, options, auditLogger).handle(session).block(Duration.ofSeconds(1));

        JsonNode error = objectMapper.readTree(session.sentText().getFirst());
        assertThat(error.get("code").asText()).isEqualTo("PTY_TIMEOUT");
        assertThat(error.get("message").asText()).isEqualTo("terminal hard timeout");
        assertThat(session.closed()).isTrue();
        verify(terminal).close();
        verify(auditLogger).timeout(ticket, "terminal hard timeout");
        assertThat(registry.isActive(new SessionId("ses_1234567890abcdef"))).isFalse();
    }

    private TerminalWebSocketHandler handler(
            TerminalApplicationService terminalService,
            TerminalProcessFactory processFactory,
            TerminalActiveSessionRegistry registry) {
        return handler(terminalService, processFactory, registry, defaultOptions());
    }

    private TerminalWebSocketHandler handler(
            TerminalApplicationService terminalService,
            TerminalProcessFactory processFactory,
            TerminalActiveSessionRegistry registry,
            HandlerOptions options) {
        return handler(terminalService, processFactory, registry, options, new TerminalAuditLogger());
    }

    private TerminalWebSocketHandler handler(
            TerminalApplicationService terminalService,
            TerminalProcessFactory processFactory,
            TerminalActiveSessionRegistry registry,
            HandlerOptions options,
            TerminalAuditLogger auditLogger) {
        return new TerminalWebSocketHandler(
                terminalService,
                processFactory,
                new TerminalMessageCodec(objectMapper),
                "http://localhost:3000,http://127.0.0.1:3000",
                options.maxInputBytes(),
                64,
                10,
                Duration.ofSeconds(1),
                options.idleTimeout(),
                options.hardTimeout(),
                registry,
                auditLogger);
    }

    private HandlerOptions defaultOptions() {
        return new HandlerOptions(16 * 1024, Duration.ofMinutes(10), Duration.ofHours(2));
    }

    private record HandlerOptions(int maxInputBytes, Duration idleTimeout, Duration hardTimeout) {

        HandlerOptions withMaxInputBytes(int value) {
            return new HandlerOptions(value, idleTimeout, hardTimeout);
        }

        HandlerOptions withIdleTimeout(Duration value) {
            return new HandlerOptions(maxInputBytes, value, hardTimeout);
        }

        HandlerOptions withHardTimeout(Duration value) {
            return new HandlerOptions(maxInputBytes, idleTimeout, value);
        }
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
        private final List<String> incoming;
        private final boolean receiveUntilClose;
        private final Sinks.Empty<Void> closeSignal = Sinks.empty();
        private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();
        private boolean closed;

        private FakeWebSocketSession(String path, String origin) {
            this(path, origin, List.of(), false);
        }

        private FakeWebSocketSession(String path, String origin, List<String> incoming) {
            this(path, origin, incoming, false);
        }

        private FakeWebSocketSession(String path, String origin, List<String> incoming, boolean receiveUntilClose) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin(origin);
            headers.set("X-Trace-Id", "trace_1234567890abcdef");
            this.handshakeInfo = new HandshakeInfo(URI.create("ws://127.0.0.1:8080" + path), headers, Mono.<Principal>empty(), null);
            this.incoming = List.copyOf(incoming);
            this.receiveUntilClose = receiveUntilClose;
        }

        static FakeWebSocketSession allowed(String path) {
            return new FakeWebSocketSession(path, "http://localhost:3000");
        }

        static FakeWebSocketSession allowed(String path, List<String> incoming) {
            return new FakeWebSocketSession(path, "http://localhost:3000", incoming);
        }

        static FakeWebSocketSession allowedUntilClose(String path) {
            return new FakeWebSocketSession(path, "http://localhost:3000", List.of(), true);
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
            if (receiveUntilClose) {
                return closeSignal.asMono().thenMany(Flux.empty());
            }
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
            closeSignal.tryEmitEmpty();
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
