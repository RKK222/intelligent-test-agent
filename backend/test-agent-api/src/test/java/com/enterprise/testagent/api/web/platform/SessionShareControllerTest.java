package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareCandidate;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

/** 会话协作分享 HTTP 契约、认证主体与分享头测试。 */
class SessionShareControllerTest {

    private static final UserId USER = new UserId("usr_share_controller");
    private static final UserId OWNER = new UserId("usr_share_owner");
    private static final SessionId SESSION = new SessionId("ses_share_controller");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_share_controller");
    private static final SessionShareId SHARE_ID = new SessionShareId(
            "shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    private static final String TRACE_ID = "trace_share_controller";
    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");

    @Test
    void createsOrUpdatesShareAndReturnsStableSharePath() {
        SessionCollaborationShareService service = mock(SessionCollaborationShareService.class);
        when(service.put(eq(USER), eq(SESSION), eq(3L), eq(NOW.plusSeconds(3600)),
                argThat(members -> members.size() == 1
                        && members.get(0).userId().equals(new UserId("usr_share_member"))
                        && members.get(0).canChat()), eq(TRACE_ID)))
                .thenReturn(share());

        authenticatedClient(service).put()
                .uri("/api/internal/platform/opencode-runtime/sessions/ses_share_controller/collaboration-share")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "expectedVersion":3,
                          "expiresAt":"2026-08-09T09:00:00Z",
                          "members":[{"userId":"usr_share_member","canChat":true}]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.shareId").isEqualTo(SHARE_ID.value())
                .jsonPath("$.data.sharePath").isEqualTo("/s/" + SHARE_ID.value())
                .jsonPath("$.data.members[0].unifiedAuthId").isEqualTo("ucid-member")
                .jsonPath("$.data.members[0].canChat").isEqualTo(true);
    }

    @Test
    void resolvesAccessFromDedicatedHeaderWithoutChangingAuthenticatedActor() {
        SessionCollaborationShareService service = mock(SessionCollaborationShareService.class);
        when(service.requireAccess(USER, SHARE_ID, false, TRACE_ID)).thenReturn(context());

        authenticatedClient(service).get()
                .uri("/api/internal/platform/opencode-runtime/session-shares/access")
                .header("X-Trace-Id", TRACE_ID)
                .header(SessionShareController.SHARE_HEADER, SHARE_ID.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.actorUserId").isEqualTo(USER.value())
                .jsonPath("$.data.executionOwnerUserId").isEqualTo(OWNER.value())
                .jsonPath("$.data.delegated").isEqualTo(true)
                .jsonPath("$.data.canChat").isEqualTo(true);

        verify(service).requireAccess(USER, SHARE_ID, false, TRACE_ID);
    }

    @Test
    void listsMinimalActiveCandidatesAndRequiresAuthentication() {
        SessionCollaborationShareService service = mock(SessionCollaborationShareService.class);
        when(service.findCandidates(eq(USER), eq("member"), argThat(page -> page.page() == 1 && page.size() == 20)))
                .thenReturn(new PageResponse<>(List.of(new SessionShareCandidate(
                        new UserId("usr_share_member"), "ucid-member", "协作成员")), 1, 20, 1));

        authenticatedClient(service).get()
                .uri("/api/internal/platform/opencode-runtime/session-share-candidates?q=member&page=1&size=20")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].userId").isEqualTo("usr_share_member")
                .jsonPath("$.data.items[0].unifiedAuthId").isEqualTo("ucid-member")
                .jsonPath("$.data.items[0].username").isEqualTo("协作成员")
                .jsonPath("$.data.items[0].passwordHash").doesNotExist();

        WebTestClient.bindToController(new SessionShareController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .get().uri("/api/internal/platform/opencode-runtime/session-shares")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void runtimeStreamPublishesActiveRunThenInvalidatesWhenPermissionIsRevoked() {
        SessionCollaborationShareService service = mock(SessionCollaborationShareService.class);
        RunApplicationService runService = mock(RunApplicationService.class);
        when(service.requireAccess(USER, SHARE_ID, false, TRACE_ID)).thenReturn(context());
        when(service.sessionUpdatedAt(org.mockito.ArgumentMatchers.any())).thenReturn(NOW);
        when(service.refreshAccess(USER, SHARE_ID, TRACE_ID)).thenThrow(new PlatformException(
                ErrorCode.SESSION_SHARE_EXPIRED, "分享已取消", java.util.Map.of("reason", "REVOKED")));
        when(runService.findActiveRun(SESSION)).thenReturn(Optional.of(new Run(
                new RunId("run_share_runtime_state"), SESSION, WORKSPACE, RunStatus.RUNNING,
                NOW, NOW, TRACE_ID)));
        SessionShareController controller = new SessionShareController(service, runService);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/platform/opencode-runtime/session-shares/runtime-state/events")
                .header("X-Trace-Id", TRACE_ID)
                .header(SessionShareController.SHARE_HEADER, SHARE_ID.value())
                .build());
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                "token", USER, "ucid-controller", "当前用户", List.of(), NOW, NOW.plusSeconds(3600)));

        StepVerifier.create(controller.runtimeStateEvents(SHARE_ID.value(), exchange)
                        .filter(event -> event.data() != null)
                        .take(2))
                .assertNext(event -> {
                    org.assertj.core.api.Assertions.assertThat(event.event())
                            .isEqualTo("session-share.snapshot");
                    org.assertj.core.api.Assertions.assertThat(event.data().activeRun().runId())
                            .isEqualTo("run_share_runtime_state");
                    org.assertj.core.api.Assertions.assertThat(event.data().sessionUpdatedAt())
                            .isEqualTo(NOW);
                })
                .assertNext(event -> {
                    org.assertj.core.api.Assertions.assertThat(event.event())
                            .isEqualTo("session-share.invalidated");
                    org.assertj.core.api.Assertions.assertThat(event.data().reason()).isEqualTo("REVOKED");
                })
                .verifyComplete();
    }

    private static WebTestClient authenticatedClient(SessionCollaborationShareService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER, "ucid-controller", "当前用户", List.of(), NOW, NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new SessionShareController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static SessionShare share() {
        return new SessionShare(
                SHARE_ID, SESSION, WORKSPACE, USER, SessionShareStatus.ACTIVE,
                NOW.plusSeconds(3600), 4,
                List.of(SessionShareMembership.active(
                        new UserId("usr_share_member"), "ucid-member", "协作成员", true, NOW)),
                NOW, NOW, null, TRACE_ID);
    }

    private static DelegatedOperationContext context() {
        return new DelegatedOperationContext(
                SHARE_ID, 4, USER, "ucid-controller", "当前用户", OWNER,
                SESSION, WORKSPACE, true, true, false, NOW.plusSeconds(3600));
    }
}
