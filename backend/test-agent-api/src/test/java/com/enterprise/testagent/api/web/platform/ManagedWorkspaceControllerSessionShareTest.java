package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.workspace.ManagedWorkspaceApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 协作成员的当前工作区 Git 操作以所属人执行，同时保留真实 actor。 */
class ManagedWorkspaceControllerSessionShareTest {

    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void sharedMemberReadsAndStagesWorkspaceGitAsOwner() {
        UserId actor = new UserId("usr_actor_12345678");
        UserId owner = new UserId("usr_owner_12345678");
        WorkspaceId workspaceId = new WorkspaceId("wrk_shared_12345678");
        SessionShareId shareId = new SessionShareId(
                "shr_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        ManagedWorkspaceApplicationService service = mock(ManagedWorkspaceApplicationService.class);
        SessionCollaborationShareService shareService = mock(SessionCollaborationShareService.class);
        DelegatedOperationContext context = new DelegatedOperationContext(
                shareId, 2, actor, "A0001", "协作者", owner,
                new SessionId("ses_shared_12345678"), workspaceId,
                true, true, false, Instant.parse("2026-08-10T00:00:00Z"));
        when(shareService.requireAccess(actor, shareId, false, TRACE_ID)).thenReturn(context);
        when(shareService.requireAccess(actor, shareId, true, TRACE_ID)).thenReturn(context);

        AuthPrincipal principal = new AuthPrincipal(
                "token", actor, "A0001", "协作者", List.of("USER"),
                Instant.parse("2026-08-09T00:00:00Z"), Instant.parse("2026-08-10T00:00:00Z"));
        WebTestClient client = WebTestClient.bindToController(new ManagedWorkspaceController(
                        service, mock(UserOpencodeProcessAssignmentService.class), shareService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.get()
                .uri("/api/internal/platform/workspace-management/workspaces/"
                        + workspaceId.value() + "/git-diff")
                .header("X-Trace-Id", TRACE_ID)
                .header(SessionShareController.SHARE_HEADER, shareId.value())
                .exchange()
                .expectStatus().isOk();
        client.post()
                .uri("/api/internal/platform/workspace-management/workspaces/"
                        + workspaceId.value() + "/git-stage")
                .header("X-Trace-Id", TRACE_ID)
                .header(SessionShareController.SHARE_HEADER, shareId.value())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"files":["src/Main.java"]}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).getWorkspaceGitDiff(workspaceId.value(), owner);
        verify(service).stageWorkspaceGitFiles(workspaceId.value(), List.of("src/Main.java"), owner);
        verify(shareService).recordOperation(
                context, "WORKSPACE_GIT_DIFF_READ", "WORKSPACE", workspaceId.value(), null,
                "SUCCESS", null, TRACE_ID);
        verify(shareService).recordOperation(
                context, "WORKSPACE_GIT_STAGE", "WORKSPACE", workspaceId.value(), null,
                "SUCCESS", null, TRACE_ID);
    }
}
