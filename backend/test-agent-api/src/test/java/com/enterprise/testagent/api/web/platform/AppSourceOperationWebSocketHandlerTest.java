package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
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

class AppSourceOperationWebSocketHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sendsPersistedSnapshotThenStepAndCompletedFrames() throws Exception {
        AppSourceOperationTicketService tickets = mock(AppSourceOperationTicketService.class);
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(tickets.consume("ast_ticket", "aso_12345678", "http://localhost:3000"))
                .thenReturn(ticket("ast_ticket"));
        when(appSources.getOperation("aso_12345678", new UserId("usr_1"), false))
                .thenReturn(
                        operation(AppSourceOperationStatus.RUNNING, List.of()),
                        operation(AppSourceOperationStatus.RUNNING, List.of(step(AppSourceStepStatus.RUNNING))),
                        operation(AppSourceOperationStatus.SUCCEEDED, List.of(step(AppSourceStepStatus.SUCCEEDED))));
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/app-source-operations/"
                        + "aso_12345678/ws?ticket=ast_ticket");

        new AppSourceOperationWebSocketHandler(
                tickets, appSources, objectMapper, "http://localhost:3000", Duration.ofMillis(1))
                .handle(session)
                .block(Duration.ofSeconds(2));

        assertThat(session.sentText()).hasSize(3);
        JsonNode snapshot = objectMapper.readTree(session.sentText().get(0));
        JsonNode step = objectMapper.readTree(session.sentText().get(1));
        JsonNode completed = objectMapper.readTree(session.sentText().get(2));
        assertThat(snapshot.path("type").asText()).isEqualTo("snapshot");
        assertThat(snapshot.path("operation").path("status").asText()).isEqualTo("RUNNING");
        assertThat(step.path("type").asText()).isEqualTo("step");
        assertThat(step.path("operation").path("globalSteps").get(0).path("safeSummary").asText())
                .isEqualTo("正在检出固定提交");
        assertThat(completed.path("type").asText())
                .as(session.sentText().toString())
                .isEqualTo("completed");
        assertThat(completed.path("operation").path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(session.sentText()).allSatisfy(payload ->
                assertThat(payload).doesNotContain(
                        "rootPath", "repositoryPath", "privateKey", "stderr", "/data/"));
    }

    @Test
    void reconnectWithANewTicketAlwaysStartsFromTheLatestPersistedSnapshot() throws Exception {
        AppSourceOperationTicketService tickets = mock(AppSourceOperationTicketService.class);
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(tickets.consume("ast_first", "aso_12345678", "http://localhost:3000"))
                .thenReturn(ticket("ast_first"));
        when(tickets.consume("ast_second", "aso_12345678", "http://localhost:3000"))
                .thenReturn(ticket("ast_second"));
        when(appSources.getOperation("aso_12345678", new UserId("usr_1"), false))
                .thenReturn(operation(
                        AppSourceOperationStatus.SUCCEEDED,
                        List.of(step(AppSourceStepStatus.SUCCEEDED))));
        AppSourceOperationWebSocketHandler handler = new AppSourceOperationWebSocketHandler(
                tickets, appSources, objectMapper, "http://localhost:3000", Duration.ofMillis(1));
        FakeWebSocketSession first = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/app-source-operations/"
                        + "aso_12345678/ws?ticket=ast_first");
        FakeWebSocketSession second = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/app-source-operations/"
                        + "aso_12345678/ws?ticket=ast_second");

        handler.handle(first).block(Duration.ofSeconds(2));
        handler.handle(second).block(Duration.ofSeconds(2));

        assertThat(objectMapper.readTree(first.sentText().getFirst()).path("type").asText())
                .isEqualTo("snapshot");
        assertThat(objectMapper.readTree(second.sentText().getFirst()).path("type").asText())
                .isEqualTo("snapshot");
    }

    @Test
    void disallowedOriginIsRejectedBeforeTicketConsumption() throws Exception {
        AppSourceOperationTicketService tickets = mock(AppSourceOperationTicketService.class);
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        FakeWebSocketSession session = FakeWebSocketSession.withOrigin(
                "/api/internal/platform/workspace-management/app-source-operations/"
                        + "aso_12345678/ws?ticket=ast_ticket",
                "https://untrusted.example");

        new AppSourceOperationWebSocketHandler(
                tickets, appSources, objectMapper, "http://localhost:3000", Duration.ofMillis(1))
                .handle(session)
                .block(Duration.ofSeconds(2));

        assertThat(session.sentText()).singleElement().satisfies(payload -> {
            try {
                JsonNode failed = objectMapper.readTree(payload);
                assertThat(failed.path("type").asText()).isEqualTo("failed");
                assertThat(failed.path("errorCode").asText()).isEqualTo("FORBIDDEN");
                assertThat(failed.path("errorMessage").asText())
                        .isEqualTo("应用源码进度 WebSocket 拒绝连接");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        verifyNoInteractions(tickets, appSources);
    }

    @Test
    void membershipRevocationAfterTicketIssuanceTerminatesTheNextProgressRead() throws Exception {
        AppSourceOperationTicketService tickets = mock(AppSourceOperationTicketService.class);
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(tickets.consume("ast_ticket", "aso_12345678", "http://localhost:3000"))
                .thenReturn(ticket("ast_ticket"));
        when(appSources.getOperation("aso_12345678", new UserId("usr_1"), false))
                .thenReturn(operation(AppSourceOperationStatus.RUNNING, List.of()))
                .thenThrow(new PlatformException(ErrorCode.FORBIDDEN, "当前用户已不是应用有效成员"));
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/app-source-operations/"
                        + "aso_12345678/ws?ticket=ast_ticket");

        new AppSourceOperationWebSocketHandler(
                tickets, appSources, objectMapper, "http://localhost:3000", Duration.ofMillis(1))
                .handle(session)
                .block(Duration.ofSeconds(2));

        assertThat(session.sentText()).hasSize(2);
        assertThat(objectMapper.readTree(session.sentText().get(0)).path("type").asText())
                .isEqualTo("snapshot");
        JsonNode failed = objectMapper.readTree(session.sentText().get(1));
        assertThat(failed.path("type").asText()).isEqualTo("failed");
        assertThat(failed.path("errorCode").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void partialFailureIsATerminalCompletedFrameWithOnlyPersistedSafeSummary() throws Exception {
        AppSourceOperationTicketService tickets = mock(AppSourceOperationTicketService.class);
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(tickets.consume("ast_ticket", "aso_12345678", "http://localhost:3000"))
                .thenReturn(ticket("ast_ticket"));
        when(appSources.getOperation("aso_12345678", new UserId("usr_1"), false))
                .thenReturn(operation(
                        AppSourceOperationStatus.PARTIAL_FAILED,
                        List.of(step(AppSourceStepStatus.FAILED))));
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/app-source-operations/"
                        + "aso_12345678/ws?ticket=ast_ticket");

        new AppSourceOperationWebSocketHandler(
                tickets, appSources, objectMapper, "http://localhost:3000", Duration.ofMillis(1))
                .handle(session)
                .block(Duration.ofSeconds(2));

        assertThat(session.sentText()).hasSize(2);
        JsonNode terminal = objectMapper.readTree(session.sentText().get(1));
        assertThat(terminal.path("type").asText()).isEqualTo("completed");
        assertThat(terminal.path("operation").path("status").asText()).isEqualTo("PARTIAL_FAILED");
        assertThat(session.sentText().get(1)).contains("正在检出固定提交").doesNotContain("stderr", "/data/");
    }

    @Test
    void failedOperationFrameContainsStatusAndSafeErrorFields() throws Exception {
        AppSourceOperationTicketService tickets = mock(AppSourceOperationTicketService.class);
        AppSourceApplicationService appSources = mock(AppSourceApplicationService.class);
        when(tickets.consume("ast_ticket", "aso_12345678", "http://localhost:3000"))
                .thenReturn(ticket("ast_ticket"));
        when(appSources.getOperation("aso_12345678", new UserId("usr_1"), false))
                .thenReturn(operation(AppSourceOperationStatus.FAILED, List.of()));
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/app-source-operations/"
                        + "aso_12345678/ws?ticket=ast_ticket");

        new AppSourceOperationWebSocketHandler(
                tickets, appSources, objectMapper, "http://localhost:3000", Duration.ofMillis(1))
                .handle(session)
                .block(Duration.ofSeconds(2));

        JsonNode failed = objectMapper.readTree(session.sentText().get(1));
        assertThat(failed.path("type").asText()).isEqualTo("failed");
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("errorCode").asText()).isEqualTo("APP_SOURCE_OPERATION_FAILED");
        assertThat(failed.path("errorMessage").asText()).isEqualTo("应用源码操作失败");
        assertThat(failed.path("operation").path("operationId").asText()).isEqualTo("aso_12345678");
        assertThat(failed.path("traceId").asText()).isEqualTo("trace_operation");
    }

    private AppSourceOperationTicket ticket(String value) {
        return new AppSourceOperationTicket(
                value, "aso_12345678", "usr_1", false, "bjp_server_a",
                "http://localhost:3000", "trace_ticket", NOW.plusSeconds(60));
    }

    private AppSourceApplicationService.OperationSnapshot operation(
            AppSourceOperationStatus status,
            List<AppSourceApplicationService.StepSummary> steps) {
        return new AppSourceApplicationService.OperationSnapshot(
                "aso_12345678", "app_1", "repo_1", null, 1L,
                AppSourceOperationType.DOWNLOAD, status, AppSourcePurpose.TEAM,
                "main", "b".repeat(40), List.of(), NOW.plusSeconds(3600),
                "trace_operation", NOW.minusSeconds(30),
                status == AppSourceOperationStatus.SUCCEEDED ? NOW : null,
                steps, List.of());
    }

    private AppSourceApplicationService.StepSummary step(AppSourceStepStatus status) {
        return new AppSourceApplicationService.StepSummary(
                "CHECKOUT", 1, status, "正在检出固定提交",
                NOW.minusSeconds(10), status == AppSourceStepStatus.SUCCEEDED ? NOW : null,
                10_000L, NOW);
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

        static FakeWebSocketSession allowed(String path) {
            return new FakeWebSocketSession(path, "http://localhost:3000");
        }

        static FakeWebSocketSession withOrigin(String path, String origin) {
            return new FakeWebSocketSession(path, origin);
        }

        List<String> sentText() {
            return sentText;
        }

        @Override
        public String getId() {
            return "ws_app_source_test";
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
