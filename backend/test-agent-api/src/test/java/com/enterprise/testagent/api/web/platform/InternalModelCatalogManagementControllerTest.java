package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.configuration.management.InternalModelCatalogManagementApplicationService;
import com.enterprise.testagent.configuration.management.InternalModelCatalogManagementService;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.model.gateway.ModelCapabilityProbe;
import com.enterprise.testagent.model.gateway.ModelCapabilityProbeResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class InternalModelCatalogManagementControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-30T06:00:00Z");

    @Test
    void superAdminCanReplaceListAndProbeProviderModels() {
        FakeCatalog catalog = new FakeCatalog();
        FakeProbe probe = new FakeProbe();
        WebTestClient client = client(catalog, probe);

        client.put()
                .uri("/api/internal/platform/configuration-management/internal-model-providers/provider-a/models")
                .header("X-Trace-Id", "trace_model_catalog_123456")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"models":[{"modelId":"enterprise-chat","upstreamModelId":"upstream-chat",
                        "displayName":"企业对话","contextLimit":128000,"enabled":true,
                        "capabilities":["CHAT"]}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].modelId").isEqualTo("enterprise-chat");
        assertThat(catalog.savedProviderId).isEqualTo("provider-a");

        client.post()
                .uri("/api/internal/platform/configuration-management/internal-model-providers/provider-a/models/enterprise-chat/probe")
                .header("X-Trace-Id", "trace_model_probe_123456")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"capability\":\"CHAT\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.succeeded").isEqualTo(true);
        assertThat(probe.ucid).isEqualTo("AUTH_ADMIN");
    }

    private static WebTestClient client(FakeCatalog catalog, FakeProbe probe) {
        AuthPrincipal principal = new AuthPrincipal(
                "platform-token",
                new UserId("usr_admin"),
                "平台管理员",
                "AUTH_ADMIN",
                List.of(Dictionary.ROLE_SUPER_ADMIN),
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new InternalModelCatalogManagementController(catalog, probe))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static InternalModelProviderModel model() {
        return new InternalModelProviderModel(
                "provider-a", "enterprise-chat", "upstream-chat", "企业对话", 128_000L, true,
                Set.of(ModelCapability.CHAT), Set.of(), null, NOW, NOW);
    }

    private static final class FakeCatalog implements InternalModelCatalogManagementService {
        private String savedProviderId;

        @Override
        public List<InternalModelProviderModel> current(String providerId) {
            return List.of(model());
        }

        @Override
        public List<InternalModelProviderModel> save(
                String providerId,
                InternalModelCatalogManagementApplicationService.UpdateCatalogCommand command,
                String traceId) {
            savedProviderId = providerId;
            return List.of(model());
        }
    }

    private static final class FakeProbe implements ModelCapabilityProbe {
        private String ucid;

        @Override
        public Mono<ModelCapabilityProbeResult> probe(
                String providerId,
                String modelId,
                ModelCapability capability,
                String ucid,
                String traceId) {
            this.ucid = ucid;
            return Mono.just(new ModelCapabilityProbeResult(capability, true, NOW));
        }
    }
}
