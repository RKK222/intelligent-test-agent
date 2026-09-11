package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.analytics.AnalyticsQueryService;
import com.enterprise.testagent.opencode.runtime.analytics.AnalyticsSessionUsageQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.reactive.server.WebTestClient;

class AnalyticsControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final String TRACE_ID = "trace_analytics123456";

    @Test
    void superAdminCanQueryAnalyticsOverview() {
        AnalyticsQueryService service = org.mockito.Mockito.mock(AnalyticsQueryService.class);
        AnalyticsModels.Filter filter = filter();
        when(service.filter(any(), any(), eq("day"), any(), any(), any(), any(), any(), any(), any(), eq(10), eq(1), eq(20), eq("active")))
                .thenReturn(filter);
        when(service.overview(filter)).thenReturn(overview());

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .get()
                .uri("/api/internal/platform/analytics/overview?startTime=2026-06-28T00:00:00Z&endTime=2026-06-29T00:00:00Z&granularity=day&topN=10&page=1&pageSize=20&sort=active")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.activeUsers").isEqualTo(1)
                .jsonPath("$.data.satisfactionRate").isEqualTo(0.5);
    }

    @Test
    void nonSuperAdminAndAnonymousCannotQueryAnalytics() {
        AnalyticsQueryService service = org.mockito.Mockito.mock(AnalyticsQueryService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN))
                .get()
                .uri("/api/internal/platform/analytics/overview")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        WebTestClient.bindToController(new AnalyticsController(
                        service, org.mockito.Mockito.mock(AnalyticsSessionUsageQueryService.class)))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .get()
                .uri("/api/internal/platform/analytics/overview")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void invalidInstantUsesUnifiedValidationError() {
        AnalyticsQueryService service = org.mockito.Mockito.mock(AnalyticsQueryService.class);

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .get()
                .uri("/api/internal/platform/analytics/overview?startTime=not-a-time")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details.value").isEqualTo("not-a-time");
    }

    @Test
    void superAdminCanQueryFunnelWithFuzzyUserParameter() {
        AnalyticsQueryService service = org.mockito.Mockito.mock(AnalyticsQueryService.class);
        AnalyticsModels.Filter filter = filter();
        when(service.filter(
                nullable(Instant.class), nullable(Instant.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(Integer.class), nullable(Integer.class), nullable(Integer.class), nullable(String.class)))
                .thenReturn(filter);
        when(service.funnel(filter)).thenReturn(new AnalyticsModels.Funnel(
                10, 6, 2, 0.6, 2.0 / 6.0,
                "活跃用户：所选时间内至少发送 1 条用户消息",
                "深度用户：活跃用户中，至少 2 个自然日有使用且累计至少 5 条用户消息",
                new AnalyticsModels.Freshness(NOW, AnalyticsModels.FreshnessStatus.FRESH, null)));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .get()
                .uri(builder -> builder.path("/api/internal/platform/analytics/funnel")
                        .queryParam("user", "张")
                        .build())
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.totalUsers").isEqualTo(10)
                .jsonPath("$.data.deepUsers").isEqualTo(2)
                .jsonPath("$.data.activeDefinition").isNotEmpty();

        ArgumentCaptor<String> userKeyword = ArgumentCaptor.forClass(String.class);
        verify(service).filter(
                nullable(Instant.class), nullable(Instant.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), userKeyword.capture(),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(Integer.class), nullable(Integer.class), nullable(Integer.class), nullable(String.class));
        assertThat(userKeyword.getValue()).isEqualTo("张");
    }

    @Test
    void superAdminCanQuerySessionUsageFromBusinessDatabase() {
        AnalyticsQueryService service = org.mockito.Mockito.mock(AnalyticsQueryService.class);
        AnalyticsSessionUsageQueryService sessionUsageService =
                org.mockito.Mockito.mock(AnalyticsSessionUsageQueryService.class);
        when(sessionUsageService.sessionMessageUsage(any())).thenReturn(new PageResponse<>(
                List.of(new AnalyticsModels.SessionUsageRow(
                        "usr_1",
                        "张三",
                        "ses_1",
                        "一个非常长的会话标题用于验证前端截断与悬浮展示",
                        3,
                        NOW,
                        NOW.plusSeconds(120))),
                1,
                20,
                1));

        client(service, sessionUsageService, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .get()
                .uri("/api/internal/platform/analytics/sessions?startTime=2026-06-28T00:00:00Z"
                        + "&endTime=2026-06-29T00:00:00Z&page=1&pageSize=20")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.total").isEqualTo(1)
                .jsonPath("$.data.items[0].username").isEqualTo("张三")
                .jsonPath("$.data.items[0].userMessageCount").isEqualTo(3)
                .jsonPath("$.data.items[0].sessionTitle")
                .isEqualTo("一个非常长的会话标题用于验证前端截断与悬浮展示");
    }

    @Test
    void superAdminCanQuerySessionUsageSummary() {
        AnalyticsQueryService service = org.mockito.Mockito.mock(AnalyticsQueryService.class);
        AnalyticsSessionUsageQueryService sessionUsageService =
                org.mockito.Mockito.mock(AnalyticsSessionUsageQueryService.class);
        when(sessionUsageService.sessionMessageSummary(any())).thenReturn(new PageResponse<>(
                List.of(new AnalyticsModels.SessionUsageSummaryRow(
                        "usr_1", "张三", "AUTH_1", "总行", "研发一部", "平台部", 3, 9, NOW, NOW.plusSeconds(120))),
                1,
                20,
                1));

        client(service, sessionUsageService, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .get()
                .uri("/api/internal/platform/analytics/sessions/summary?page=1&pageSize=20")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.total").isEqualTo(1)
                .jsonPath("$.data.items[0].sessionCount").isEqualTo(3)
                .jsonPath("$.data.items[0].userMessageCount").isEqualTo(9);
    }

    private static WebTestClient client(AnalyticsQueryService service, List<String> roles) {
        return client(service, org.mockito.Mockito.mock(AnalyticsSessionUsageQueryService.class), roles);
    }

    private static WebTestClient client(
            AnalyticsQueryService service,
            AnalyticsSessionUsageQueryService sessionUsageService,
            List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_admin1234567890"),
                "admin",
                "admin",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new AnalyticsController(service, sessionUsageService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static AnalyticsModels.Filter filter() {
        return new AnalyticsModels.Filter(
                NOW,
                NOW.plusSeconds(86_400),
                AnalyticsModels.Granularity.DAY,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                1,
                20,
                "active");
    }

    private static AnalyticsModels.Overview overview() {
        return new AnalyticsModels.Overview(
                2,
                2,
                1,
                1,
                1,
                0,
                0.5,
                1.0,
                1.0,
                null,
                1,
                1,
                0,
                0,
                2,
                2,
                1,
                1.0,
                2.0,
                2.0,
                null,
                1,
                0,
                1,
                0,
                0,
                0,
                1.0,
                0.0,
                0.0,
                1_000L,
                2_000L,
                1,
                1,
                0.5,
                1.0,
                1,
                1,
                0,
                1.0,
                0.0,
                10,
                8,
                2,
                20,
                20.0,
                20.0,
                new AnalyticsModels.Freshness(NOW, AnalyticsModels.FreshnessStatus.FRESH, null));
    }
}
