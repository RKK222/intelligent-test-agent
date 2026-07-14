package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeServiceStatus;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class UserOpencodeBackendRoutingWebFilterTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");

    @Test
    void routesUserProcessRequestToBoundBackendAndPreservesAuthTraceAndBody() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("server-b"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_1234567890abcdef","data":{"status":"READY"}}
                """);
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore(List.of(
                backend("bjp_1234567890abcdef", "server-b", "http://10.8.0.22:8080", NOW))), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post("/api/internal/agent/opencode/processes/me/initialize?force=true")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"reason\":\"retry\"}"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(httpClient.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo(
                    "http://10.8.0.22:8080/api/internal/agent/opencode/processes/me/initialize?force=true");
            assertThat(request.headers().firstValue("X-Trace-Id")).contains("trace_1234567890abcdef");
            assertThat(request.headers().firstValue(org.springframework.http.HttpHeaders.AUTHORIZATION)).contains("Bearer user-token");
            assertThat(request.headers().firstValue(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER)).contains("true");
        });
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void routedHeaderSkipsForwardingToAvoidLoops() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("10.8.0.22"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore("10.8.0.22"), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .get("/api/internal/agent/opencode/api/status")
                .header(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER, "true")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        assertThat(httpClient.requests).isEmpty();
    }

    @Test
    void weakHealthRequestSkipsBindingRoutingBecauseTargetComesFromQueryParameters() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore("server-b"), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me/health?linuxServerId=server-b&containerId=ctr_01&port=4096")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        assertThat(httpClient.requests).isEmpty();
        Mockito.verifyNoInteractions(assignmentService);
    }

    @Test
    void routesConfigurationWorkspaceCreationBecauseItRequiresUserOpencodeServer() {
        assertRequestIsForwarded("/api/internal/platform/configuration-management/applications/app_1/workspaces");
    }

    @Test
    void routesManagedWorkspaceVersionCreationBecauseItRequiresUserOpencodeServer() {
        assertRequestIsForwarded("/api/internal/platform/workspace-management/applications/app_1/workspace-templates/tpl_1/versions");
    }

    @Test
    void routesManagedWorkspaceGitPullBecauseItRequiresUserOpencodeServer() {
        assertRequestIsForwarded("/api/internal/platform/workspace-management/workspace-versions/ver_1/git-pull");
    }

    @Test
    void routesSideQuestionRunStartToActiveBindingWithoutCallingLocalChain() {
        assertRequestIsForwarded(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/side-question/runs");
    }

    @Test
    void routesManualQuestionRunStartToActiveBindingWithoutCallingLocalChain() {
        assertRequestIsForwarded(
                "/api/internal/platform/opencode-runtime/manual-question/runs");
    }

    @Test
    void routesAgentScopedSideQuestionRunStartToActiveBindingWithoutCallingLocalChain() {
        assertRequestIsForwarded(
                "/api/internal/agent/opencode/session/ses_1234567890abcdef/side-question/runs");
    }

    @Test
    void routedSideQuestionRunStartHeaderSkipsSecondForward() {
        assertRoutedSideQuestionRunStartSkipsSecondForward(
                "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/side-question/runs");
    }

    @Test
    void routedAgentScopedSideQuestionRunStartHeaderSkipsSecondForward() {
        assertRoutedSideQuestionRunStartSkipsSecondForward(
                "/api/internal/agent/opencode/session/ses_1234567890abcdef/side-question/runs");
    }

    private static void assertRoutedSideQuestionRunStartSkipsSecondForward(String path) {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore("server-b"), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post(path)
                .header(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER, "true")
                .body("{\"question\":\"what happened?\"}"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        assertThat(httpClient.requests).isEmpty();
        Mockito.verifyNoInteractions(assignmentService);
    }

    @Test
    void missingTargetBackendReturnsUnavailableWithoutCallingLocalController() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("10.8.0.22"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore("10.8.0.33"), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .body("{\"sessionId\":\"ses_1234567890abcdef\",\"prompt\":\"run\"}"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(httpClient.requests).isEmpty();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE.httpStatus());
    }

    @Test
    void startRunWithContextRoutesFromRedisWithoutReadingProcessAssignmentAndPreservesBody() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        ConversationRunContext context = Mockito.mock(ConversationRunContext.class);
        Mockito.when(context.linuxServerId()).thenReturn("server-b");
        Mockito.when(contextStore.resolveForRouting(
                        "ctx_secret",
                        USER_ID,
                        "opencode",
                        new SessionId("ses_1234567890abcdef")))
                .thenReturn(Optional.of(context));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(
                assignmentService,
                heartbeatStore("server-b"),
                httpClient,
                contextStore);
        String requestBody = "{\"sessionId\":\"ses_1234567890abcdef\",\"prompt\":\"run\","
                + "\"contextToken\":\"ctx_secret\"}";
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body(requestBody));

        filter.filter(exchange, chain(ignored -> Mono.empty())).block(Duration.ofSeconds(2));

        Mockito.verifyNoInteractions(assignmentService);
        assertThat(httpClient.requestBodies).containsExactly(requestBody);
        assertThat(httpClient.requests).singleElement().satisfies(request ->
                assertThat(request.uri().toString()).isEqualTo(
                        "http://server-b:8080/api/internal/agent/opencode/runs"));
    }

    @Test
    void localStartRunWithContextPreservesBodyForControllerWithoutReadingAssignment() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        ConversationRunContext context = Mockito.mock(ConversationRunContext.class);
        Mockito.when(context.linuxServerId()).thenReturn("10.8.0.21");
        Mockito.when(contextStore.resolveForRouting(
                        "ctx_local",
                        USER_ID,
                        "opencode",
                        new SessionId("ses_1234567890abcdef")))
                .thenReturn(Optional.of(context));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(
                assignmentService,
                heartbeatStore("server-b"),
                httpClient,
                contextStore);
        String requestBody = "{\"sessionId\":\"ses_1234567890abcdef\","
                + "\"contextToken\":\"ctx_local\",\"prompt\":\"local\"}";
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body(requestBody));
        List<String> controllerBodies = new ArrayList<>();

        filter.filter(exchange, chain(routedExchange -> DataBufferUtils.join(routedExchange.getRequest().getBody())
                .doOnNext(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    controllerBodies.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    DataBufferUtils.release(buffer);
                })
                .then())).block(Duration.ofSeconds(2));

        Mockito.verifyNoInteractions(assignmentService);
        assertThat(controllerBodies).containsExactly(requestBody);
        assertThat(httpClient.requests).isEmpty();
    }

    @Test
    void platformStartRunCompatibilityPathAlsoRoutesFromContextWithoutAssignmentLookup() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        ConversationRunContext context = Mockito.mock(ConversationRunContext.class);
        Mockito.when(context.linuxServerId()).thenReturn("server-b");
        Mockito.when(contextStore.resolveForRouting(
                        "ctx_platform",
                        USER_ID,
                        "opencode",
                        new SessionId("ses_1234567890abcdef")))
                .thenReturn(Optional.of(context));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(
                assignmentService,
                heartbeatStore("server-b"),
                httpClient,
                contextStore);
        String requestBody = "{\"sessionId\":\"ses_1234567890abcdef\","
                + "\"contextToken\":\"ctx_platform\",\"prompt\":\"compat\"}";
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post("/api/internal/platform/opencode-runtime/runs")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body(requestBody));

        filter.filter(exchange, chain(ignored -> Mono.empty())).block(Duration.ofSeconds(2));

        Mockito.verifyNoInteractions(assignmentService);
        assertThat(httpClient.requestBodies).containsExactly(requestBody);
        assertThat(httpClient.requests).singleElement().satisfies(request ->
                assertThat(request.uri().getRawPath()).isEqualTo("/api/internal/platform/opencode-runtime/runs"));
    }

    @Test
    void expiredContextAtRoutingLayerReturnsUnified409WithoutAssignmentLookup() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        Mockito.when(contextStore.resolveForRouting(
                        "ctx_expired",
                        USER_ID,
                        "opencode",
                        new SessionId("ses_1234567890abcdef")))
                .thenReturn(Optional.empty());
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(
                assignmentService,
                heartbeatStore("server-b"),
                httpClient,
                contextStore);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"sessionId\":\"ses_1234567890abcdef\",\"contextToken\":\"ctx_expired\"}"));

        filter.filter(exchange, chain(ignored -> Mono.empty())).block(Duration.ofSeconds(2));

        Mockito.verifyNoInteractions(assignmentService);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(409);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"CONVERSATION_CONTEXT_EXPIRED\"")
                .doesNotContain("ctx_expired");
    }

    @Test
    void presentButInvalidContextTokenFailsClosedWithoutAssignmentLookup() {
        for (String invalidToken : List.of("\"   \"", "123", "null")) {
            UserOpencodeProcessAssignmentService assignmentService =
                    Mockito.mock(UserOpencodeProcessAssignmentService.class);
            ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
            RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
            UserOpencodeBackendRoutingWebFilter filter = filter(
                    assignmentService,
                    heartbeatStore("server-b"),
                    httpClient,
                    contextStore);
            MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                    .post("/api/internal/agent/opencode/runs")
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .body("{\"sessionId\":\"ses_1234567890abcdef\",\"contextToken\":" + invalidToken + "}"));

            filter.filter(exchange, chain(ignored -> Mono.empty())).block(Duration.ofSeconds(2));

            Mockito.verifyNoInteractions(assignmentService, contextStore);
            assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(409);
            assertThat(exchange.getResponse().getBodyAsString().block())
                    .contains("\"code\":\"CONVERSATION_CONTEXT_EXPIRED\"");
            assertThat(httpClient.requests).isEmpty();
        }
    }

    @Test
    void oversizedStartRunBodyReturnsValidationErrorBeforeRoutingLookup() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = new UserOpencodeBackendRoutingWebFilter(
                new UserOpencodeBackendRoutingService(
                        assignmentService,
                        new WorkspaceServerIdentity("10.8.0.21"),
                        heartbeatStore("server-b"),
                        new ObjectMapper().findAndRegisterModules(),
                        httpClient,
                        contextStore,
                        64));
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"sessionId\":\"ses_1234567890abcdef\",\"prompt\":\"" + "x".repeat(64) + "\"}"));

        filter.filter(exchange, chain(ignored -> Mono.empty())).block(Duration.ofSeconds(2));

        Mockito.verifyNoInteractions(assignmentService, contextStore);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(400);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("\"maxBytes\":64");
        assertThat(httpClient.requests).isEmpty();
    }

    @Test
    void missingTargetBackendReturnsAllocationStatusForReadOnlyProcessStatus() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("server-b"));
        Mockito.when(assignmentService.allocationStatus(
                        Mockito.eq(USER_ID),
                        Mockito.eq("opencode"),
                        Mockito.anyString(),
                        Mockito.eq("trace_1234567890abcdef")))
                .thenReturn(allocatedStatus("目标服务器后端不可用，暂无法确认 TestAgent 进程健康状态"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore("10.8.0.33"), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(httpClient.requests).isEmpty();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(
                "\"status\":\"UNAVAILABLE\"",
                "\"serviceStatus\":\"NOT_RUNNING\"",
                "\"linuxServerId\":\"server-b\"",
                "\"port\":4097",
                "\"serviceAddress\":null");
    }

    @Test
    void failedForwardReturnsAllocationStatusForReadOnlyProcessStatus() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("server-b"));
        Mockito.when(assignmentService.allocationStatus(
                        Mockito.eq(USER_ID),
                        Mockito.eq("opencode"),
                        Mockito.anyString(),
                        Mockito.eq("trace_1234567890abcdef")))
                .thenReturn(allocatedStatus("目标服务器后端不可用，暂无法确认 TestAgent 进程健康状态"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}", true);
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore(List.of(
                backend("bjp_1234567890abcdef", "server-b", "http://10.8.0.22:8080", NOW))), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .build());

        filter.filter(exchange, chain(exchange1 -> Mono.empty())).block(Duration.ofSeconds(2));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(
                "\"status\":\"UNAVAILABLE\"",
                "\"serviceStatus\":\"NOT_RUNNING\"",
                "\"linuxServerId\":\"server-b\"",
                "\"port\":4097",
                "\"serviceAddress\":null");
    }

    @Test
    void duplicateBackendSnapshotsUseLatestHeartbeatForSameServer() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("10.8.0.22"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore(List.of(
                backend("bjp_old_backend", "10.8.0.22", "http://10.8.0.22:8080", NOW.minusSeconds(30)),
                backend("bjp_new_backend", "10.8.0.22", "http://10.8.0.22:18080", NOW))), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me")
                .build());

        filter.filter(exchange, chain(exchange1 -> Mono.empty())).block(Duration.ofSeconds(2));

        assertThat(httpClient.requests).singleElement().satisfies(request -> assertThat(request.uri().toString())
                .isEqualTo("http://10.8.0.22:18080/api/internal/agent/opencode/processes/me"));
    }

    @Test
    void forwardsWithinSameLinuxServerWhenSelectedBackendIsAnotherJavaProcess() {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("10.8.0.21"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_1234567890abcdef","data":{"status":"READY"}}
                """);
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore(List.of(
                backend("bjp_same_server_target", "10.8.0.21", "http://10.8.0.21:18080", NOW))), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me")
                .build());

        filter.filter(exchange, chain(exchange1 -> Mono.empty())).block(Duration.ofSeconds(2));

        assertThat(httpClient.requests).singleElement().satisfies(request -> assertThat(request.uri().toString())
                .isEqualTo("http://10.8.0.21:18080/api/internal/agent/opencode/processes/me"));
    }

    private static UserOpencodeProcessStatusResponse allocatedStatus(String message) {
        return new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.UNAVAILABLE,
                false,
                message,
                null,
                "server-b",
                null,
                4097,
                null,
                NOW,
                UserOpencodeServiceStatus.NOT_RUNNING,
                null);
    }

    private static void assertRequestIsForwarded(String path) {
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        Mockito.when(assignmentService.routingLinuxServerId(USER_ID, "opencode"))
                .thenReturn(Optional.of("10.8.0.22"));
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_1234567890abcdef","data":{}}
                """);
        UserOpencodeBackendRoutingWebFilter filter = filter(assignmentService, heartbeatStore("10.8.0.22"), httpClient);
        MockServerWebExchange exchange = authenticatedExchange(MockServerHttpRequest
                .post(path)
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"operationId\":\"op_1\"}"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(httpClient.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo("http://10.8.0.22:8080" + path);
            assertThat(request.headers().firstValue(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER)).contains("true");
        });
    }

    private static UserOpencodeBackendRoutingWebFilter filter(
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessHeartbeatStore heartbeatStore,
            RecordingHttpClient httpClient) {
        return new UserOpencodeBackendRoutingWebFilter(new UserOpencodeBackendRoutingService(
                assignmentService,
                new WorkspaceServerIdentity("10.8.0.21"),
                heartbeatStore,
                new ObjectMapper().findAndRegisterModules(),
                httpClient));
    }

    private static UserOpencodeBackendRoutingWebFilter filter(
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeProcessHeartbeatStore heartbeatStore,
            RecordingHttpClient httpClient,
            ConversationContextStore contextStore) {
        return new UserOpencodeBackendRoutingWebFilter(new UserOpencodeBackendRoutingService(
                assignmentService,
                new WorkspaceServerIdentity("10.8.0.21"),
                heartbeatStore,
                new ObjectMapper().findAndRegisterModules(),
                httpClient,
                contextStore));
    }

    private static WebFilterChain chain(java.util.function.Function<org.springframework.web.server.ServerWebExchange, Mono<Void>> delegate) {
        return delegate::apply;
    }

    private static MockServerWebExchange authenticatedExchange(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                "token-123",
                USER_ID,
                "alice",
                "u123",
                List.of(),
                Instant.parse("2026-06-29T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")));
        return exchange;
    }

    private static OpencodeProcessHeartbeatStore heartbeatStore(String linuxServerId) {
        return heartbeatStore(List.of(backend(
                "bjp_1234567890abcdef",
                linuxServerId,
                "http://" + linuxServerId + ":8080",
                NOW)));
    }

    private static OpencodeProcessHeartbeatStore heartbeatStore(List<BackendJavaProcess> backends) {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) {}
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {}
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) {}
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {}
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() {
                return backends.stream()
                        .map(backend -> new BackendRuntimeSnapshot(
                                new LinuxServer(
                                        backend.linuxServerId(),
                                        backend.linuxServerId().value(),
                                        LinuxServerStatus.READY,
                                        Map.of(),
                                        backend.lastHeartbeatAt(),
                                        backend.createdAt(),
                                        backend.updatedAt(),
                                        "trace_backend"),
                                backend))
                        .toList();
            }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() {}
        };
    }

    private static BackendJavaProcess backend(String processId, String linuxServerId, String listenUrl, Instant heartbeatAt) {
        return new BackendJavaProcess(
                new BackendProcessId(processId),
                new LinuxServerId(linuxServerId),
                listenUrl,
                BackendJavaProcessStatus.READY,
                NOW.minusSeconds(60),
                heartbeatAt,
                NOW.minusSeconds(60),
                heartbeatAt,
                "trace_backend");
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final int status;
        private final String responseBody;
        private final boolean failSend;
        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<String> requestBodies = new ArrayList<>();

        private RecordingHttpClient(int status, String responseBody) {
            this(status, responseBody, false);
        }

        private RecordingHttpClient(int status, String responseBody, boolean failSend) {
            this.status = status;
            this.responseBody = responseBody;
            this.failSend = failSend;
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            requests.add(request);
            requestBodies.add(readRequestBody(request));
            if (failSend) {
                throw new IOException("connection refused");
            }
            return new BytesResponse<>((T) responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8), request, status);
        }

        private static String readRequestBody(HttpRequest request) {
            if (request.bodyPublisher().isEmpty()) {
                return "";
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompletableFuture<String> completed = new CompletableFuture<>();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.writeBytes(bytes);
                }

                @Override
                public void onError(Throwable throwable) {
                    completed.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    completed.complete(output.toString(java.nio.charset.StandardCharsets.UTF_8));
                }
            });
            return completed.join();
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record BytesResponse<T>(T body, HttpRequest request, int statusCode) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("content-type", List.of("application/json")), (left, right) -> true);
        }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
