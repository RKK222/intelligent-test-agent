package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialResponses;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证 Client key 一次性显示响应的缓存、错误和兼容字段契约。 */
class LocalClientCredentialControllerTest {

    private static final UserId USER_ID = new UserId("usr_local_credential_api");
    private static final String TRACE_ID = "trace_local_credential_api";

    @Test
    void exposesRevealAvailabilityWithoutReturningCiphertext() {
        LocalClientCredentialApplicationService service = mock(LocalClientCredentialApplicationService.class);
        when(service.get(USER_ID)).thenReturn(new LocalClientCredentialResponses.CredentialView(
                true,
                "tack_v1_ABC...WXYZ",
                3,
                LocalClientCredentialStatus.ACTIVE,
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-20T10:00:00Z"),
                true));

        client(service).get()
                .uri("/api/internal/platform/local-opencode-client/credentials/me")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.revealAvailable").isEqualTo(true)
                .jsonPath("$.data.maskedKey").isEqualTo("tack_v1_ABC...WXYZ")
                .jsonPath("$.data.encryptedClientKey").doesNotExist()
                .jsonPath("$.data.clientKey").doesNotExist();
    }

    @Test
    void firstCopyIsNonCacheableAndSecondCopyReturnsSafeConflict() {
        LocalClientCredentialApplicationService service = mock(LocalClientCredentialApplicationService.class);
        when(service.copy(USER_ID, TRACE_ID))
                .thenReturn(new LocalClientCredentialResponses.PlaintextKey("tack_v1_plaintext"))
                .thenThrow(new PlatformException(
                        ErrorCode.CONFLICT, "Client key 已显示，需轮换后才能再次查看"));
        WebTestClient client = client(service);

        client.post()
                .uri("/api/internal/platform/local-opencode-client/credentials/me/copy")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectBody()
                .jsonPath("$.data.clientKey").isEqualTo("tack_v1_plaintext");

        client.post()
                .uri("/api/internal/platform/local-opencode-client/credentials/me/copy")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").isEqualTo("Client key 已显示，需轮换后才能再次查看")
                .jsonPath("$.clientKey").doesNotExist();
    }

    private static WebTestClient client(LocalClientCredentialApplicationService service) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                USER_ID,
                "user",
                "AUTH-LOCAL",
                List.of("USER"),
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"));
        return WebTestClient.bindToController(new LocalClientCredentialController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
