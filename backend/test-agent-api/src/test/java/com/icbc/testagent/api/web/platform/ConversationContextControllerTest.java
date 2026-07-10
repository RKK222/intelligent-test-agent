package com.icbc.testagent.api.web.platform;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.opencode.runtime.run.ConversationContextApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class ConversationContextControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @Test
    void authenticatedUserCanBootstrapConversationContext() {
        ConversationContextApplicationService service = mock(ConversationContextApplicationService.class);
        when(service.bootstrap(
                        eq(new UserId("usr_1234567890abcdef")),
                        eq("opencode"),
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new ConversationContextApplicationService.IssuedConversationContext("ctx_secret", context()));
        WebTestClient client = WebTestClient.bindToController(new ConversationContextController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.post()
                .uri("/api/internal/agent/opencode/sessions/ses_1234567890abcdef/run-context")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.contextToken").isEqualTo("ctx_secret")
                .jsonPath("$.data.contextVersion").isEqualTo(1)
                .jsonPath("$.data.expiresAt").isEqualTo("2026-07-11T00:00:00Z");
    }

    @Test
    void anonymousUserCannotBootstrapConversationContext() {
        ConversationContextApplicationService service = mock(ConversationContextApplicationService.class);
        WebTestClient client = WebTestClient.bindToController(new ConversationContextController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.post()
                .uri("/api/internal/agent/opencode/sessions/ses_1234567890abcdef/run-context")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private static ConversationRunContext context() {
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "demo",
                "/srv/workspaces/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-a",
                "trace_test");
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_ocp_1234567890abcdef"),
                "http://10.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("opencode"),
                NOW,
                NOW,
                "trace_test");
        Session session = new Session(
                new SessionId("ses_1234567890abcdef"),
                workspace.workspaceId(),
                "session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_test");
        AgentSessionBinding binding = new AgentSessionBinding(
                session.sessionId(),
                "opencode",
                "remote-session-1",
                node.executionNodeId(),
                NOW,
                NOW,
                "trace_test");
        return new ConversationRunContext(
                new UserId("usr_1234567890abcdef"),
                "opencode",
                "ocp_1234567890abcdef",
                "server-a",
                session,
                workspace,
                node,
                binding,
                1,
                NOW.plusSeconds(24 * 60 * 60));
    }

    private static org.springframework.web.server.WebFilter authenticatedUserFilter() {
        return (exchange, chain) -> {
            exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                    "token",
                    new UserId("usr_1234567890abcdef"),
                    "admin",
                    "admin",
                    List.of("APP_ADMIN"),
                    NOW,
                    NOW.plusSeconds(3600)));
            return chain.filter(exchange);
        };
    }
}
