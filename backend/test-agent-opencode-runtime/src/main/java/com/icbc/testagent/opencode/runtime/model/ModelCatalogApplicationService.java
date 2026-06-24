package com.icbc.testagent.opencode.runtime.model;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.domain.model.AiModelConfig;
import com.icbc.testagent.domain.model.AiModelConfigRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 模型目录应用服务：外网读取百炼 /models，内网读取 ai_model_configs 表，并尽力同步 opencode provider 配置。
 */
@Service
public class ModelCatalogApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelCatalogApplicationService.class);
    private final ModelCatalogProperties properties;
    private final AiModelConfigRepository modelConfigRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 注入配置、模型配置仓储和 JSON 工具。
     */
    public ModelCatalogApplicationService(
            ModelCatalogProperties properties,
            AiModelConfigRepository modelConfigRepository,
            ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.modelConfigRepository = Objects.requireNonNull(modelConfigRepository, "modelConfigRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 应用就绪后把 openclaw 企业 patch 脚本中的模型清单 seed 到数据库。
     * 这里必须晚于 Flyway 建表，否则本地新库首次启动会因为 ai_model_configs 尚未创建而失败。
     */
    @EventListener(ApplicationReadyEvent.class)
    void seedInternalModelsAfterStartup() {
        if (!"internal".equals(properties.getSource())) {
            return;
        }
        seedInternalModels();
    }

    /**
     * 当前是否由平台托管模型列表，而不是直接代理 opencode。
     */
    public boolean managedSourceEnabled() {
        return !"opencode".equals(properties.getSource());
    }

    /**
     * 返回当前来源的模型列表。
     */
    public List<Map<String, Object>> listModels() {
        if ("internal".equals(properties.getSource())) {
            return internalModels().stream().map(this::toModelPayload).toList();
        }
        return externalModels();
    }

    /**
     * 返回当前来源的 provider 列表。
     */
    public List<Map<String, Object>> listProviders() {
        ModelCatalogProperties.Provider provider = properties.activeProvider();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", provider.getProviderId());
        payload.put("providerID", provider.getProviderId());
        payload.put("providerId", provider.getProviderId());
        payload.put("name", provider.getName());
        payload.put("status", "configured");
        return List.of(payload);
    }

    /**
     * 尽力把当前 provider 定义写入 opencode 配置；失败只记录日志，保留原 Run 错误路径。
     */
    public void syncProviderConfig(AgentRuntime runtime, ExecutionNode node, String traceId) {
        if (!managedSourceEnabled()) {
            return;
        }
        try {
            runtime.runtime(new AgentRuntimeCommand(node, "PATCH", "/global/config", null, null, Map.of(), providerConfigPatch(), traceId))
                    .block();
        } catch (Exception exception) {
            LOGGER.warn("event=model_provider_sync_failed traceId={} providerId={} error={}",
                    traceId,
                    properties.activeProvider().getProviderId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void seedInternalModels() {
        Instant now = Instant.now();
        String providerId = properties.getInternal().getProviderId();
        for (ModelCatalogProperties.Model model : properties.getInternal().getModels()) {
            if (modelConfigRepository.existsByProviderAndModel(providerId, model.getId())) {
                continue;
            }
            modelConfigRepository.save(new AiModelConfig(
                    providerId,
                    model.getId(),
                    blankToDefault(model.getName(), model.getId()),
                    true,
                    model.isDefaultModel() || model.getId().equals(properties.getInternal().getDefaultModel()),
                    Set.copyOf(model.getInput()),
                    model.getContextLimit(),
                    model.getOutputLimit(),
                    model.getSortOrder(),
                    Map.of("source", "openclaw-enterprise-patch"),
                    now,
                    now));
        }
    }

    private List<AiModelConfig> internalModels() {
        String providerId = properties.getInternal().getProviderId();
        List<AiModelConfig> models = modelConfigRepository.findEnabledByProvider(providerId);
        if (!models.isEmpty()) {
            return models;
        }
        seedInternalModels();
        return modelConfigRepository.findEnabledByProvider(providerId);
    }

    private List<Map<String, Object>> externalModels() {
        ModelCatalogProperties.Provider provider = properties.getExternal();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(provider.getBaseUrl()) + "/models"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json");
            String apiKey = System.getenv(provider.getApiKeyEnv());
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Bailian models returned HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return configuredExternalModels();
            }
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            data.forEach(item -> result.add(toExternalPayload(provider, item)));
            return result;
        } catch (Exception exception) {
            LOGGER.warn("event=bailian_models_fetch_failed providerId={} error={}", provider.getProviderId(), exception.getClass().getSimpleName());
            return configuredExternalModels();
        }
    }

    private List<Map<String, Object>> configuredExternalModels() {
        ModelCatalogProperties.Provider provider = properties.getExternal();
        return provider.getModels().stream().map(model -> toConfiguredPayload(provider.getProviderId(), model)).toList();
    }

    private Map<String, Object> toExternalPayload(ModelCatalogProperties.Provider provider, JsonNode raw) {
        String id = raw.path("id").asText("unknown");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("modelId", id);
        payload.put("modelID", id);
        payload.put("providerId", provider.getProviderId());
        payload.put("providerID", provider.getProviderId());
        payload.put("name", id);
        return payload;
    }

    private Map<String, Object> toConfiguredPayload(String providerId, ModelCatalogProperties.Model model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", model.getId());
        payload.put("modelId", model.getId());
        payload.put("modelID", model.getId());
        payload.put("providerId", providerId);
        payload.put("providerID", providerId);
        payload.put("name", blankToDefault(model.getName(), model.getId()));
        payload.put("contextLimit", model.getContextLimit());
        payload.put("outputLimit", model.getOutputLimit());
        payload.put("defaultModel", model.isDefaultModel());
        return payload;
    }

    private Map<String, Object> toModelPayload(AiModelConfig model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", model.modelId());
        payload.put("modelId", model.modelId());
        payload.put("modelID", model.modelId());
        payload.put("providerId", model.providerId());
        payload.put("providerID", model.providerId());
        payload.put("name", model.name());
        payload.put("contextLimit", model.contextLimit());
        payload.put("outputLimit", model.outputLimit());
        payload.put("defaultModel", model.defaultModel());
        return payload;
    }

    private Map<String, Object> providerConfigPatch() {
        ModelCatalogProperties.Provider provider = properties.activeProvider();
        Map<String, Object> models = new LinkedHashMap<>();
        if ("internal".equals(properties.getSource())) {
            for (AiModelConfig model : internalModels()) {
                models.put(model.modelId(), toOpenCodeModelConfig(model));
            }
        } else {
            for (ModelCatalogProperties.Model model : provider.getModels()) {
                models.put(model.getId(), toOpenCodeModelConfig(model));
            }
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("baseURL", stripTrailingSlash(provider.getBaseUrl()));
        Map<String, String> headers = new LinkedHashMap<>();
        if ("internal".equals(properties.getSource())) {
            headers.put("environment", "test");
        }
        String envRef = "{env:" + provider.getApiKeyEnv() + "}";
        if ("auth-token".equals(provider.getAuthMode())) {
            headers.put("Auth-Token", envRef);
        }
        if (!headers.isEmpty()) {
            options.put("headers", headers);
        }
        return Map.of(
                "model", provider.getProviderId() + "/" + provider.getDefaultModel(),
                "provider", Map.of(provider.getProviderId(), Map.of(
                        "name", provider.getName(),
                        "env", List.of(provider.getApiKeyEnv()),
                        "npm", "@ai-sdk/openai-compatible",
                        "api", stripTrailingSlash(provider.getBaseUrl()),
                        "options", options,
                        "models", models)));
    }

    private Map<String, Object> toOpenCodeModelConfig(AiModelConfig model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", model.name());
        payload.put("tool_call", true);
        payload.put("modalities", Map.of("input", List.copyOf(model.inputModalities()), "output", List.of("text")));
        payload.put("limit", Map.of("context", model.contextLimit(), "output", model.outputLimit()));
        return payload;
    }

    private Map<String, Object> toOpenCodeModelConfig(ModelCatalogProperties.Model model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", blankToDefault(model.getName(), model.getId()));
        payload.put("tool_call", true);
        payload.put("modalities", Map.of("input", model.getInput(), "output", List.of("text")));
        payload.put("limit", Map.of("context", model.getContextLimit(), "output", model.getOutputLimit()));
        return payload;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
