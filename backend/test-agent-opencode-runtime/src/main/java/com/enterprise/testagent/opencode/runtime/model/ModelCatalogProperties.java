package com.enterprise.testagent.opencode.runtime.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台大模型目录配置，控制模型列表来源和同步到 opencode 的 provider 定义。
 */
@Component
@ConfigurationProperties(prefix = "test-agent.model-catalog")
public class ModelCatalogProperties {

    private String source = "external";
    private final Provider external = new Provider(
            "external-openai",
            "External OpenAI Compatible",
            "",
            "EXTERNAL_API_KEY",
            "bearer",
            "",
            new ArrayList<>());
    private final Provider bailian = new Provider(
            "modelstudio",
            "Model Studio Coding Plan",
            "https://coding.dashscope.aliyuncs.com/v1",
            "MODELSTUDIO_API_KEY",
            "bearer",
            "qwen3.5-plus",
            new ArrayList<>(List.of(
                    new Model("qwen3.5-plus", "qwen3.5-plus", List.of("text"), 131072, 16384, true, 10),
                    new Model("qwen3-max-2026-01-23", "qwen3-max-2026-01-23", List.of("text"), 131072, 16384, false, 20),
                    new Model("kimi-k2.5", "kimi-k2.5", List.of("text"), 131072, 16384, false, 30),
                    new Model("qwen3-coder-next", "qwen3-coder-next", List.of("text"), 131072, 16384, false, 40),
                    new Model("qwen3-coder-plus", "qwen3-coder-plus", List.of("text"), 131072, 16384, false, 50))));
    private final Provider internal = new Provider(
            "enterprise-openai",
            "Enterprise OpenAI",
            "http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1",
            "ENTERPRISE_OPENAI_AUTH_TOKEN",
            "auth-token",
            "Qwen3.6-27B",
            new ArrayList<>(List.of(
                    new Model("Qwen3.6-27B", "Qwen3.6-27B", List.of("text"), 131072, 8192, true, 10),
                    new Model("DeepSeek-V4-Flash-W8A8", "DeepSeek-V4-Flash-W8A8", List.of("text"), 65536, 8192, false, 20))));

    /**
     * 返回模型来源：opencode 保持原生代理，external 直连 OpenAI-compatible /models，internal 从数据库读取。
     * 历史 bailian 明确保留为 Model Studio 兼容源。
     */
    public String getSource() {
        return source;
    }

    /**
     * 绑定模型来源，空值回退到 external；历史 bailian 配置保留旧 Model Studio 默认模型。
     */
    public void setSource(String source) {
        if (source == null || source.isBlank()) {
            this.source = "external";
            return;
        }
        this.source = source.trim().toLowerCase();
    }

    public Provider getExternal() {
        return external;
    }

    public Provider getBailian() {
        return bailian;
    }

    public Provider getInternal() {
        return internal;
    }

    /**
     * 按当前 source 返回需要同步到 opencode 的 provider 配置。
     */
    public Provider activeProvider() {
        if ("internal".equals(source)) {
            return internal;
        }
        if ("bailian".equals(source)) {
            return bailian;
        }
        return external;
    }

    /**
     * provider 级配置。
     */
    public static class Provider {
        private String providerId;
        private String name;
        private String baseUrl;
        private String apiKeyEnv;
        private String apiKey;
        private String ucidHeaderName = "ucid";
        private String authMode;
        private String defaultModel;
        private List<Model> models;

        public Provider() {
            this("", "", "", "", "bearer", "", new ArrayList<>());
        }

        public Provider(
                String providerId,
                String name,
                String baseUrl,
                String apiKeyEnv,
                String authMode,
                String defaultModel,
                List<Model> models) {
            this.providerId = providerId;
            this.name = name;
            this.baseUrl = baseUrl;
            this.apiKeyEnv = apiKeyEnv;
            this.apiKey = "";
            this.authMode = authMode;
            this.defaultModel = defaultModel;
            this.models = models == null ? new ArrayList<>() : new ArrayList<>(models);
        }

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        /**
         * 返回 yml 中直接配置的 provider API Key。本地 IDEA 启动可用它替代 shell 环境变量。
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * 绑定 yml 中直接配置的 provider API Key，空白值会按未配置处理。
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        /**
         * 企业内 provider 透传当前登录人统一认证号时使用的请求头名称。
         */
        public String getUcidHeaderName() {
            return ucidHeaderName;
        }

        /**
         * 绑定 UCID 请求头名称，空白值回退到企业 API 默认的 ucid。
         */
        public void setUcidHeaderName(String ucidHeaderName) {
            if (ucidHeaderName == null || ucidHeaderName.isBlank()) {
                this.ucidHeaderName = "ucid";
                return;
            }
            this.ucidHeaderName = ucidHeaderName.trim();
        }

        public String getAuthMode() {
            return authMode;
        }

        public void setAuthMode(String authMode) {
            this.authMode = authMode;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models == null ? new ArrayList<>() : new ArrayList<>(models);
        }
    }

    /**
     * 单个模型 seed 配置。
     */
    public static class Model {
        private String id;
        private String name;
        private List<String> input = new ArrayList<>(List.of("text"));
        private int contextLimit = 131072;
        private int outputLimit = 16384;
        private boolean defaultModel;
        private int sortOrder = 100;

        public Model() {
        }

        public Model(String id, String name, List<String> input, int contextLimit, int outputLimit, boolean defaultModel, int sortOrder) {
            this.id = id;
            this.name = name;
            this.input = input == null ? new ArrayList<>(List.of("text")) : new ArrayList<>(input);
            this.contextLimit = contextLimit;
            this.outputLimit = outputLimit;
            this.defaultModel = defaultModel;
            this.sortOrder = sortOrder;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getInput() {
            return input;
        }

        public void setInput(List<String> input) {
            this.input = input == null ? new ArrayList<>(List.of("text")) : new ArrayList<>(input);
        }

        public int getContextLimit() {
            return contextLimit;
        }

        public void setContextLimit(int contextLimit) {
            this.contextLimit = contextLimit;
        }

        public int getOutputLimit() {
            return outputLimit;
        }

        public void setOutputLimit(int outputLimit) {
            this.outputLimit = outputLimit;
        }

        public boolean isDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(boolean defaultModel) {
            this.defaultModel = defaultModel;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
