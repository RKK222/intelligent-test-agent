package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.maintenance.IdentityManagedTable;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityMaintenanceService;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.IdentityStatusDto;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class DatabaseIdentityControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void superAdminCanListIdentityStatuses() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.listIdentityStatuses()).thenReturn(List.of(status("USERS", "users", 8L, 8L, false)));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/system-management/identity")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].table").isEqualTo("USERS")
                .jsonPath("$.data[0].maxId").isEqualTo(8)
                .jsonPath("$.data[0].conflict").isEqualTo(false);
    }

    @Test
    void superAdminCanAlignIdentity() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.alignIdentity(eq(IdentityManagedTable.USERS)))
                .thenReturn(status("USERS", "users", 9L, 8L, false));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/system-management/identity/align")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"table\":\"USERS\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.table").isEqualTo("USERS");

        verify(service).alignIdentity(eq(IdentityManagedTable.USERS));
    }

    @Test
    void superAdminCanRestartIdentity() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.restartIdentity(any(RestartIdentityCommand.class)))
                .thenReturn(status("USERS", "users", 1000000L, 8L, false));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/system-management/identity/restart")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"table\":\"USERS\",\"targetValue\":1000000}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.currentValue").isEqualTo(1000000);
    }

    @Test
    void nonSuperAdminIsForbidden() {
        WebTestClient client = client(
                mock(DatabaseIdentityMaintenanceService.class),
                List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/system-management/identity")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void alignRejectsUnknownTable() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.alignIdentity(any()))
                .thenThrow(new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的数据表"));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/system-management/identity/align")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"table\":\"unknown_table\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    private WebTestClient client(DatabaseIdentityMaintenanceService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "admin", "AUTH_1", roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new DatabaseIdentityController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private IdentityStatusDto status(String table, String tableName, Long current, Long max, boolean conflict) {
        return new IdentityStatusDto(table, tableName, current, max, conflict, Instant.parse("2026-07-01T00:00:00Z"));
    }
}
