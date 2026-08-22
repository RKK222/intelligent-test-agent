package com.enterprise.testagent.domain.trace;

import java.time.Instant;
import java.util.List;

/** Trace 目录与时间线索引模型；只包含可运营查询的元数据，严禁承载正文或物理归档路径。 */
public final class TraceModels {

    private TraceModels() {
    }

    public record Filter(
            Instant startTime,
            Instant endTime,
            String userKeyword,
            String organization,
            String agentId,
            String skill,
            String tool,
            String status,
            String traceId,
            String runId,
            int page,
            int pageSize) {
    }

    public record Catalog(
            String traceId,
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String runtimeKind,
            String source,
            String processId,
            String clientInstanceId,
            String backendProcessId,
            String linuxServerId,
            String sessionId,
            String runId,
            String agentId,
            String status,
            String archiveStatus,
            Instant startedAt,
            Instant updatedAt,
            Instant coverageStartAt,
            long completeThrough,
            long eventCount,
            long archivedBytes,
            long droppedCount,
            long pendingChunks,
            boolean complete,
            boolean redacted) {
    }

    public record Span(
            String traceId,
            String eventId,
            String type,
            String lane,
            Instant occurredAt,
            long globalSequence,
            long sessionSequence,
            String sessionId,
            String runId,
            String turnId,
            String stepId,
            String messageId,
            String callId,
            String parentId,
            String capabilityKind,
            String capabilityName,
            String status,
            long durationMs,
            long tokensInput,
            long tokensOutput,
            long tokensReasoning,
            String source) {
    }

    public record CapabilityFact(
            String eventId,
            long version,
            Instant occurredAt,
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String sessionId,
            String runId,
            String callId,
            String capabilityType,
            String capabilityName,
            String status,
            long durationMs,
            String source) {
    }

    public record EventPage(List<Span> items, long total, long completeThrough) {
        public EventPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
