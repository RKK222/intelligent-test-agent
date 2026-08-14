package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.tcds.TcdsCaseMaintenanceService;
import com.enterprise.testagent.integration.tcds.TcdsTaskTypeOption;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证当前认证统一认证号、统一响应、匿名拒绝和 DTO 校验。 */
class TcdsCaseMaintenanceControllerTest {

    private static final String TRACE_ID = "trace_tcds_case_api";

    @Test
    void authenticatedUserLoadsTaskTypes() {
        TcdsCaseMaintenanceService service = mock(TcdsCaseMaintenanceService.class);
        when(service.getTaskTypes(TRACE_ID)).thenReturn(List.of(
                new TcdsTaskTypeOption("准入测试任务", "5"),
                new TcdsTaskTypeOption("功能测试任务", "3")));

        client(service, true).get()
                .uri("/api/internal/platform/integration/tcds/task-types")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.traceId").isEqualTo(TRACE_ID)
                .jsonPath("$.data[0].name").isEqualTo("准入测试任务")
                .jsonPath("$.data[0].value").isEqualTo("5")
                .jsonPath("$.data[1].name").isEqualTo("功能测试任务")
                .jsonPath("$.data[1].value").isEqualTo("3");

        verify(service).getTaskTypes(TRACE_ID);
    }

    @Test
    void anonymousUserCannotLoadTaskTypes() {
        TcdsCaseMaintenanceService service = mock(TcdsCaseMaintenanceService.class);

        client(service, false).get()
                .uri("/api/internal/platform/integration/tcds/task-types")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        verify(service, never()).getTaskTypes(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void authenticatedUserMaintainsCasesWithPrincipalUnifiedAuthId() {
        TcdsCaseMaintenanceService service = mock(TcdsCaseMaintenanceService.class);

        client(service, true).post()
                .uri("/api/internal/platform/integration/tcds/test-cases")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "itemNo":"S20260703-000081",
                          "caseList":[
                            {"name":"案例一","step":"步骤","data":"数据","expect":"预期","taskType":"探索性,功能"}
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.traceId").isEqualTo(TRACE_ID);

        verify(service).maintain(
                org.mockito.ArgumentMatchers.eq("S20260703-000081"),
                org.mockito.ArgumentMatchers.eq("555033606"),
                anyList(),
                org.mockito.ArgumentMatchers.eq(TRACE_ID));
    }

    @Test
    void missingAuthenticationAndInvalidTaskTypeFormatAreRejectedBeforeService() {
        TcdsCaseMaintenanceService service = mock(TcdsCaseMaintenanceService.class);
        String body = """
                {"itemNo":"S20260703-000081","caseList":[
                  {"name":"案例一","step":"","data":"","expect":"","taskType":"准入，功能测试"}
                ]}
                """;

        client(service, false).post()
                .uri("/api/internal/platform/integration/tcds/test-cases")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");

        client(service, false).post()
                .uri("/api/internal/platform/integration/tcds/test-cases")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.replace("准入，功能测试", "3"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        client(service, false).post()
                .uri("/api/internal/platform/integration/tcds/test-cases")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.replace("准入，功能测试", "准入,功能"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        verify(service, never()).maintain(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                anyList(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private WebTestClient client(TcdsCaseMaintenanceService service, boolean authenticated) {
        var builder = WebTestClient.bindToController(new TcdsCaseMaintenanceController(service))
                .webFilter(new TraceIdWebFilter());
        if (authenticated) {
            AuthPrincipal principal = new AuthPrincipal(
                    "token",
                    new UserId("usr_tcds_case"),
                    "case-user",
                    "555033606",
                    List.of(),
                    Instant.parse("2026-08-12T00:00:00Z"),
                    Instant.parse("2026-08-13T00:00:00Z"));
            builder.webFilter((exchange, chain) -> {
                exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                return chain.filter(exchange);
            });
        }
        return builder.controllerAdvice(new GlobalExceptionHandler()).build();
    }
}
