package com.enterprise.testagent.domain.run;

import java.util.List;
import java.util.Objects;

/** 活跃 SSE 从 Redis runtime Stream 读取的有序尾部；容量截断时携带新的物化快照。 */
public record RunRuntimeTail(
        RunRuntimeManifest manifest,
        RunRuntimeSnapshot snapshot,
        List<RunRuntimeStreamEvent> events,
        boolean resetRequired,
        String resetReason) {

    public RunRuntimeTail {
        Objects.requireNonNull(manifest, "manifest must not be null");
        snapshot = snapshot == null ? RunRuntimeSnapshot.empty(manifest.runId()) : snapshot;
        events = events == null ? List.of() : List.copyOf(events);
        if (resetRequired && (resetReason == null || resetReason.isBlank())) {
            throw new IllegalArgumentException("resetReason must be present when reset is required");
        }
    }
}
