package com.enterprise.testagent.model.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.configuration.ModelProbeResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelGatewayCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T02:00:00Z");

    @Test
    void listsOnlyProbedCapabilitiesAndResolvesPublicModelToProviderSecret() {
        InternalModelProvider provider = provider(true);
        InMemoryProviders providers = new InMemoryProviders(provider, "provider-secret");
        InMemoryModels models = new InMemoryModels(List.of(model(
                true,
                Set.of(ModelCapability.CHAT, ModelCapability.VISION),
                Set.of(ModelCapability.CHAT))));
        ModelGatewayCatalogService service = new ModelGatewayCatalogService(providers, models);

        List<ModelGatewayModelView> catalog = service.listModels();

        assertThat(catalog).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo("enterprise-chat");
            assertThat(view.capabilities()).containsExactly(ModelCapability.CHAT);
            assertThat(view.providerId()).isNull();
        });

        ResolvedModel resolved = service.resolve("enterprise-chat", ModelCapability.CHAT);
        assertThat(resolved.provider().provider().providerId()).isEqualTo("provider-a");
        assertThat(resolved.provider().authToken()).isEqualTo("provider-secret");
        assertThat(resolved.upstreamModelId()).isEqualTo("upstream-chat-v2");
    }

    @Test
    void rejectsDisabledUnprobedAndCapabilityMismatchedModels() {
        InMemoryProviders providers = new InMemoryProviders(provider(true), "provider-secret");
        InMemoryModels models = new InMemoryModels(List.of(model(
                true,
                Set.of(ModelCapability.CHAT),
                Set.of())));
        ModelGatewayCatalogService service = new ModelGatewayCatalogService(providers, models);

        assertThat(service.listModels()).isEmpty();
        assertThatThrownBy(() -> service.resolve("enterprise-chat", ModelCapability.CHAT))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        models.models = List.of(model(
                true,
                Set.of(ModelCapability.CHAT),
                Set.of(ModelCapability.CHAT)));
        assertThatThrownBy(() -> service.resolve("enterprise-chat", ModelCapability.EMBEDDING))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static InternalModelProvider provider(boolean enabled) {
        return new InternalModelProvider(
                "provider-a",
                "Provider A",
                "http://models.internal/v1",
                enabled,
                0,
                1L,
                "token-a",
                true,
                NOW,
                NOW);
    }

    private static InternalModelProviderModel model(
            boolean enabled,
            Set<ModelCapability> declared,
            Set<ModelCapability> probed) {
        return new InternalModelProviderModel(
                "provider-a",
                "enterprise-chat",
                "upstream-chat-v2",
                "企业对话",
                128_000L,
                enabled,
                declared,
                probed,
                NOW,
                NOW,
                NOW);
    }

    private static final class InMemoryProviders implements InternalModelProviderRepository {
        private final InternalModelProvider provider;
        private final String token;

        private InMemoryProviders(InternalModelProvider provider, String token) {
            this.provider = provider;
            this.token = token;
        }

        @Override
        public List<InternalModelProvider> findAll() {
            return List.of(provider);
        }

        @Override
        public List<InternalModelProvider> findEnabled() {
            return provider.enabled() ? List.of(provider) : List.of();
        }

        @Override
        public List<InternalModelProviderRuntimeConfig> findEnabledRuntimeConfigs() {
            return provider.enabled()
                    ? List.of(new InternalModelProviderRuntimeConfig(provider, token))
                    : List.of();
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
        public Optional<String> findAuthToken() {
            return Optional.empty();
        }

        @Override
        public void saveAuthToken(String authToken, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryModels implements InternalModelProviderModelRepository {
        private List<InternalModelProviderModel> models;

        private InMemoryModels(List<InternalModelProviderModel> models) {
            this.models = new ArrayList<>(models);
        }

        @Override
        public List<InternalModelProviderModel> findByProviderId(String providerId) {
            return models.stream().filter(model -> model.providerId().equals(providerId)).toList();
        }

        @Override
        public List<InternalModelProviderModel> findEnabled() {
            return models.stream().filter(InternalModelProviderModel::enabled).toList();
        }

        @Override
        public Optional<InternalModelProviderModel> findByModelId(String modelId) {
            return models.stream().filter(model -> model.modelId().equals(modelId)).findFirst();
        }

        @Override
        public void replaceForProvider(
                String providerId,
                List<InternalModelProviderModel> models,
                Instant updatedAt) {
            this.models = List.copyOf(models);
        }

        @Override
        public void saveProbeResult(ModelProbeResult result) {
            throw new UnsupportedOperationException();
        }
    }
}
