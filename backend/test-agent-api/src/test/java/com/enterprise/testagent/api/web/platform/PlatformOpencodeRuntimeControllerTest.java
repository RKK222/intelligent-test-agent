package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionInput;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionResult;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionRunStartResult;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionStreamingApplicationService;
import com.enterprise.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class PlatformOpencodeRuntimeControllerTest {

    @Test
    void runtimeControllerListsAgentsThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.listAgents(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "build")));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/agents?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].id").isEqualTo("build");
    }

    @Test
    void runtimeControllerExposesStatusThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.runtimeStatus(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("healthy", true));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/status?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.healthy").isEqualTo(true);
    }

    @Test
    void runtimeControllerReadsEffectiveConfigForProviderAllowlist() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.getEffectiveConfig(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("enabled_providers", List.of("enterprise-qwen", "enterprise-deepseek")));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/config?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.enabled_providers[0]").isEqualTo("enterprise-qwen")
                .jsonPath("$.data.enabled_providers[1]").isEqualTo("enterprise-deepseek");
    }

    @Test
    void runtimeControllerRepliesToPermissionThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.replyPermission(
                        eq("ses_1234567890abcdef"),
                        eq("req_1"),
                        eq(Map.of("decision", "once")),
                        eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("accepted", true));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.post()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/permissions/req_1/reply")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"decision":"once"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.accepted").isEqualTo(true);
    }

    @Test
    void runtimeControllerListsMcpToolsThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.mcpTools(
                        eq("wrk_1234567890abcdef"),
                        eq("anthropic"),
                        eq("claude-sonnet"),
                        eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "bash")));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/mcp/tools?workspaceId=wrk_1234567890abcdef&provider=anthropic&model=claude-sonnet")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].id").isEqualTo("bash");
    }

    @Test
    void runtimeControllerSharesSessionThroughUnifiedResponse() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.shareSession(eq("ses_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("url", "https://opencode.ai/s/abc"));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.post()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/share")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.url").isEqualTo("https://opencode.ai/s/abc");
    }

    @Test
    void runtimeControllerAnswersSideQuestionWithoutExposingTemporarySession() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.sideQuestion(
                        eq("ses_1234567890abcdef"),
                        eq(new SideQuestionInput("what did we decide?", "msg_1", "plan", "anthropic/claude-sonnet")),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new SideQuestionResult("answer from fork", true));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.post()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/side-question")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"what did we decide?","messageId":"msg_1","agent":"plan","model":"anthropic/claude-sonnet"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.answer").isEqualTo("answer from fork")
                .jsonPath("$.data.compacted").isEqualTo(true);
    }

    @Test
    void runtimeControllerStartsAuthenticatedSideQuestionRunWithoutAcceptingAgentSelection() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        SideQuestionStreamingApplicationService streamingService =
                org.mockito.Mockito.mock(SideQuestionStreamingApplicationService.class);
        UserId userId = new UserId("usr_1234567890abcdef");
        when(streamingService.start(
                        eq(userId),
                        eq("opencode"),
                        eq(new com.enterprise.testagent.domain.session.SessionId("ses_1234567890abcdef")),
                        eq("what did we decide?"),
                        eq("msg_1"),
                        eq("anthropic/claude-sonnet"),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new SideQuestionRunStartResult(new RunId("run_1234567890abcdef")));
        WebTestClient client = client(service, streamingService, principal(userId));

        client.post()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/side-question/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"what did we decide?","messageId":"msg_1","model":"anthropic/claude-sonnet"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.runId").isEqualTo("run_1234567890abcdef");
    }

    @Test
    void runtimeControllerStartsManualQuestionRunWithoutMainSession() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        SideQuestionStreamingApplicationService streamingService =
                org.mockito.Mockito.mock(SideQuestionStreamingApplicationService.class);
        UserId userId = new UserId("usr_1234567890abcdef");
        when(streamingService.startManual(
                        eq(userId),
                        eq("opencode"),
                        eq(new com.enterprise.testagent.domain.workspace.WorkspaceId("wrk_1234567890abcdef")),
                        eq("how do I initialize?"),
                        eq("anthropic/claude-sonnet"),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new SideQuestionRunStartResult(new RunId("run_manual1234567890")));
        WebTestClient client = client(service, streamingService, principal(userId));

        client.post()
                .uri("/api/internal/platform/opencode-runtime/manual-question/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"workspaceId":"wrk_1234567890abcdef","question":"how do I initialize?","model":"anthropic/claude-sonnet"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.runId").isEqualTo("run_manual1234567890");
    }

    @Test
    void runtimeControllerRequiresAuthenticatedUserForStreamingSideQuestion() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        SideQuestionStreamingApplicationService streamingService =
                org.mockito.Mockito.mock(SideQuestionStreamingApplicationService.class);
        WebTestClient client = client(service, streamingService, null);

        client.post()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/side-question/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"question\":\"what did we decide?\"}")
                .exchange()
                .expectStatus().isUnauthorized();

        org.mockito.Mockito.verifyNoInteractions(streamingService);
    }

    @Test
    void runtimeControllerRejectsBlankStreamingSideQuestion() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        SideQuestionStreamingApplicationService streamingService =
                org.mockito.Mockito.mock(SideQuestionStreamingApplicationService.class);
        WebTestClient client = client(
                service,
                streamingService,
                principal(new UserId("usr_1234567890abcdef")));

        client.post()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/side-question/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"question\":\"   \"}")
                .exchange()
                .expectStatus().isBadRequest();

        org.mockito.Mockito.verifyNoInteractions(streamingService);
    }

    @Test
    void platformControllerPassesAuthenticatedUserToRuntimeContext() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        UserId userId = new UserId("usr_1234567890abcdef");
        when(service.runtimeStatus(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("healthy", true));
        stubWithUser(service);
        WebTestClient client = client(service, principal(userId));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/status?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        verify(service).withUser(eq(userId), any());
    }

    @Test
    void platformControllerKeepsNullUserContextForStaticTokenCompatibility() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.runtimeStatus(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("healthy", true));
        stubWithUser(service);
        WebTestClient client = client(service, null);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/status?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk();

        verify(service).withUser(isNull(), any());
    }

    private static void stubWithUser(OpencodeRuntimeApplicationService service) {
        when(service.withUser(nullable(UserId.class), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(1)).get());
    }

    private static WebTestClient client(OpencodeRuntimeApplicationService service, AuthPrincipal principal) {
        return client(service, org.mockito.Mockito.mock(SideQuestionStreamingApplicationService.class), principal);
    }

    private static WebTestClient client(
            OpencodeRuntimeApplicationService service,
            SideQuestionStreamingApplicationService streamingService,
            AuthPrincipal principal) {
        return WebTestClient.bindToController(new PlatformOpencodeRuntimeController(service, streamingService))
                .controllerAdvice(new GlobalExceptionHandler())
                .webFilter((exchange, chain) -> {
                    if (principal != null) {
                        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    }
                    return chain.filter(exchange);
                })
                .webFilter(new TraceIdWebFilter())
                .build();
    }

    private static AuthPrincipal principal(UserId userId) {
        return new AuthPrincipal(
                "token-123",
                userId,
                "alice",
                "u123",
                List.of(),
                Instant.parse("2026-06-19T00:00:00Z"),
                Instant.parse("2026-06-20T00:00:00Z"));
    }
}
