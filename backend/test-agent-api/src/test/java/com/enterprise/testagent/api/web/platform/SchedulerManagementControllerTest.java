package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class SchedulerManagementControllerTest {

    @Test
    void everyLegacySchedulerManagementRouteReturnsApiGone() {
        WebTestClient client = WebTestClient.bindToController(new SchedulerManagementController())
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.get()
                .uri("/api/internal/platform/scheduler-management/tasks")
                .header("X-Trace-Id", "trace_scheduler_gone_1")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody()
                .jsonPath("$.code").isEqualTo("API_GONE");

        client.post()
                .uri("/api/internal/platform/scheduler-management/runs/str_1234567890abcdef/stop")
                .header("X-Trace-Id", "trace_scheduler_gone_2")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody()
                .jsonPath("$.code").isEqualTo("API_GONE");
    }
}
