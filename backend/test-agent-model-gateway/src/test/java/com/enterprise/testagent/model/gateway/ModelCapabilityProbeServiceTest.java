package com.enterprise.testagent.model.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.configuration.ModelProbeResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ModelCapabilityProbeServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T05:00:00Z");

    @Test
    void probesDeclaredCapabilityWithProviderSecretAndStoresOnlySafeResult() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
                })
                .build();
        CapturingModels models = new CapturingModels(model());
        ModelCapabilityProbeService service = new ModelCapabilityProbeService(
                new FixedProviders(),
                models,
                client,
                Clock.fixed(NOW, ZoneOffset.UTC));

        ModelCapabilityProbeResult result = service.probe(
                "provider-a",
                "enterprise-chat",
                ModelCapability.CHAT,
                "AUTH_ADMIN",
                "trace_probe_123456").block();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.capability()).isEqualTo(ModelCapability.CHAT);
        assertThat(captured.get().url().toString())
                .isEqualTo("http://models.internal/v1/chat/completions");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer provider-secret");
        assertThat(captured.get().headers().getFirst("ucid")).isEqualTo("AUTH_ADMIN");
        assertThat(models.saved).isEqualTo(new ModelProbeResult(
                "provider-a", "enterprise-chat", ModelCapability.CHAT, true, NOW));
    }

    @Test
    void probesAllDeclaredModalitiesAgainstTheirFixedInternalEndpoints() {
        List<String> paths = new CopyOnWriteArrayList<>();
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> {
                    paths.add(request.url().getPath());
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
                })
                .build();
        InternalModelProviderModel allCapabilityModel = new InternalModelProviderModel(
                "provider-a", "enterprise-chat", "upstream-chat", "企业对话", 128_000L, true,
                Set.of(ModelCapability.values()), Set.of(), null, NOW, NOW);
        CapturingModels models = new CapturingModels(allCapabilityModel);
        ModelCapabilityProbeService service = new ModelCapabilityProbeService(
                new FixedProviders(), models, client, Clock.fixed(NOW, ZoneOffset.UTC));

        for (ModelCapability capability : ModelCapability.values()) {
            assertThat(service.probe(
                            "provider-a",
                            "enterprise-chat",
                            capability,
                            "AUTH_ADMIN",
                            "trace_probe_all").block())
                    .satisfies(result -> {
                        assertThat(result.capability()).isEqualTo(capability);
                        assertThat(result.succeeded()).isTrue();
                    });
        }

        assertThat(models.savedResults).hasSize(ModelCapability.values().length);
        assertThat(paths).contains(
                "/v1/chat/completions",
                "/v1/embeddings",
                "/v1/rerank",
                "/v1/images/generations",
                "/v1/audio/speech",
                "/v1/audio/transcriptions");
    }

    private static InternalModelProviderModel model() {
        return new InternalModelProviderModel(
                "provider-a", "enterprise-chat", "upstream-chat", "企业对话", 128_000L, true,
                Set.of(ModelCapability.CHAT), Set.of(), null, NOW, NOW);
    }

    private static final class FixedProviders implements InternalModelProviderRepository {
        private final InternalModelProvider provider = new InternalModelProvider(
                "provider-a", "Provider A", "http://models.internal/v1", true, 0,
                1L, "token-a", true, NOW, NOW);

        @Override
        public List<InternalModelProvider> findAll() { return List.of(provider); }

        @Override
        public List<InternalModelProvider> findEnabled() { return List.of(provider); }

        @Override
        public List<InternalModelProviderRuntimeConfig> findEnabledRuntimeConfigs() {
            return List.of(new InternalModelProviderRuntimeConfig(provider, "provider-secret"));
        }

        @Override
        public Optional<InternalModelProvider> findByProviderId(String providerId) {
            return provider.providerId().equals(providerId) ? Optional.of(provider) : Optional.empty();
        }

        @Override
        public void replaceProviders(List<InternalModelProvider> providers, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> findAuthToken() { return Optional.empty(); }

        @Override
        public void saveAuthToken(String authToken, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CapturingModels implements InternalModelProviderModelRepository {
        private final InternalModelProviderModel model;
        private ModelProbeResult saved;
        private final List<ModelProbeResult> savedResults = new CopyOnWriteArrayList<>();

        private CapturingModels(InternalModelProviderModel model) { this.model = model; }

        @Override
        public List<InternalModelProviderModel> findByProviderId(String providerId) { return List.of(model); }

        @Override
        public List<InternalModelProviderModel> findEnabled() { return List.of(model); }

        @Override
        public Optional<InternalModelProviderModel> findByModelId(String modelId) {
            return model.modelId().equals(modelId) ? Optional.of(model) : Optional.empty();
        }

        @Override
        public void replaceForProvider(String providerId, List<InternalModelProviderModel> models, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveProbeResult(ModelProbeResult result) {
            saved = result;
            savedResults.add(result);
        }
    }
}
