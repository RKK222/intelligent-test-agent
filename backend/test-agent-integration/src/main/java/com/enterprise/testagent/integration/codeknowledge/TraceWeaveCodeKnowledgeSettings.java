package com.enterprise.testagent.integration.codeknowledge;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 每次请求从通用参数读取 TraceWeave 连接、试点用户和版本库映射，不在 JVM 中缓存。 */
@Component
public final class TraceWeaveCodeKnowledgeSettings {

    public static final String PARAM_BASE_URL = "TRACEWEAVE_BASE_URL";
    public static final String PARAM_WEB_BASE_URL = "TRACEWEAVE_WEB_BASE_URL";
    public static final String PARAM_SCOPE = "TRACEWEAVE_CODE_KNOWLEDGE_SCOPE";
    private static final int MAX_SCOPE_BYTES = 64 * 1024;

    private final CommonParameterValues values;
    private final ObjectMapper mapper;

    public TraceWeaveCodeKnowledgeSettings(CommonParameterValues values, ObjectMapper mapper) {
        this.values = Objects.requireNonNull(values);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /** 加载并验证当前用户的完整只读配置；停用、未入试点或配置不完整均失败关闭。 */
    public Snapshot require(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        ScopeConfig config = parseScope(values.resolvedValue(PARAM_SCOPE).orElse(""));
        if (!config.enabled()) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN, "代码知识查询未启用", Map.of("reason", "CODE_KNOWLEDGE_DISABLED"));
        }
        if (config.pilotUserIds() == null || !config.pilotUserIds().contains(userId.value())) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN, "当前用户不在代码知识试点范围", Map.of("reason", "NOT_IN_PILOT"));
        }
        String view = normalizeView(config.defaultView());
        List<RepositoryMapping> repositories = normalizeMappings(config.repositories());
        return new Snapshot(
                validateBaseUrl(values.resolvedValue(PARAM_BASE_URL).orElse(""), PARAM_BASE_URL),
                validateBaseUrl(values.resolvedValue(PARAM_WEB_BASE_URL).orElse(""), PARAM_WEB_BASE_URL),
                view,
                repositories);
    }

    /**
     * 返回工作台可以展示的最小范围信息。停用和未入试点是正常的不可用状态；
     * 其它配置错误继续失败，避免界面展示一个实际无法查询的范围。
     */
    public SelectionScope selectionScope(UserId userId) {
        try {
            Snapshot snapshot = require(userId);
            return new SelectionScope(
                    true,
                    null,
                    snapshot.defaultView(),
                    snapshot.repositories().stream().map(RepositoryMapping::codeRepositoryId).toList());
        } catch (PlatformException exception) {
            Object reason = exception.details().get("reason");
            if (exception.errorCode() == ErrorCode.FORBIDDEN
                    && ("CODE_KNOWLEDGE_DISABLED".equals(reason) || "NOT_IN_PILOT".equals(reason))) {
                return new SelectionScope(false, reason.toString(), null, List.of());
            }
            throw exception;
        }
    }

    private ScopeConfig parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalidConfig("SCOPE_MISSING");
        }
        if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SCOPE_BYTES) {
            throw invalidConfig("SCOPE_TOO_LARGE");
        }
        try {
            return mapper.readValue(raw, ScopeConfig.class);
        } catch (Exception exception) {
            throw invalidConfig("SCOPE_INVALID_JSON");
        }
    }

    private List<RepositoryMapping> normalizeMappings(List<RepositoryMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            throw invalidConfig("REPOSITORY_MAPPING_MISSING");
        }
        Map<String, RepositoryMapping> unique = new LinkedHashMap<>();
        Set<String> traceKeys = new HashSet<>();
        for (RepositoryMapping candidate : mappings) {
            if (candidate == null) {
                throw invalidConfig("REPOSITORY_MAPPING_INVALID");
            }
            RepositoryMapping mapping = new RepositoryMapping(
                    required(candidate.codeRepositoryId()),
                    required(candidate.traceweaveApplicationGroupId()),
                    required(candidate.traceweaveRepositoryId()),
                    normalizeIds(candidate.dependencyRepositoryIds()));
            if (unique.putIfAbsent(mapping.codeRepositoryId(), mapping) != null
                    || !traceKeys.add(mapping.traceweaveApplicationGroupId() + "\n" + mapping.traceweaveRepositoryId())) {
                throw invalidConfig("REPOSITORY_MAPPING_DUPLICATE");
            }
        }
        for (RepositoryMapping mapping : unique.values()) {
            if (mapping.dependencyRepositoryIds().stream().anyMatch(id -> !unique.containsKey(id))) {
                throw invalidConfig("DEPENDENCY_MAPPING_MISSING");
            }
        }
        return List.copyOf(unique.values());
    }

    private List<String> normalizeIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            String normalized = required(value);
            if (!unique.add(normalized)) {
                throw invalidConfig("DEPENDENCY_MAPPING_DUPLICATE");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private String normalizeView(String value) {
        String view = value == null || value.isBlank() ? "DEV" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("DEV", "PROD").contains(view)) {
            throw invalidConfig("DEFAULT_VIEW_INVALID");
        }
        return view;
    }

    private String validateBaseUrl(String raw, String parameter) {
        try {
            URI uri = URI.create(required(raw));
            if (!uri.isAbsolute()
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw invalidConfig(parameter + "_INVALID");
            }
            String value = uri.toString();
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidConfig(parameter + "_INVALID");
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 256) {
            throw invalidConfig("CONFIG_VALUE_INVALID");
        }
        return value.trim();
    }

    private PlatformException invalidConfig(String reason) {
        return new PlatformException(
                ErrorCode.CONFLICT, "代码知识配置不可用", Map.of("reason", reason));
    }

    private record ScopeConfig(
            boolean enabled,
            List<String> pilotUserIds,
            String defaultView,
            List<RepositoryMapping> repositories) {
    }

    public record RepositoryMapping(
            String codeRepositoryId,
            String traceweaveApplicationGroupId,
            String traceweaveRepositoryId,
            List<String> dependencyRepositoryIds) {
        public RepositoryMapping {
            dependencyRepositoryIds = dependencyRepositoryIds == null
                    ? List.of() : List.copyOf(dependencyRepositoryIds);
        }
    }

    public record Snapshot(
            String serviceBaseUrl,
            String webBaseUrl,
            String defaultView,
            List<RepositoryMapping> repositories) {
        public Snapshot {
            repositories = List.copyOf(repositories);
        }
    }

    /** 浏览器只获得 Mimo 逻辑版本库 ID，不暴露 TraceWeave 地址和内部映射。 */
    public record SelectionScope(
            boolean available,
            String reason,
            String defaultView,
            List<String> repositoryIds) {
        public SelectionScope {
            repositoryIds = repositoryIds == null ? List.of() : List.copyOf(repositoryIds);
        }
    }

}
