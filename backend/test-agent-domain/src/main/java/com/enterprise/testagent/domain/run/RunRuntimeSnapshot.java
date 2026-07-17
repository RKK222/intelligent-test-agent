package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.event.RunEventDraft;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 截断或断线恢复使用的物化快照；events 按 reducer 应用顺序排列，但本身不推进 durable 游标。
 */
public record RunRuntimeSnapshot(
        RunId runId,
        long barrierSeq,
        long runtimeVersion,
        long resetGeneration,
        List<RunEventDraft> events,
        Instant createdAt) {

    public RunRuntimeSnapshot {
        Objects.requireNonNull(runId, "runId must not be null");
        if (barrierSeq < 0 || runtimeVersion < 0 || resetGeneration < 0) {
            throw new IllegalArgumentException("snapshot counters must not be negative");
        }
        events = events == null ? List.of() : List.copyOf(events);
        if (events.stream().anyMatch(event -> !runId.equals(event.runId()))) {
            throw new IllegalArgumentException("snapshot event runId must match snapshot runId");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** 兼容旧调用方；未记录 runtime version 的历史快照按 0 处理。 */
    public RunRuntimeSnapshot(
            RunId runId,
            long barrierSeq,
            long resetGeneration,
            List<RunEventDraft> events,
            Instant createdAt) {
        this(runId, barrierSeq, 0L, resetGeneration, events, createdAt);
    }

    public static RunRuntimeSnapshot empty(RunId runId) {
        return new RunRuntimeSnapshot(runId, 0, 0, 0, List.of(), Instant.EPOCH);
    }
}
