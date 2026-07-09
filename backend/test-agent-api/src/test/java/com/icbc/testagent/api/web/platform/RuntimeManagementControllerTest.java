package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementOpencodeProcess;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementOverview;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementBackendMetricHistory;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementBackendMetricSample;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementBackendProcess;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementContainerMetricHistory;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementContainerMetricSample;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementContainer;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementManager;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementManagedProcess;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementManagedProcessOwnership;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessControlResult;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementCommandService;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementQueryService;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class RuntimeManagementControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");

    @Test
    void superAdminCanQueryRuntimeManagementOverview() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        when(service.overview(
                        argThat(filter -> filter.status() == OpencodeServerProcessStatus.RUNNING
                                && filter.linuxServerId().equals(new LinuxServerId("10.8.0.12"))
                                && filter.containerId().equals(new OpencodeContainerId("ctr_01"))
                                && "process-user".equals(filter.username())
                                && filter.userId().equals(new UserId("usr_1234567890abcdef"))),
                        eq(new PageRequest(1, 20)),
                        eq("trace_1234567890abcdef")))
                .thenReturn(overview());
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/overview?status=RUNNING&linuxServerId=10.8.0.12&containerId=ctr_01&username=process-user&userId=usr_1234567890abcdef&page=1&size=20")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.summary.runningOpencodeProcesses").isEqualTo(1)
                .jsonPath("$.data.linuxServers[0].linuxServerId").isEqualTo("10.8.0.12")
                .jsonPath("$.data.backendProcesses[0].backendProcessId").isEqualTo("bjp_1234567890abcdef")
                .jsonPath("$.data.containers[0].currentProcesses").isEqualTo(1)
                .jsonPath("$.data.managers[0].connectionStatus").isEqualTo("CONNECTED")
                .jsonPath("$.data.managers[0].managedProcesses[0].port").isEqualTo(4096)
                .jsonPath("$.data.managers[0].managedProcesses[0].ownership").isEqualTo("BOUND")
                .jsonPath("$.data.managers[0].managedProcesses[0].processId").isEqualTo("ocp_1234567890abcdef")
                .jsonPath("$.data.managers[0].managedProcesses[0].username").isEqualTo("process-user")
                .jsonPath("$.data.managers[0].managedProcesses[0].bindingStatus").isEqualTo("ACTIVE")
                .jsonPath("$.data.managers[0].managedProcesses[0].startCommand").isEqualTo("XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs")
                .jsonPath("$.data.managerBackendConnections[0].status").isEqualTo("CONNECTED")
                .jsonPath("$.data.opencodeProcesses.items[0].processId").isEqualTo("ocp_1234567890abcdef")
                .jsonPath("$.data.opencodeProcesses.items[0].username").isEqualTo("process-user")
                .jsonPath("$.data.opencodeProcesses.items[0].bindingAgentId").isEqualTo("opencode")
                .jsonPath("$.data.opencodeProcesses.items[0].bindingStatus").isEqualTo("ACTIVE");
    }

    @Test
    void superAdminCanQueryRuntimeManagementUserProcessesByKeyword() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        OpencodeServerProcess process = process(OpencodeServerProcessStatus.STOPPED);
        when(service.userProcesses(
                        eq("process-user"),
                        eq(new PageRequest(1, 20)),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new PageResponse<>(
                        List.of(new RuntimeManagementOpencodeProcess(
                                process,
                                Optional.of(binding(process)),
                                Optional.of("process-user"),
                                "NOT_RUNNING",
                                "NOT_RUNNING",
                                true)),
                        1,
                        20,
                        1));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/user-processes?keyword=process-user&page=1&size=20")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].processId").isEqualTo("ocp_1234567890abcdef")
                .jsonPath("$.data.items[0].username").isEqualTo("process-user")
                .jsonPath("$.data.items[0].status").isEqualTo("STOPPED")
                .jsonPath("$.data.items[0].managerStatus").isEqualTo("NOT_RUNNING")
                .jsonPath("$.data.items[0].healthStatus").isEqualTo("NOT_RUNNING")
                .jsonPath("$.data.items[0].restartable").isEqualTo(true);
    }

    @Test
    void appAdminAndAnonymousUsersCannotQueryRuntimeManagementOverview() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).get()
                .uri("/api/internal/platform/opencode-runtime/management/overview")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        WebTestClient.bindToController(new RuntimeManagementController(
                        service,
                        org.mockito.Mockito.mock(RuntimeManagementCommandService.class),
                        org.mockito.Mockito.mock(RuntimeManagementBackendRoutingService.class)))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .get()
                .uri("/api/internal/platform/opencode-runtime/management/overview")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void superAdminCanRestartAndStopManagedProcess() {
        RuntimeManagementQueryService queryService = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        RuntimeManagementCommandService commandService = org.mockito.Mockito.mock(RuntimeManagementCommandService.class);
        when(commandService.restartManagedProcess(
                        eq(new OpencodeContainerId("ctr_01")),
                        eq(4096),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new OpencodeProcessControlResult(
                        "restart",
                        "STARTED",
                        4096,
                        12346L,
                        "http://10.8.0.12:4096",
                        "/data/opencode/session/4096",
                        "/data/opencode/.config/opencode/",
                        true,
                        "opencode server started",
                        "trace_1234567890abcdef"));
        when(commandService.stopManagedProcess(
                        eq(new OpencodeContainerId("ctr_01")),
                        eq(4097),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new OpencodeProcessControlResult(
                        "stop",
                        "STOPPED",
                        4097,
                        22345L,
                        "http://10.8.0.12:4097",
                        "/data/opencode/session/4097",
                        "/data/opencode/.config/opencode/",
                        true,
                        "opencode server stopped",
                        "trace_1234567890abcdef"));
        WebTestClient client = client(queryService, commandService, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/restart")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.command").isEqualTo("restart")
                .jsonPath("$.data.status").isEqualTo("STARTED")
                .jsonPath("$.data.pid").isEqualTo(12346);

        client.post()
                .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4097/stop")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.command").isEqualTo("stop")
                .jsonPath("$.data.status").isEqualTo("STOPPED")
                .jsonPath("$.data.message").isEqualTo("opencode server stopped");
    }

    @Test
    void superAdminCommandUsesBackendRoutingBeforeLocalManagerGateway() {
        RuntimeManagementQueryService queryService = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        RuntimeManagementCommandService commandService = org.mockito.Mockito.mock(RuntimeManagementCommandService.class);
        RuntimeManagementBackendRoutingService routingService = org.mockito.Mockito.mock(RuntimeManagementBackendRoutingService.class);
        when(routingService.forwardTargetForContainer(
                        org.mockito.Mockito.any(),
                        eq(new OpencodeContainerId("ctr_01"))))
                .thenReturn(Optional.of("10.8.0.22"));
        when(routingService.forward(
                        org.mockito.Mockito.any(),
                        eq("10.8.0.22"),
                        org.mockito.Mockito.any()))
                .thenReturn(ApiResponse.ok(new RuntimeManagementDtos.ManagedProcessCommandResponse(
                        "restart",
                        "STARTED",
                        4096,
                        12346L,
                        "http://10.8.0.22:4096",
                        "/data/opencode/session/4096",
                        "/data/opencode/.config/opencode/",
                        true,
                        "opencode server started",
                        "trace_1234567890abcdef"), "trace_1234567890abcdef"));
        WebTestClient client = client(queryService, commandService, routingService, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/restart")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.command").isEqualTo("restart")
                .jsonPath("$.data.status").isEqualTo("STARTED")
                .jsonPath("$.data.baseUrl").isEqualTo("http://10.8.0.22:4096");
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    @Test
    void invalidPaginationUsesUnifiedValidationError() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/overview?page=0&size=20")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void unknownProcessStatusUsesUnifiedValidationError() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/overview?status=UNKNOWN")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void invalidLinuxServerIdUsesUnifiedValidationError() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/overview?linuxServerId=bad:id")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details.linuxServerId").isEqualTo("bad:id");
    }

    @Test
    void superAdminCanQueryRuntimeMetricHistory() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        when(service.containerMetrics(
                        eq(new OpencodeContainerId("ctr_01")),
                        eq(Duration.ofMinutes(30)),
                        eq(720),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new RuntimeManagementContainerMetricHistory(
                        NOW,
                        new OpencodeContainerId("ctr_01"),
                        NOW.minusSeconds(3600),
                        NOW,
                        List.of(new RuntimeManagementContainerMetricSample(
                                NOW,
                                4,
                                2,
                                "cgroup",
                                12.5,
                                1024L,
                                512L,
                                50.0,
                                128.0,
                                256.0))));
        when(service.backendServerMetrics(
                        eq(new LinuxServerId("10.8.0.12")),
                        eq(Duration.ofMinutes(30)),
                        eq(720),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new RuntimeManagementBackendMetricHistory(
                        NOW,
                        new LinuxServerId("10.8.0.12"),
                        Optional.of(new BackendProcessId("bjp_1234567890abcdef")),
                        NOW.minusSeconds(3600),
                        NOW,
                        List.of(new RuntimeManagementBackendMetricSample(
                                NOW,
                                22.5,
                                8,
                                1.5,
                                1.2,
                                0.8,
                                2048L,
                                2048L,
                                1536L,
                                1280L,
                                512L,
                                25.0,
                                64L,
                                256L,
                                1024L,
                                768L,
                                256L,
                                25.0,
                                4096L,
                                3072L,
                                1024L,
                                25.0,
                                7.5,
                                0.6,
                                123456789L,
                                700L,
                                900L,
                                4096L,
                                32L,
                                50L,
                                1024L,
                                300L,
                                400L,
                                500L,
                                200L,
                                300L,
                                400L,
                                100L,
                                100L,
                                100L,
                                2L,
                                16L,
                                32L,
                                1L,
                                8L,
                                16L,
                                7L,
                                7L,
                                3L,
                                0.4,
                                42,
                                12,
                                48,
                                1000L))));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/metrics?windowMinutes=30&hours=48&maxPoints=720")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.containerId").isEqualTo("ctr_01")
                .jsonPath("$.data.samples[0].cpuUsagePercent").isEqualTo(12.5);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/linux-servers/10.8.0.12/backend-metrics?windowMinutes=30&maxPoints=720")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.linuxServerId").isEqualTo("10.8.0.12")
                .jsonPath("$.data.backendProcessId").isEqualTo("bjp_1234567890abcdef")
                .jsonPath("$.data.samples[0].memoryAvailableBytes").isEqualTo(1536)
                .jsonPath("$.data.samples[0].jvmProcessResidentMemoryBytes").isEqualTo(700)
                .jsonPath("$.data.samples[0].jvmHeapUsedBytes").isEqualTo(200)
                .jsonPath("$.data.samples[0].jvmGcCollectionCountDelta").isEqualTo(3)
                .jsonPath("$.data.samples[0].jvmThreadsDaemon").isEqualTo(12)
                .jsonPath("$.data.samples[0].jvmThreadsLive").isEqualTo(42);
    }

    @Test
    void metricHistoryAcceptsOnlyPresetWindowMinutes() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));
        int[] allowedWindows = {1, 30, 60, 360, 720, 1440};
        for (int windowMinutes : allowedWindows) {
            when(service.containerMetrics(
                            eq(new OpencodeContainerId("ctr_01")),
                            eq(Duration.ofMinutes(windowMinutes)),
                            eq(720),
                            eq("trace_1234567890abcdef")))
                    .thenReturn(new RuntimeManagementContainerMetricHistory(
                            NOW,
                            new OpencodeContainerId("ctr_01"),
                            NOW.minus(Duration.ofMinutes(windowMinutes)),
                            NOW,
                            List.of()));

            client.get()
                    .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/metrics?windowMinutes=" + windowMinutes)
                    .header("X-Trace-Id", "trace_1234567890abcdef")
                    .exchange()
                    .expectStatus().isOk();
            verify(service).containerMetrics(
                    eq(new OpencodeContainerId("ctr_01")),
                    eq(Duration.ofMinutes(windowMinutes)),
                    eq(720),
                    eq("trace_1234567890abcdef"));
            reset(service);
        }

        for (int windowMinutes : new int[] {0, 2, 3000}) {
            client.get()
                    .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/metrics?windowMinutes=" + windowMinutes)
                    .header("X-Trace-Id", "trace_1234567890abcdef")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.details.windowMinutes").isEqualTo(windowMinutes);
        }
    }

    @Test
    void metricHistoryDefaultsToOneHourAndKeepsLegacyHoursCompatibility() {
        RuntimeManagementQueryService service = org.mockito.Mockito.mock(RuntimeManagementQueryService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));
        when(service.containerMetrics(
                        eq(new OpencodeContainerId("ctr_01")),
                        eq(Duration.ofMinutes(60)),
                        eq(720),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new RuntimeManagementContainerMetricHistory(
                        NOW,
                        new OpencodeContainerId("ctr_01"),
                        NOW.minus(Duration.ofHours(1)),
                        NOW,
                        List.of()));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/metrics")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk();
        verify(service).containerMetrics(
                eq(new OpencodeContainerId("ctr_01")),
                eq(Duration.ofMinutes(60)),
                eq(720),
                eq("trace_1234567890abcdef"));
        reset(service);

        when(service.containerMetrics(
                        eq(new OpencodeContainerId("ctr_01")),
                        eq(Duration.ofHours(6)),
                        eq(720),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new RuntimeManagementContainerMetricHistory(
                        NOW,
                        new OpencodeContainerId("ctr_01"),
                        NOW.minus(Duration.ofHours(6)),
                        NOW,
                        List.of()));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/containers/ctr_01/metrics?hours=6")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk();
        verify(service).containerMetrics(
                eq(new OpencodeContainerId("ctr_01")),
                eq(Duration.ofHours(6)),
                eq(720),
                eq("trace_1234567890abcdef"));
    }

    private static WebTestClient client(RuntimeManagementQueryService service, List<String> roles) {
        return client(service, org.mockito.Mockito.mock(RuntimeManagementCommandService.class), roles);
    }

    private static WebTestClient client(
            RuntimeManagementQueryService service,
            RuntimeManagementCommandService commandService,
            List<String> roles) {
        return client(
                service,
                commandService,
                org.mockito.Mockito.mock(RuntimeManagementBackendRoutingService.class),
                roles);
    }

    private static WebTestClient client(
            RuntimeManagementQueryService service,
            RuntimeManagementCommandService commandService,
            RuntimeManagementBackendRoutingService routingService,
            List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_admin_1234567890"),
                "admin",
                "AUTH_1",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new RuntimeManagementController(
                        service,
                        commandService,
                        routingService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static RuntimeManagementOverview overview() {
        LinuxServer linuxServer = new LinuxServer(
                new LinuxServerId("10.8.0.12"),
                "10.8.0.12",
                LinuxServerStatus.READY,
                Map.of("cpu", 4),
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        BackendJavaProcess backendProcess = new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        OpencodeContainer container = new OpencodeContainer(
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-a",
                4096,
                4100,
                4,
                1,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        OpencodeContainerManager manager = new OpencodeContainerManager(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-manager.v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of("start", true),
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        OpencodeManagerBackendConnection connection = new OpencodeManagerBackendConnection(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new BackendProcessId("bjp_1234567890abcdef"),
                ManagerConnectionStatus.CONNECTED,
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        OpencodeServerProcess process = new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                12345L,
                "http://10.8.0.12:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                "trace_1234567890abcdef");
        UserOpencodeProcessBinding binding = new UserOpencodeProcessBinding(
                process.userId(),
                "opencode",
                process.processId(),
                process.linuxServerId(),
                process.port(),
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        return new RuntimeManagementOverview(
                NOW,
                new RuntimeManagementSummary(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                List.of(linuxServer),
                List.of(new RuntimeManagementBackendProcess(backendProcess, null)),
                List.of(new RuntimeManagementContainer(container, null)),
                List.of(new RuntimeManagementManager(
                        manager,
                        List.of(new RuntimeManagementManagedProcess(
                                4096,
                                12345L,
                                "http://10.8.0.12:4096",
                                "/data/opencode/session/4096",
                                "/data/opencode/.config/opencode/",
                                NOW,
                                "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
                                "trace_process",
                                RuntimeManagementManagedProcessOwnership.BOUND,
                                process.processId(),
                                process.status(),
                                process.healthMessage(),
                                process.userId(),
                                Optional.of("process-user"),
                                binding.agentId(),
                                binding.status(),
                                binding.updatedAt())))),
                List.of(connection),
                new PageResponse<>(List.of(new RuntimeManagementOpencodeProcess(process, Optional.of(binding), Optional.of("process-user"))), 1, 20, 1));
    }

    private static OpencodeServerProcess process(OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                12345L,
                "http://10.8.0.12:4096",
                status,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "process pid is not alive",
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static UserOpencodeProcessBinding binding(OpencodeServerProcess process) {
        return new UserOpencodeProcessBinding(
                process.userId(),
                "opencode",
                process.processId(),
                process.linuxServerId(),
                process.port(),
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }
}
