package com.icbc.testagent.api.web.platform;

import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerBackendDiscoveryService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerBackendEndpoint;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class ManagerBackendDiscoveryControllerTest {

    @Test
    void returnsBackendsWhenManagerTokenIsValid() {
        ManagerBackendDiscoveryService service = org.mockito.Mockito.mock(ManagerBackendDiscoveryService.class);
        when(service.discover("trace_1234567890abcdef"))
                .thenReturn(List.of(new ManagerBackendEndpoint(
                        "bjp_1234567890abcdef",
                        "10.8.0.21",
                        "http://10.8.0.21:8080",
                        "ws://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager/ws",
                        Instant.parse("2026-06-24T00:00:00Z"))));
        WebTestClient client = client(service);

        client.get()
                .uri("/api/internal/platform/opencode-runtime/manager-backends")
                .header("Authorization", "Bearer manager-secret")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].backendProcessId").isEqualTo("bjp_1234567890abcdef")
                .jsonPath("$.data[0].webSocketUrl").isEqualTo("ws://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager/ws");
    }

    @Test
    void rejectsMissingManagerToken() {
        WebTestClient client = client(org.mockito.Mockito.mock(ManagerBackendDiscoveryService.class));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/manager-backends")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private static WebTestClient client(ManagerBackendDiscoveryService service) {
        return WebTestClient.bindToController(new ManagerBackendDiscoveryController(
                        service,
                        new ManagerControlSettings(
                                "manager-secret",
                                "http://10.8.0.21:8080",
                                new LinuxServerId("10.8.0.21"),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                100)))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
