package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemorySkillProposalStatus;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.memory.MemoryDocumentStore;
import com.enterprise.testagent.memory.MemoryViews.AdminHealthView;
import com.enterprise.testagent.memory.MemoryViews.MemoryView;
import com.enterprise.testagent.memory.MemoryViews.SkillProposalView;
import com.enterprise.testagent.memory.QaMemoryApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class QaMemoryControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final UserId USER = new UserId("usr_memory_api");

    @Test
    void currentUserCanQueryAvailabilityAndSubmitTeamProposal() {
        QaMemoryApplicationService service = mock(QaMemoryApplicationService.class);
        when(service.availableFor(USER)).thenReturn(true);
        when(service.createTeamCandidate(
                eq(USER), eq("app_memory"), eq("覆盖边界"), eq("mem_personal")))
                .thenReturn(memoryView());
        WebTestClient client = client(new QaMemoryController(service), List.of(Dictionary.ROLE_USER));

        client.get().uri("/api/internal/platform/memory/v1/availability").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data.enabled").isEqualTo(true);
        client.post().uri("/api/internal/platform/memory/v1/team/proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"applicationId\":\"app_memory\",\"content\":\"覆盖边界\","
                        + "\"sourceMemoryId\":\"mem_personal\"}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.scope").isEqualTo("TEAM_APPLICATION")
                .jsonPath("$.data.status").isEqualTo("CANDIDATE")
                .jsonPath("$.data.taskTypes").doesNotExist()
                .jsonPath("$.data.confidence").doesNotExist();
        verify(service).createTeamCandidate(USER, "app_memory", "覆盖边界", "mem_personal");
    }

    @Test
    void appAdminCannotUseSystemMemoryPage() {
        QaMemoryApplicationService service = mock(QaMemoryApplicationService.class);
        client(new QaMemoryAdminController(service), List.of(Dictionary.ROLE_APP_ADMIN)).get()
                .uri("/api/internal/platform/memory/v1/admin/health")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void superAdminCanReadMemoryHealthWithoutExposingSecrets() {
        QaMemoryApplicationService service = mock(QaMemoryApplicationService.class);
        when(service.adminHealth()).thenReturn(new AdminHealthView(
                true, new MemoryDocumentStore.Health(
                        true, "READY", "2.0.17",
                        List.of(new MemoryDocumentStore.EmbeddingProfileHealth(
                                "cpu", "java-gateway", "BAAI/bge-small-zh-v1.5", 512,
                                "revision", "collection-v1", true, true)),
                        new MemoryDocumentStore.ProjectionBacklog(1, 0, 0)),
                "chat-model", null, "memory-bge-small-zh-v1.5", 1, 0, 0));

        client(new QaMemoryAdminController(service), List.of(Dictionary.ROLE_SUPER_ADMIN)).get()
                .uri("/api/internal/platform/memory/v1/admin/health")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.memoryService.profiles[0].dimension").isEqualTo(512)
                .jsonPath("$.data.memoryService.status").isEqualTo("READY")
                .jsonPath("$.data.serviceApiKey").doesNotExist();
    }

    @Test
    void skillReviewAndPublishedAssetLinkRequireAppAdmin() {
        QaMemoryApplicationService service = mock(QaMemoryApplicationService.class);
        when(service.reviewSkillProposal(USER, "msp_1", "APPROVE", 0L))
                .thenReturn(skillView(MemorySkillProposalStatus.DRAFT, 1L));
        when(service.linkPublishedSkill(USER, "msp_1", "asset_skill_1", 1L))
                .thenReturn(skillView(MemorySkillProposalStatus.PUBLISHED, 2L));

        client(new QaMemoryController(service), List.of(Dictionary.ROLE_USER)).post()
                .uri("/api/internal/platform/memory/v1/skill-proposals/msp_1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
                .exchange().expectStatus().isForbidden();

        WebTestClient appAdmin = client(
                new QaMemoryController(service), List.of(Dictionary.ROLE_APP_ADMIN));
        appAdmin.post().uri("/api/internal/platform/memory/v1/skill-proposals/msp_1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("DRAFT");
        appAdmin.post().uri("/api/internal/platform/memory/v1/skill-proposals/msp_1/published-asset")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"publishedAssetId\":\"asset_skill_1\",\"expectedVersion\":1}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("PUBLISHED");

        verify(service).reviewSkillProposal(USER, "msp_1", "APPROVE", 0L);
        verify(service).linkPublishedSkill(USER, "msp_1", "asset_skill_1", 1L);
    }

    @Test
    void legacyQaApiReturnsGone() {
        client(new LegacyQaMemoryGoneController(), List.of(Dictionary.ROLE_USER)).get()
                .uri("/api/internal/platform/qa-memory/v1/personal")
                .exchange().expectStatus().isEqualTo(410)
                .expectBody().jsonPath("$.code").isEqualTo("API_GONE");
    }

    private MemoryView memoryView() {
        return new MemoryView(
                "mem_api", MemoryScope.TEAM_APPLICATION, null, "app_memory",
                MemoryStatus.CANDIDATE, MemorySource.TEAM_PROPOSAL,
                "覆盖边界", true, "覆盖边界", 0L, null, NOW, NOW);
    }

    private SkillProposalView skillView(MemorySkillProposalStatus status, long version) {
        return new SkillProposalView(
                "msp_1", "mem_1", "app_memory", "异常边界检查",
                status == MemorySkillProposalStatus.PENDING_REVIEW ? "" : "---\nname: edge-check\n---",
                status, USER.value(), USER.value(),
                status == MemorySkillProposalStatus.PUBLISHED ? "asset_skill_1" : null,
                version, NOW, NOW);
    }

    private WebTestClient client(Object controller, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER, "memory-user", "AUTH_MEMORY", roles, NOW, NOW.plusSeconds(3600));
        return WebTestClient.bindToController(controller)
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }
}
