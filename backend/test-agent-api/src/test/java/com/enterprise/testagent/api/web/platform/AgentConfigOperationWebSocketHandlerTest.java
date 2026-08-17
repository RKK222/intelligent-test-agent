package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.AgentConfigOperationStatus;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.AgentConfigProgressEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
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

class AgentConfigOperationWebSocketHandlerTest {

    private static final String OPERATION_ID = "aco_12345678";
    private static final String TICKET = "agt_12345678";
    private static final String PATH = "/api/internal/platform/workspace-management/agent-config/operations/"
            + OPERATION_ID + "/ws?ticket=" + TICKET;
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void wildcardOriginAllowsConfiguredTestProfileOrigin() {
        AgentConfigOperationTicketService tickets = mock(AgentConfigOperationTicketService.class);
        AgentConfigApplicationService service = mock(AgentConfigApplicationService.class);
        AgentConfigProgressHub progress = mock(AgentConfigProgressHub.class);
        when(tickets.consume(TICKET, "http://127.0.0.1:3000")).thenReturn(ticket());
        when(service.findOperation(OPERATION_ID)).thenReturn(java.util.Optional.empty());
        when(progress.events(OPERATION_ID)).thenReturn(Flux.just(completed()));
        FakeWebSocketSession session = new FakeWebSocketSession(PATH, "http://127.0.0.1:3000");

        new AgentConfigOperationWebSocketHandler(tickets, service, progress, objectMapper, "*")
                .handle(session)
                .block(Duration.ofSeconds(2));

        verify(tickets).consume(TICKET, "http://127.0.0.1:3000");
        assertThat(session.sentText()).hasSize(2);
        assertThat(session.sentText().get(1)).contains("\"type\":\"completed\"");
    }

    @Test
    void disallowedOriginIsRejectedBeforeTicketConsumption() throws Exception {
        AgentConfigOperationTicketService tickets = mock(AgentConfigOperationTicketService.class);
        AgentConfigApplicationService service = mock(AgentConfigApplicationService.class);
        AgentConfigProgressHub progress = mock(AgentConfigProgressHub.class);
        FakeWebSocketSession session = new FakeWebSocketSession(PATH, "https://untrusted.example");

        new AgentConfigOperationWebSocketHandler(
                tickets, service, progress, objectMapper, "http://127.0.0.1:3000")
                .handle(session)
                .block(Duration.ofSeconds(2));

        JsonNode failed = objectMapper.readTree(session.sentText().getFirst());
        assertThat(failed.path("type").asText()).isEqualTo("failed");
        assertThat(failed.path("errorCode").asText()).isEqualTo("FORBIDDEN");
        verifyNoInteractions(tickets, service, progress);
    }

    private AgentConfigOperationTicket ticket() {
        return new AgentConfigOperationTicket(
                TICKET, OPERATION_ID, "usr_test_dev", true, "trace_ticket", NOW.plusSeconds(60));
    }

    private AgentConfigProgressEvent completed() {
        return new AgentConfigProgressEvent(
                OPERATION_ID,
                "completed",
                AgentConfigOperationStatus.SUCCEEDED,
                "COMPLETED",
                null,
                null,
                null,
                "a".repeat(40),
                "trace_completed",
                NOW);
    }

    private static final class FakeWebSocketSession implements WebSocketSession {

        private final HandshakeInfo handshakeInfo;
        private final DataBufferFactory buffers = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();

        private FakeWebSocketSession(String path, String origin) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin(origin);
            headers.set("X-Trace-Id", "trace_ws");
            this.handshakeInfo = new HandshakeInfo(
                    URI.create("ws://127.0.0.1:8080" + path),
                    headers,
                    Mono.<Principal>empty(),
                    null);
        }

        List<String> sentText() {
            return sentText;
        }

        @Override
        public String getId() {
            return "ws_agent_config_test";
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshakeInfo;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return buffers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            return Flux.never();
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages)
                    .doOnNext(message -> sentText.add(message.getPayloadAsText()))
                    .then();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public Mono<Void> close(CloseStatus status) {
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
                    buffers.wrap(payload.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public WebSocketMessage binaryMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(buffers));
        }

        @Override
        public WebSocketMessage pingMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(buffers));
        }

        @Override
        public WebSocketMessage pongMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(buffers));
        }
    }
}
