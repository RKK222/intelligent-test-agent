package com.enterprise.testagent.localclient.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** v1 帧载荷集合；嵌套 record 避免服务端与客户端各自复制线协议 DTO。 */
public final class LocalClientPayloads {

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
            List<String> reportedAddresses) {
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

    public record Cancel(String targetRequestId, String reason) {
    }

    public record Error(String code, String message, boolean retryable, Map<String, Object> details) {
    }
}
