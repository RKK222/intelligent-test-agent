package com.enterprise.testagent.configuration.management;

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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InternalModelCatalogManagementApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T04:00:00Z");

    @Test
    void replacesProviderCatalogAndResetsProbeState() {
        InMemoryModels models = new InMemoryModels();
        InternalModelCatalogManagementApplicationService service = service(models);

        List<InternalModelProviderModel> saved = service.save(
                "provider-a",
                new InternalModelCatalogManagementApplicationService.UpdateCatalogCommand(List.of(
                        new InternalModelCatalogManagementApplicationService.ModelItem(
                                " enterprise-chat ",
                                " upstream-chat ",
                                " 企业对话 ",
                                128_000L,
                                true,
                                Set.of(ModelCapability.CHAT, ModelCapability.VISION)))),
                "trace_catalog_123456");

        assertThat(saved).singleElement().satisfies(model -> {
            assertThat(model.modelId()).isEqualTo("enterprise-chat");
            assertThat(model.upstreamModelId()).isEqualTo("upstream-chat");
            assertThat(model.declaredCapabilities())
                    .containsExactlyInAnyOrder(ModelCapability.CHAT, ModelCapability.VISION);
            assertThat(model.probedCapabilities()).isEmpty();
            assertThat(model.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    void rejectsPublicModelIdAlreadyOwnedByAnotherProvider() {
        InMemoryModels models = new InMemoryModels();
        models.models.add(model("provider-b", "enterprise-chat"));
        InternalModelCatalogManagementApplicationService service = service(models);

        assertThatThrownBy(() -> service.save(
                "provider-a",
                new InternalModelCatalogManagementApplicationService.UpdateCatalogCommand(List.of(
                        new InternalModelCatalogManagementApplicationService.ModelItem(
                                "enterprise-chat",
                                "upstream-chat",
                                "企业对话",
                                128_000L,
                                true,
                                Set.of(ModelCapability.CHAT)))),
                "trace_catalog_123456"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void rejectsCatalogTextThatCannotFitTheDatabaseContract() {
        InternalModelCatalogManagementApplicationService service = service(new InMemoryModels());
        String overlongModelId = "m".repeat(257);

        assertThatThrownBy(() -> service.save(
                "provider-a",
                new InternalModelCatalogManagementApplicationService.UpdateCatalogCommand(List.of(
                        new InternalModelCatalogManagementApplicationService.ModelItem(
                                overlongModelId,
                                "upstream-chat",
                                "企业对话",
                                128_000L,
                                true,
                                Set.of(ModelCapability.CHAT)))),
                "trace_catalog_123456"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static InternalModelCatalogManagementApplicationService service(InMemoryModels models) {
        return new InternalModelCatalogManagementApplicationService(
                new FixedProviders(),
                models,
                event -> { },
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InternalModelProviderModel model(String providerId, String modelId) {
        return new InternalModelProviderModel(
                providerId,
                modelId,
                "upstream-chat",
                "企业对话",
                128_000L,
                true,
                Set.of(ModelCapability.CHAT),
                Set.of(),
                null,
                NOW,
                NOW);
    }

    private static final class FixedProviders implements InternalModelProviderRepository {
        private final InternalModelProvider provider = new InternalModelProvider(
                "provider-a", "Provider A", "http://models.internal/v1", true, 0,
                1L, "token-a", true, NOW, NOW);

        @Override
        public List<InternalModelProvider> findAll() {
            return List.of(provider);
        }

        @Override
        public List<InternalModelProvider> findEnabled() {
            return List.of(provider);
        }

        @Override
        public List<InternalModelProviderRuntimeConfig> findEnabledRuntimeConfigs() {
            return List.of(new InternalModelProviderRuntimeConfig(provider, "secret"));
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
        private final List<InternalModelProviderModel> models = new ArrayList<>();

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
                List<InternalModelProviderModel> replacement,
                Instant updatedAt) {
            models.removeIf(model -> model.providerId().equals(providerId));
            models.addAll(replacement);
        }

        @Override
        public void saveProbeResult(ModelProbeResult result) {
            throw new UnsupportedOperationException();
        }
    }
}
