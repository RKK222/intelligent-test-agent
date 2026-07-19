package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ClusterResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ParameterResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessStatus;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class CommonParameterMemoryControllerTest {

    private static final String BASE = "/api/internal/platform/configuration-management/common-parameters/memory-values";
    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final String PROCESS_ID = "bjp_target_backend";
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    @Test
    void superAdminCanQueryAllAndOneJavaProcess() {
        CommonParameterMemoryBackendRoutingService service = mock(CommonParameterMemoryBackendRoutingService.class);
        ProcessResponse process = process(PROCESS_ID, ProcessStatus.SUCCESS);
        when(service.queryAll(any())).thenReturn(Mono.just(ClusterResponse.from(NOW, List.of(process))));
        when(service.queryOne(any(), eq(new BackendProcessId(PROCESS_ID)))).thenReturn(Mono.just(process));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get().uri(BASE).header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.totalProcesses").isEqualTo(1)
                .jsonPath("$.data.processes[0].backendProcessId").isEqualTo(PROCESS_ID)
                .jsonPath("$.data.processes[0].parameters[0].memoryValue").isEqualTo("20");

        client.get().uri(BASE + "/" + PROCESS_ID).header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.backendProcessId").isEqualTo(PROCESS_ID);
    }

    @Test
    void superAdminCanRefreshAllAndOneJavaProcess() {
        CommonParameterMemoryBackendRoutingService service = mock(CommonParameterMemoryBackendRoutingService.class);
        ProcessResponse process = process(PROCESS_ID, ProcessStatus.SUCCESS);
        when(service.refreshAll(any(), eq(TRACE_ID)))
                .thenReturn(Mono.just(ClusterResponse.from(NOW, List.of(process))));
        when(service.refreshOne(any(), eq(new BackendProcessId(PROCESS_ID)), eq(TRACE_ID)))
                .thenReturn(Mono.just(process));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post().uri(BASE + "/refresh").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.successfulProcesses").isEqualTo(1);

        client.post().uri(BASE + "/" + PROCESS_ID + "/refresh").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("SUCCESS");
    }

    @Test
    void partialClusterStillReturnsHttpOkAndSingleUnavailableReturns503() {
        CommonParameterMemoryBackendRoutingService service = mock(CommonParameterMemoryBackendRoutingService.class);
        ProcessResponse partial = process(PROCESS_ID, ProcessStatus.PARTIAL);
        ProcessResponse unavailable = ProcessResponse.unavailable(
                "bjp_offline_backend", "server-b", "http://server-b:8080", NOW,
                "PROCESS_UNAVAILABLE", "Java 进程不可用");
        when(service.refreshAll(any(), eq(TRACE_ID)))
                .thenReturn(Mono.just(ClusterResponse.from(NOW, List.of(partial, unavailable))));
        when(service.queryOne(any(), eq(new BackendProcessId("bjp_offline_backend"))))
                .thenReturn(Mono.error(new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "目标 Java 进程不可用",
                        Map.of("backendProcessId", "bjp_offline_backend"))));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post().uri(BASE + "/refresh").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.partiallySuccessfulProcesses").isEqualTo(1)
                .jsonPath("$.data.failedProcesses").isEqualTo(1);

        client.get().uri(BASE + "/bjp_offline_backend").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("OPENCODE_UNAVAILABLE");
    }

    @Test
    void appAdminCannotQueryMemoryValues() {
        CommonParameterMemoryBackendRoutingService service = mock(CommonParameterMemoryBackendRoutingService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).get()
                .uri(BASE)
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    private static WebTestClient client(CommonParameterMemoryBackendRoutingService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_admin_1234567890"),
                "admin",
                "AUTH_1",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new CommonParameterMemoryController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ProcessResponse process(String backendProcessId, ProcessStatus status) {
        return new ProcessResponse(
                backendProcessId,
                "server-a",
                "http://server-a:8080",
                "instance-a",
                NOW,
                status,
                status == ProcessStatus.SUCCESS ? null : "REFRESH_PARTIAL",
                status == ProcessStatus.SUCCESS ? null : "部分参数刷新失败",
                List.of(new ParameterResponse(
                        "NIGHT_EXECUTION_SLOT_CAPACITY",
                        "all",
                        "20",
                        "20",
                        NOW,
                        NOW,
                        status == ProcessStatus.SUCCESS ? "SUCCESS" : "FAILED",
                        status == ProcessStatus.SUCCESS ? null : "读取或应用失败")));
    }
}
