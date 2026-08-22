package com.enterprise.testagent.opencode.runtime.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.enterprise.testagent.domain.user.User;
import java.time.Instant;
import java.util.List;

/** OpenCode 插件、loopback relay 与服务端归档之间的版本化模型。 */
public final class OpencodeObservabilityModels {

    private OpencodeObservabilityModels() {
    }

    public record RuntimeIdentity(
            String kind,
            String generation,
            String processId,
            String serverId,
            String clientInstanceId) {
    }

    public record PluginBatch(
            String schemaVersion,
            RuntimeIdentity runtime,
            Instant coverageStartAt,
            long droppedCount,
            boolean complete,
            List<JsonNode> events) {

        public PluginBatch {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    /** 归档身份由已认证入口构造，不接受客户端伪造用户、组织或存储节点。 */
    public record IngestionIdentity(
            User user,
            String processId,
            String clientInstanceId,
            String generation) {
    }

    public record TraceAck(
            String traceId,
            long firstSequence,
            long lastSequence,
            String sha256,
            long contentLength,
            long completeThrough,
            boolean duplicate,
            String archiveStatus,
            Instant archivedAt) {
    }

    public record BatchAck(List<TraceAck> traces) {
        public BatchAck {
            traces = traces == null ? List.of() : List.copyOf(traces);
        }
    }

    public record RawEventPage(List<JsonNode> items, long completeThrough, boolean complete) {
        public RawEventPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** Java 到归档 owner Java 的内部转发体；不允许携带文件路径。 */
    public record InternalTraceChunk(
            String userId,
            String clientInstanceId,
            String generation,
            RuntimeIdentity runtime,
            Instant coverageStartAt,
            long droppedCount,
            boolean complete,
            String traceId,
            long firstSequence,
            long lastSequence,
            String sha256,
            String dataBase64) {
    }
}
