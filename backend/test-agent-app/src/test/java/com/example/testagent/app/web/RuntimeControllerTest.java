package com.example.testagent.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.testagent.app.run.RunApplicationService;
import com.example.testagent.app.run.RunDiffActionResponse;
import com.example.testagent.app.run.RunDiffApplicationService;
import com.example.testagent.app.run.RunDiffFileResponse;
import com.example.testagent.app.run.RunDiffResponse;
import com.example.testagent.app.workspace.WorkspaceApplicationService;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunStatus;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceStatus;
import com.example.testagent.event.RunEventSseStreamService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
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

    @Test
    void runControllerAcceptsPhase11PromptPartsPayload() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        when(service.startRun(
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq("run the tests"),
                        eq("trace_1234567890abcdef")))
                .thenReturn(run());
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "sessionId":"ses_1234567890abcdef",
                          "parts":[{"type":"text","text":"run the tests"}],
                          "agent":"build",
                          "model":"anthropic/claude-sonnet-4-5",
                          "variant":"default",
                          "mode":"build"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("RUNNING");
    }

    @Test
    void runControllerRejectsRunStartWithoutPromptOrTextPart() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.post()
                .uri("/api/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sessionId":"ses_1234567890abcdef","parts":[{"type":"agent","agentId":"build"}]}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void runControllerOffloadsBlockingRunStartFromWebFluxThread() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        when(service.startRun(
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq("run the tests"),
                        eq("trace_1234567890abcdef")))
                .thenAnswer(ignored -> {
                    assertThat(Thread.currentThread().getName()).contains("boundedElastic");
                    return Mono.just(run()).block();
                });
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
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
                .jsonPath("$.traceId").isEqualTo("trace_1234567890abcdef")
                .jsonPath("$.data.status").isEqualTo("RUNNING");
    }

    @Test
    void runControllerExposesDiffAndDiffActions() {
        RunApplicationService runService = org.mockito.Mockito.mock(RunApplicationService.class);
        RunDiffApplicationService diffService = org.mockito.Mockito.mock(RunDiffApplicationService.class);
        when(diffService.getDiff(eq(new RunId("run_1234567890abcdef")), eq("trace_1234567890abcdef")))
                .thenReturn(new RunDiffResponse(
                        "run_1234567890abcdef",
                        List.of(new RunDiffFileResponse("src/App.tsx", "@@", 2, 1, "modified"))));
        when(diffService.acceptDiff(eq(new RunId("run_1234567890abcdef")), eq("trace_1234567890abcdef")))
                .thenReturn(new RunDiffActionResponse("run_1234567890abcdef", "accept", "accepted", 1));
        when(diffService.rejectDiff(eq(new RunId("run_1234567890abcdef")), eq("trace_1234567890abcdef")))
                .thenReturn(new RunDiffActionResponse("run_1234567890abcdef", "reject", "rejected", 1));
        WebTestClient client = WebTestClient.bindToController(new RunController(runService, diffService, null))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/runs/run_1234567890abcdef/diff")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.files[0].path").isEqualTo("src/App.tsx")
                .jsonPath("$.data.files[0].additions").isEqualTo(2);

        client.post()
                .uri("/api/runs/run_1234567890abcdef/diff/accept")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.action").isEqualTo("accept")
                .jsonPath("$.data.status").isEqualTo("accepted");

        client.post()
                .uri("/api/runs/run_1234567890abcdef/diff/reject")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.action").isEqualTo("reject")
                .jsonPath("$.data.status").isEqualTo("rejected");
    }

    @Test
    void runControllerAcceptsLastEventIdFromQueryForBrowserEventSource() {
        RunApplicationService runService = org.mockito.Mockito.mock(RunApplicationService.class);
        RunEventSseStreamService eventStreamService = org.mockito.Mockito.mock(RunEventSseStreamService.class);
        when(eventStreamService.streamAfter(
                        eq(new RunId("run_1234567890abcdef")),
                        eq("7"),
                        any(),
                        eq(100)))
                .thenReturn(Flux.empty());
        WebTestClient client = WebTestClient.bindToController(new RunController(runService, null, eventStreamService))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/runs/run_1234567890abcdef/events?lastEventId=7")
                .exchange()
                .expectStatus().isOk();

        org.mockito.Mockito.verify(eventStreamService).streamAfter(
                eq(new RunId("run_1234567890abcdef")),
                eq("7"),
                any(),
                eq(100));
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
