package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.opencode.runtime.run.RunApplicationService;
import com.icbc.testagent.opencode.runtime.run.RunDiffActionResponse;
import com.icbc.testagent.opencode.runtime.run.RunDiffApplicationService;
import com.icbc.testagent.opencode.runtime.run.RunDiffFileResponse;
import com.icbc.testagent.opencode.runtime.run.RunDiffResponse;
import com.icbc.testagent.opencode.runtime.run.RunMessageRecoveryService;
import com.icbc.testagent.opencode.runtime.run.StartRunInput;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.opencode.runtime.session.SessionApplicationService;
import com.icbc.testagent.workspace.WorkspaceDirectoryEntryResponse;
import com.icbc.testagent.workspace.WorkspaceDirectoryListResponse;
import com.icbc.testagent.workspace.WorkspaceDirectoryService;
import com.icbc.testagent.workspace.WorkspaceApplicationService;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.event.RunEventSseMapper;
import com.icbc.testagent.event.RunEventSsePayload;
import com.icbc.testagent.event.RunEventSseStreamService;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class RuntimeControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void workspaceControllerWrapsCreatedWorkspaceInApiResponse() {
        WorkspaceApplicationService service = org.mockito.Mockito.mock(WorkspaceApplicationService.class);
        when(service.createWorkspace(eq("Demo"), any(), eq("trace_1234567890abcdef")))
                .thenReturn(workspace());
        WebTestClient client = WebTestClient.bindToController(new WorkspaceController(service, org.mockito.Mockito.mock(WorkspaceDirectoryService.class)))
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
    void workspaceControllerAlsoExposesInternalPlatformWorkspaceUrl() {
        WorkspaceApplicationService service = org.mockito.Mockito.mock(WorkspaceApplicationService.class);
        when(service.createWorkspace(eq("Demo"), any(), eq("trace_1234567890abcdef")))
                .thenReturn(workspace());
        WebTestClient client = WebTestClient.bindToController(new WorkspaceController(service, org.mockito.Mockito.mock(WorkspaceDirectoryService.class)))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/internal/platform/workspace-management/workspaces")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Demo","rootPath":"/tmp/demo"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.traceId").isEqualTo("trace_1234567890abcdef")
                .jsonPath("$.data.workspaceId").isEqualTo("wrk_1234567890abcdef");
    }

    @Test
    void workspaceControllerListsSelectableDirectoriesOnCompatibilityAndInternalUrls() {
        WorkspaceApplicationService workspaceService = org.mockito.Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceDirectoryService directoryService = org.mockito.Mockito.mock(WorkspaceDirectoryService.class);
        when(directoryService.listDirectories("/Users/huang/workspace"))
                .thenReturn(new WorkspaceDirectoryListResponse(
                        "/Users/huang/workspace",
                        null,
                        List.of(new WorkspaceDirectoryEntryResponse("demo", "/Users/huang/workspace/demo"))));
        WebTestClient client = WebTestClient.bindToController(new WorkspaceController(workspaceService, directoryService))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/workspace-directories?path=/Users/huang/workspace")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.path").isEqualTo("/Users/huang/workspace")
                .jsonPath("$.data.entries[0].name").isEqualTo("demo")
                .jsonPath("$.data.entries[0].path").isEqualTo("/Users/huang/workspace/demo");

        client.get()
                .uri("/api/internal/platform/workspace-management/workspace-directories?path=/Users/huang/workspace")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.entries[0].name").isEqualTo("demo");
    }

    @Test
    void runControllerStartsRunAndReturnsRunningStatus() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        when(service.startRun(
                        eq(new UserId("usr_1234567890abcdef")),
                        argThat(input -> new SessionId("ses_1234567890abcdef").equals(input.sessionId())
                                && "run the tests".equals(input.effectivePrompt())),
                        eq("trace_1234567890abcdef")))
                .thenReturn(run());
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
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
                .jsonPath("$.data.status").isEqualTo("RUNNING")
                .jsonPath("$.data.tokens.input").isEqualTo(11)
                .jsonPath("$.data.tokens.output").isEqualTo(12)
                .jsonPath("$.data.costUsd").isEqualTo(0.25);
    }

    @Test
    void runControllerAlsoExposesInternalPlatformRunUrl() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        when(service.startRun(
                        eq(new UserId("usr_1234567890abcdef")),
                        argThat(input -> new SessionId("ses_1234567890abcdef").equals(input.sessionId())
                                && "run the tests".equals(input.effectivePrompt())),
                        eq("trace_1234567890abcdef")))
                .thenReturn(run());
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
                .build();

        client.post()
                .uri("/api/internal/platform/opencode-runtime/runs")
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
    void runControllerExposesAgentScopedRunUrl() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        when(service.startRun(
                        eq(new UserId("usr_1234567890abcdef")),
                        eq("opencode"),
                        argThat(input -> new SessionId("ses_1234567890abcdef").equals(input.sessionId())
                                && "run the tests".equals(input.effectivePrompt())),
                        eq("trace_1234567890abcdef")))
                .thenReturn(run());
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
                .build();

        client.post()
                .uri("/api/internal/agent/opencode/runs")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sessionId":"ses_1234567890abcdef","prompt":"run the tests"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.runId").isEqualTo("run_1234567890abcdef");
    }

    @Test
    void runControllerAcceptsPhase11PromptPartsPayload() {
        RunApplicationService service = org.mockito.Mockito.mock(RunApplicationService.class);
        when(service.startRun(
                        eq(new UserId("usr_1234567890abcdef")),
                        argThat(input -> new SessionId("ses_1234567890abcdef").equals(input.sessionId())
                                && "run the tests".equals(input.effectivePrompt())
                                && input.parts().size() == 1
                                && "build".equals(input.agent())
                                && "anthropic/claude-sonnet-4-5".equals(input.model())),
                        eq("trace_1234567890abcdef")))
                .thenReturn(run());
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
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
                        eq(new UserId("usr_1234567890abcdef")),
                        any(StartRunInput.class),
                        eq("trace_1234567890abcdef")))
                .thenAnswer(ignored -> {
                    assertThat(Thread.currentThread().getName()).contains("boundedElastic");
                    return Mono.just(run()).block();
                });
        WebTestClient client = WebTestClient.bindToController(new RunController(service, null, null))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
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
    void opencodeProcessControllerReturnsCurrentUserProcessStatusAndInitializesIt() {
        UserOpencodeProcessAssignmentService service = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        UserOpencodeProcessStatusResponse ready = new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.READY,
                false,
                "opencode 进程可用",
                "ocp_1234567890abcdef",
                "10.8.0.12",
                "ctr_01",
                4096,
                "http://10.8.0.12:4096",
                NOW);
        when(service.status(eq(new UserId("usr_1234567890abcdef")), eq("opencode"), eq("trace_1234567890abcdef")))
                .thenReturn(ready);
        when(service.initialize(eq(new UserId("usr_1234567890abcdef")), eq("opencode"), eq("trace_1234567890abcdef")))
                .thenReturn(ready);
        WebTestClient client = WebTestClient.bindToController(new UserOpencodeProcessController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
                .build();

        client.get()
                .uri("/api/internal/agent/opencode/processes/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("READY")
                .jsonPath("$.data.baseUrl").isEqualTo("http://10.8.0.12:4096")
                .jsonPath("$.data.serviceStatus").isEqualTo("RUNNING")
                .jsonPath("$.data.serviceAddress").isEqualTo("10.8.0.12:4096");

        client.post()
                .uri("/api/internal/agent/opencode/processes/me/initialize")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("READY")
                .jsonPath("$.data.processId").isEqualTo("ocp_1234567890abcdef")
                .jsonPath("$.data.serviceStatus").isEqualTo("RUNNING")
                .jsonPath("$.data.serviceAddress").isEqualTo("10.8.0.12:4096");
    }

    @Test
    void opencodeProcessControllerRequiresAuthenticationAndOpencodeAgent() {
        UserOpencodeProcessAssignmentService service = org.mockito.Mockito.mock(UserOpencodeProcessAssignmentService.class);
        doThrow(new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 opencode 用户进程"))
                .when(service).status(any(), eq("otheragent"), eq("trace_1234567890abcdef"));
        WebTestClient unauthenticatedClient = WebTestClient.bindToController(new UserOpencodeProcessController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        unauthenticatedClient.get()
                .uri("/api/internal/agent/opencode/processes/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        WebTestClient authenticatedClient = WebTestClient.bindToController(new UserOpencodeProcessController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter(authenticatedUserFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        authenticatedClient.get()
                .uri("/api/internal/agent/otheragent/processes/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
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
        when(eventStreamService.streamAfterWithSnapshot(
                        eq(new RunId("run_1234567890abcdef")),
                        eq("7"),
                        any(),
                        eq(100),
                        any()))
                .thenReturn(Flux.empty());
        WebTestClient client = WebTestClient.bindToController(new RunController(runService, null, eventStreamService))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/runs/run_1234567890abcdef/events?lastEventId=7")
                .exchange()
                .expectStatus().isOk();

        org.mockito.Mockito.verify(eventStreamService).streamAfterWithSnapshot(
                eq(new RunId("run_1234567890abcdef")),
                eq("7"),
                any(),
                eq(100),
                any());
    }

    @Test
    void runControllerAlsoExposesInternalPlatformEventUrl() {
        RunApplicationService runService = org.mockito.Mockito.mock(RunApplicationService.class);
        RunEventSseStreamService eventStreamService = org.mockito.Mockito.mock(RunEventSseStreamService.class);
        when(eventStreamService.streamAfterWithSnapshot(
                        eq(new RunId("run_1234567890abcdef")),
                        eq("7"),
                        any(),
                        eq(100),
                        any()))
                .thenReturn(Flux.empty());
        WebTestClient client = WebTestClient.bindToController(new RunController(runService, null, eventStreamService))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/internal/platform/opencode-runtime/runs/run_1234567890abcdef/events?lastEventId=7")
                .exchange()
                .expectStatus().isOk();

        org.mockito.Mockito.verify(eventStreamService).streamAfterWithSnapshot(
                eq(new RunId("run_1234567890abcdef")),
                eq("7"),
                any(),
                eq(100),
                any());
    }

    @Test
    void runControllerMergesOpencodeMessageSnapshotWithDurableStream() {
        RunApplicationService runService = org.mockito.Mockito.mock(RunApplicationService.class);
        RunEventSseStreamService eventStreamService = org.mockito.Mockito.mock(RunEventSseStreamService.class);
        RunMessageRecoveryService recoveryService = org.mockito.Mockito.mock(RunMessageRecoveryService.class);
        RunId runId = new RunId("run_1234567890abcdef");
        RunEventSsePayload snapshot = new RunEventSsePayload(
                "evt_live_snapshot",
                runId.value(),
                0,
                "message.updated",
                "trace_1234567890abcdef",
                NOW,
                Map.of("message", Map.of("id", "msg_1", "role", "assistant")));
        RunEventSsePayload durable = new RunEventSsePayload(
                "evt_1",
                runId.value(),
                1,
                "run.started",
                "trace_1234567890abcdef",
                NOW,
                Map.of("status", "RUNNING"));
        when(recoveryService.recover(eq(runId), eq("trace_1234567890abcdef"))).thenReturn(Flux.just(snapshot));
        when(eventStreamService.streamAfterWithSnapshot(eq(runId), eq(null), any(), eq(100), any()))
                .thenAnswer(invocation -> {
                    Flux<ServerSentEvent<RunEventSsePayload>> snapshotEvents = invocation.getArgument(4);
                    return Flux.concat(
                            snapshotEvents,
                            Flux.just(ServerSentEvent.<RunEventSsePayload>builder()
                                    .id("1")
                                    .event("run.started")
                                    .data(durable)
                                    .build()));
                });
        WebTestClient client = WebTestClient.bindToController(new RunController(
                        runService,
                        null,
                        eventStreamService,
                        recoveryService,
                        new RunEventSseMapper()))
                .webFilter(new TraceIdWebFilter())
                .build();

        List<RunEventSsePayload> payloads = client.get()
                .uri("/api/runs/run_1234567890abcdef/events")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .returnResult(RunEventSsePayload.class)
                .getResponseBody()
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).extracting(RunEventSsePayload::type)
                .containsExactly("message.updated", "run.started");
    }

    @Test
    void sessionControllerListsSearchesUpdatesAndSoftDeletesSessions() {
        SessionApplicationService service = org.mockito.Mockito.mock(SessionApplicationService.class);
        when(service.listSessions(eq("demo"), any()))
                .thenReturn(new PageResponse<>(List.of(session("Demo session", true, SessionStatus.ACTIVE)), 1, 20, 1));
        when(service.updateSession(
                        eq(new SessionId("ses_1234567890abcdef")),
                        eq("Renamed"),
                        eq(false),
                        eq("trace_1234567890abcdef")))
                .thenReturn(session("Renamed", false, SessionStatus.ACTIVE));
        when(service.archiveSession(eq(new SessionId("ses_1234567890abcdef")), eq("trace_1234567890abcdef")))
                .thenReturn(session("Renamed", false, SessionStatus.ARCHIVED));
        WebTestClient client = WebTestClient.bindToController(new SessionController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/sessions?q=demo&page=1&size=20")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].title").isEqualTo("Demo session")
                .jsonPath("$.data.items[0].pinned").isEqualTo(true);

        client.patch()
                .uri("/api/sessions/ses_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"title":"Renamed","pinned":false}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.title").isEqualTo("Renamed")
                .jsonPath("$.data.pinned").isEqualTo(false);

        client.delete()
                .uri("/api/sessions/ses_1234567890abcdef")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("ARCHIVED");
    }

    @Test
    void sessionControllerAlsoExposesInternalPlatformSessionUrl() {
        SessionApplicationService service = org.mockito.Mockito.mock(SessionApplicationService.class);
        when(service.listSessions(eq("demo"), any()))
                .thenReturn(new PageResponse<>(List.of(session("Demo session", true, SessionStatus.ACTIVE)), 1, 20, 1));
        WebTestClient client = WebTestClient.bindToController(new SessionController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/internal/platform/opencode-runtime/sessions?q=demo&page=1&size=20")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].title").isEqualTo("Demo session")
                .jsonPath("$.data.items[0].pinned").isEqualTo(true);
    }

    @Test
    void sessionControllerOffloadsMessageSnapshotRefreshFromReactorThread() {
        SessionApplicationService service = org.mockito.Mockito.mock(SessionApplicationService.class);
        AtomicBoolean calledOnNonBlockingThread = new AtomicBoolean(true);
        when(service.listMessages(
                        eq(new SessionId("ses_1234567890abcdef")),
                        any(),
                        eq("trace_1234567890abcdef")))
                .thenAnswer(ignored -> {
                    calledOnNonBlockingThread.set(Schedulers.isInNonBlockingThread());
                    return new PageResponse<>(List.of(), 1, 20, 0);
                });
        WebTestClient client = WebTestClient.bindToController(new SessionController(service))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/sessions/ses_1234567890abcdef/messages?page=1&size=20")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items").isArray();

        assertThat(calledOnNonBlockingThread).isFalse();
    }

    private static org.springframework.web.server.WebFilter authenticatedUserFilter() {
        return (exchange, chain) -> {
            exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                    "token",
                    new UserId("usr_1234567890abcdef"),
                    "admin",
                    "admin",
                    List.of("APP_ADMIN"),
                    NOW,
                    NOW.plusSeconds(3600)));
            return chain.filter(exchange);
        };
    }

    @Test
    void sessionControllerReturnsLatestActiveRunForRefreshRecovery() {
        SessionApplicationService sessionService = org.mockito.Mockito.mock(SessionApplicationService.class);
        RunApplicationService runService = org.mockito.Mockito.mock(RunApplicationService.class);
        when(runService.findActiveRun(eq(new SessionId("ses_1234567890abcdef"))))
                .thenReturn(Optional.of(run()));
        WebTestClient client = WebTestClient.bindToController(new SessionController(sessionService, runService))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.get()
                .uri("/api/sessions/ses_1234567890abcdef/active-run")
                .header("X-Trace-Id", "trace_1234567890abcdef")
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
                "trace_1234567890abcdef")
                .withUsage(new TokenUsage(11L, 12L, null, null, null), new BigDecimal("0.25"));
    }

    private static Session session(String title, boolean pinned, SessionStatus status) {
        return new Session(
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                title,
                status,
                NOW,
                NOW,
                "trace_1234567890abcdef",
                null,
                null,
                pinned);
    }
}
