package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.RequirementImportApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class RequirementImportControllerTest {

    private static final String TRACE_ID = "trace_requirement_import";

    @Test
    void usesAuthenticatedUnifiedIdentityAndReturnsOnlyCatalogFields() {
        RequirementImportApplicationService service = mock(RequirementImportApplicationService.class);
        when(service.listApplications("u001")).thenReturn(List.of(
                new RequirementImportApplicationService.ApplicationOption("个人金融", "PSN")));
        when(service.listItems("u001", "PSN", "2026年8月")).thenReturn(List.of(
                new RequirementImportApplicationService.ItemOption(
                        "I-01", "登录需求", List.of(
                                new RequirementImportApplicationService.SubItemOption("SI-01", "登录校验")))));
        WebTestClient client = client(service, true);

        client.get().uri("/api/v1/requirement-import/applications")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].appName").isEqualTo("个人金融")
                .jsonPath("$.data[0].appShortName").isEqualTo("PSN")
                .jsonPath("$.data[0].token").doesNotExist()
                .jsonPath("$.data[0].documentUrl").doesNotExist();

        client.get().uri(uriBuilder -> uriBuilder
                        .path("/api/v1/requirement-import/sub-items")
                        .queryParam("appShortName", "PSN")
                        .queryParam("editionId", "2026年8月")
                        .build())
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].children[0].itemNo").isEqualTo("SI-01")
                .jsonPath("$.data[0].children[0].graphList").doesNotExist()
                .jsonPath("$.data[0].children[0].documentUrl").doesNotExist();

        verify(service).listApplications("u001");
        verify(service).listItems("u001", "PSN", "2026年8月");
    }

    @Test
    void rejectsMissingAuthenticationBeforeCallingTcdsService() {
        RequirementImportApplicationService service = mock(RequirementImportApplicationService.class);

        client(service, false).get().uri("/api/v1/requirement-import/applications")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private static WebTestClient client(
            RequirementImportApplicationService service,
            boolean authenticated) {
        var builder = WebTestClient.bindToController(new RequirementImportController(service))
                .webFilter(new TraceIdWebFilter());
        if (authenticated) {
            AuthPrincipal principal = new AuthPrincipal(
                    "token",
                    new UserId("usr_1"),
                    "tester",
                    "u001",
                    List.of(),
                    Instant.parse("2026-08-13T00:00:00Z"),
                    Instant.parse("2026-08-14T00:00:00Z"));
            builder.webFilter((exchange, chain) -> {
                exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                return chain.filter(exchange);
            });
        }
        return builder.controllerAdvice(new GlobalExceptionHandler()).build();
    }
}
