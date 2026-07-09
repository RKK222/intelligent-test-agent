package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.system.management.user.UserManagementApplicationService;
import com.icbc.testagent.system.management.user.UserManagementResponses.CreateUserCommand;
import com.icbc.testagent.system.management.user.UserManagementResponses.RoleOption;
import com.icbc.testagent.system.management.user.UserManagementResponses.UpdateUserRoleCommand;
import com.icbc.testagent.system.management.user.UserManagementResponses.UserResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class UserManagementControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void superAdminCanListUsers() {
        UserManagementApplicationService service = org.mockito.Mockito.mock(UserManagementApplicationService.class);
        when(service.listUsers(eq("ali"), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(userResponse("usr_1", "alice")), 1, 50, 1));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/system-management/users?keyword=ali&page=1&size=50")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].username").isEqualTo("alice")
                .jsonPath("$.data.items[0].roles[0]").isEqualTo("APP_ADMIN")
                .jsonPath("$.data.total").isEqualTo(1);
    }

    @Test
    void superAdminCanCreateUser() {
        UserManagementApplicationService service = org.mockito.Mockito.mock(UserManagementApplicationService.class);
        when(service.createUser(any(CreateUserCommand.class)))
                .thenReturn(userResponse("usr_new", "bob"));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/system-management/users")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"unifiedAuthId":"AUTH_2","username":"bob","role":"APP_ADMIN",
                         "organization":"工行","rdDepartment":"研发部","department":"测试部"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.username").isEqualTo("bob")
                .jsonPath("$.data.roles[0]").isEqualTo("APP_ADMIN");

        // 校验 Controller 把请求 DTO 正确转换为 CreateUserCommand（含默认密码由服务层注入，前端不传密码）
        verify(service).createUser(org.mockito.ArgumentMatchers.argThat((CreateUserCommand command) ->
                "AUTH_2".equals(command.unifiedAuthId())
                        && "bob".equals(command.username())
                        && "APP_ADMIN".equals(command.role())
                        && "工行".equals(command.organization())));
    }

    @Test
    void superAdminCanListRoles() {
        UserManagementApplicationService service = org.mockito.Mockito.mock(UserManagementApplicationService.class);
        when(service.listRoles()).thenReturn(List.of(
                new RoleOption("SUPER_ADMIN", "超级管理员"),
                new RoleOption("APP_ADMIN", "应用管理员")));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/system-management/roles")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].roleCode").isEqualTo("SUPER_ADMIN")
                .jsonPath("$.data[1].roleLabel").isEqualTo("应用管理员");
    }

    @Test
    void superAdminCanUpdateUserRole() {
        UserManagementApplicationService service = org.mockito.Mockito.mock(UserManagementApplicationService.class);
        when(service.updateUserRole(any(UpdateUserRoleCommand.class)))
                .thenReturn(userResponse("usr_target", "alice"));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.put()
                .uri("/api/internal/platform/system-management/users/usr_target/roles")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"role\":\"USER\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("usr_target");

        verify(service).updateUserRole(org.mockito.ArgumentMatchers.argThat((UpdateUserRoleCommand command) ->
                "usr_target".equals(command.userId()) && "USER".equals(command.role())));
    }

    @Test
    void appAdminCannotAccessUserManagement() {
        WebTestClient client = client(
                org.mockito.Mockito.mock(UserManagementApplicationService.class),
                List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/system-management/users")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        WebTestClient client = clientWithoutAuth(org.mockito.Mockito.mock(UserManagementApplicationService.class));

        client.get()
                .uri("/api/internal/platform/system-management/users")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private WebTestClient client(UserManagementApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "admin",
                "AUTH_1",
                roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new UserManagementController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private WebTestClient clientWithoutAuth(UserManagementApplicationService service) {
        return WebTestClient.bindToController(new UserManagementController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserResponse userResponse(String userId, String username) {
        return new UserResponse(
                userId, username, "AUTH_" + username,
                "工行", "研发部", "测试部",
                "ACTIVE",
                List.of("APP_ADMIN"),
                List.of("应用管理员"),
                Instant.parse("2026-06-26T00:00:00Z"));
    }
}
