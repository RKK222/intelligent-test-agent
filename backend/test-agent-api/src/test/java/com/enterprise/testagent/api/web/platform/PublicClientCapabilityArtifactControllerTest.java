package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.PublicClientCapabilityPackageService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class PublicClientCapabilityArtifactControllerTest {

    private static final String DIGEST = "b".repeat(64);
    private static final String PATH = "/api/internal/platform/workspace-management/agent-config/public/"
            + "client-capabilities/" + DIGEST + "/artifact";

    @Test
    void superAdminDownloadsAvailableArtifactThroughApplicationService() {
        PublicClientCapabilityPackageService service = mock(PublicClientCapabilityPackageService.class);
        byte[] artifact = "portable-capability".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.availableReleaseForDigest(DIGEST)).thenReturn(new LocalClientPublicCapabilityModels.Release(
                "c".repeat(40), DIGEST, "a".repeat(64),
                LocalClientPublicCapabilityModels.Compatibility.AVAILABLE, null,
                "{\"schemaVersion\":1}", "{\"initial\":true}",
                new LocalClientPublicCapabilityModels.Counts(1, 2, 3), true,
                artifact, artifact.length, artifact.length, 7,
                Instant.parse("2026-08-23T08:00:00Z")));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).get().uri(PATH).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-TestAgent-Source-Commit", "c".repeat(40))
                .expectHeader().valueEquals("X-TestAgent-Bundle-Digest", DIGEST)
                .expectBody().consumeWith(result -> assertThat(result.getResponseBody()).isEqualTo(artifact));

        verify(service).availableReleaseForDigest(DIGEST);
    }

    @Test
    void nonSuperAdminCannotDownloadArtifact() {
        PublicClientCapabilityPackageService service = mock(PublicClientCapabilityPackageService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).get().uri(PATH).exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(service);
    }

    private static WebTestClient client(PublicClientCapabilityPackageService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_public_capability_artifact_test"),
                "admin",
                "AUTH_1",
                roles,
                Instant.parse("2026-08-23T07:00:00Z"),
                Instant.parse("2026-08-23T09:00:00Z"));
        return WebTestClient.bindToController(new PublicClientCapabilityArtifactController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
