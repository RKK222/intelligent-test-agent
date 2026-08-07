package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.internalmodel.observability.InternalModelObservabilityQueryService;
import com.enterprise.testagent.opencode.runtime.internalmodel.observability.InternalModelProviderProbeService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class InternalModelObservabilityControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void superAdminCanQueryCallRecords() {
        InternalModelObservabilityQueryService queryService = mock(InternalModelObservabilityQueryService.class);
        InternalModelCallRecord record = new InternalModelCallRecord(
                null, "enterprise-deepseek", "DeepSeek-V4", "/chat/completions",
                InternalModelCallSource.USER_CALL, InternalModelCallOutcome.UPSTREAM_HTTP_ERROR,
                500, "WebClientResponseException", true, 800L, 50L,
                TRACE_ID, "ucid", NOW);
        when(queryService.queryCallRecords(
                        eq("enterprise-deepseek"), any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(record), 1, 20, 1));
        WebTestClient client = client(queryService, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/call-records?providerId=enterprise-deepseek")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].providerId").isEqualTo("enterprise-deepseek")
                .jsonPath("$.data.items[0].outcome").isEqualTo("UPSTREAM_HTTP_ERROR")
                .jsonPath("$.data.items[0].httpStatus").isEqualTo(500)
                .jsonPath("$.data.items[0].source").isEqualTo("USER_CALL");

        verify(queryService).queryCallRecords(
                eq("enterprise-deepseek"), any(), any(), any(), any(), any(PageRequest.class));
    }

    @Test
    void onlySuperAdminCanQueryStatsAndProbeStatus() {
        InternalModelObservabilityQueryService queryService = mock(InternalModelObservabilityQueryService.class);
        when(queryService.queryHourlyStats(any(), any(), any())).thenReturn(List.of());
        when(queryService.findProbeStatus()).thenReturn(List.of(new InternalModelProbeStatus(
                "enterprise-qwen", InternalModelCallOutcome.SUCCESS, 200, null, 300L,
                NOW, NOW, 0, TRACE_ID)));

        WebTestClient superAdmin = client(queryService, List.of(Dictionary.ROLE_SUPER_ADMIN));
        superAdmin.get()
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/stats")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk();
        superAdmin.get()
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/probe-status")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].providerId").isEqualTo("enterprise-qwen")
                .jsonPath("$.data[0].lastOutcome").isEqualTo("SUCCESS");

        WebTestClient appAdmin = client(queryService, List.of(Dictionary.ROLE_APP_ADMIN));
        appAdmin.get()
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    private WebTestClient client(InternalModelObservabilityQueryService queryService, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "admin", "AUTH_1", roles,
                Instant.parse("2026-08-06T00:00:00Z"), Instant.parse("2026-08-08T00:00:00Z"));
        return WebTestClient.bindToController(new InternalModelObservabilityController(
                        queryService, mock(InternalModelProviderProbeService.class)))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
