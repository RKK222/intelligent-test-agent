package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.analytics.AiMessageFeedback;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackId;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.analytics.AiMessageFeedbackApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AiMessageFeedbackControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_feedback123456");
    private static final UserId OWNER_ID = new UserId("usr_owner123456789");
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

    @Test
    void sharedMemberFeedbackExecutesAsOwnerAndKeepsActorInAudit() {
        AiMessageFeedbackApplicationService service = org.mockito.Mockito.mock(AiMessageFeedbackApplicationService.class);
        SessionCollaborationShareService shareService = org.mockito.Mockito.mock(SessionCollaborationShareService.class);
        SessionId sessionId = new SessionId("ses_feedback123456");
        SessionShareId shareId = new SessionShareId("shr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        DelegatedOperationContext context = new DelegatedOperationContext(
                shareId, 3, USER_ID, "U1001", "feedback-user", OWNER_ID,
                sessionId, new WorkspaceId("wrk_feedback123456"), true, true, false, NOW.plusSeconds(3600));
        AiMessageFeedback ownerFeedback = new AiMessageFeedback(
                new AiMessageFeedbackId("fb_feedback1234567"), OWNER_ID, sessionId,
                new RunId("run_feedback123456"), MESSAGE_ID, AiMessageFeedbackRating.POSITIVE,
                null, null, "总行", "研发一部", "效能平台", TRACE_ID, NOW, NOW);
        when(shareService.requireAccess(USER_ID, shareId, true, TRACE_ID)).thenReturn(context);
        when(service.requireMessageSessionId(MESSAGE_ID)).thenReturn(sessionId);
        when(service.submitOrUpdate(OWNER_ID, MESSAGE_ID, "POSITIVE", null, null, TRACE_ID))
                .thenReturn(ownerFeedback);

        client(service, shareService).put()
                .uri("/api/internal/platform/opencode-runtime/messages/msg_feedback123456/feedback")
                .header("X-Trace-Id", TRACE_ID)
                .header(SessionShareController.SHARE_HEADER, shareId.value())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rating":"POSITIVE"}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).submitOrUpdate(OWNER_ID, MESSAGE_ID, "POSITIVE", null, null, TRACE_ID);
        verify(shareService).recordOperation(
                context, "MESSAGE_FEEDBACK_UPDATED", "MESSAGE", "msg_feedback123456", null,
                "SUCCESS", null, TRACE_ID);
    }

    private static WebTestClient client(AiMessageFeedbackApplicationService service) {
        return client(service, null);
    }

    private static WebTestClient client(
            AiMessageFeedbackApplicationService service,
            SessionCollaborationShareService shareService) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "feedback-user",
                "feedback-user",
                List.of("APP_ADMIN"),
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new AiMessageFeedbackController(service, shareService))
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
