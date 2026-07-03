package com.icbc.testagent.opencode.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.agent.runtime.AgentRuntimeResult;
import com.icbc.testagent.domain.model.AiModelConfig;
import com.icbc.testagent.domain.model.AiModelConfigRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ModelCatalogApplicationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void internalSourceSeedsEnterpriseModelsWithDsv4FlashAsDefault() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("internal");
        FakeModelRepository repository = new FakeModelRepository();
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, repository, objectMapper);

        service.seedInternalModelsAfterStartup();

        List<Map<String, Object>> models = service.listModels();
        assertThat(models).isNotEmpty();
        assertThat(models.getFirst())
                .containsEntry("providerId", "icbc-openai")
                .containsEntry("id", "DeepSeek-V4-Flash-W8A8")
                .containsEntry("defaultModel", true);
        assertThat(models).extracting(item -> item.get("id"))
                .contains("Qwen3.6-27B", "Qwen3.6-35B-A3B", "Qwen3-32B-128K", "deepseekV3-0324-chat", "glm-51");
    }

    @Test
    void internalSeedDoesNotOverrideExistingTableConfig() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("internal");
        FakeModelRepository repository = new FakeModelRepository();
        Instant now = Instant.now();
        repository.save(new AiModelConfig(
                "icbc-openai",
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

        AiModelConfig preserved = repository.models.get("icbc-openai/DeepSeek-V4-Flash-W8A8");
        assertThat(preserved.name()).isEqualTo("人工禁用的 dsv4 flash");
        assertThat(preserved.enabled()).isFalse();
        assertThat(preserved.defaultModel()).isFalse();
        assertThat(preserved.contextLimit()).isEqualTo(4096);
    }

    @Test
    void syncProviderConfigPatchesOpencodeWithCurrentProvider() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("internal");
        FakeModelRepository repository = new FakeModelRepository();
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, repository, objectMapper);
        service.seedInternalModelsAfterStartup();
        RecordingRuntime runtime = new RecordingRuntime();

        service.syncProviderConfig(runtime, node(), "trace_model_test");

        assertThat(runtime.command).isNotNull();
        assertThat(runtime.command.method()).isEqualTo("PATCH");
        assertThat(runtime.command.path()).isEqualTo("/global/config");
        assertThat(runtime.command.body()).asString()
                .contains("icbc-openai")
                .contains("DeepSeek-V4-Flash-W8A8")
                .contains("Auth-Token")
                .contains("provider=")
                .doesNotContain("providers=");
    }

    @Test
    void syncProviderConfigUsesConfiguredApiKeyBeforeEnvironmentReference() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("external");
        properties.getExternal().setApiKey("configured-model-key");
        FakeModelRepository repository = new FakeModelRepository();
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, repository, objectMapper);
        RecordingRuntime runtime = new RecordingRuntime();

        service.syncProviderConfig(runtime, node(), "trace_model_key_test");

        assertThat(runtime.command).isNotNull();
        assertThat(runtime.command.body()).asString()
                .contains("apiKey=configured-model-key")
                .doesNotContain("{env:EXTERNAL_API_KEY}");
    }

    @Test
    void opencodeSourceLeavesModelCatalogUnmanaged() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setSource("opencode");
        ModelCatalogApplicationService service = new ModelCatalogApplicationService(properties, new FakeModelRepository(), objectMapper);

        assertThat(service.managedSourceEnabled()).isFalse();
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

    private ExecutionNode node() {
        Instant now = Instant.now();
        return new ExecutionNode(
                new ExecutionNodeId("node_model_test"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                now);
    }

    private static class RecordingRuntime implements AgentRuntime {
        private AgentRuntimeCommand command;

        @Override
        public String agentId() {
            return "opencode";
        }

        @Override
        public Mono<AgentRuntimeResult> runtime(AgentRuntimeCommand command) {
            this.command = command;
            return Mono.just(new AgentRuntimeResult(new ObjectMapper().createObjectNode().put("ok", true)));
        }
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
