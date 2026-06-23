package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.configuration.management.ConfigurationManagementApplicationService;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.ApplicationResponse;
import com.icbc.testagent.configuration.management.ConfigurationManagementResponses.SshKeyResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ConfigurationManagementControllerTest {

    @Test
    void appAdminCanListEnabledApplications() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.listApplications(true)).thenReturn(List.of(new ApplicationResponse("app_gcms", "F-GCMS", true)));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/configuration-management/applications?enabled=true")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].appId").isEqualTo("app_gcms")
                .jsonPath("$.data[0].appName").isEqualTo("F-GCMS");
    }

    @Test
    void nonAdminCannotAccessApplicationManagement() {
        WebTestClient client = client(org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class), List.of());

        client.get()
                .uri("/api/internal/platform/configuration-management/applications?enabled=true")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void personalSshKeyResponseDoesNotExposePrivateKey() {
        ConfigurationManagementApplicationService service = org.mockito.Mockito.mock(ConfigurationManagementApplicationService.class);
        when(service.addSshKey(eq(new UserId("usr_1234567890abcdef")), eq("work"), anyString()))
                .thenReturn(new SshKeyResponse("ssh_123", "work", "SHA256:abc", Instant.parse("2026-06-23T00:00:00Z")));
        WebTestClient client = client(service, List.of());

        client.post()
                .uri("/api/internal/platform/configuration-management/personal/ssh-keys")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"work","privateKey":"-----BEGIN OPENSSH PRIVATE KEY-----\\nsecret\\n-----END OPENSSH PRIVATE KEY-----"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.sshKeyId").isEqualTo("ssh_123")
                .jsonPath("$.data.fingerprint").isEqualTo("SHA256:abc")
                .jsonPath("$.data.privateKey").doesNotExist();
    }

    private WebTestClient client(ConfigurationManagementApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_1234567890abcdef"),
                "admin",
                "AUTH_1",
                roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new ConfigurationManagementController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
