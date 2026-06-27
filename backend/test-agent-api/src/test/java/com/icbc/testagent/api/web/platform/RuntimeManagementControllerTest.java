package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
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
                .jsonPath("$.data.managerBackendConnections[0].status").isEqualTo("CONNECTED")
                .jsonPath("$.data.opencodeProcesses.items[0].processId").isEqualTo("ocp_1234567890abcdef")
                .jsonPath("$.data.opencodeProcesses.items[0].username").isEqualTo("process-user")
                .jsonPath("$.data.opencodeProcesses.items[0].bindingAgentId").isEqualTo("opencode")
                .jsonPath("$.data.opencodeProcesses.items[0].bindingStatus").isEqualTo("ACTIVE");
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

        WebTestClient.bindToController(new RuntimeManagementController(service))
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
                .uri("/api/internal/platform/opencode-runtime/management/overview?linuxServerId=not-an-ip")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details.linuxServerId").isEqualTo("not-an-ip");
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
        when(service.backendProcessMetrics(
                        eq(new BackendProcessId("bjp_1234567890abcdef")),
                        eq(Duration.ofMinutes(30)),
                        eq(720),
                        eq("trace_1234567890abcdef")))
                .thenReturn(new RuntimeManagementBackendMetricHistory(
                        NOW,
                        new BackendProcessId("bjp_1234567890abcdef"),
                        NOW.minusSeconds(3600),
                        NOW,
                        List.of(new RuntimeManagementBackendMetricSample(
                                NOW,
                                22.5,
                                2048L,
                                1024L,
                                50.0,
                                4096L,
                                1024L,
                                25.0,
                                300L,
                                400L,
                                500L,
                                7L,
                                42))));
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
                .uri("/api/internal/platform/opencode-runtime/management/backend-processes/bjp_1234567890abcdef/metrics?windowMinutes=30&maxPoints=720")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.backendProcessId").isEqualTo("bjp_1234567890abcdef")
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
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_admin_1234567890"),
                "admin",
                "AUTH_1",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new RuntimeManagementController(service))
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
                List.of(manager),
                List.of(connection),
                new PageResponse<>(List.of(new RuntimeManagementOpencodeProcess(process, Optional.of(binding), Optional.of("process-user"))), 1, 20, 1));
    }
}
