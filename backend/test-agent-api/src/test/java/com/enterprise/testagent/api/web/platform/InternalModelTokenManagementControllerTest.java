package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.InternalModelTokenManagementApplicationService;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.InternalModelToken;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class InternalModelTokenManagementControllerTest {

    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void superAdminCanCreateExternalTokenWithoutResponseLeak() {
        InternalModelTokenManagementApplicationService service = mock(InternalModelTokenManagementApplicationService.class);
        when(service.create("Qwen Token", "external-secret", TRACE_ID)).thenReturn(token(7L, "Qwen Token", 0));

        byte[] body = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .post()
                .uri("/api/internal/platform/configuration-management/internal-model-tokens")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Qwen Token\",\"token\":\"external-secret\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tokenId").isEqualTo(7)
                .jsonPath("$.data.name").isEqualTo("Qwen Token")
                .jsonPath("$.data.referencedProviderCount").isEqualTo(0)
                .jsonPath("$.data.token").doesNotExist()
                .returnResult()
                .getResponseBody();

        assertThat(body).asString().doesNotContain("external-secret");
        verify(service).create("Qwen Token", "external-secret", TRACE_ID);
    }

    @Test
    void patchPassesBlankTokenThroughForKeepExistingSemantics() {
        InternalModelTokenManagementApplicationService service = mock(InternalModelTokenManagementApplicationService.class);
        when(service.update(7L, "新名称", "", TRACE_ID)).thenReturn(token(7L, "新名称", 2));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .patch()
                .uri("/api/internal/platform/configuration-management/internal-model-tokens/7")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"新名称\",\"token\":\"\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo("新名称")
                .jsonPath("$.data.referencedProviderCount").isEqualTo(2)
                .jsonPath("$.data.token").doesNotExist();

        verify(service).update(7L, "新名称", "", TRACE_ID);
    }

    @Test
    void referencedTokenDeletionUsesUnifiedConflictResponse() {
        InternalModelTokenManagementApplicationService service = mock(InternalModelTokenManagementApplicationService.class);
        org.mockito.Mockito.doThrow(new PlatformException(ErrorCode.CONFLICT, "内部模型 Token 仍被供应商引用，不能删除"))
                .when(service).delete(7L, TRACE_ID);

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN))
                .delete()
                .uri("/api/internal/platform/configuration-management/internal-model-tokens/7")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").isEqualTo("内部模型 Token 仍被供应商引用，不能删除");
    }

    @Test
    void nonSuperAdminCannotListTokens() {
        InternalModelTokenManagementApplicationService service = mock(InternalModelTokenManagementApplicationService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN))
                .get()
                .uri("/api/internal/platform/configuration-management/internal-model-tokens")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        verifyNoInteractions(service);
    }

    private WebTestClient client(InternalModelTokenManagementApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_1234567890abcdef"),
                "admin",
                "AUTH_1",
                roles,
                Instant.parse("2026-07-22T00:00:00Z"),
                Instant.parse("2026-07-23T00:00:00Z"));
        return WebTestClient.bindToController(new InternalModelTokenManagementController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private InternalModelToken token(long tokenId, String name, long references) {
        Instant now = Instant.parse("2026-07-22T08:00:00Z");
        return new InternalModelToken(tokenId, name, references, now, now);
    }
}
