package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackId;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRating;
import com.icbc.testagent.domain.analytics.AiRunFeedback;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.analytics.AiRunFeedbackApplicationService;
import com.icbc.testagent.opencode.runtime.analytics.RunFeedbackState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AiRunFeedbackControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-15T13:30:00Z");
    private static final UserId USER_ID = new UserId("usr_run_feedback_api01");
    private static final RunId RUN_ID = new RunId("run_feedback_api_test01");
    private static final SessionId SESSION_ID = new SessionId("ses_feedback_api_test01");
    private static final String TRACE_ID = "trace_run_feedback_api";

    @Test
    void authenticatedUserCanSubmitReadAndBatchQueryRunFeedback() {
        AiRunFeedbackApplicationService service = org.mockito.Mockito.mock(AiRunFeedbackApplicationService.class);
        AiRunFeedback feedback = feedback();
        when(service.submitOrUpdate(USER_ID, RUN_ID, "POSITIVE", null, null, TRACE_ID)).thenReturn(feedback);
        when(service.findMyFeedback(USER_ID, RUN_ID)).thenReturn(Optional.of(feedback));
        when(service.findMyFeedbackStates(USER_ID, List.of(RUN_ID)))
                .thenReturn(List.of(new RunFeedbackState(RUN_ID, SESSION_ID, RunStatus.SUCCEEDED, Optional.of(feedback))));
        WebTestClient client = client(service);

        client.put().uri("/api/internal/platform/opencode-runtime/runs/{runId}/feedback", RUN_ID.value())
                .header("X-Trace-Id", TRACE_ID).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"rating\":\"POSITIVE\"}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.runId").isEqualTo(RUN_ID.value())
                .jsonPath("$.data.messageId").doesNotExist();

        client.get().uri("/api/internal/platform/opencode-runtime/runs/{runId}/feedback/me", RUN_ID.value())
                .header("X-Trace-Id", TRACE_ID).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.rating").isEqualTo("POSITIVE");

        client.post().uri("/api/internal/platform/opencode-runtime/run-feedbacks/me/query")
                .header("X-Trace-Id", TRACE_ID).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"runIds\":[\"%s\"]}".formatted(RUN_ID.value())).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0].runStatus").isEqualTo("SUCCEEDED")
                .jsonPath("$.data[0].feedback.runId").isEqualTo(RUN_ID.value());

        verify(service).findMyFeedbackStates(USER_ID, List.of(RUN_ID));
    }

    private WebTestClient client(AiRunFeedbackApplicationService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "feedback-user", "feedback-user", List.of("APP_ADMIN"), NOW, NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new AiRunFeedbackController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }

    private AiRunFeedback feedback() {
        return new AiRunFeedback(
                new AiMessageFeedbackId("fb_run_feedback_api01"), USER_ID, SESSION_ID, RUN_ID,
                AiMessageFeedbackRating.POSITIVE, null, null, "机构", "研发部", "部门", TRACE_ID, NOW, NOW);
    }
}
