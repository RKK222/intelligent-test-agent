package com.enterprise.testagent.integration.uitest;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** uitest6 集成参数；外部 Bearer Token 始终只驻留 Java 进程。 */
@Component
public class UiTestExecutionSettings {

    private final String baseUrl;
    private final String token;
    private final Duration requestTimeout;
    private final int maxSteps;

    public UiTestExecutionSettings(
            @Value("${test-agent.ui-test.base-url:${UITEST6_BASE_URL:}}") String baseUrl,
            @Value("${test-agent.ui-test.token:${UITEST6_INTEGRATION_TOKEN:}}") String token,
            @Value("${test-agent.ui-test.request-timeout-seconds:${UITEST6_REQUEST_TIMEOUT_SECONDS:15}}")
                    long requestTimeoutSeconds,
            @Value("${test-agent.ui-test.max-steps:${UITEST6_MAX_STEPS:25}}") int maxSteps) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.token = token == null ? "" : token.trim();
        this.requestTimeout = Duration.ofSeconds(Math.max(1, Math.min(requestTimeoutSeconds, 120)));
        this.maxSteps = Math.max(1, Math.min(maxSteps, 100));
    }

    /** 返回已校验的独立平台地址，配置缺失或协议不安全时失败关闭。 */
    public URI requireBaseUri() {
        if (baseUrl.isBlank()) {
            throw unavailable("UI 自动化平台地址未配置");
        }
        try {
            URI value = URI.create(baseUrl);
            if (!("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme()))
                    || value.getHost() == null
                    || value.getUserInfo() != null) {
                throw unavailable("UI 自动化平台地址配置无效");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw unavailable("UI 自动化平台地址配置无效");
        }
    }

    /** 外部 Token 不向 Tool 或响应暴露。 */
    public String requireToken() {
        if (token.isBlank()) {
            throw unavailable("UI 自动化平台 Token 未配置");
        }
        return token;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public int maxSteps() {
        return maxSteps;
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static PlatformException unavailable(String message) {
        return new PlatformException(ErrorCode.UI_TEST_UNAVAILABLE, message);
    }
}
