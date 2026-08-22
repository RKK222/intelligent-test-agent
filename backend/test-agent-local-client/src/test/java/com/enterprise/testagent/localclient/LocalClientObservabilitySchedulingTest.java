package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalClientObservabilitySchedulingTest {

    @Test
    void traceUploadWaitsForIdleAndYieldsToEveryForegroundLane() {
        long now = Duration.ofSeconds(20).toNanos();
        long idleActivity = now - LocalClientConnection.OBSERVABILITY_IDLE_NANOS;

        assertThat(allowed(now, idleActivity, false, false, false, 0)).isTrue();
        assertThat(allowed(now, idleActivity, true, false, false, 0)).isFalse();
        assertThat(allowed(now, idleActivity, false, true, false, 0)).isFalse();
        assertThat(allowed(now, idleActivity, false, false, true, 0)).isFalse();
        assertThat(allowed(now, now - Duration.ofMillis(2999).toNanos(), false, false, false, 0)).isFalse();
        assertThat(allowed(now, idleActivity, false, false, false, now + 1)).isFalse();
        assertThat(LocalClientConnection.OBSERVABILITY_BYTES_PER_SECOND).isEqualTo(1024L * 1024L);
        assertThat(LocalObservabilityRelay.MAX_UPLOAD_CHUNK_BYTES).isEqualTo(256 * 1024);
    }

    private boolean allowed(
            long now,
            long lastActivity,
            boolean traceInFlight,
            boolean foregroundOperation,
            boolean modelActive,
            long retryNotBefore) {
        return LocalClientConnection.shouldUploadObservability(
                7,
                true,
                traceInFlight,
                foregroundOperation,
                modelActive,
                now,
                retryNotBefore,
                lastActivity);
    }
}
