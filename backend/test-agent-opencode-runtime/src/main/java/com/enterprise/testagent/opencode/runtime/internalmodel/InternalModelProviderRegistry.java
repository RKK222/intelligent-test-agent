package com.enterprise.testagent.opencode.runtime.internalmodel;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProvidersReloadedEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        repository.findEnabled().forEach(provider -> providers.put(provider.providerId(), provider));
        String authToken = repository.findAuthToken().orElse(null);
        snapshot = new Snapshot(providers, authToken, Instant.now(), traceId);
        return currentSnapshot();
    }

    public InternalModelProviderSnapshot currentSnapshot() {
        Snapshot current = snapshot;
        return new InternalModelProviderSnapshot(
                current.providers().values().stream().toList(),
                current.authToken() != null && !current.authToken().isBlank(),
                current.loadedAt(),
                current.traceId());
    }

    public InternalModelProvider requireProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "缺少内部模型供应商标识");
        }
        InternalModelProvider provider = snapshot.providers().get(providerId);
        if (provider == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商未启用或不存在");
        }
        return provider;
    }

    public String requireAuthToken() {
        return Optional.ofNullable(snapshot.authToken())
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型接口 token 未配置"));
    }

    private record Snapshot(
            Map<String, InternalModelProvider> providers,
            String authToken,
            Instant loadedAt,
            String traceId) {

        private Snapshot {
            providers = providers == null ? Map.of() : Map.copyOf(providers);
        }

        private static Snapshot empty() {
            return new Snapshot(Map.of(), null, Instant.EPOCH, null);
        }
    }
}
