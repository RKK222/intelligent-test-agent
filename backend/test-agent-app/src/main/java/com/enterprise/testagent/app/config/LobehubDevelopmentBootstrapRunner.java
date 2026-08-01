package com.enterprise.testagent.app.config;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.CommonParameterManagementApplicationService;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.integration.lobehub.LobehubDevelopmentOwnerResolver;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 在显式本地 LobeHub 联调模式下初始化平台公共参数。
 *
 * <p>该 Runner 只在 test/local profile 且显式开关开启时装配，并拒绝非回环 PostgreSQL，
 * 避免开发启动命令修改共享或企业数据库。启用开关始终先关闭、最后写入；
 * 任一步失败都会尽力补偿为关闭状态。
 */
@Component
@Profile({"test", "local"})
@ConditionalOnProperty(prefix = "test-agent.lobehub", name = "dev-bootstrap-enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LobehubDevelopmentBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobehubDevelopmentBootstrapRunner.class);
    private static final String PARAM_ENABLED = "LOBEHUB_ENABLED";
    private static final String PARAM_BASE_URL = "LOBEHUB_BASE_URL";
    private static final String PARAM_EMAIL_DOMAIN = "LOBEHUB_SSO_EMAIL_DOMAIN";
    private static final String PARAM_OWNER = "LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID";
    private static final String ACTOR = "lobehub-dev-bootstrap";

    private final CommonParameterValues commonParameterValues;
    private final CommonParameterManagementApplicationService managementService;
    private final LobehubDevelopmentOwnerResolver ownerResolver;
    private final String datasourceUrl;
    private final String requestedOwner;
    private final String baseUrl;
    private final String emailDomain;
    private final boolean targetEnabled;

    public LobehubDevelopmentBootstrapRunner(
            CommonParameterValues commonParameterValues,
            CommonParameterManagementApplicationService managementService,
            LobehubDevelopmentOwnerResolver ownerResolver,
            @Value("${spring.datasource.druid.url:}") String datasourceUrl,
            @Value("${test-agent.lobehub.dev-owner-unified-auth-id:}") String requestedOwner,
            @Value("${test-agent.lobehub.dev-base-url:http://127.0.0.1:3210}") String baseUrl,
            @Value("${test-agent.lobehub.dev-email-domain:lobehub.local}") String emailDomain,
            @Value("${test-agent.lobehub.dev-target-enabled:true}") boolean targetEnabled) {
        this.commonParameterValues = Objects.requireNonNull(
                commonParameterValues, "commonParameterValues must not be null");
        this.managementService = Objects.requireNonNull(managementService, "managementService must not be null");
        this.ownerResolver = Objects.requireNonNull(ownerResolver, "ownerResolver must not be null");
        this.datasourceUrl = Objects.requireNonNull(datasourceUrl, "datasourceUrl must not be null");
        this.requestedOwner = requestedOwner == null ? "" : requestedOwner.trim();
        this.baseUrl = requireLoopbackOrigin(baseUrl);
        this.emailDomain = requireEmailDomain(emailDomain);
        this.targetEnabled = targetEnabled;
    }

    /** 通过现有通用参数管理服务写入值和审计记录，未完成配置时不留下已启用状态。 */
    @Override
    public void run(ApplicationArguments args) {
        requireLoopbackDatasource(datasourceUrl);
        Map<String, CommonParameter> parameters = requiredParameters();
        String traceId = "trace_lobehub_dev_bootstrap_" + UUID.randomUUID().toString().replace("-", "");
        CommonParameter enabledParameter = parameters.get(PARAM_ENABLED);
        try {
            if (Boolean.parseBoolean(enabledParameter.parameterValue())) {
                update(enabledParameter, "false", traceId);
            }
            if (!targetEnabled) {
                LOGGER.warn("LobeHub 本地开发公共参数已补偿为关闭状态 traceId={}", traceId);
                return;
            }

            String owner = ownerResolver.resolve(requestedOwner, parameters.get(PARAM_OWNER).parameterValue());
            Map<String, String> desired = new LinkedHashMap<>();
            desired.put(PARAM_BASE_URL, baseUrl);
            desired.put(PARAM_EMAIL_DOMAIN, emailDomain);
            desired.put(PARAM_OWNER, owner);
            desired.forEach((name, value) -> {
                CommonParameter current = parameters.get(name);
                if (!value.equals(current.parameterValue())) {
                    update(current, value, traceId);
                }
            });
            update(enabledParameter, "true", traceId);
            LOGGER.info("LobeHub 本地开发公共参数已初始化 traceId={} baseUrl={}", traceId, baseUrl);
        } catch (RuntimeException exception) {
            compensateDisabled(enabledParameter, traceId, exception);
            throw exception;
        }
    }

    private Map<String, CommonParameter> requiredParameters() {
        Map<String, CommonParameter> result = new LinkedHashMap<>();
        for (String name : new String[]{PARAM_ENABLED, PARAM_BASE_URL, PARAM_EMAIL_DOMAIN, PARAM_OWNER}) {
            CommonParameter parameter = commonParameterValues.raw(name, ParameterPlatform.ALL)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.INTERNAL_ERROR,
                            "LobeHub 本地开发公共参数缺失: " + name));
            result.put(name, parameter);
        }
        return result;
    }

    private void update(CommonParameter parameter, String value, String traceId) {
        managementService.updateValue(parameter.parameterId(), value, traceId, null, ACTOR);
    }

    /** 即使启用写库后审计失败，也再次通过审计服务把入口恢复为关闭。 */
    private void compensateDisabled(CommonParameter enabledParameter, String traceId, RuntimeException original) {
        try {
            if (Boolean.parseBoolean(currentValue(PARAM_ENABLED))) {
                update(enabledParameter, "false", traceId);
            }
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
        }
    }

    private String currentValue(String name) {
        return commonParameterValues.raw(name, ParameterPlatform.ALL)
                .map(CommonParameter::parameterValue)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "LobeHub 本地开发公共参数缺失: " + name));
    }

    private static void requireLoopbackDatasource(String jdbcUrl) {
        String normalized = jdbcUrl == null ? "" : jdbcUrl.trim();
        if (!normalized.startsWith("jdbc:")) {
            throw new IllegalStateException("LobeHub 本地开发初始化要求有效的回环地址 PostgreSQL URL");
        }
        try {
            URI uri = new URI(normalized.substring("jdbc:".length()));
            if (!isLoopbackHost(uri.getHost())) {
                throw new IllegalStateException("LobeHub 本地开发初始化只允许回环地址 PostgreSQL");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "LobeHub 本地开发初始化要求有效的回环地址 PostgreSQL URL", exception);
        }
    }

    private static String requireLoopbackOrigin(String rawBaseUrl) {
        String normalized = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        try {
            URI uri = new URI(normalized);
            String path = uri.getRawPath();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                    || !isLoopbackHost(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || (path != null && !path.isEmpty() && !"/".equals(path))) {
                throw new IllegalStateException("LobeHub 本地开发基址必须是固定 HTTP 回环 origin");
            }
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("LobeHub 本地开发基址必须是固定 HTTP 回环 origin", exception);
        }
    }

    private static String requireEmailDomain(String rawDomain) {
        String normalized = rawDomain == null ? "" : rawDomain.trim().toLowerCase(java.util.Locale.ROOT);
        if ("disabled.invalid".equals(normalized)
                || !normalized.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) {
            throw new IllegalStateException("LobeHub 本地开发虚拟邮箱域无效");
        }
        return normalized;
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if ("localhost".equals(normalized) || "::1".equals(normalized)) {
            return true;
        }
        String[] octets = normalized.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) {
            return false;
        }
        for (int index = 1; index < octets.length; index++) {
            try {
                int value = Integer.parseInt(octets[index]);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }
}
