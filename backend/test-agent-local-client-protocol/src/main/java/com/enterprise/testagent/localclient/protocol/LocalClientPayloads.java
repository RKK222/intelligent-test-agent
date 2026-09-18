package com.enterprise.testagent.localclient.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** v1 帧载荷集合；嵌套 record 避免服务端与客户端各自复制线协议 DTO。 */
public final class LocalClientPayloads {

    private static final java.util.Set<String> LEGACY_REGISTER_FIELDS = java.util.Set.of(
            "clientKey",
            "clientInstanceId",
            "clientName",
            "platform",
            "architecture",
            "clientVersion",
            "opencodeVersion",
            "reportedAddresses");

    private LocalClientPayloads() {
    }

    public record Register(
            String clientKey,
            String clientInstanceId,
            String clientName,
            String platform,
            String architecture,
            String clientVersion,
            String opencodeVersion,
            List<String> reportedAddresses,
            String unifiedAuthId,
            String launcherVersion,
            List<String> capabilities) {

        /** 保留旧客户端和现有调用方使用的 v1 八字段构造方式。 */
        public Register(
                String clientKey,
                String clientInstanceId,
                String clientName,
                String platform,
                String architecture,
                String clientVersion,
                String opencodeVersion,
                List<String> reportedAddresses) {
            this(
                    clientKey,
                    clientInstanceId,
                    clientName,
                    platform,
                    architecture,
                    clientVersion,
                    opencodeVersion,
                    reportedAddresses,
                    null,
                    null,
                    null);
        }
    }

    /** 只有线协议恰好为旧八字段且版本为 0.1.0 时，才允许缺少统一认证号。 */
    public static boolean isStrictLegacyRegister(JsonNode rawPayload, Register payload) {
        if (rawPayload == null || !rawPayload.isObject() || payload == null || !"0.1.0".equals(payload.clientVersion())) {
            return false;
        }
        java.util.Set<String> actualFields = new java.util.HashSet<>();
        rawPayload.fieldNames().forEachRemaining(actualFields::add);
        return actualFields.equals(LEGACY_REGISTER_FIELDS);
    }

    public record Registered(
            long connectionGeneration,
            String modelGrant,
            Instant modelGrantExpiresAt,
            Instant serverTime,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> managedModelConfig,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> managedRuntimeConfig) {

        /** 保留仅协商模型配置的五字段构造；运行时配置仍按能力单独下发。 */
        public Registered(
                long connectionGeneration,
                String modelGrant,
                Instant modelGrantExpiresAt,
                Instant serverTime,
                Map<String, Object> managedModelConfig) {
            this(connectionGeneration, modelGrant, modelGrantExpiresAt, serverTime, managedModelConfig, null);
        }

        /** 保留未协商 MANAGED_MODEL_CONFIG_V1 的旧服务端/客户端四字段构造。 */
        public Registered(
                long connectionGeneration,
                String modelGrant,
                Instant modelGrantExpiresAt,
                Instant serverTime) {
            this(connectionGeneration, modelGrant, modelGrantExpiresAt, serverTime, null, null);
        }
    }

    public record Heartbeat(
            String processStatus,
            Long processId,
            Instant processStartedAt,
            Integer opencodePort,
            boolean opencodeHealthy,
            List<String> reportedAddresses,
            Instant observedAt) {
    }

    public record HeartbeatAck(Instant serverTime, Instant expiresAt) {
    }

    public record LifecycleCommand(String action, Integer preferredPort, Map<String, String> environment) {
    }

    public record LifecycleResult(
            boolean success,
            String processStatus,
            Long processId,
            Instant processStartedAt,
            Integer opencodePort,
            boolean opencodeHealthy,
            String executable,
            String message) {
    }

    public record HttpRequest(
            String method,
            String pathAndQuery,
            Map<String, List<String>> headers,
            String bodyBase64,
            boolean streaming) {
    }

    public record HttpResponse(int status, Map<String, List<String>> headers, String bodyBase64) {
    }

    public record StreamOpen(int status, Map<String, List<String>> headers) {
    }

    public record BinaryChunk(long sequence, String dataBase64, boolean endOfStream) {
        public BinaryChunk {
            if (sequence < 0) {
                throw new IllegalArgumentException("sequence must not be negative");
            }
            if (dataBase64 == null) {
                dataBase64 = "";
            }
            long maxEncoded = 4L * ((LocalClientProtocol.BINARY_CHUNK_BYTES + 2L) / 3L);
            if (dataBase64.length() > maxEncoded) {
                throw new IllegalArgumentException("binary chunk exceeds 256 KiB");
            }
        }
    }

    public record FileRequest(String workspaceId, String rootDigest, String operation, JsonNode parameters) {
    }

    public record FileResponse(boolean success, JsonNode result) {
    }

    /** 客户端主动选择本机目录后，只提交显示名称和绝对路径；用户与实例身份取自已认证连接。 */
    public record WorkspaceRegister(String name, String rootPath) {
    }

    /** 平台完成根目录校验、注册和持久化后返回的工作区摘要。 */
    public record WorkspaceRegistered(String workspaceId, String name, String rootPath) {
    }

