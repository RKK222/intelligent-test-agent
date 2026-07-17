package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.event.RunEvent;
import java.util.List;
import java.util.Objects;

/** Redis snapshot barrier 与 durable Stream 的一次一致回放结果。 */
public record RunRuntimeReplay(
        RunRuntimeManifest manifest,
        RunRuntimeSnapshot snapshot,
        List<RunEvent> durableEvents,
        boolean cursorResetRequired,
        String resetReason) {

    public RunRuntimeReplay {
        Objects.requireNonNull(manifest, "manifest must not be null");
        snapshot = snapshot == null ? RunRuntimeSnapshot.empty(manifest.runId()) : snapshot;
        durableEvents = durableEvents == null ? List.of() : List.copyOf(durableEvents);
        if (cursorResetRequired && (resetReason == null || resetReason.isBlank())) {
            throw new IllegalArgumentException("resetReason must be present when cursor reset is required");
        }
    }
}
