package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * UI 自动化平台地址的数据库直读服务。
 *
 * <p>地址由超级管理员通过通用参数维护，消费方每次查询都读取数据库，不进入 JVM、Redis 或
 * OpenCode 进程环境缓存，因此修改后的值会在下一次 Tool 调用时生效。</p>
 */
@Service
public class UiTestPlatformConfigurationService {

    public static final String PARAMETER_ENGLISH_NAME = "UITEST_BASE_URL";
    public static final String UNCONFIGURED = "UNCONFIGURED";

    private final CommonParameterValues commonParameterValues;

    public UiTestPlatformConfigurationService(CommonParameterValues commonParameterValues) {
        this.commonParameterValues = Objects.requireNonNull(
                commonParameterValues, "commonParameterValues must not be null");
    }

    /** 读取当前全局配置；缺失或显式 UNCONFIGURED 时返回未配置状态。 */
    public UiTestPlatformConfiguration current() {
        String rawValue = commonParameterValues
                .resolvedValue(PARAMETER_ENGLISH_NAME, ParameterPlatform.ALL)
                .map(String::trim)
                .orElse(UNCONFIGURED);
        if (rawValue.isBlank() || UNCONFIGURED.equalsIgnoreCase(rawValue)) {
            return UiTestPlatformConfiguration.unconfigured();
        }
        try {
            return UiTestPlatformConfiguration.configured(normalizeConfiguredBaseUrl(rawValue));
        } catch (PlatformException exception) {
            // 不回显数据库中的非法 URL，避免把用户信息或内部地址带入 Tool 错误与对话。
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "UI 测试执行平台地址配置无效，请联系超级管理员",
                    Map.of("englishName", PARAMETER_ENGLISH_NAME));
        }
    }

    /**
     * 校验并规范化管理端写入值；UNCONFIGURED 用于显式关闭 UI 自动化入口。
     */
    public static String normalizeManagedValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (UNCONFIGURED.equalsIgnoreCase(value)) {
            return UNCONFIGURED;
        }
        return normalizeConfiguredBaseUrl(value);
    }

    private static String normalizeConfiguredBaseUrl(String value) {
        try {
            URI uri = URI.create(value).normalize();
            String scheme = uri.getScheme() == null
                    ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean valid = ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
            if (!valid) {
                throw invalidValue();
            }
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException exception) {
            throw invalidValue();
        }
    }

    private static PlatformException invalidValue() {
        return new PlatformException(
                ErrorCode.VALIDATION_ERROR,
                "UI 测试执行平台地址必须是无用户信息、查询参数和片段的 HTTP/HTTPS 地址",
                Map.of("englishName", PARAMETER_ENGLISH_NAME));
    }

    /** Tool 只需要是否已配置及规范化地址，不暴露通用参数表的其它字段。 */
    public record UiTestPlatformConfiguration(boolean configured, String baseUrl) {

        private static UiTestPlatformConfiguration unconfigured() {
            return new UiTestPlatformConfiguration(false, null);
        }

        private static UiTestPlatformConfiguration configured(String baseUrl) {
            return new UiTestPlatformConfiguration(true, baseUrl);
        }
    }
}
