package com.icbc.testagent.opencode.runtime.model;

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

    private String source = "bailian";
    private final Provider external = new Provider(
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
            "icbc-openai",
            "ICBC OpenAI",
            "http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1",
            "ICBC_OPENAI_AUTH_TOKEN",
            "auth-token",
            "DeepSeek-V4-Flash-W8A8",
            new ArrayList<>(List.of(
                    new Model("DeepSeek-V4-Flash-W8A8", "DeepSeek-V4-Flash-W8A8", List.of("text"), 131072, 16384, true, 10),
                    new Model("Qwen3.6-27B", "Qwen3.6-27B", List.of("text"), 131072, 16384, false, 20),
                    new Model("Qwen3.6-35B-A3B", "Qwen3.6-35B-A3B", List.of("text", "image"), 131072, 16384, false, 30),
                    new Model("Qwen3.5-397B-A17B-W8A8", "Qwen3.5-397B-A17B-W8A8", List.of("text"), 131072, 16384, false, 40),
                    new Model("Qwen3-32B-128K", "Qwen3-32B-128K", List.of("text"), 131072, 16384, false, 50),
                    new Model("deepseekV3-0324-chat", "deepseekV3-0324-chat", List.of("text"), 131072, 16384, false, 60),
                    new Model("glm-51", "glm-51", List.of("text"), 131072, 16384, false, 70))));

    /**
     * 返回模型来源：opencode 保持旧代理，bailian 直连百炼 /models，internal 从数据库读取。
     */
    public String getSource() {
        return source;
    }

    /**
     * 绑定模型来源，空值回退到 bailian。
     */
    public void setSource(String source) {
        this.source = source == null || source.isBlank() ? "bailian" : source.trim().toLowerCase();
    }

    public Provider getExternal() {
        return external;
    }

    public Provider getInternal() {
        return internal;
    }

    /**
     * 按当前 source 返回需要同步到 opencode 的 provider 配置。
     */
    public Provider activeProvider() {
        return "internal".equals(source) ? internal : external;
    }

    /**
     * provider 级配置。
     */
    public static class Provider {
        private String providerId;
        private String name;
        private String baseUrl;
        private String apiKeyEnv;
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
