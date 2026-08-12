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
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcomeGroup;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelLatencyDistribution;
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
                500, "WebClientResponseException", true, 800L, 50L, 75L, 675L, 700L, 13L,
                TRACE_ID, "ucid", NOW);
        when(queryService.queryCallRecords(
                        eq("enterprise-deepseek"), any(), eq(InternalModelCallOutcomeGroup.UPSTREAM_FAILURE),
                        any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(record), 1, 20, 1));
        WebTestClient client = client(queryService, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/call-records"
                        + "?providerId=enterprise-deepseek&outcomeGroup=UPSTREAM_FAILURE")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].providerId").isEqualTo("enterprise-deepseek")
                .jsonPath("$.data.items[0].outcome").isEqualTo("UPSTREAM_HTTP_ERROR")
                .jsonPath("$.data.items[0].httpStatus").isEqualTo(500)
                .jsonPath("$.data.items[0].streamCompleteMillis").isEqualTo(700)
                .jsonPath("$.data.items[0].lastTokenMillis").isEqualTo(675)
                .jsonPath("$.data.items[0].outputTokenCount").isEqualTo(13)
                .jsonPath("$.data.items[0].source").isEqualTo("USER_CALL")
                .jsonPath("$.data.items[0].ucid").isEqualTo("ucid");

        verify(queryService).queryCallRecords(
                eq("enterprise-deepseek"), any(), eq(InternalModelCallOutcomeGroup.UPSTREAM_FAILURE),
                any(), any(), any(), any(PageRequest.class));
    }

    @Test
    void onlySuperAdminCanQueryStatsAndProbeStatus() {
        InternalModelObservabilityQueryService queryService = mock(InternalModelObservabilityQueryService.class);
        when(queryService.queryHourlyStats(any(), any(), any(), any())).thenReturn(List.of());
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
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/stats?source=USER_CALL")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk();
        verify(queryService).queryHourlyStats(
                eq(null), eq(InternalModelCallSource.USER_CALL), any(), any());
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

    @Test
    void superAdminCanQueryFilteredTtftDistribution() {
        InternalModelObservabilityQueryService queryService = mock(InternalModelObservabilityQueryService.class);
        when(queryService.queryTtftDistribution(
                        eq("enterprise-deepseek"),
                        eq(InternalModelCallOutcomeGroup.SUCCESS),
                        eq(InternalModelCallSource.USER_CALL),
                        any(),
                        any()))
                .thenReturn(new InternalModelLatencyDistribution(4, 250.0, 100.0, 175.0, 250.0, 325.0, 400.0));
        WebTestClient client = client(queryService, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/ttft-distribution"
                        + "?providerId=enterprise-deepseek&outcomeGroup=SUCCESS&source=USER_CALL"
                        + "&from=2026-08-07T00:00:00Z&to=2026-08-08T00:00:00Z")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.sampleCount").isEqualTo(4)
                .jsonPath("$.data.averageMillis").isEqualTo(250.0)
                .jsonPath("$.data.minimumMillis").isEqualTo(100.0)
                .jsonPath("$.data.firstQuartileMillis").isEqualTo(175.0)
                .jsonPath("$.data.medianMillis").isEqualTo(250.0)
                .jsonPath("$.data.thirdQuartileMillis").isEqualTo(325.0)
                .jsonPath("$.data.maximumMillis").isEqualTo(400.0);
    }

    @Test
    void superAdminCanQueryFilteredItlDistribution() {
        InternalModelObservabilityQueryService queryService = mock(InternalModelObservabilityQueryService.class);
        when(queryService.queryItlDistribution(
                        eq("enterprise-deepseek"),
                        eq(InternalModelCallOutcomeGroup.SUCCESS),
                        eq(InternalModelCallSource.USER_CALL),
                        any(),
                        any()))
                .thenReturn(new InternalModelLatencyDistribution(3, 40.0, 20.0, 30.0, 40.0, 50.0, 60.0));
        WebTestClient client = client(queryService, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/opencode-runtime/internal-model-observability/itl-distribution"
                        + "?providerId=enterprise-deepseek&outcomeGroup=SUCCESS&source=USER_CALL")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.sampleCount").isEqualTo(3)
                .jsonPath("$.data.averageMillis").isEqualTo(40.0)
                .jsonPath("$.data.medianMillis").isEqualTo(40.0);
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
