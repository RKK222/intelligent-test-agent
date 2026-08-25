package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.enterprise.testagent.opencode.runtime.model.ModelCatalogApplicationService;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 从企业公共 OpenCode 配置生成只包含 loopback 代理信息的本地客户端受管模型配置。 */
@Service
public class LocalClientManagedModelConfigService {

    private static final String PUBLIC_CONFIG_PARAMETER = "OPENCODE_PUBLIC_CONFIG_DIR";
    private static final String CONFIG_FILE = "opencode.jsonc";
    private static final String PROVIDER_HEADER = "X-Enterprise-Model-Provider";
    private static final String PROXY_BASE_URL = "{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}";
    private static final String PROXY_API_KEY = "{env:TEST_AGENT_INTERNAL_PROXY_API_KEY}";
    private static final long MAX_CONFIG_BYTES = 1024L * 1024L;
    private final ModelCatalogApplicationService modelCatalogService;
    private final CommonParameterValues commonParameterValues;
    private final InternalModelProviderRegistry providerRegistry;
    private final ObjectMapper jsoncMapper;

    public LocalClientManagedModelConfigService(
            ModelCatalogApplicationService modelCatalogService,
            CommonParameterValues commonParameterValues,
            InternalModelProviderRegistry providerRegistry) {
        this.modelCatalogService = Objects.requireNonNull(modelCatalogService);
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues);
        this.providerRegistry = Objects.requireNonNull(providerRegistry);
        JsonFactory factory = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();
        this.jsoncMapper = new ObjectMapper(factory);
    }

    /** 非企业来源保持原有目录；企业来源以当前公共 opencode.jsonc 的 Provider 映射为事实源。 */
    public Map<String, Object> managedProviderConfig() {
        if (!modelCatalogService.managedSourceEnabled()) {
            return null;
        }
        if (!modelCatalogService.internalSourceEnabled()) {
            return modelCatalogService.localClientProviderConfig();
        }
        JsonNode source = readPublicConfig();
        if (source == null || !source.isObject()) {
            throw unavailable("企业公共 opencode.jsonc 根节点无效");
        }
        ObjectNode sanitized = jsoncMapper.createObjectNode();
        ArrayNode enabled = sanitized.putArray("enabled_providers");
        ObjectNode providers = sanitized.putObject("provider");
        Set<String> acceptedProviderIds = new LinkedHashSet<>();

        JsonNode sourceProviders = source.path("provider");
        JsonNode sourceEnabled = source.path("enabled_providers");
        if (!sourceProviders.isObject() || !sourceEnabled.isArray()) {
            throw unavailable("企业公共 opencode.jsonc 缺少 provider 或 enabled_providers");
        }
        for (JsonNode providerIdNode : sourceEnabled) {
            String providerId = boundedText(providerIdNode, 128);
            if (providerId == null || !acceptedProviderIds.add(providerId)) {
                continue;
            }
            JsonNode provider = sourceProviders.path(providerId);
            String routeProviderId = boundedText(
                    provider.path("options").path("headers").path(PROVIDER_HEADER), 128);
            if (!provider.isObject()
                    || routeProviderId == null
                    || !provider.path("models").isObject()
                    || !runtimeProviderAvailable(routeProviderId)) {
                acceptedProviderIds.remove(providerId);
                continue;
            }
            ObjectNode sanitizedProvider = sanitizeProvider(provider, routeProviderId);
            if (sanitizedProvider.path("models").isEmpty()) {
                acceptedProviderIds.remove(providerId);
                continue;
            }
            enabled.add(providerId);
            providers.set(providerId, sanitizedProvider);
        }
        if (acceptedProviderIds.isEmpty()) {
            throw unavailable("企业公共 opencode.jsonc 没有可用且已启用的内部模型供应商");
        }

        String defaultModel = validModelReference(source.path("model"), acceptedProviderIds, providers);
        if (defaultModel == null) {
            defaultModel = firstModelReference(acceptedProviderIds, providers);
        }
        String smallModel = validModelReference(source.path("small_model"), acceptedProviderIds, providers);
        sanitized.put("model", defaultModel);
        sanitized.put("small_model", smallModel == null ? defaultModel : smallModel);
        return jsoncMapper.convertValue(sanitized, new TypeReference<>() { });
    }

    private JsonNode readPublicConfig() {
        String configuredPath = commonParameterValues.resolvedValue(PUBLIC_CONFIG_PARAMETER)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> unavailable("通用参数未配置：" + PUBLIC_CONFIG_PARAMETER));
        Path directory;
        try {
            directory = Path.of(configuredPath).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            throw unavailable("企业公共 OpenCode 配置目录无效");
        }
        Path configFile = directory.resolve(CONFIG_FILE).normalize();
        try {
            if (!configFile.startsWith(directory)
                    || Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(configFile)
                    || !Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
                throw unavailable("企业公共 opencode.jsonc 不可用");
            }
            byte[] content;
            try (InputStream input = Files.newInputStream(configFile)) {
                content = input.readNBytes((int) MAX_CONFIG_BYTES + 1);
            }
            if (content.length <= 0 || content.length > MAX_CONFIG_BYTES) {
                throw unavailable("企业公共 opencode.jsonc 大小无效");
            }
            return jsoncMapper.readTree(content);
        } catch (PlatformException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw unavailable("企业公共 opencode.jsonc 读取失败");
        }
    }

    private ObjectNode sanitizeProvider(JsonNode source, String routeProviderId) {
        ObjectNode provider = jsoncMapper.createObjectNode();
        copyText(source, provider, "name");
        copyText(source, provider, "npm");
        provider.put("api", PROXY_BASE_URL);
        ArrayNode environment = provider.putArray("env");
        environment.add("TEST_AGENT_INTERNAL_PROXY_API_KEY");
        environment.add("TEST_AGENT_INTERNAL_PROXY_BASE_URL");
        ObjectNode options = provider.putObject("options");
        options.put("baseURL", PROXY_BASE_URL);
        options.put("apiKey", PROXY_API_KEY);
        JsonNode includeUsage = source.path("options").path("includeUsage");
        if (includeUsage.isBoolean()) {
            options.put("includeUsage", includeUsage.booleanValue());
        }
        JsonNode timeout = source.path("options").path("timeout");
        if (timeout.isBoolean()) {
            options.put("timeout", timeout.booleanValue());
        } else if (timeout.canConvertToLong() && timeout.longValue() > 0) {
            options.put("timeout", timeout.longValue());
        }
        copyPositiveInteger(source.path("options"), options, "headerTimeout");
        copyPositiveInteger(source.path("options"), options, "chunkTimeout");
        options.putObject("headers").put(PROVIDER_HEADER, routeProviderId);
        ObjectNode models = provider.putObject("models");
        source.path("models").properties().forEach(entry -> {
            String modelId = boundedText(entry.getKey(), 256);
            if (modelId != null && entry.getValue().isObject()) {
                models.set(modelId, sanitizeModel(modelId, entry.getValue()));
            }
        });
        return provider;
    }

    private ObjectNode sanitizeModel(String modelId, JsonNode source) {
        ObjectNode model = jsoncMapper.createObjectNode();
        String configuredId = boundedText(source.path("id"), 256);
        String configuredName = boundedText(source.path("name"), 512);
        model.put("id", configuredId == null ? modelId : configuredId);
        model.put("name", configuredName == null ? modelId : configuredName);
        copyText(source, model, "release_date");
        for (String field : List.of("attachment", "reasoning", "temperature", "tool_call")) {
            JsonNode value = source.path(field);
            if (value.isBoolean()) {
                model.put(field, value.booleanValue());
            }
        }
        String interleavedField = boundedText(source.path("interleaved").path("field"), 128);
        if (interleavedField != null) {
            model.putObject("interleaved").put("field", interleavedField);
        }
        ObjectNode limit = jsoncMapper.createObjectNode();
        copyPositiveInteger(source.path("limit"), limit, "context");
        copyPositiveInteger(source.path("limit"), limit, "output");
        if (!limit.isEmpty()) {
            model.set("limit", limit);
        }
        ObjectNode modalities = jsoncMapper.createObjectNode();
        copyTextArray(source.path("modalities"), modalities, "input");
        copyTextArray(source.path("modalities"), modalities, "output");
        if (!modalities.isEmpty()) {
            model.set("modalities", modalities);
        }
        return model;
    }

    private boolean runtimeProviderAvailable(String providerId) {
        try {
            providerRegistry.requireRuntimeConfig(providerId);
            return true;
        } catch (PlatformException exception) {
            return false;
        }
    }

    private String validModelReference(
            JsonNode value,
            Set<String> acceptedProviderIds,
            ObjectNode providers) {
        String reference = boundedText(value, 512);
        if (reference == null) {
            return null;
        }
        int separator = reference.indexOf('/');
        if (separator <= 0 || separator == reference.length() - 1) {
            return null;
        }
        String providerId = reference.substring(0, separator);
        String modelId = reference.substring(separator + 1);
        return acceptedProviderIds.contains(providerId)
                && providers.path(providerId).path("models").has(modelId)
                        ? reference
                        : null;
    }

    private String firstModelReference(Set<String> acceptedProviderIds, ObjectNode providers) {
        String providerId = acceptedProviderIds.iterator().next();
        List<String> modelIds = new ArrayList<>();
        providers.path(providerId).path("models").fieldNames().forEachRemaining(modelIds::add);
        if (modelIds.isEmpty()) {
            throw unavailable("企业公共 opencode.jsonc 的可用供应商没有模型");
        }
        return providerId + "/" + modelIds.getFirst();
    }

    private static void copyText(JsonNode source, ObjectNode target, String field) {
        String value = boundedText(source.path(field), 512);
        if (value != null) {
            target.put(field, value);
        }
    }

    private static void copyPositiveInteger(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.path(field);
        if (value.canConvertToLong() && value.longValue() > 0) {
            target.put(field, value.longValue());
        }
    }

    private static void copyTextArray(JsonNode source, ObjectNode target, String field) {
        JsonNode values = source.path(field);
        if (!values.isArray()) {
            return;
        }
        ArrayNode sanitized = target.putArray(field);
        for (JsonNode value : values) {
            String text = boundedText(value, 64);
            if (text != null) {
                sanitized.add(text);
            }
        }
        if (sanitized.isEmpty()) {
            target.remove(field);
        }
    }

    private static String boundedText(JsonNode value, int maxLength) {
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() || text.length() > maxLength ? null : text;
    }

    private static String boundedText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() || text.length() > maxLength ? null : text;
    }

    private static PlatformException unavailable(String message) {
        return new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, message);
    }
}
