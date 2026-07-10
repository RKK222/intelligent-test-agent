package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.event.RunEventSsePayload;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 一次历史消息恢复结果，同时携带前端展示完整度和详情失效边界。
 */
public record RunHistoryRecoveryResult(
        List<RunEventSsePayload> events,
        String historyRepresentation,
        boolean replayAvailable,
        Instant detailsAvailableUntil,
        RunHistoryRecoverySource source) {

    public RunHistoryRecoveryResult {
        events = events == null ? List.of() : List.copyOf(events);
        if (!"FULL".equals(historyRepresentation) && !"SUMMARY".equals(historyRepresentation)) {
            throw new IllegalArgumentException("historyRepresentation must be FULL or SUMMARY");
        }
        Objects.requireNonNull(source, "source must not be null");
        if ("SUMMARY".equals(historyRepresentation) && replayAvailable) {
            throw new IllegalArgumentException("summary history must not be replayable");
        }
    }

    /** Redis/OpenCode 均提供完整消息；只有 Redis 详情具有明确的过期时刻。 */
    public static RunHistoryRecoveryResult full(
            List<RunEventSsePayload> events,
            Instant detailsAvailableUntil,
            RunHistoryRecoverySource source) {
        return full(events, detailsAvailableUntil, source, true);
    }

    /** 容量截断后的 Redis 快照仍可展示当前内容，但不能宣称原始详情可完整重放。 */
    public static RunHistoryRecoveryResult full(
            List<RunEventSsePayload> events,
            Instant detailsAvailableUntil,
            RunHistoryRecoverySource source,
            boolean replayAvailable) {
        return new RunHistoryRecoveryResult(
                events, "FULL", replayAvailable, detailsAvailableUntil, source);
    }

    /** PostgreSQL 仅提供终态双摘要，不宣称可重放原始详情。 */
    public static RunHistoryRecoveryResult summary(List<RunEventSsePayload> events) {
        return new RunHistoryRecoveryResult(
                events,
                "SUMMARY",
                false,
                null,
                RunHistoryRecoverySource.POSTGRESQL_SUMMARY);
    }

    /** Session 跨越 Redis 详情窗口时，旧轮次只能展示摘要，因此整体按 SUMMARY 对外声明。 */
    public static RunHistoryRecoveryResult redisWithSummaries(
            List<RunEventSsePayload> events,
            Instant detailsAvailableUntil) {
        return new RunHistoryRecoveryResult(
                events,
                "SUMMARY",
                false,
                detailsAvailableUntil,
                RunHistoryRecoverySource.REDIS_POSTGRESQL_SUMMARY);
    }

    /** 所有来源都不可用时保持安全空结果，不伪装为完整历史。 */
    public static RunHistoryRecoveryResult empty() {
        return new RunHistoryRecoveryResult(List.of(), "SUMMARY", false, null, RunHistoryRecoverySource.NONE);
    }
}
