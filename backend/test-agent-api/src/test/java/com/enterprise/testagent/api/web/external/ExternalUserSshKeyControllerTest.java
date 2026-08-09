package com.enterprise.testagent.api.web.external;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.ExternalApiWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialId;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import com.enterprise.testagent.integration.externalapi.ExternalSshKeyEnvelope;
import com.enterprise.testagent.integration.externalapi.ExternalUserSshKeyApplicationService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证外部 SSH Key Controller 只返回 TAEK1 密文 envelope。 */
class ExternalUserSshKeyControllerTest {

    @Test
    void returnsEncryptedEnvelopeWithUnifiedSuccessContract() {
        ExternalUserSshKeyApplicationService service = mock(ExternalUserSshKeyApplicationService.class);
        ExternalApiPrincipal principal = new ExternalApiPrincipal(
                new ExternalApiCredentialId("eac_one"), "deploy.bot",
                Set.of(ExternalApiScope.USER_SSH_KEY_READ), API_KEY);
        ExternalSshKeyEnvelope envelope = new ExternalSshKeyEnvelope(
                "TAEK1", "HKDF-SHA256", "AES-256-GCM", "salt", "nonce", "ciphertext-value");
        when(service.get("u001", principal, TRACE_ID)).thenReturn(envelope);
        WebTestClient client = WebTestClient.bindToController(new ExternalUserSshKeyController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(ExternalApiWebSupport.PRINCIPAL_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        client.get().uri("/api/external/v1/users/u001/ssh-key")
                .header("X-Trace-Id", TRACE_ID)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.traceId").isEqualTo(TRACE_ID)
                .jsonPath("$.data.version").isEqualTo("TAEK1")
                .jsonPath("$.data.ciphertext").isEqualTo("ciphertext-value")
                .jsonPath("$.data.privateKey").doesNotExist();

        verify(service).get("u001", principal, TRACE_ID);
    }

    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final String API_KEY = "taak_v1_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
}
