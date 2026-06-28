package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.analytics.AiMessageFeedback;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackId;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRating;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.analytics.AiMessageFeedbackApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AiMessageFeedbackControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_feedback123456");
    private static final SessionMessageId MESSAGE_ID = new SessionMessageId("msg_feedback123456");
    private static final String TRACE_ID = "trace_feedback123456";

    @Test
    void authenticatedUserCanSubmitAndReadOwnFeedback() {
        AiMessageFeedbackApplicationService service = org.mockito.Mockito.mock(AiMessageFeedbackApplicationService.class);
        AiMessageFeedback feedback = feedback();
        when(service.submitOrUpdate(
                        eq(USER_ID),
                        eq(MESSAGE_ID),
                        eq("NEGATIVE"),
                        eq("WRONG_ANSWER"),
                        eq("不准确"),
                        eq(TRACE_ID)))
                .thenReturn(feedback);
        when(service.findMyFeedback(eq(USER_ID), eq(MESSAGE_ID))).thenReturn(Optional.of(feedback));
        WebTestClient client = client(service);

        client.put()
                .uri("/api/internal/platform/opencode-runtime/messages/msg_feedback123456/feedback")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rating":"NEGATIVE","reasonCode":"WRONG_ANSWER","comment":"不准确"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.feedbackId").isEqualTo("fb_feedback1234567")
                .jsonPath("$.data.rating").isEqualTo("NEGATIVE")
                .jsonPath("$.data.reasonCode").isEqualTo("WRONG_ANSWER");

        client.get()
                .uri("/api/internal/platform/opencode-runtime/messages/msg_feedback123456/feedback/me")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.messageId").isEqualTo(MESSAGE_ID.value());

        verify(service).submitOrUpdate(USER_ID, MESSAGE_ID, "NEGATIVE", "WRONG_ANSWER", "不准确", TRACE_ID);
        verify(service).findMyFeedback(USER_ID, MESSAGE_ID);
    }

    @Test
    void anonymousUserCannotSubmitFeedback() {
        AiMessageFeedbackApplicationService service = org.mockito.Mockito.mock(AiMessageFeedbackApplicationService.class);

        WebTestClient.bindToController(new AiMessageFeedbackController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .put()
                .uri("/api/internal/platform/opencode-runtime/messages/msg_feedback123456/feedback")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rating":"POSITIVE"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private static WebTestClient client(AiMessageFeedbackApplicationService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "feedback-user",
                "feedback-user",
                List.of("APP_ADMIN"),
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new AiMessageFeedbackController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static AiMessageFeedback feedback() {
        return new AiMessageFeedback(
                new AiMessageFeedbackId("fb_feedback1234567"),
                USER_ID,
                new SessionId("ses_feedback123456"),
                new RunId("run_feedback123456"),
                MESSAGE_ID,
                AiMessageFeedbackRating.NEGATIVE,
                AiMessageFeedbackReasonCode.WRONG_ANSWER,
                "不准确",
                "总行",
                "研发一部",
                "效能平台",
                TRACE_ID,
                NOW,
                NOW);
    }
}
