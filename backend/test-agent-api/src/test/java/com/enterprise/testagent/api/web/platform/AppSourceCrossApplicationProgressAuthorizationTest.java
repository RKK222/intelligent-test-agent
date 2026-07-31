package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import com.enterprise.testagent.workspace.AppSourceIndexManager;
import com.enterprise.testagent.workspace.AppSourceMaterializationRegistrar;
import com.enterprise.testagent.workspace.AppSourceReplicaRetryRegistrar;
import com.enterprise.testagent.workspace.AppSourceReplicaTaskDispatcher;
import com.enterprise.testagent.workspace.AppSourceWorkspaceOpener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 真实业务授权贯通 HTTP、ticket 与 WS，覆盖跨当前关联应用成员和实时撤权。 */
class AppSourceCrossApplicationProgressAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final ApplicationId APP_A = new ApplicationId("app_a");
    private static final ApplicationId APP_B = new ApplicationId("app_b");
    private static final UserId MEMBER_B = new UserId("usr_b");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_cross_app");
    private static final String OPERATION_ID = "aso_cross_app";
    private static final String ORIGIN = "http://localhost:3000";

    @Test
    void bOnlyTeamMemberCanUseHttpTicketAndWebSocketForOperationStartedByA() throws Exception {
        AtomicBoolean member = new AtomicBoolean(true);
        AppSourceApplicationService appSources = service(member);
        AppSourceOperationTicketService tickets = tickets(appSources, "ast_cross_app");
        WebTestClient client = client(appSources, tickets);

        client.get().uri("/api/internal/platform/workspace-management/app-source-operations/" + OPERATION_ID)
                .header("X-Trace-Id", "trace_cross_http")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.operationId").isEqualTo(OPERATION_ID)
                .jsonPath("$.data.status").isEqualTo("SUCCEEDED");
        client.post().uri("/api/internal/platform/workspace-management/app-source-operations/" + OPERATION_ID + "/ticket")
                .header("X-Trace-Id", "trace_cross_ticket")
                .header("Origin", ORIGIN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").isEqualTo("ast_cross_app");

        FakeWebSocketSession session = new FakeWebSocketSession("ast_cross_app");
        new AppSourceOperationWebSocketHandler(
                tickets, appSources, new ObjectMapper().findAndRegisterModules(), ORIGIN, Duration.ofMillis(1))
                .handle(session)
                .block(Duration.ofSeconds(2));

        assertThat(session.sentText()).hasSize(2);
        JsonNode snapshot = new ObjectMapper().readTree(session.sentText().getFirst());
        assertThat(snapshot.path("type").asText()).isEqualTo("snapshot");
        assertThat(snapshot.path("operation").path("appId").asText()).isEqualTo(APP_A.value());
    }

    @Test
    void revocationRejectsNewTicketAndPreviouslyIssuedTicketAtWebSocketConnect() throws Exception {
        AtomicBoolean member = new AtomicBoolean(true);
        AppSourceApplicationService appSources = service(member);
        AppSourceOperationTicketService tickets = tickets(appSources, "ast_before_revoke");
        tickets.createTicket(principal(), OPERATION_ID, ORIGIN, "trace_before_revoke");

        member.set(false);

        assertThatThrownBy(() -> tickets.createTicket(
                        principal(), OPERATION_ID, ORIGIN, "trace_after_revoke"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        FakeWebSocketSession session = new FakeWebSocketSession("ast_before_revoke");
        new AppSourceOperationWebSocketHandler(
                tickets, appSources, new ObjectMapper().findAndRegisterModules(), ORIGIN, Duration.ofMillis(1))
                .handle(session)
                .block(Duration.ofSeconds(2));
        JsonNode failed = new ObjectMapper().readTree(session.sentText().getFirst());
        assertThat(failed.path("type").asText()).isEqualTo("failed");
        assertThat(failed.path("errorCode").asText()).isEqualTo("FORBIDDEN");
    }

    private AppSourceApplicationService service(AtomicBoolean member) {
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        CodeRepository repository = new CodeRepository(
                REPOSITORY_ID, "https://git.example.test/cross.git", "Cross", "cross",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), false,
                NOW.minusSeconds(3600), NOW);
        ApplicationDefinition appB = new ApplicationDefinition(
                APP_B, "Application B", true, NOW.minusSeconds(3600), NOW);
        AppSourceOperation operation = new AppSourceOperation(
                OPERATION_ID, APP_A, REPOSITORY_ID, null, 1L, new UserId("usr_a"),
                AppSourceOperationType.DOWNLOAD, "request-hash", AppSourceOperationStatus.SUCCEEDED,
                "trace_cross_operation", NOW.minusSeconds(30), NOW.minusSeconds(10));
        AppSourceSnapshot snapshot = new AppSourceSnapshot(
                REPOSITORY_ID, 1L, "cross", AppSourcePurpose.TEAM, new UserId("usr_a"),
                "main", "b".repeat(40), List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                "a".repeat(64), NOW.minusSeconds(30), NOW.plusSeconds(3570), AppSourceSnapshotStatus.ACTIVE,
                NOW.minusSeconds(30), NOW.minusSeconds(10));
        when(configuration.findRepository(REPOSITORY_ID)).thenReturn(java.util.Optional.of(repository));
        when(configuration.findApplicationsByRepository(REPOSITORY_ID)).thenReturn(List.of(appB));
        when(configuration.isActiveMember(APP_B, MEMBER_B)).thenAnswer(invocation -> member.get());
        when(appSources.findOperation(OPERATION_ID)).thenReturn(java.util.Optional.of(operation));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(java.util.Optional.of(snapshot));
        when(appSources.findSteps(OPERATION_ID)).thenReturn(List.of());
        when(appSources.findReplicas(REPOSITORY_ID, 1L)).thenReturn(List.of());
        return new AppSourceApplicationService(
                configuration, appSources, mock(UserRepository.class),
                mock(OpencodeProcessManagementRepository.class), mock(OpencodeProcessHeartbeatStore.class),
                mock(SshKeyEncryptionService.class), mock(AppSourceMaterializationRegistrar.class),
                mock(AppSourceReplicaRetryRegistrar.class), mock(AppSourceReplicaTaskDispatcher.class),
                mock(AppSourceWorkspaceOpener.class), new AppSourceIndexManager());
    }

    private AppSourceOperationTicketService tickets(
            AppSourceApplicationService appSources, String ticketId) {
        BackendInstanceIdentity identity = identity();
        return new AppSourceOperationTicketService(
                new AppSourceOperationTicketStore(Clock.fixed(NOW, ZoneOffset.UTC), () -> ticketId),
                appSources, new CurrentBackendWebSocketUrlFactory(identity), identity);
    }

    private WebTestClient client(
            AppSourceApplicationService appSources, AppSourceOperationTicketService tickets) {
        return WebTestClient.bindToController(new AppSourceOperationController(appSources, tickets))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal());
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
                "token", MEMBER_B, "B001", "B 成员", List.of("USER"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
    }

    private BackendInstanceIdentity identity() {
        return new BackendInstanceIdentity() {
            @Override public String instanceId() { return "instance-a"; }
            @Override public String linuxServerId() { return "server-a"; }
            @Override public String backendProcessId() { return "bjp_server_a"; }
            @Override public String listenUrl() { return "http://server-a:8080"; }
        };
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private final HandshakeInfo handshakeInfo;
        private final DataBufferFactory buffers = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();

        private FakeWebSocketSession(String ticket) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin(ORIGIN);
            handshakeInfo = new HandshakeInfo(
                    URI.create("ws://127.0.0.1:8080/api/internal/platform/workspace-management/"
                            + "app-source-operations/" + OPERATION_ID + "/ws?ticket=" + ticket),
                    headers, Mono.<Principal>empty(), null);
        }

        List<String> sentText() { return sentText; }
        @Override public String getId() { return "ws_cross_app"; }
        @Override public HandshakeInfo getHandshakeInfo() { return handshakeInfo; }
        @Override public DataBufferFactory bufferFactory() { return buffers; }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }
        @Override public Flux<WebSocketMessage> receive() { return Flux.never(); }
        @Override public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages).doOnNext(message -> sentText.add(message.getPayloadAsText())).then();
        }
        @Override public boolean isOpen() { return true; }
        @Override public Mono<Void> close(CloseStatus status) { return Mono.empty(); }
        @Override public Mono<CloseStatus> closeStatus() { return Mono.just(CloseStatus.NORMAL); }
        @Override public WebSocketMessage textMessage(String payload) {
            return new WebSocketMessage(WebSocketMessage.Type.TEXT,
                    buffers.wrap(payload.getBytes(StandardCharsets.UTF_8)));
        }
        @Override public WebSocketMessage binaryMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(buffers));
        }
        @Override public WebSocketMessage pingMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(buffers));
        }
        @Override public WebSocketMessage pongMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(buffers));
        }
    }
}
