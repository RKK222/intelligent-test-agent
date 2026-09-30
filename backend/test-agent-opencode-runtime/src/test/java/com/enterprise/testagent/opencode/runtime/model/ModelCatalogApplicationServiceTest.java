package com.enterprise.testagent.opencode.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.model.AiModelConfig;
import com.enterprise.testagent.domain.model.AiModelConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelCatalogApplicationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void internalSourceSeedsEnterpriseModelsWithQwen27BAsDefault() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("internal");
        FakeModelRepository repository = new FakeModelRepository();
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, repository, objectMapper);

        service.seedInternalModelsAfterStartup();

        List<Map<String, Object>> models = service.listModels();
        assertThat(models).isNotEmpty();
        assertThat(models.getFirst())
                .containsEntry("providerId", "enterprise-openai")
                .containsEntry("id", "Qwen3.6-27B")
                .containsEntry("defaultModel", true);
        assertThat(models).extracting(item -> item.get("id"))
                .containsExactly("Qwen3.6-27B", "DeepSeek-V4-Flash-W8A8");
    }

    @Test
    void internalSeedDoesNotOverrideExistingTableConfig() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("internal");
        FakeModelRepository repository = new FakeModelRepository();
        Instant now = Instant.now();
        repository.save(new AiModelConfig(
                "enterprise-openai",
                "DeepSeek-V4-Flash-W8A8",
                "人工禁用的 dsv4 flash",
                false,
                false,
                Set.of("text"),
                4096,
                1024,
                99,
                Map.of("source", "manual"),
                now,
                now));
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, repository, objectMapper);

        service.seedInternalModelsAfterStartup();

        AiModelConfig preserved = repository.models.get("enterprise-openai/DeepSeek-V4-Flash-W8A8");
        assertThat(preserved.name()).isEqualTo("人工禁用的 dsv4 flash");
        assertThat(preserved.enabled()).isFalse();
        assertThat(preserved.defaultModel()).isFalse();
        assertThat(preserved.contextLimit()).isEqualTo(4096);
    }

    @Test
    void localClientProviderConfigUsesOnlyLoopbackRelayEnvironmentReferences() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("internal");
        properties.getInternal().setApiKey("platform-secret-must-not-leave-backend");
        FakeModelRepository repository = new FakeModelRepository();
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(
                properties, repository, objectMapper);
        service.seedInternalModelsAfterStartup();

        assertThat(service.localClientProviderConfig()).asString()
                .contains("{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}")
                .contains("{env:TEST_AGENT_INTERNAL_PROXY_API_KEY}")
                .contains("X-Enterprise-Model-Provider=enterprise-openai")
                .contains("small_model=enterprise-openai/Qwen3.6-27B")
                .contains("enabled_providers=[enterprise-openai]")
                .doesNotContain("platform-secret-must-not-leave-backend")
                .doesNotContain("Auth-Token");
    }

    @Test
    void opencodeSourceLeavesModelCatalogUnmanaged() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("opencode");
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, new FakeModelRepository(), objectMapper);

        assertThat(service.managedSourceEnabled()).isFalse();
    }

    @Test
    void internalSourceIsRecognizedForUserScopedHeaders() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("internal");
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, new FakeModelRepository(), objectMapper);

        assertThat(service.internalSourceEnabled()).isTrue();
    }

    @Test
    void externalSourceUsesConfiguredExternalProviderModels() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("external");
        properties.getExternal().setProviderId("deepseek");
        properties.getExternal().setDefaultModel("deepseek-v4-flash");
        properties.getExternal().setModels(List.of(
                new ModelCatalogProperties.Model("deepseek-v4-flash", "DeepSeek V4 Flash", List.of("text"), 65536, 8192, true, 10)));
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, new FakeModelRepository(), objectMapper);

        List<Map<String, Object>> models = service.listModels();

        assertThat(models).hasSize(1);
        assertThat(models.getFirst())
                .containsEntry("providerId", "deepseek")
                .containsEntry("id", "deepseek-v4-flash")
                .containsEntry("name", "DeepSeek V4 Flash");
    }

    @Test
    void legacyBailianSourceKeepsModelStudioDefaults() {
        ModelCatalogProperties properties = new ModelCatalogProperties();

        properties.setSource("bailian");
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, new FakeModelRepository(), objectMapper);

        assertThat(properties.getSource()).isEqualTo("bailian");
        assertThat(properties.activeProvider()).isSameAs(properties.getBailian());
        assertThat(service.listProviders().getFirst())
                .containsEntry("providerId", "modelstudio")
                .containsEntry("name", "Model Studio Coding Plan");
        assertThat(service.listModels()).extracting(item -> item.get("id"))
                .contains("qwen3.5-plus", "kimi-k2.5", "qwen3-coder-plus");
    }

    private static class FakeModelRepository implements AiModelConfigRepository {
        private final Map<String, AiModelConfig> models = new LinkedHashMap<>();

        @Override
        public AiModelConfig save(AiModelConfig modelConfig) {
            models.put(modelConfig.providerId() + "/" + modelConfig.modelId(), modelConfig);
            return modelConfig;
        }

        @Override
        public boolean existsByProviderAndModel(String providerId, String modelId) {
            return models.containsKey(providerId + "/" + modelId);
        }

        @Override
        public List<AiModelConfig> findEnabledByProvider(String providerId) {
            return models.values().stream()
                    .filter(model -> model.enabled() && model.providerId().equals(providerId))
                    .sorted((left, right) -> {
                        int defaultCompare = Boolean.compare(right.defaultModel(), left.defaultModel());
                        if (defaultCompare != 0) {
                            return defaultCompare;
                        }
                        return Integer.compare(left.sortOrder(), right.sortOrder());
                    })
                    .toList();
        }

        @Override
        public Optional<AiModelConfig> findDefaultByProvider(String providerId) {
            return findEnabledByProvider(providerId).stream().filter(AiModelConfig::defaultModel).findFirst();
        }
    }

}
