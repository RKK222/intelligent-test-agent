package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.workspace.UserWorkspaceQueryService;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 分享工作台读取绑定工作区时以所属人范围执行，不要求 actor 本身是工作区成员。 */
class WorkspaceControllerSessionShareTest {

    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void sharedMemberReadsExactWorkspaceWithoutMembershipFallback() {
        UserId actor = new UserId("usr_actor_12345678");
        UserId owner = new UserId("usr_owner_12345678");
        WorkspaceId workspaceId = new WorkspaceId("wrk_shared_12345678");
        WorkspaceApplicationService workspaceService = mock(WorkspaceApplicationService.class);
        UserWorkspaceQueryService userQuery = mock(UserWorkspaceQueryService.class);
        SessionCollaborationShareService shareService = mock(SessionCollaborationShareService.class);
        Workspace workspace = new Workspace(
                workspaceId, "协作工作区", "/srv/workspace", Instant.parse("2026-08-09T00:00:00Z"));
        DelegatedOperationContext context = new DelegatedOperationContext(
                new SessionShareId("shr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), 3, actor,
                "A0001", "协作者", owner, new SessionId("ses_shared_12345678"), workspaceId,
                false, true, false, Instant.parse("2026-08-10T00:00:00Z"));
        when(shareService.requireAccess(actor, context.shareId(), false, TRACE_ID))
                .thenReturn(context);
        when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);

        AuthPrincipal principal = new AuthPrincipal(
                "token", actor, "A0001", "协作者", List.of("USER"),
                Instant.parse("2026-08-09T00:00:00Z"), Instant.parse("2026-08-10T00:00:00Z"));
        WebTestClient client = WebTestClient.bindToController(new WorkspaceController(
                        workspaceService, userQuery, ManagedWorkspacePathResolver.legacyOnly(), shareService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.get()
                .uri("/api/internal/platform/workspace-management/workspaces/" + workspaceId.value())
                .header("X-Trace-Id", TRACE_ID)
                .header(SessionShareController.SHARE_HEADER, context.shareId().value())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workspaceId").isEqualTo(workspaceId.value())
                .jsonPath("$.data.rootPath").isEqualTo("workspace:" + workspaceId.value())
                .jsonPath("$.data.physicalRootPath").doesNotExist();

        verify(userQuery, never()).requireUserWorkspace(actor, workspaceId);
        verify(shareService).recordOperation(
                context, "WORKSPACE_READ", "WORKSPACE", workspaceId.value(), null,
                "SUCCESS", null, TRACE_ID);
    }
}
