package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.configuration.management.CommonParameterManagementApplicationService;
import com.enterprise.testagent.configuration.management.CommonParameterManagementApplicationService.CommonParameterFilter;
import com.enterprise.testagent.configuration.management.CommonParameterManagementResponses.CommonParameterResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
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
                .jsonPath("$.data.items[0].editable").isEqualTo(false)
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

        WebTestClient.bindToController(new CommonParameterManagementController(service))
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
    void macosPlatformCanBeQueried() {
        CommonParameterManagementApplicationService service = org.mockito.Mockito.mock(CommonParameterManagementApplicationService.class);
        when(service.find(eq(new CommonParameterFilter(com.enterprise.testagent.domain.configuration.ParameterPlatform.MACOS)),
                eq(new PageRequest(1, 50))))
                .thenReturn(new PageResponse<>(List.of(response("/tmp/ws", "macos")), 1, 50, 1));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/common-parameters?platform=macos")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].platform").isEqualTo("macos");
    }

    @Test
    void invalidPlatformUsesUnifiedValidationError() {
        CommonParameterManagementApplicationService service = org.mockito.Mockito.mock(CommonParameterManagementApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/common-parameters?platform=solaris")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details.platform").isEqualTo("solaris");
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
        return WebTestClient.bindToController(new CommonParameterManagementController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static CommonParameterResponse response(String value) {
        return response(value, "linux");
    }

    private static CommonParameterResponse response(String value, String platform) {
        return new CommonParameterResponse(
                PARAMETER_ID,
                "OPENCODE_WORKSPACE_ROOT",
                "工作空间根目录",
                value,
                platform,
                false,
                NOW,
                NOW);
    }
}
