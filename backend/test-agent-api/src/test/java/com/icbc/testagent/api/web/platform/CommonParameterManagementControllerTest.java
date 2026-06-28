package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.configuration.management.CommonParameterManagementApplicationService;
import com.icbc.testagent.configuration.management.CommonParameterManagementApplicationService.CommonParameterFilter;
import com.icbc.testagent.configuration.management.CommonParameterManagementResponses.CommonParameterResponse;
import com.icbc.testagent.configuration.management.CommonParameterLoadSnapshotQueryService;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class CommonParameterManagementControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-26T00:00:00Z");
    private static final UserId ADMIN_USER_ID = new UserId("usr_admin_1234567890");
    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final String PARAMETER_ID = "param_opencode_workspace_root_linux";

    @Test
    void superAdminCanListParameters() {
        CommonParameterManagementApplicationService service = org.mockito.Mockito.mock(CommonParameterManagementApplicationService.class);
        when(service.find(eq(new CommonParameterFilter(null)), eq(new PageRequest(1, 50))))
                .thenReturn(new PageResponse<>(List.of(response("/opt/ws")), 1, 50, 1));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/common-parameters?page=1&size=50")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].parameterId").isEqualTo(PARAMETER_ID)
                .jsonPath("$.data.items[0].parameterValue").isEqualTo("/opt/ws")
                .jsonPath("$.data.items[0].platform").isEqualTo("linux")
                .jsonPath("$.data.total").isEqualTo(1);
    }

    @Test
    void superAdminCanUpdateValue() {
        CommonParameterManagementApplicationService service = org.mockito.Mockito.mock(CommonParameterManagementApplicationService.class);
        when(service.updateValue(eq(PARAMETER_ID), eq("/new"), eq(TRACE_ID), eq(ADMIN_USER_ID.value()), eq("admin")))
                .thenReturn(response("/new"));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.patch()
                .uri("/api/internal/platform/configuration-management/common-parameters/" + PARAMETER_ID)
                .header("X-Trace-Id", TRACE_ID)
                .bodyValue(Map.of("value", "/new"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.parameterId").isEqualTo(PARAMETER_ID)
                .jsonPath("$.data.parameterValue").isEqualTo("/new");
    }

    @Test
    void appAdminAndAnonymousUsersCannotAccessCommonParameters() {
        CommonParameterManagementApplicationService service = org.mockito.Mockito.mock(CommonParameterManagementApplicationService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).get()
                .uri("/api/internal/platform/configuration-management/common-parameters")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        WebTestClient.bindToController(new CommonParameterManagementController(service, org.mockito.Mockito.mock(CommonParameterLoadSnapshotQueryService.class)))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .get()
                .uri("/api/internal/platform/configuration-management/common-parameters")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void invalidPlatformUsesUnifiedValidationError() {
        CommonParameterManagementApplicationService service = org.mockito.Mockito.mock(CommonParameterManagementApplicationService.class);
        when(service.find(eq(new CommonParameterFilter(null)), eq(new PageRequest(1, 50))))
                .thenReturn(new PageResponse<>(List.of(), 1, 50, 0));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/common-parameters?platform=macos")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details.platform").isEqualTo("macos");
    }

    @Test
    void superAdminCanQueryLoadSnapshots() {
        CommonParameterManagementApplicationService service = org.mockito.Mockito.mock(CommonParameterManagementApplicationService.class);
        CommonParameterLoadSnapshotQueryService queryService = org.mockito.Mockito.mock(CommonParameterLoadSnapshotQueryService.class);
        when(queryService.list()).thenReturn(List.of(
                new com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshot(
                        "bjp_a",
                        "srv-a",
                        "http://a:8080",
                        "instance-a",
                        NOW,
                        List.of(new com.icbc.testagent.domain.configuration.LoadedParameter(
                                "OPENCODE_MANAGER_MAX_PROCESSES", "all", "8", "8", false, null)))));
        WebTestClient client = WebTestClient.bindToController(new CommonParameterManagementController(service, queryService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, superAdmin());
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.get()
                .uri("/api/internal/platform/configuration-management/common-parameters/load-snapshots")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].backendProcessId").isEqualTo("bjp_a")
                .jsonPath("$.data[0].linuxServerId").isEqualTo("srv-a")
                .jsonPath("$.data[0].parameters[0].englishName").isEqualTo("OPENCODE_MANAGER_MAX_PROCESSES")
                .jsonPath("$.data[0].parameters[0].resolvedValue").isEqualTo("8");
    }

    private static AuthPrincipal superAdmin() {
        return new AuthPrincipal(
                "token",
                ADMIN_USER_ID,
                "admin",
                "AUTH_1",
                List.of(Dictionary.ROLE_SUPER_ADMIN),
                NOW,
                NOW.plusSeconds(3600));
    }

    private static WebTestClient client(CommonParameterManagementApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                ADMIN_USER_ID,
                "admin",
                "AUTH_1",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new CommonParameterManagementController(service, org.mockito.Mockito.mock(CommonParameterLoadSnapshotQueryService.class)))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static CommonParameterResponse response(String value) {
        return new CommonParameterResponse(
                PARAMETER_ID,
                "OPENCODE_WORKSPACE_ROOT",
                "工作空间根目录",
                value,
                "linux",
                NOW,
                NOW);
    }
}
