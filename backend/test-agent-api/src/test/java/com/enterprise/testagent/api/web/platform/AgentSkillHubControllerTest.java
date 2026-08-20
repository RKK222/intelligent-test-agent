package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AgentSkillHubApplicationService;
import com.enterprise.testagent.workspace.AgentSkillHubResponses;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Skill Hub 分类入口必须在 HTTP 边界强校验超级管理员身份。 */
class AgentSkillHubControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void superAdminCanClassifyUserPushedSkill() {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);
        when(service.classifySkill("hub_asset_1", "TEST", "TEST_DESIGN", new UserId("usr_admin")))
                .thenReturn(new AgentSkillHubResponses.ClassificationResponse(
                        "hub_asset_1", "TEST", "TEST_DESIGN", "usr_admin", NOW));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).put()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets/hub_asset_1/classification")
                .header("X-Trace-Id", "trace_hub_classification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"category":"TEST","subcategory":"TEST_DESIGN"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.category").isEqualTo("TEST")
                .jsonPath("$.data.subcategory").isEqualTo("TEST_DESIGN")
                .jsonPath("$.data.classifiedByUserId").isEqualTo("usr_admin");

        verify(service).classifySkill("hub_asset_1", "TEST", "TEST_DESIGN", new UserId("usr_admin"));
    }

    @Test
    void appAdminAndAnonymousCannotClassifySkill() {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).put()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets/hub_asset_1/classification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"OTHER\"}")
                .exchange()
                .expectStatus().isForbidden();

        WebTestClient.bindToController(new AgentSkillHubController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .put()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets/hub_asset_1/classification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"OTHER\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listForwardsExplicitSourceAndExternalSyncRequiresSuperAdmin() {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);
        when(service.listAssets(
                "SKILL", null, null, "ALL", null, false, 1, 30, null, new UserId("usr_admin")))
                .thenReturn(new AgentSkillHubResponses.PageResponse<>(List.of(), 0, 1, 30));
        when(service.syncExternalSkillHubCatalog())
                .thenReturn(new AgentSkillHubResponses.ExternalSyncResponse(2, NOW));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).get()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets?type=SKILL&source=ALL")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data.total").isEqualTo(0);
        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/external/sync")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data.assetCount").isEqualTo(2);
        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/external/sync")
                .exchange().expectStatus().isForbidden();

        verify(service).listAssets(
                "SKILL", null, null, "ALL", null, false, 1, 30, null, new UserId("usr_admin"));
        verify(service).syncExternalSkillHubCatalog();
    }

    private static WebTestClient client(AgentSkillHubApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "platform-token", new UserId("usr_admin"), "平台管理员", "AUTH_HUB",
                roles, NOW, NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new AgentSkillHubController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
