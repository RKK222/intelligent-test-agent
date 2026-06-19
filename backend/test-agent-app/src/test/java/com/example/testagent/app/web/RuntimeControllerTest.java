package com.example.testagent.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.testagent.app.run.RunApplicationService;
import com.example.testagent.app.workspace.WorkspaceApplicationService;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunStatus;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class RuntimeControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void workspaceControllerWrapsCreatedWorkspaceInApiResponse() {
        WorkspaceApplicationService service = org.mockito.Mockito.mock(WorkspaceApplicationService.class);
        when(service.createWorkspace(eq("Demo"), any(), eq("trace_1234567890abcdef")))
                .thenReturn(workspace());
        WebTestClient client = WebTestClient.bindToController(new WorkspaceController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/workspaces")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Demo","rootPath":"/tmp/demo"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Trace-Id", "trace_1234567890abcdef")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.traceId").isEqualTo("trace_1234567890abcdef")
                .jsonPath("$.data.workspaceId").isEqualTo("wrk_1234567890abcdef");
    }

    @Test
    void runControllerStartsRunAndReturnsRunningStatus() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        when(service.startRun(
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq("run the tests"),
                        eq("trace_1234567890abcdef")))
                .thenReturn(run());
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sessionId":"ses_1234567890abcdef","prompt":"run the tests"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.runId").isEqualTo("run_1234567890abcdef")
                .jsonPath("$.data.status").isEqualTo("RUNNING");
    }

    private static Workspace workspace() {
        return new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo",
                "/tmp/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Run run() {
        return new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }
}
