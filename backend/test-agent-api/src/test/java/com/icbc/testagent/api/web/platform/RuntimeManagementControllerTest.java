package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementQueryService;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementSummary;
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
                                && filter.userId().equals(new UserId("usr_1234567890abcdef"))),
                        eq(new PageRequest(1, 20)),
                        eq("trace_1234567890abcdef")))
                .thenReturn(overview());
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/management/overview?status=RUNNING&linuxServerId=10.8.0.12&containerId=ctr_01&userId=usr_1234567890abcdef&page=1&size=20")
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
                List.of(backendProcess),
                List.of(container),
                List.of(manager),
                List.of(connection),
                new PageResponse<>(List.of(new RuntimeManagementOpencodeProcess(process, Optional.of(binding))), 1, 20, 1));
    }
}
