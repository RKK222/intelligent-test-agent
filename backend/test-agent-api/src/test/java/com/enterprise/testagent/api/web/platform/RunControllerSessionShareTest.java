package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventSseMapper;
import com.enterprise.testagent.event.RunEventSseStreamService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** 验证分享头不会替换 AuthPrincipal，并以所属人执行、真实成员归因。 */
class RunControllerSessionShareTest {

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final UserId ACTOR = new UserId("usr_shared_actor");
    private static final UserId OWNER = new UserId("usr_session_owner");
    private static final SessionId SESSION = new SessionId("ses_shared_run_test");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_shared_run_test");
    private static final SessionShareId SHARE = new SessionShareId(
            "shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @Test
    void sharedStartUsesDelegatedContextAndReturnsActualSenderMetadata() {
        RunApplicationService runService = mock(RunApplicationService.class);
        SessionCollaborationShareService shareService = mock(SessionCollaborationShareService.class);
        DelegatedOperationContext context = context(true);
        when(shareService.requireAccess(ACTOR, SHARE, true, "trace_shared_run"))
                .thenReturn(context);
        when(shareService.findUsername(ACTOR)).thenReturn("分享成员");
        when(runService.startRun(
                        eq(context),
                        argThat(input -> SESSION.equals(input.sessionId())
                                && "run delegated tests".equals(input.effectivePrompt())),
                        eq("trace_shared_run")))
                .thenReturn(run());
        when(runService.storageMetadata(new RunId("run_shared_run_test"))).thenReturn(Optional.empty());

        WebTestClient client = WebTestClient.bindToController(new RunController(
                        runService, null, null, null, new RunEventSseMapper(), null, shareService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                            "token", ACTOR, "分享成员", "ucid_shared_actor", List.of("USER"),
                            NOW, NOW.plusSeconds(3600)));
                    return chain.filter(exchange);
                })
                .build();

        client.post()
                .uri("/api/internal/platform/opencode-runtime/runs")
                .header("X-Trace-Id", "trace_shared_run")
                .header(SessionShareController.SHARE_HEADER, SHARE.value())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sessionId":"ses_shared_run_test","prompt":"run delegated tests"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.messageSenderUserId").isEqualTo(ACTOR.value())
                .jsonPath("$.data.messageSenderUsername").isEqualTo("分享成员")
                .jsonPath("$.data.messageSenderUnifiedAuthId").isEqualTo("ucid_shared_actor")
                .jsonPath("$.data.messageSentBySharedUser").isEqualTo(true);

        verify(shareService).requireAccess(ACTOR, SHARE, true, "trace_shared_run");
    }

    @Test
    void sharedRunEventStreamClosesWhenShareAuthorizationChanges() {
        RunApplicationService runService = mock(RunApplicationService.class);
        RunEventSseStreamService eventStreamService = mock(RunEventSseStreamService.class);
        SessionCollaborationShareService shareService = mock(SessionCollaborationShareService.class);
        DelegatedOperationContext initial = context(true);
        when(shareService.requireAccess(ACTOR, SHARE, false, "trace_shared_sse"))
                .thenReturn(initial);
        when(shareService.refreshAccess(ACTOR, SHARE, "trace_shared_sse"))
                .thenThrow(new com.enterprise.testagent.common.error.PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.SESSION_SHARE_EXPIRED,
                        "会话分享已失效"));
        when(runService.eventStorageMode(new RunId("run_shared_run_test")))
                .thenReturn(RunStorageMode.REDIS_SUMMARY);
        when(eventStreamService.streamAfterWithSnapshot(
                        eq(new RunId("run_shared_run_test")),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(),
                        eq(100),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Flux.never());
        RunController controller = new RunController(
                runService, null, eventStreamService, null, new RunEventSseMapper(), null, shareService);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/platform/opencode-runtime/runs/run_shared_run_test/events")
                .header("X-Trace-Id", "trace_shared_sse")
                .header(SessionShareController.SHARE_HEADER, SHARE.value())
                .build());
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                "token", ACTOR, "分享成员", "ucid_shared_actor", List.of("USER"),
                NOW, NOW.plusSeconds(3600)));

        StepVerifier.create(controller.events(
                        null, "run_shared_run_test", null, SHARE.value(), null, exchange))
                .verifyComplete();

        verify(runService).requireRunAccess(initial, new RunId("run_shared_run_test"));
        verify(shareService).refreshAccess(ACTOR, SHARE, "trace_shared_sse");
    }

    private DelegatedOperationContext context(boolean canChat) {
        return new DelegatedOperationContext(
                SHARE, 3L, ACTOR, "ucid_shared_actor", "分享成员", OWNER,
                SESSION, WORKSPACE, canChat, true, false, NOW.plusSeconds(3600));
    }

    private Run run() {
        return new Run(
                new RunId("run_shared_run_test"), SESSION, WORKSPACE, RunStatus.RUNNING,
                NOW, NOW, "trace_shared_run")
                .withSource(com.enterprise.testagent.domain.session.ConversationSourceType.MANUAL, null, OWNER)
                .withMessageSender(ACTOR, "ucid_shared_actor", true);
    }
}
