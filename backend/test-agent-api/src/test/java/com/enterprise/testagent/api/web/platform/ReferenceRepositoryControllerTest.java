package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.ReferenceRepositoryApplicationService;
import com.enterprise.testagent.workspace.ReferenceRepositoryResponses;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ReferenceRepositoryControllerTest {

    private static final String BASE =
            "/api/internal/platform/workspace-management/applications/app-demo/reference-repositories";
    private static final String TRACE_ID = "trace_reference_api";
    private static final UserId USER_ID = new UserId("usr-admin");

    @Test
    void appAdminCanUseAllReferenceRepositoryEndpoints() {
        ReferenceRepositoryApplicationService service = mock(ReferenceRepositoryApplicationService.class);
        ReferenceRepositoryResponses.Status status = status();
        when(service.list("app-demo")).thenReturn(List.of(status));
        when(service.initialize("app-demo", "repo-assets", "main", USER_ID, TRACE_ID)).thenReturn(status);
        when(service.synchronize("app-demo", "repo-assets", USER_ID, TRACE_ID)).thenReturn(status);
        when(service.switchBranch("app-demo", "repo-assets", "release", USER_ID, TRACE_ID)).thenReturn(status);
        when(service.verify("app-demo", "repo-assets", TRACE_ID)).thenReturn(status);
        when(service.status("app-demo", "repo-assets")).thenReturn(status);
        when(service.tree("app-demo", "repo-assets", "docs")).thenReturn(List.of(
                new ReferenceRepositoryResponses.TreeNode("docs/spec.md", "spec.md", false, 12L, false, false)));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_APP_ADMIN));

        client.get().uri(BASE).header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data[0].repositoryId").isEqualTo("repo-assets");
        client.post().uri(BASE + "/repo-assets/initialize").header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"branch\":\"main\"}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data.generation").isEqualTo(1);
        client.post().uri(BASE + "/repo-assets/synchronize").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk();
        client.post().uri(BASE + "/repo-assets/switch-branch").header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"branch\":\"release\"}").exchange()
                .expectStatus().isOk();
        client.post().uri(BASE + "/repo-assets/verify").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk();
        client.get().uri(BASE + "/repo-assets/status").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.data.status").isEqualTo("INITIALIZING")
                .jsonPath("$.data.operation").isEqualTo("SYNCHRONIZE");
        client.get().uri(BASE + "/repo-assets/tree?path=docs").header("X-Trace-Id", TRACE_ID).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data[0].path").isEqualTo("docs/spec.md");

        verify(service).initialize("app-demo", "repo-assets", "main", USER_ID, TRACE_ID);
        verify(service).synchronize("app-demo", "repo-assets", USER_ID, TRACE_ID);
        verify(service).switchBranch("app-demo", "repo-assets", "release", USER_ID, TRACE_ID);
        verify(service).verify("app-demo", "repo-assets", TRACE_ID);
        verify(service).tree("app-demo", "repo-assets", "docs");
    }

    @Test
    void ordinaryUserCannotUseAnyReferenceRepositoryEndpoint() {
        ReferenceRepositoryApplicationService service = mock(ReferenceRepositoryApplicationService.class);
        WebTestClient client = client(service, List.of(Dictionary.ROLE_USER));

        client.get().uri(BASE).exchange().expectStatus().isForbidden();
        client.post().uri(BASE + "/repo-assets/initialize")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"branch\":\"main\"}")
                .exchange().expectStatus().isForbidden();
        client.post().uri(BASE + "/repo-assets/synchronize").exchange().expectStatus().isForbidden();
        client.post().uri(BASE + "/repo-assets/switch-branch")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"branch\":\"release\"}")
                .exchange().expectStatus().isForbidden();
        client.post().uri(BASE + "/repo-assets/verify").exchange().expectStatus().isForbidden();
        client.get().uri(BASE + "/repo-assets/status").exchange().expectStatus().isForbidden();
        client.get().uri(BASE + "/repo-assets/tree?path=").exchange().expectStatus().isForbidden();

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void superAdminInheritsApplicationAdminPermission() {
        ReferenceRepositoryApplicationService service = mock(ReferenceRepositoryApplicationService.class);
        when(service.list("app-demo")).thenReturn(List.of());

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).get().uri(BASE)
                .exchange().expectStatus().isOk();
    }

    private static WebTestClient client(ReferenceRepositoryApplicationService service, List<String> roles) {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "admin", "001", roles, now, now.plusSeconds(3600));
        return WebTestClient.bindToController(new ReferenceRepositoryController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ReferenceRepositoryResponses.Status status() {
        return new ReferenceRepositoryResponses.Status(
                "repo-assets",
                "资产库",
                "assets",
                "https://git.example.test/assets.git",
                true,
                "main",
                "commit-1",
                1L,
                "INITIALIZING",
                1,
                0,
                List.of(),
                TRACE_ID,
                null);
    }
}
