package com.enterprise.testagent.opencode.runtime.internalmodel;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内部模型代理运行参数，敏感 apiKey 只从 Java 进程环境/配置注入。
 */
@Component
public class InternalModelProxyRuntimeSettings {

    public static final String API_KEY_ENV_NAME = "TEST_AGENT_INTERNAL_PROXY_API_KEY";
    public static final String BASE_URL_ENV_NAME = "TEST_AGENT_INTERNAL_PROXY_BASE_URL";
    public static final String UCID_ENV_NAME = "ENTERPRISE_UCID";
    public static final String PROXY_PATH = "/api/internal/platform/opencode-runtime/internal-model-proxy/v1";

    private final BackendJavaProcessLifecycleService backendLifecycle;
    private final String apiKey;

    public InternalModelProxyRuntimeSettings(
            BackendJavaProcessLifecycleService backendLifecycle,
            @Value("${test-agent.internal-model-proxy.api-key:${TEST_AGENT_INTERNAL_PROXY_API_KEY:}}") String apiKey) {
        this.backendLifecycle = backendLifecycle;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String requireApiKey() {
        if (apiKey.isBlank()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "内部模型代理 apiKey 未配置");
        }
        return apiKey;
    }

    public boolean matchesApiKey(String bearerToken) {
        return !apiKey.isBlank() && apiKey.equals(bearerToken);
    }

    public String sameNodeProxyBaseUrl() {
        String listenUrl = backendLifecycle.listenUrl();
        if (listenUrl.endsWith("/")) {
            listenUrl = listenUrl.substring(0, listenUrl.length() - 1);
        }
        return listenUrl + PROXY_PATH;
    }
}
