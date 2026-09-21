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
            boolean redacted,
            Boolean rtkEnabled,
            Boolean conciseOutputSelected,
            List<String> skills) {

        public Catalog {
            skills = skills == null ? List.of() : List.copyOf(skills);
        }

        /** 兼容 ClickHouse 既有目录结构；能力快照由查询层按 runId 从 PostgreSQL 补齐。 */
        public Catalog(
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
            this(traceId, userId, username, organization, rdDepartment, department, runtimeKind, source,
                    processId, clientInstanceId, backendProcessId, linuxServerId, sessionId, runId, agentId,
                    status, archiveStatus, startedAt, updatedAt, coverageStartAt, completeThrough, eventCount,
                    archivedBytes, droppedCount, pendingChunks, complete, redacted, null, null, List.of());
        }

        /** 返回附带 Run 创建时能力快照的目录视图，不改变 ClickHouse 目录事实。 */
        public Catalog withRuntimeFeatureSnapshot(Boolean rtkEnabled, Boolean conciseOutputSelected) {
            return new Catalog(traceId, userId, username, organization, rdDepartment, department, runtimeKind,
                    source, processId, clientInstanceId, backendProcessId, linuxServerId, sessionId, runId,
                    agentId, status, archiveStatus, startedAt, updatedAt, coverageStartAt, completeThrough,
                    eventCount, archivedBytes, droppedCount, pendingChunks, complete, redacted,
                    rtkEnabled, conciseOutputSelected, skills);
        }

        /** 返回附带本 Trace 已观测 Skill 名称的目录视图；名称已去重且不包含参数或结果正文。 */
        public Catalog withSkills(List<String> skills) {
            return new Catalog(traceId, userId, username, organization, rdDepartment, department, runtimeKind,
                    source, processId, clientInstanceId, backendProcessId, linuxServerId, sessionId, runId,
                    agentId, status, archiveStatus, startedAt, updatedAt, coverageStartAt, completeThrough,
                    eventCount, archivedBytes, droppedCount, pendingChunks, complete, redacted,
                    rtkEnabled, conciseOutputSelected, skills);
        }
    }

    public record Span(
            String traceId,
            String eventId,
            String type,
            String lane,
            String recordKind,
            Instant occurredAt,
            Instant startedAt,
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
            long tokensCacheRead,
            long tokensCacheWrite,
            long tokensTotal,
            Long ttftMs,
            Long decodeMs,
            long decodeTokens,
            Double cost,
            String finishReason,
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
