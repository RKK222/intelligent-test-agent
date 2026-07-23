package com.enterprise.testagent.opencode.runtime.internalmodel;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.InternalModelProvidersReloadedEvent;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Java 进程内的内部模型供应商缓存，所有代理请求只读取这里的快照。
 */
@Service
public class InternalModelProviderRegistry {

    private final InternalModelProviderRepository repository;
    private volatile Snapshot snapshot = Snapshot.empty();

    public InternalModelProviderRegistry(InternalModelProviderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refresh("trace_startup", "startup");
    }

    @EventListener(InternalModelProvidersReloadedEvent.class)
    public void reload(InternalModelProvidersReloadedEvent event) {
        refresh(event.traceId(), event.reason());
    }

    public synchronized InternalModelProviderSnapshot refresh(String traceId, String reason) {
        Map<String, InternalModelProvider> providers = new LinkedHashMap<>();
        Map<String, String> authTokensByProviderId = new LinkedHashMap<>();
        repository.findEnabledRuntimeConfigs().forEach(runtimeConfig -> {
            InternalModelProvider provider = runtimeConfig.provider();
            providers.put(provider.providerId(), provider);
            if (runtimeConfig.authToken() != null && !runtimeConfig.authToken().isBlank()) {
                authTokensByProviderId.put(provider.providerId(), runtimeConfig.authToken());
            }
        });
        snapshot = new Snapshot(providers, authTokensByProviderId, Instant.now(), traceId);
        return currentSnapshot();
    }

    public InternalModelProviderSnapshot currentSnapshot() {
        Snapshot current = snapshot;
        return new InternalModelProviderSnapshot(
                current.providers().values().stream().toList(),
                current.providers().keySet().stream().allMatch(current.authTokensByProviderId()::containsKey),
                current.loadedAt(),
                current.traceId());
    }

    public InternalModelProvider requireProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "缺少内部模型供应商标识");
        }
        InternalModelProvider provider = snapshot.providers().get(providerId.trim());
        if (provider == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商未启用或不存在");
        }
        return provider;
    }

    /**
     * 从同一代不可变快照中同时解析供应商和 Token，避免刷新瞬间发生地址与密钥串代。
     */
    public InternalModelProviderRuntimeConfig requireRuntimeConfig(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "缺少内部模型供应商标识");
        }
        String normalizedProviderId = providerId.trim();
        Snapshot current = snapshot;
        InternalModelProvider provider = current.providers().get(normalizedProviderId);
        if (provider == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商未启用或不存在");
        }
        String authToken = current.authTokensByProviderId().get(normalizedProviderId);
        if (authToken == null || authToken.isBlank()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "内部模型供应商 " + normalizedProviderId + " 的 Token 未配置");
        }
        return new InternalModelProviderRuntimeConfig(provider, authToken);
    }

    private record Snapshot(
            Map<String, InternalModelProvider> providers,
            Map<String, String> authTokensByProviderId,
            Instant loadedAt,
            String traceId) {

        private Snapshot {
            providers = immutableLinkedMap(providers);
            authTokensByProviderId = immutableLinkedMap(authTokensByProviderId);
        }

        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Instant.EPOCH, null);
        }

        private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
            return source == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
}
