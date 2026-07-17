package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeAttention;
import com.enterprise.testagent.domain.session.SessionRuntimeState;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.session.SessionRuntimeStateApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 验证用户级会话运行状态 HTTP 摘要和 fetch SSE 边界契约。
 */
class SessionRuntimeStateControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final Instant NOW = Instant.parse("2026-07-08T08:00:00Z");

    @Test
    void returnsAuthenticatedUserRuntimeStateSummary() {
        SessionRuntimeStateApplicationService service =
                org.mockito.Mockito.mock(SessionRuntimeStateApplicationService.class);
        when(service.snapshot(USER_ID)).thenReturn(summary(1, 1));
        WebTestClient client = authenticatedClient(service);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/sessions/runtime-state")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.traceId").isEqualTo("trace_1234567890abcdef")
                .jsonPath("$.data.runningCount").isEqualTo(1)
                .jsonPath("$.data.questionCount").isEqualTo(1)
                .jsonPath("$.data.sessions[0].sessionId").isEqualTo("ses_runtime_question")
                .jsonPath("$.data.sessions[0].runId").isEqualTo("run_runtime_question")
                .jsonPath("$.data.sessions[0].runStatus").isEqualTo("RUNNING")
                .jsonPath("$.data.sessions[0].attention").isEqualTo("QUESTION")
                .jsonPath("$.data.sessions[0].attentionEventId").isEqualTo("evt_runtime_question");
    }

    @Test
    void rejectsUnauthenticatedRuntimeStateRequest() {
        WebTestClient client = WebTestClient.bindToController(new SessionRuntimeStateController(
                        org.mockito.Mockito.mock(SessionRuntimeStateApplicationService.class)))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.get()
                .uri("/api/internal/platform/opencode-runtime/sessions/runtime-state")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void runtimeStateEventsStreamInitialSnapshotAndUpdates() {
        SessionRuntimeStateApplicationService service =
                org.mockito.Mockito.mock(SessionRuntimeStateApplicationService.class);
        when(service.stream(USER_ID)).thenReturn(Flux.just(summary(0, 0), summary(1, 1)));
        WebTestClient client = authenticatedClient(service);

        var result = client.get()
                .uri("/api/internal/platform/opencode-runtime/sessions/runtime-state/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<RuntimeDtos.SessionRuntimeStateResponse>>() {
                });

        StepVerifier.create(result.getResponseBody())
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("session-runtime.snapshot");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().runningCount()).isZero();
                })
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("session-runtime.updated");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().runningCount()).isEqualTo(1);
                    assertThat(event.data().sessions().get(0).attention()).isEqualTo("QUESTION");
                })
                .verifyComplete();
    }

    private static WebTestClient authenticatedClient(SessionRuntimeStateApplicationService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "admin",
                "admin",
                List.of("APP_ADMIN"),
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new SessionRuntimeStateController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static SessionRuntimeStateSummary summary(int runningCount, int questionCount) {
        List<SessionRuntimeState> sessions = runningCount == 0
                ? List.of()
                : List.of(new SessionRuntimeState(
                        new SessionId("ses_runtime_question"),
                        new RunId("run_runtime_question"),
                        RunStatus.RUNNING,
                        questionCount > 0 ? SessionRuntimeAttention.QUESTION : null,
                        questionCount > 0 ? "evt_runtime_question" : null,
                        questionCount > 0 ? NOW : null,
                        NOW));
        return new SessionRuntimeStateSummary(runningCount, questionCount, sessions, NOW);
    }
}
