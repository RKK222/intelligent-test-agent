package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.ExperienceWorkspaceApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class ExperienceWorkspaceControllerTest {

    private static final UserId USER_ID = new UserId("usr_experience");
    private static final String TRACE_ID = "trace_experience_open";
    private static final Instant NOW = Instant.parse("2026-08-09T13:00:00Z");

    @Test
    void openUsesReadyProcessServerAndReturnsExistingWorkspaceContract() {
        ExperienceWorkspaceApplicationService service = mock(ExperienceWorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignments = mock(UserOpencodeProcessAssignmentService.class);
        when(assignments.requireReadyProcess(USER_ID, "opencode", TRACE_ID))
                .thenReturn(assignment("server-a"));
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_exp_1234567890abcdef"),
                "体验工作区",
                "/srv/platform-experience",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-a",
                TRACE_ID);
        when(service.open(USER_ID, "server-a", TRACE_ID)).thenReturn(workspace);

        client(service, assignments).post()
                .uri("/api/internal/platform/workspace-management/workspaces/experience/open")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workspaceId").isEqualTo("wrk_exp_1234567890abcdef")
                .jsonPath("$.data.name").isEqualTo("体验工作区")
                .jsonPath("$.data.linuxServerId").isEqualTo("server-a")
                .jsonPath("$.data.rootPath").isEqualTo("workspace:wrk_exp_1234567890abcdef")
                .jsonPath("$.data.physicalRootPath").doesNotExist();

        verify(assignments).requireReadyProcess(USER_ID, "opencode", TRACE_ID);
        verify(service).open(USER_ID, "server-a", TRACE_ID);
    }

    private WebTestClient client(
            ExperienceWorkspaceApplicationService service,
            UserOpencodeProcessAssignmentService assignments) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "888888888",
                "体验用户",
                List.of("USER"),
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new ExperienceWorkspaceController(
                        service,
                        assignments,
                        ManagedWorkspacePathResolver.legacyOnly()))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserOpencodeProcessAssignment assignment(String linuxServerId) {
        return new UserOpencodeProcessAssignment(
                new ExecutionNode(
                        new ExecutionNodeId("node_experience_server"),
                        "http://server-a:4096",
                        ExecutionNodeStatus.READY,
                        0,
                        4,
                        100,
                        NOW.minusSeconds(60),
                        Set.of("opencode"),
                        NOW.minusSeconds(60),
                        NOW,
                        TRACE_ID),
                linuxServerId);
    }
}
