package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.ApplicationAutomationReferenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AutomationReferenceRepositoryControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void applicationMemberCanReadRepositoryList() {
        ApplicationAutomationReferenceService service = org.mockito.Mockito.mock(
                ApplicationAutomationReferenceService.class);
        when(service.list("app_gcms", USER_ID, false)).thenReturn(List.of());

        client(service, List.of("USER")).get()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/automation-reference-repositories")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray();

        verify(service).list("app_gcms", USER_ID, false);
    }

    @Test
    void ordinaryMemberCannotChangeSharedConfiguration() {
        ApplicationAutomationReferenceService service = org.mockito.Mockito.mock(
                ApplicationAutomationReferenceService.class);

        client(service, List.of("USER")).put()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/automation-reference-repositories/repo_automation/configuration")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "branch":"main",
                          "directoryPath":"src/test",
                          "description":"只读自动化引用",
                          "merge":false,
                          "expectedGeneration":0,
                          "operationId":"op_configure_1"
                        }
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        verifyNoInteractions(service);
    }

    @Test
    void applicationAdministratorPassesConfigurationAndMemberCheckToService() {
        ApplicationAutomationReferenceService service = org.mockito.Mockito.mock(
                ApplicationAutomationReferenceService.class);

        client(service, List.of("APP_ADMIN")).put()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/automation-reference-repositories/repo_automation/configuration")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "branch":"feature/e2e",
                          "directoryPath":"src/test",
                          "description":"E2E 自动化脚本",
                          "merge":false,
                          "expectedGeneration":2,
                          "operationId":"op_configure_2"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).configure(
                "app_gcms",
                "repo_automation",
                "feature/e2e",
                "src/test",
                "E2E 自动化脚本",
                false,
                2L,
                "op_configure_2",
                USER_ID,
                false,
                TRACE_ID);
    }

    @Test
    void superAdministratorUsesInheritedAdminPermissionWithoutMembershipCheck() {
        ApplicationAutomationReferenceService service = org.mockito.Mockito.mock(
                ApplicationAutomationReferenceService.class);

        client(service, List.of("SUPER_ADMIN")).post()
                .uri("/api/internal/platform/workspace-management/applications/app_gcms/automation-reference-repositories/repo_automation/synchronize")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"expectedGeneration":3,"operationId":"op_sync_3"}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(service).synchronize(
                "app_gcms", "repo_automation", 3L, "op_sync_3", USER_ID, true, TRACE_ID);
    }

    private WebTestClient client(ApplicationAutomationReferenceService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "888888888",
                "888888888",
                roles,
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-08-22T00:00:00Z"));
        return WebTestClient.bindToController(new AutomationReferenceRepositoryController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
