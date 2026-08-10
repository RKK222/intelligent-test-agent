package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialApplicationService;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialResponses;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证 API Key 管理入口仅限超管，且明文响应禁止缓存。 */
class ExternalApiCredentialControllerTest {

    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final String API_KEY = "taak_v1_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";

    @Test
    void superAdminCreatesCredentialWithNoStoreHeaders() {
        ExternalApiCredentialApplicationService service = mock(ExternalApiCredentialApplicationService.class);
        ExternalApiCredentialResponses.CredentialView view = view();
        when(service.create(any(), eq(TRACE_ID)))
                .thenReturn(new ExternalApiCredentialResponses.Created(view, API_KEY));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).post()
                .uri("/api/internal/platform/system-management/api-keys")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"toolCode":"deploy.bot","toolName":"部署工具","scopes":["USER_SSH_KEY_READ"],"enabled":true}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectBody()
                .jsonPath("$.data.apiKey").isEqualTo(API_KEY)
                .jsonPath("$.data.credential.toolCode").isEqualTo("deploy.bot");
    }

    @Test
    void nonSuperAdminCannotReadOrMutateCredentials() {
        ExternalApiCredentialApplicationService service = mock(ExternalApiCredentialApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get().uri("/api/internal/platform/system-management/api-keys")
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/internal/platform/system-management/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"toolCode":"deploy.bot","toolName":"部署工具","scopes":["USER_SSH_KEY_READ"],"enabled":true}
                        """)
                .exchange().expectStatus().isForbidden();

        verify(service, never()).list(any(), any(), any());
        verify(service, never()).create(any(), any());
    }

    @Test
    void listUsesOneBasedTwentyItemDefaultAndRevealIsNotCacheable() {
        ExternalApiCredentialApplicationService service = mock(ExternalApiCredentialApplicationService.class);
        when(service.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(view()), 1, 20, 1));
        when(service.reveal("eac_one"))
                .thenReturn(new ExternalApiCredentialResponses.Revealed("eac_one", "deploy.bot", API_KEY));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get().uri("/api/internal/platform/system-management/api-keys")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.size").isEqualTo(20)
                .jsonPath("$.data.items[0].keyHint").isEqualTo("taak_v1_ABC...WXYZ")
                .jsonPath("$.data.items[0].encryptedApiKey").doesNotExist();
        client.post().uri("/api/internal/platform/system-management/api-keys/eac_one/reveal")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody().jsonPath("$.data.apiKey").isEqualTo(API_KEY);
    }

    @Test
    void createRejectsMissingEnabledAsValidationError() {
        ExternalApiCredentialApplicationService service = mock(ExternalApiCredentialApplicationService.class);

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).post()
                .uri("/api/internal/platform/system-management/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"toolCode":"deploy.bot","toolName":"部署工具","scopes":["USER_SSH_KEY_READ"]}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");

        verify(service, never()).create(any(), any());
    }

    private static ExternalApiCredentialResponses.CredentialView view() {
        return new ExternalApiCredentialResponses.CredentialView(
                "eac_one", "deploy.bot", "部署工具", Set.of(ExternalApiScope.USER_SSH_KEY_READ),
                "taak_v1_ABC...WXYZ", true, Instant.EPOCH, Instant.EPOCH);
    }

    private static WebTestClient client(ExternalApiCredentialApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", new UserId("usr_admin"), "admin", "AUTH_1", roles,
                Instant.parse("2026-08-09T00:00:00Z"), Instant.parse("2026-08-10T00:00:00Z"));
        return WebTestClient.bindToController(new ExternalApiCredentialController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
