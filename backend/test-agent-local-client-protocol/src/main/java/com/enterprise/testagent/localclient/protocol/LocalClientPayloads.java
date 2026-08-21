package com.enterprise.testagent.localclient.protocol;

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
            Instant serverTime) {
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

    public record Cancel(String targetRequestId, String reason) {
    }

    public record Error(String code, String message, boolean retryable, Map<String, Object> details) {
    }
}
