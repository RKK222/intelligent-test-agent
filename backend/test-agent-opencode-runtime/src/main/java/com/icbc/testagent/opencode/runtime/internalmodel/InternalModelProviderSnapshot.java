package com.icbc.testagent.opencode.runtime.internalmodel;

import com.icbc.testagent.domain.configuration.InternalModelProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 当前 Java 进程内存中的内部模型供应商快照。
 */
public record InternalModelProviderSnapshot(
        List<InternalModelProvider> providers,
        boolean tokenConfigured,
        Instant loadedAt,
        String traceId) {

    public InternalModelProviderSnapshot {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public Map<String, Object> toResponse() {
        return Map.of(
                "providers", providers,
                "tokenConfigured", tokenConfigured,
                "loadedAt", loadedAt,
                "traceId", traceId == null ? "" : traceId);
    }
}
