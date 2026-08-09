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
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.memory.MemoryDocumentStore;
import com.enterprise.testagent.memory.MemoryViews.AdminHealthView;
import com.enterprise.testagent.memory.MemoryViews.EmbeddingProfile;
import com.enterprise.testagent.memory.MemoryViews.MemoryView;
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
    void currentUserCanQueryAvailabilityAndAppAdminCanCreateTeamMemory() {
        QaMemoryApplicationService service = mock(QaMemoryApplicationService.class);
        when(service.availableFor(USER)).thenReturn(true);
        when(service.createTeamDirect(eq(USER), eq("app_memory"), eq("覆盖边界"), any()))
                .thenReturn(memoryView());
        WebTestClient client = client(new QaMemoryController(service), List.of(Dictionary.ROLE_APP_ADMIN));

        client.get().uri("/api/internal/platform/qa-memory/v1/availability").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data.enabled").isEqualTo(true);
        client.post().uri("/api/internal/platform/qa-memory/v1/team")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"applicationId":"app_memory","content":"覆盖边界","taskTypes":["TEST_CASE_GENERATION"]}
                        """).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.scope").isEqualTo("TEAM_APPLICATION");
        verify(service).createTeamDirect(USER, "app_memory", "覆盖边界", List.of(QaTaskType.TEST_CASE_GENERATION));
    }

    @Test
    void ordinaryUserCannotUseDirectTeamCreationAndAppAdminCannotUseSystemMemoryPage() {
        QaMemoryApplicationService service = mock(QaMemoryApplicationService.class);
        client(new QaMemoryController(service), List.of(Dictionary.ROLE_USER)).post()
                .uri("/api/internal/platform/qa-memory/v1/team")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"applicationId\":\"app_memory\",\"content\":\"覆盖边界\"}")
                .exchange().expectStatus().isForbidden();

        client(new QaMemoryAdminController(service), List.of(Dictionary.ROLE_APP_ADMIN)).get()
                .uri("/api/internal/platform/system-management/memory/health")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void superAdminCanReadMemoryHealthWithoutExposingSecrets() {
        QaMemoryApplicationService service = mock(QaMemoryApplicationService.class);
        when(service.adminHealth()).thenReturn(new AdminHealthView(
                true, new MemoryDocumentStore.Health(true, "READY", "2.0.3"),
                new EmbeddingProfile("LOCAL_BGE", "BAAI/bge-small-zh-v1.5", "revision", 512,
                        "CPU", true, "collection-v1"),
                "chat-model", true, 1, 0, 0));

        client(new QaMemoryAdminController(service), List.of(Dictionary.ROLE_SUPER_ADMIN)).get()
                .uri("/api/internal/platform/system-management/memory/health")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.embedding.dimension").isEqualTo(512)
                .jsonPath("$.data.memoryService.status").isEqualTo("READY")
                .jsonPath("$.data.serviceApiKey").doesNotExist();
    }

    private MemoryView memoryView() {
        return new MemoryView(
                "mem_api", MemoryScope.TEAM_APPLICATION, null, "app_memory",
                MemoryStatus.ACTIVE, MemorySource.ADMIN_CREATED, List.of(QaTaskType.TEST_CASE_GENERATION),
                "覆盖边界", true, "覆盖边界", 1.0d, 0, 1, 0L,
                NOW, NOW, NOW);
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
