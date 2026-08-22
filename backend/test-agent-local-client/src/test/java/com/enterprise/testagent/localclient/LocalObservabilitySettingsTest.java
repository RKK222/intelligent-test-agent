package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalObservabilitySettingsTest {

    @Test
    void defaultsMatchTheLocalPerformanceBudget() {
        LocalObservabilitySettings settings = LocalObservabilitySettings.defaults();

        assertThat(settings.chunkBytes()).isEqualTo(256 * 1024);
        assertThat(settings.maxInFlight()).isOne();
        assertThat(settings.uploadBytesPerSecond()).isEqualTo(1024L * 1024L);
        assertThat(settings.idleBeforeUpload()).hasSeconds(3);
        assertThat(settings.memoryQueueMaxBytes()).isEqualTo(16L * 1024L * 1024L);
    }

    @Test
    void externalSettingsCanOnlyTightenSafetyBudgets() {
        Map<String, String> environment = Map.of(
                "TEST_AGENT_OBSERVABILITY_CHUNK_BYTES", "9999999",
                "TEST_AGENT_OBSERVABILITY_MAX_IN_FLIGHT", "7",
                "TEST_AGENT_OBSERVABILITY_UPLOAD_BYTES_PER_SECOND", "99999999",
                "TEST_AGENT_OBSERVABILITY_IDLE_MILLIS", "1",
                "TEST_AGENT_OBSERVABILITY_MEMORY_QUEUE_MAX_BYTES", "999999999");

        LocalObservabilitySettings settings = LocalObservabilitySettings.load(environment::get);

        assertThat(settings.chunkBytes()).isEqualTo(256 * 1024);
        assertThat(settings.maxInFlight()).isOne();
        assertThat(settings.uploadBytesPerSecond()).isEqualTo(1024L * 1024L);
        assertThat(settings.idleBeforeUpload()).hasSeconds(3);
        assertThat(settings.memoryQueueMaxBytes()).isEqualTo(16L * 1024L * 1024L);
    }

    @Test
    void uploadCanBeDisabledWithoutDeletingTheSpool() {
        LocalObservabilitySettings settings = LocalObservabilitySettings.load(
                name -> "TEST_AGENT_OBSERVABILITY_MAX_IN_FLIGHT".equals(name) ? "0" : null);

        assertThat(settings.maxInFlight()).isZero();
        assertThat(settings.spoolMaxBytes()).isEqualTo(1024L * 1024L * 1024L);
    }
}
