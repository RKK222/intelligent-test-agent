package com.enterprise.testagent.localclient;

import java.time.Duration;
import java.util.function.Function;

/**
 * 本地 Trace 资源预算。外部配置只能收紧预算或延长等待，不能突破对话优先的安全上限。
 */
record LocalObservabilitySettings(
        int chunkBytes,
        int maxInFlight,
        long uploadBytesPerSecond,
        Duration idleBeforeUpload,
        Duration acknowledgementTimeout,
        long spoolMaxBytes,
        long memoryQueueMaxBytes) {

    static final int DEFAULT_CHUNK_BYTES = 256 * 1024;
    static final int MAX_IN_FLIGHT = 1;
    static final long DEFAULT_UPLOAD_BYTES_PER_SECOND = 1024L * 1024L;
    static final Duration DEFAULT_IDLE_BEFORE_UPLOAD = Duration.ofSeconds(3);
    static final Duration DEFAULT_ACKNOWLEDGEMENT_TIMEOUT = Duration.ofSeconds(30);
    static final long DEFAULT_SPOOL_MAX_BYTES = 1024L * 1024L * 1024L;
    static final long DEFAULT_MEMORY_QUEUE_MAX_BYTES = 16L * 1024L * 1024L;

    LocalObservabilitySettings {
        chunkBytes = bounded(chunkBytes, 16 * 1024, DEFAULT_CHUNK_BYTES);
        maxInFlight = bounded(maxInFlight, 0, MAX_IN_FLIGHT);
        uploadBytesPerSecond = bounded(uploadBytesPerSecond, 64 * 1024L, DEFAULT_UPLOAD_BYTES_PER_SECOND);
        idleBeforeUpload = maximum(idleBeforeUpload, DEFAULT_IDLE_BEFORE_UPLOAD);
        acknowledgementTimeout = maximum(acknowledgementTimeout, Duration.ofSeconds(5));
        spoolMaxBytes = Math.max(chunkBytes, spoolMaxBytes);
        memoryQueueMaxBytes = bounded(memoryQueueMaxBytes, chunkBytes, DEFAULT_MEMORY_QUEUE_MAX_BYTES);
    }

    static LocalObservabilitySettings defaults() {
        return new LocalObservabilitySettings(
                DEFAULT_CHUNK_BYTES,
                MAX_IN_FLIGHT,
                DEFAULT_UPLOAD_BYTES_PER_SECOND,
                DEFAULT_IDLE_BEFORE_UPLOAD,
                DEFAULT_ACKNOWLEDGEMENT_TIMEOUT,
                DEFAULT_SPOOL_MAX_BYTES,
                DEFAULT_MEMORY_QUEUE_MAX_BYTES);
    }

    static LocalObservabilitySettings load() {
        return load(System::getenv);
    }

    static LocalObservabilitySettings load(Function<String, String> environment) {
        LocalObservabilitySettings defaults = defaults();
        return new LocalObservabilitySettings(
                integer(environment, "TEST_AGENT_OBSERVABILITY_CHUNK_BYTES", defaults.chunkBytes()),
                integer(environment, "TEST_AGENT_OBSERVABILITY_MAX_IN_FLIGHT", defaults.maxInFlight()),
                number(environment, "TEST_AGENT_OBSERVABILITY_UPLOAD_BYTES_PER_SECOND",
                        defaults.uploadBytesPerSecond()),
                Duration.ofMillis(number(environment, "TEST_AGENT_OBSERVABILITY_IDLE_MILLIS",
                        defaults.idleBeforeUpload().toMillis())),
                Duration.ofMillis(number(environment, "TEST_AGENT_OBSERVABILITY_ACK_TIMEOUT_MILLIS",
                        defaults.acknowledgementTimeout().toMillis())),
                number(environment, "TEST_AGENT_OBSERVABILITY_SPOOL_MAX_BYTES", defaults.spoolMaxBytes()),
                number(environment, "TEST_AGENT_OBSERVABILITY_MEMORY_QUEUE_MAX_BYTES",
                        defaults.memoryQueueMaxBytes()));
    }

    private static int integer(Function<String, String> environment, String name, int fallback) {
        return Math.toIntExact(number(environment, name, fallback));
    }

    private static long number(Function<String, String> environment, String name, long fallback) {
        String raw = environment.apply(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long bounded(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Duration maximum(Duration value, Duration minimum) {
        return value.compareTo(minimum) < 0 ? minimum : value;
    }
}
