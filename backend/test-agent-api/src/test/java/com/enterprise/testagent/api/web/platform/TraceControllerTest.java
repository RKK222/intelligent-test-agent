package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.trace.TraceModels;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityModels;
import com.enterprise.testagent.opencode.runtime.observability.TraceAccessAuditLogger;
import com.enterprise.testagent.opencode.runtime.observability.TraceArchiveService;
import com.enterprise.testagent.opencode.runtime.observability.TraceQueryService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Trace 管理入口权限回归；菜单隐藏不能替代后端 SUPER_ADMIN 门禁。 */
class TraceControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final String TRACE_ID = "trc_1234567890abcdef1234567890abcdef";
    private static final String REQUEST_TRACE_ID = "trace_tracecontroller123456";

    @Test
    void superAdminCanListTraceCatalog() {
        Fixture fixture = fixture(List.of(Dictionary.ROLE_SUPER_ADMIN));
        when(fixture.queryService().search(
                null, null, null, null, null, null, null, null, null, null, null, null))
                .thenReturn(new PageResponse<>(List.of(catalog()), 1, 30, 1));

        fixture.client().get()
                .uri("/api/internal/platform/traces")
                .header("X-Trace-Id", REQUEST_TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].traceId").isEqualTo(TRACE_ID)
                .jsonPath("$.data.items[0].archiveStatus").isEqualTo("ARCHIVED")
                .jsonPath("$.data.items[0].complete").isEqualTo(true);
    }

    @Test
    void nonSuperAdminCannotReadTraceBodyAndAttemptIsAudited() {
        Fixture fixture = fixture(List.of(Dictionary.ROLE_APP_ADMIN));

        fixture.client().get()
                .uri("/api/internal/platform/traces/{traceId}/events", TRACE_ID)
                .header("X-Trace-Id", REQUEST_TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        verify(fixture.auditLogger()).recordDenied(
                Optional.of(new UserId("usr_trace_admin123")),
                TRACE_ID,
                "VIEW",
                REQUEST_TRACE_ID);
    }

    @Test
    void anonymousDownloadAttemptIsRejectedAndAudited() {
        Fixture fixture = anonymousFixture();

        fixture.client().get()
                .uri("/api/internal/platform/traces/{traceId}/download", TRACE_ID)
                .header("X-Trace-Id", REQUEST_TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        verify(fixture.auditLogger()).recordDenied(
                Optional.empty(),
                TRACE_ID,
                "DOWNLOAD",
                REQUEST_TRACE_ID);
    }

    @Test
    void staleBackendProcessIdStillReadsFromCurrentFrozenStorageNode() {
        Fixture fixture = fixture(List.of(Dictionary.ROLE_SUPER_ADMIN));
        when(fixture.queryService().require(TRACE_ID)).thenReturn(catalog());
        when(fixture.routeResolver().isCurrent("linux-server-a")).thenReturn(true);
        when(fixture.archiveService().readRawEvents(TRACE_ID, 0, 200))
                .thenReturn(new OpencodeObservabilityModels.RawEventPage(List.of(), 0, true));

        fixture.client().get()
                .uri("/api/internal/platform/traces/{traceId}/events", TRACE_ID)
                .header("X-Trace-Id", REQUEST_TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items.length()").isEqualTo(0);

        verify(fixture.forwarder(), never()).forwardRaw(
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private static Fixture fixture(List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_trace_admin123"),
                "trace-admin",
                "trace-admin",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return fixture(principal);
    }

    private static Fixture anonymousFixture() {
        return fixture((AuthPrincipal) null);
    }

    private static Fixture fixture(AuthPrincipal principal) {
        TraceQueryService queryService = mock(TraceQueryService.class);
        TraceArchiveService archiveService = mock(TraceArchiveService.class);
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        TraceAccessAuditLogger auditLogger = mock(TraceAccessAuditLogger.class);
        TraceController controller = new TraceController(
                queryService,
                archiveService,
                routeResolver,
                forwarder,
                auditLogger,
                new ObjectMapper());
        WebTestClient.ControllerSpec client = WebTestClient.bindToController(controller)
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler());
        if (principal != null) {
            client.webFilter((exchange, chain) -> {
                exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                return chain.filter(exchange);
            });
        }
        return new Fixture(
                client.build(), queryService, archiveService, routeResolver, forwarder, auditLogger);
    }

    private static TraceModels.Catalog catalog() {
        return new TraceModels.Catalog(
                TRACE_ID,
                "usr_target123456",
                "target-user",
                "org-a",
                "rd-a",
                "dept-a",
                "SERVER",
                "OPENCODE_PLUGIN",
                "ocp_1234567890abcdef",
                null,
                "bjp_1234567890abcdef",
                "linux-server-a",
                "ses_1234567890abcdef",
                "run_1234567890abcdef",
                "test-design-generation",
                "COMPLETED",
                "ARCHIVED",
                NOW,
                NOW.plusSeconds(2),
                NOW,
                12,
                12,
                2048,
                0,
                0,
                true,
                true);
    }

    private record Fixture(
            WebTestClient client,
            TraceQueryService queryService,
            TraceArchiveService archiveService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            TraceAccessAuditLogger auditLogger) {
    }
}