    public record ModelGrant(String modelGrant, Instant expiresAt) {
    }

    /** 客户端启动后及周期检查时上报的本机发布状态。 */
    public record VersionCheck(
            String clientInstanceId,
            String clientVersion,
            String launcherVersion,
            String opencodeVersion,
            List<String> capabilities,
            Instant checkedAt) {
    }

    /** 平台解析出的当前有效版本策略；force=true 表示立即执行。 */
    public record VersionPolicy(
            String clientInstanceId,
            long connectionGeneration,
            String targetVersion,
            String direction,
            long policyRevision,
            boolean force) {
    }

    /** 服务端下发的幂等更新命令，不携带任意下载地址。 */
    public record UpdateCommand(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            long policyRevision,
            String targetVersion,
            String direction) {
    }

    /** 客户端完成下载、验签及候选自检后请求最终切换许可。 */
    public record UpdatePrepared(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            long policyRevision,
            String targetVersion,
            String direction,
            String releaseDigest,
            Instant preparedAt) {
    }

    public record UpdateApply(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            long policyRevision,
            String targetVersion,
            String direction,
            String releaseDigest) {
    }

    public record UpdateCancel(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            long policyRevision,
            String targetVersion,
            String direction,
            String reasonCode) {
    }

    /** 更新阶段只上报无敏感信息的状态码和可选错误码。 */
    public record UpdateStatus(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            long policyRevision,
            String targetVersion,
            String direction,
            String status,
            String errorCode,
            Instant observedAt) {
    }

    /** 服务端持久化更新终态后的确认；客户端仅在坐标精确匹配时删除唯一 marker。 */
    public record UpdateStatusAck(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            String status) {
    }

    /** 客户端注册后上报当前已激活公共能力版本；空摘要表示尚未初始化基线。 */
    public record PublicCapabilityVersion(
            String clientInstanceId,
            long connectionGeneration,
            String activeCommit,
            String activeDigest,
            String pendingCommit,
            String pendingDigest,
            String pendingCommandId,
            String status,
            String errorCode,
            Instant observedAt) {
    }

    /** 平台仅通知可用版本和变更摘要，不代表用户已经同意安装。 */
    public record PublicCapabilityAvailable(
            String clientInstanceId,
            long connectionGeneration,
            String sourceCommit,
            String bundleDigest,
            int agentCount,
            int skillCount,
            int toolCount,
            boolean requiresRestart,
            String changeSummaryJson) {
    }

    /** 托盘确认只提交当前实例和平台已通知的目标摘要。 */
    public record PublicCapabilityUpdateRequest(
            String clientInstanceId,
            long connectionGeneration,
            String expectedBundleDigest) {
    }

    /** 用户确认后下发的不可变制品坐标；客户端逐片拉取，服务端不发送任意 URL。 */
    public record PublicCapabilityUpdateCommand(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            String sourceCommit,
            String bundleDigest,
            String artifactSha256,
            long artifactSize,
            int chunkCount,
            boolean requiresRestart,
            String manifestJson) {
    }

    /** 每次只请求一个 256 KiB 分片，用连接背压自然约束能力制品传输。 */
    public record PublicCapabilityChunkRequest(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            String bundleDigest,
            long sequence) {
    }

    public record PublicCapabilityUpdateStatus(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            String sourceCommit,
            String bundleDigest,
            String status,
            String errorCode,
            Instant observedAt) {
    }

    public record PublicCapabilityUpdateStatusAck(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            String status) {
    }

    /** 客户端先声明待上传分片的不可变坐标，服务端据此执行 generation fencing 和摘要校验。 */
    public record ObservabilityBatch(
            String batchId,
            String clientInstanceId,
            long connectionGeneration,
            String traceId,
            String runtimeGeneration,
            String runtimeKind,
            Instant coverageStartAt,
            long firstSequence,
            long lastSequence,
            String sha256,
            long contentLength,
            long droppedCount,
            long pendingChunks,
            boolean complete,
            Instant createdAt) {
    }

    /** 单个在途 Trace 分片；原始 NDJSON 固定不超过 256 KiB，Base64 后仍低于帧上限。 */
    public record TraceChunkUpload(
            String batchId,
            String clientInstanceId,
            long connectionGeneration,
            String traceId,
            long firstSequence,
            long lastSequence,
            String sha256,
            String dataBase64) {
    }

    /** 服务端完成原子归档、摘要校验和目录写入后才发送 ACK。 */
    public record TraceChunkAck(
            String batchId,
            String clientInstanceId,
            long connectionGeneration,
            String traceId,
            long firstSequence,
            long lastSequence,
            String sha256,
            long completeThrough,
            String archiveStatus,
            Instant archivedAt) {
    }

    public record TraceUploadWatermark(
            String clientInstanceId,
            long connectionGeneration,
            String traceId,
            long completeThrough,
            Instant updatedAt) {
    }

    public record Cancel(String targetRequestId, String reason) {
    }

    public record Error(String code, String message, boolean retryable, Map<String, Object> details) {
    }
}
