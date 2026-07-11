package com.icbc.testagent.api.web.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import com.icbc.testagent.opencode.runtime.runtime.SideQuestionRunStartResult;
import com.icbc.testagent.opencode.runtime.runtime.SideQuestionStreamingApplicationService;
import com.icbc.testagent.opencode.runtime.runtime.SideQuestionInput;
import com.icbc.testagent.opencode.runtime.runtime.SideQuestionResult;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.session.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AgentOpencodeRuntimeControllerTest {

    @Test
    void agentControllerExposesOpencodeOriginalAgentPath() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.listAgents(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "build")));
        stubWithAgent(service);
        WebTestClient client = client(service, null);

        client.get()
                .uri("/api/internal/agent/opencode/api/agent?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].id").isEqualTo("build");
    }

    @Test
    void agentControllerExposesOpencodeStatusPath() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.runtimeStatus(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("healthy", true));
        stubWithAgent(service);
        WebTestClient client = client(service, null);

        client.get()
                .uri("/api/internal/agent/opencode/api/status?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.healthy").isEqualTo(true);
    }

    @Test
    void agentControllerRepliesToPermissionThroughOpencodePath() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.replyPermission(
                        eq("ses_1234567890abcdef"),
                        eq("req_1"),
                        eq(Map.of("decision", "once")),
                        eq("trace_1234567890abcdef")))
                .thenReturn(Map.of("accepted", true));
        stubWithAgent(service);
        WebTestClient client = client(service, null);

        client.post()
                .uri("/api/internal/agent/opencode/permission/req_1/reply?sessionId=ses_1234567890abcdef")
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
    void agentControllerPassesAuthenticatedUserToRuntimeContext() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        UserId userId = new UserId("usr_1234567890abcdef");
        when(service.listAgents(eq("wrk_1234567890abcdef"), eq("trace_1234567890abcdef")))
                .thenReturn(List.of(Map.of("id", "build")));
        stubWithAgent(service);
        WebTestClient client = client(service, principal(userId));

        client.get()
                .uri("/api/internal/agent/opencode/api/agent?workspaceId=wrk_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        verify(service).withAgent(eq("opencode"), eq(userId), any());
    }

    @Test
    void agentControllerStartsSideQuestionRunWithPathAgentAndAuthenticatedUser() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        SideQuestionStreamingApplicationService streamingService =
                org.mockito.Mockito.mock(SideQuestionStreamingApplicationService.class);
        UserId userId = new UserId("usr_1234567890abcdef");
        when(streamingService.start(
                        eq(userId),
                        eq("opencode"),
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq("summarize the decision"),
                        eq("msg_1"),
                        eq("anthropic/claude-sonnet"),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new SideQuestionRunStartResult(new RunId("run_1234567890abcdef")));
        WebTestClient client = client(service, streamingService, principal(userId));

        client.post()
                .uri("/api/internal/agent/opencode/session/ses_1234567890abcdef/side-question/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"summarize the decision","messageId":"msg_1","model":"anthropic/claude-sonnet"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.runId").isEqualTo("run_1234567890abcdef");
    }

    @Test
    void agentControllerKeepsLegacySynchronousSideQuestionRoute() {
        OpencodeRuntimeApplicationService service = org.mockito.Mockito.mock(OpencodeRuntimeApplicationService.class);
        when(service.sideQuestion(
                        eq("ses_1234567890abcdef"),
                        eq(new SideQuestionInput("what happened?", "msg_1", "plan", "anthropic/claude-sonnet")),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new SideQuestionResult("legacy answer", false));
        stubWithAgent(service);
        WebTestClient client = client(service, null);

        client.post()
                .uri("/api/internal/agent/opencode/session/ses_1234567890abcdef/side-question")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"what happened?","messageId":"msg_1","agent":"plan","model":"anthropic/claude-sonnet"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.answer").isEqualTo("legacy answer")
                .jsonPath("$.data.compacted").isEqualTo(false);
    }

    private static void stubWithAgent(OpencodeRuntimeApplicationService service) {
        when(service.withAgent(eq("opencode"), nullable(UserId.class), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(2)).get());
    }

    private static WebTestClient client(OpencodeRuntimeApplicationService service, AuthPrincipal principal) {
        return client(service, org.mockito.Mockito.mock(SideQuestionStreamingApplicationService.class), principal);
    }

    private static WebTestClient client(
            OpencodeRuntimeApplicationService service,
            SideQuestionStreamingApplicationService streamingService,
            AuthPrincipal principal) {
        return WebTestClient.bindToController(new AgentOpencodeRuntimeController(service, streamingService))
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
