package com.icbc.testagent.opencode.runtime.model;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.domain.model.AiModelConfig;
import com.icbc.testagent.domain.model.AiModelConfigRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 模型目录应用服务：外网读取 OpenAI-compatible /models，内网读取 ai_model_configs 表，并尽力同步 opencode provider 配置。
 */
@Service
public class ModelCatalogApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelCatalogApplicationService.class);
    private final ModelCatalogProperties properties;
    private final AiModelConfigRepository modelConfigRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final HttpClient httpClient;

    /**
     * 注入配置、模型配置仓储、JSON 工具和用户仓储；internal 模式同步 provider 时需要按当前用户解析 UCID。
     */
    @Autowired
    public ModelCatalogApplicationService(
            ModelCatalogProperties properties,
            AiModelConfigRepository modelConfigRepository,
            ObjectMapper objectMapper,
            UserRepository userRepository) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.modelConfigRepository = Objects.requireNonNull(modelConfigRepository, "modelConfigRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 测试兼容构造；没有用户仓储时 internal provider 同步不会写入用户级 UCID。
     */
    ModelCatalogApplicationService(
            ModelCatalogProperties properties,
            AiModelConfigRepository modelConfigRepository,
            ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.modelConfigRepository = Objects.requireNonNull(modelConfigRepository, "modelConfigRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.userRepository = null;
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
     * 当前是否使用企业内模型来源；该来源需要登录用户和用户专属 opencode 进程避免 UCID 串号。
     */
    public boolean internalSourceEnabled() {
        return "internal".equals(properties.getSource());
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
        syncProviderConfig(runtime, node, traceId, null);
    }

    /**
     * 尽力把当前 provider 定义写入 opencode 配置；internal 模式会把当前用户统一认证号写入 provider headers。
     */
    public void syncProviderConfig(AgentRuntime runtime, ExecutionNode node, String traceId, UserId userId) {
        if (!managedSourceEnabled()) {
            return;
        }
        String ucid = resolveCurrentUcid(userId);
        logInternalUcidHeader(traceId, userId, ucid);
        try {
            runtime.runtime(new AgentRuntimeCommand(node, "PATCH", "/global/config", null, null, Map.of(), providerConfigPatch(ucid), traceId))
                    .block();
        } catch (Exception exception) {
            LOGGER.warn("event=model_provider_sync_failed traceId={} providerId={} error={}",
                    traceId,
                    properties.activeProvider().getProviderId(),
                    exception.getClass().getSimpleName());
        }
    }

    private String resolveCurrentUcid(UserId userId) {
        if (!internalSourceEnabled() || userId == null || userRepository == null) {
            return null;
        }
        return userRepository.findByUserId(userId)
                .map(User::unifiedAuthId)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
    }

    private void logInternalUcidHeader(String traceId, UserId userId, String ucid) {
        if (!internalSourceEnabled()) {
            return;
        }
        // UCID 是企业内模型 API 的路由标识，按当前项目约定允许明文记录；认证 token 仍不得写入日志。
        LOGGER.info("event=model_provider_ucid_header_resolved traceId={} providerId={} userId={} ucidHeaderName={} ucid={} ucidPresent={}",
                traceId,
                properties.getInternal().getProviderId(),
                userId == null ? "" : userId.value(),
                properties.getInternal().getUcidHeaderName(),
                ucid == null ? "" : ucid,
                ucid != null && !ucid.isBlank());
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
        ModelCatalogProperties.Provider provider = properties.activeProvider();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(provider.getBaseUrl()) + "/models"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json");
            String apiKey = resolveApiKey(provider);
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("External models returned HTTP " + response.statusCode());
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
            LOGGER.warn("event=external_models_fetch_failed providerId={} error={}", provider.getProviderId(), exception.getClass().getSimpleName());
            return configuredExternalModels();
        }
    }

    private List<Map<String, Object>> configuredExternalModels() {
        ModelCatalogProperties.Provider provider = properties.activeProvider();
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
        payload.put("defaultModel", id.equals(provider.getDefaultModel()));
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

    private Map<String, Object> providerConfigPatch(String ucid) {
        ModelCatalogProperties.Provider provider = properties.activeProvider();
        Map<String, Object> models = new LinkedHashMap<>();
        if ("internal".equals(properties.getSource())) {
            for (AiModelConfig model : internalModels()) {
                models.put(model.modelId(), toOpenCodeModelConfig(model));
            }
        } else {
            for (Map<String, Object> model : externalModels()) {
                String modelId = String.valueOf(model.get("id"));
                models.put(modelId, toOpenCodeModelConfig(modelId, String.valueOf(model.getOrDefault("name", modelId))));
            }
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("baseURL", stripTrailingSlash(provider.getBaseUrl()));
        Map<String, String> headers = new LinkedHashMap<>();
        if ("internal".equals(properties.getSource())) {
            headers.put("environment", "test");
            if (ucid != null && !ucid.isBlank()) {
                headers.put(properties.getInternal().getUcidHeaderName(), ucid);
            }
        }
        String apiKey = resolveApiKey(provider);
        String envRef = "{env:" + provider.getApiKeyEnv() + "}";
        if ("bearer".equals(provider.getAuthMode()) && apiKey != null && !apiKey.isBlank()) {
            options.put("apiKey", apiKey);
        }
        if ("auth-token".equals(provider.getAuthMode())) {
            headers.put("Auth-Token", apiKey == null || apiKey.isBlank() ? envRef : apiKey);
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

    private String resolveApiKey(ModelCatalogProperties.Provider provider) {
        if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            return provider.getApiKey();
        }
        return System.getenv(provider.getApiKeyEnv());
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

    private Map<String, Object> toOpenCodeModelConfig(String modelId, String name) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", blankToDefault(name, modelId));
        payload.put("tool_call", true);
        payload.put("modalities", Map.of("input", List.of("text"), "output", List.of("text")));
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
