package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.ApplicationAssetReferenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ApplicationAssetReferenceControllerTest {
    private static final String BASE = "/api/internal/platform/workspace-management/applications/app-demo/asset-reference-configurations";
    private static final UserId MEMBER = new UserId("usr_member");

    @Test
    void memberCanReadSafeListingButCannotMutate() {
        ApplicationAssetReferenceService service = mock(ApplicationAssetReferenceService.class);
        when(service.list("app-demo", MEMBER, false, false))
                .thenReturn(new ApplicationAssetReferenceService.Listing(List.of(
                        new ApplicationAssetReferenceService.Configuration("repo_assets", "资产库",
                                "docs-assets", "docs", true, "docs", "产品资料", 1)), false, List.of()));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_USER));

        client.get().uri(BASE).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.configurations[0].alias").isEqualTo("docs-assets")
                .jsonPath("$.data.configurations[0].directoryPath").isEqualTo("docs")
                .jsonPath("$.data.configurations[0].gitUrl").doesNotExist();
        client.put().uri(BASE + "/repo_assets").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"directoryPath\":\"docs\",\"merge\":true,\"description\":\"资料\",\"expectedVersion\":0}")
                .exchange().expectStatus().isForbidden();
        client.delete().uri(BASE + "/repo_assets?directoryPath=docs&expectedVersion=1")
                .exchange().expectStatus().isForbidden();
        verify(service).list("app-demo", MEMBER, false, false);
        verifyNoMoreInteractions(service);
    }

    @Test
    void administratorCanWriteSharedConfiguration() {
        ApplicationAssetReferenceService service = mock(ApplicationAssetReferenceService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.put().uri(BASE + "/repo_assets").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"directoryPath\":\"docs\",\"merge\":true,\"description\":\"资料\",\"expectedVersion\":0}")
                .exchange().expectStatus().isOk();
        verify(service).save("app-demo", "repo_assets", "docs", true, "资料", 0);
    }

    private static WebTestClient client(ApplicationAssetReferenceService service, List<String> roles) {
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        AuthPrincipal principal = new AuthPrincipal("token", MEMBER, "member", "001", roles,
                now, now.plusSeconds(3600));
        return WebTestClient.bindToController(new ApplicationAssetReferenceController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
