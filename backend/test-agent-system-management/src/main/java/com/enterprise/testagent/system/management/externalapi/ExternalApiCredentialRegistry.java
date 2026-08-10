package com.enterprise.testagent.system.management.externalapi;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredential;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialRepository;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** JVM 外部凭据注册表；完整构建并校验后一次性替换不可变快照。 */
@Service
public class ExternalApiCredentialRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalApiCredentialRegistry.class);
    private final ExternalApiCredentialRepository repository;
    private final ExternalApiCredentialCipher cipher;
    private volatile Map<String, RegistryEntry> snapshot;
    private volatile boolean ready;

    public ExternalApiCredentialRegistry(
            ExternalApiCredentialRepository repository,
            ExternalApiCredentialCipher cipher) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
        this.snapshot = Map.of();
    }

    /** 启动阶段严格加载；任何密文或数据异常都阻止实例进入就绪状态。 */
    public void loadOnStartup() {
        try {
            this.snapshot = buildSnapshot();
            this.ready = true;
        } catch (RuntimeException exception) {
            this.ready = false;
            throw new IllegalStateException("外部 API 凭据启动加载失败", exception);
        }
    }

    /** 运行期刷新失败时保留最后有效快照，不把半成品暴露给并发请求。 */
    public synchronized void refresh(String traceId) {
        try {
            Map<String, RegistryEntry> next = buildSnapshot();
            this.snapshot = next;
            this.ready = true;
            LOGGER.info("外部 API 凭据内存快照已刷新 traceId={} credentialCount={}", traceId, next.size());
        } catch (RuntimeException exception) {
            LOGGER.warn("外部 API 凭据内存快照刷新失败 traceId={} exceptionType={}",
                    traceId, exception.getClass().getSimpleName());
        }
    }

    /** 每 60 秒整表补偿刷新，收敛广播传输暂时失败。 */
    @Scheduled(fixedDelayString = "${test-agent.external-api.registry-refresh-ms:60000}")
    public void compensationRefresh() {
        if (ready) {
            refresh("trace_external_api_compensation");
        }
    }

    /** 按稳定工具编码 O(1) 查找，并以常量时间比较 API Key。 */
    public ExternalApiPrincipal authenticate(String toolCode, String apiKey) {
        if (!ready) {
            throw new PlatformException(ErrorCode.EXTERNAL_API_UNAVAILABLE, "外部 API 认证服务不可用");
        }
        RegistryEntry entry = toolCode == null ? null : snapshot.get(toolCode);
        if (entry == null || apiKey == null || !entry.credential().enabled()
                || !MessageDigest.isEqual(
                        entry.apiKey().getBytes(StandardCharsets.UTF_8),
                        apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "未认证");
        }
        ExternalApiCredential credential = entry.credential();
        return new ExternalApiPrincipal(
                credential.credentialId(), credential.toolCode(), credential.scopes(), entry.apiKey());
    }

    private Map<String, RegistryEntry> buildSnapshot() {
        Map<String, RegistryEntry> next = new LinkedHashMap<>();
        for (ExternalApiCredential credential : repository.findAll()) {
            String apiKey = cipher.decrypt(credential.encryptedApiKey());
            if (!apiKey.matches("taak_v1_[A-Za-z0-9_-]{43}")
                    || !ExternalApiKeyGenerator.fingerprint(apiKey).equals(credential.apiKeyFingerprint())) {
                throw new IllegalStateException("外部 API 凭据摘要或格式校验失败: " + credential.toolCode());
            }
            if (next.putIfAbsent(credential.toolCode(), new RegistryEntry(credential, apiKey)) != null) {
                throw new IllegalStateException("外部 API 工具编码重复: " + credential.toolCode());
            }
        }
        return Map.copyOf(next);
    }

    private record RegistryEntry(ExternalApiCredential credential, String apiKey) {
    }
}
