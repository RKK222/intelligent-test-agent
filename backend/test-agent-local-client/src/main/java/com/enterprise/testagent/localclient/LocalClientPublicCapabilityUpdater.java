package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 客户端公共能力包逐片下载、校验、原子激活、健康验证和失败回滚。 */
final class LocalClientPublicCapabilityUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientPublicCapabilityUpdater.class);
    interface ProtocolSink {
        void send(LocalClientFrameType type, String requestId, Object payload);

        String clientInstanceId();

        long generation();
    }

    private final LocalClientPublicCapabilityStore store;
    private final OpencodeProcessSupervisor supervisor;
    private final ProtocolSink sink;
    private Download download;

    LocalClientPublicCapabilityUpdater(
            LocalClientPublicCapabilityStore store,
            OpencodeProcessSupervisor supervisor,
            ProtocolSink sink) {
        this.store = store;
        this.supervisor = supervisor;
        this.sink = sink;
    }

    synchronized void reportVersion() {
        LocalClientPublicCapabilityStore.State state = store.snapshot();
        LOGGER.info("local_client_public_capability_version_reported generation={} status={} activeDigestPresent={} pendingDigestPresent={}",
                sink.generation(), state.status(), state.activeDigest() != null, state.pendingDigest() != null);
        sink.send(LocalClientFrameType.PUBLIC_CAPABILITY_VERSION, requestId("lcpv_"),
                new LocalClientPayloads.PublicCapabilityVersion(
                        sink.clientInstanceId(), sink.generation(), state.activeCommit(), state.activeDigest(),
                        state.pendingCommit(), state.pendingDigest(), state.pendingCommandId(),
                        state.status(), state.errorCode(), Instant.now()));
    }

    synchronized void handleAvailable(LocalClientPayloads.PublicCapabilityAvailable available) {
        requireCoordinates(available.clientInstanceId(), available.connectionGeneration());
        requireDigest(available.bundleDigest());
        store.recordAvailable(available);
        LOGGER.info("local_client_public_capability_available generation={} bundleDigest={} sourceCommit={} requiresRestart={}",
                available.connectionGeneration(), available.bundleDigest(), available.sourceCommit(),
                available.requiresRestart());
    }

    synchronized void handleCommand(LocalClientPayloads.PublicCapabilityUpdateCommand command) {
        requireCoordinates(command.clientInstanceId(), command.connectionGeneration());
        requireDigest(command.bundleDigest());
        requireDigest(command.artifactSha256());
        if (command.commandId() == null || !command.commandId().matches("lcpc_[a-f0-9]{32}")
                || command.sourceCommit() == null || !command.sourceCommit().matches("[0-9a-f]{40,64}")
                || command.artifactSize() < 1
                || command.chunkCount() < 1) {
            throw new IllegalArgumentException("公共能力更新命令无效");
        }
        if (download != null && download.command().commandId().equals(command.commandId())) {
            LOGGER.info("local_client_public_capability_download_resumed commandId={} nextSequence={} receivedBytes={}",
                    command.commandId(), download.nextSequence(), download.receivedBytes());
            requestChunk(download.nextSequence());
            return;
        }
        try {
            Path archive = store.incomingArchive(command.commandId());
            Files.deleteIfExists(archive);
            Files.createFile(archive);
            download = new Download(command, archive, 0, 0);
            store.recordPendingCommand(command.commandId(), command.sourceCommit(), command.bundleDigest());
            LOGGER.info("local_client_public_capability_download_started commandId={} generation={} bundleDigest={} artifactSize={} chunkCount={} requiresRestart={}",
                    command.commandId(), command.connectionGeneration(), command.bundleDigest(),
                    command.artifactSize(), command.chunkCount(), command.requiresRestart());
            requestChunk(0);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建公共能力下载文件", exception);
        }
    }

    synchronized void handleChunk(String commandId, LocalClientPayloads.BinaryChunk chunk) {
        Download current = download;
        if (current == null || !current.command().commandId().equals(commandId)) {
            throw new IllegalStateException("公共能力分片 commandId 无效");
        }
        if (chunk.sequence() < current.nextSequence()) {
            // 网络重试可能重复投递上一片；已写入内容不再追加，只继续拉取尚缺的下一片。
            if (current.nextSequence() < current.command().chunkCount()) {
                requestChunk(current.nextSequence());
            }
            return;
        }
        if (chunk.sequence() > current.nextSequence()) {
            fail(current, "CHUNK_SEQUENCE_MISMATCH");
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(chunk.dataBase64());
        } catch (IllegalArgumentException exception) {
            fail(current, "CHUNK_ENCODING_INVALID");
            return;
        }
        if (bytes.length > com.enterprise.testagent.localclient.protocol.LocalClientProtocol.BINARY_CHUNK_BYTES) {
            fail(current, "CHUNK_SIZE_EXCEEDED");
            return;
        }
        long nextBytes = current.receivedBytes() + bytes.length;
        if (nextBytes > current.command().artifactSize()) {
            fail(current, "ARTIFACT_SIZE_MISMATCH");
            return;
        }
        try {
            Files.write(current.archive(), bytes, StandardOpenOption.APPEND);
            download = current = new Download(
                    current.command(), current.archive(), current.nextSequence() + 1, nextBytes);
            if (current.nextSequence() == 1
                    || current.nextSequence() == current.command().chunkCount()
                    || current.nextSequence() % 32 == 0) {
                int percent = (int) Math.min(100L,
                        nextBytes * 100L / Math.max(1L, current.command().artifactSize()));
                LOGGER.info("local_client_public_capability_download_progress commandId={} receivedChunks={} totalChunks={} receivedBytes={} totalBytes={} percent={}",
                        current.command().commandId(), current.nextSequence(), current.command().chunkCount(),
                        nextBytes, current.command().artifactSize(), percent);
            }
            if (!chunk.endOfStream()) {
                requestChunk(current.nextSequence());
                return;
            }
            if (current.nextSequence() != current.command().chunkCount()
                    || nextBytes != current.command().artifactSize()
                    || !current.command().artifactSha256().equals(sha256(Files.readAllBytes(current.archive())))) {
                fail(current, "ARTIFACT_DIGEST_MISMATCH");
                return;
            }
            apply(current);
        } catch (Exception exception) {
            fail(current, "CAPABILITY_INSTALL_FAILED");
        }
    }

    synchronized void handleStatusAck(LocalClientPayloads.PublicCapabilityUpdateStatusAck ack) {
        if (download == null || !download.command().commandId().equals(ack.commandId())) {
            return;
        }
        requireCoordinates(ack.clientInstanceId(), ack.connectionGeneration());
        if ("SUCCEEDED".equals(ack.status()) || "FAILED".equals(ack.status()) || "ROLLED_BACK".equals(ack.status())) {
            try {
                Files.deleteIfExists(download.archive());
            } catch (IOException ignored) {
            }
            download = null;
            LOGGER.info("local_client_public_capability_status_acknowledged commandId={} status={}",
                    ack.commandId(), ack.status());
        }
    }

    synchronized LocalClientPublicCapabilityStore.State snapshot() {
        return store.snapshot();
    }

    private void apply(Download current) throws Exception {
        long startedNanos = System.nanoTime();
        LOGGER.info("local_client_public_capability_apply_started commandId={} bundleDigest={} requiresRestart={}",
                current.command().commandId(), current.command().bundleDigest(),
                current.command().requiresRestart());
        report(current, "APPLYING", null);
        // 在原子链接真正切换前，本地状态仍是下载/准备阶段；重启时不能误把旧 active 当成已激活目标。
        store.recordStatus("DOWNLOADING", null);
        LocalClientPublicCapabilityStore.Candidate candidate = store.installArchive(
                current.archive(), current.command().sourceCommit(), current.command().bundleDigest());
        LOGGER.info("local_client_public_capability_archive_verified commandId={} bundleDigest={} durationMs={}",
                current.command().commandId(), current.command().bundleDigest(),
                LocalClientDiagnostics.elapsedMillis(startedNanos));
        String previousDigest = null;
        boolean activated = false;
        try {
            previousDigest = store.activate(candidate);
            activated = true;
            var health = supervisor.reloadPublicCapabilities(current.command().requiresRestart());
            if (!health.success() || !health.opencodeHealthy()
                    || !supervisor.validatePublicCapabilityCatalog()) {
                throw new IllegalStateException("公共能力激活后 OpenCode 健康或目录校验失败");
            }
            store.completeActivation();
            report(current, "SUCCEEDED", null);
            LOGGER.info("local_client_public_capability_apply_completed commandId={} bundleDigest={} durationMs={}",
                    current.command().commandId(), current.command().bundleDigest(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
        } catch (Exception activationFailure) {
            if (!activated) {
                throw activationFailure;
            }
            LOGGER.warn("local_client_public_capability_activation_failed commandId={} bundleDigest={} durationMs={} rootFailureType={}",
                    current.command().commandId(), current.command().bundleDigest(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos),
                    LocalClientDiagnostics.rootFailureType(activationFailure));
            store.rollback(previousDigest, "OPENCODE_ACTIVATION_FAILED");
            var restored = supervisor.reloadPublicCapabilities(true);
            if (!restored.success() || !restored.opencodeHealthy()
                    || !supervisor.validatePublicCapabilityCatalog()) {
                throw new IllegalStateException("公共能力回滚后 OpenCode 未恢复健康", activationFailure);
            }
            store.recordStatus("ROLLED_BACK", "OPENCODE_ACTIVATION_FAILED");
            report(current, "ROLLED_BACK", "OPENCODE_ACTIVATION_FAILED");
            LOGGER.info("local_client_public_capability_rolled_back commandId={} bundleDigest={} durationMs={}",
                    current.command().commandId(), current.command().bundleDigest(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
        }
    }

    private void fail(Download current, String errorCode) {
        // 所有下载/校验失败都必须落盘，避免重连后仍以 PENDING 状态误导托盘和平台。
        store.recordStatus("FAILED", errorCode);
        report(current, "FAILED", errorCode);
        LOGGER.warn("local_client_public_capability_failed commandId={} bundleDigest={} errorCode={} receivedChunks={} receivedBytes={}",
                current.command().commandId(), current.command().bundleDigest(), errorCode,
                current.nextSequence(), current.receivedBytes());
    }

    private void requestChunk(long sequence) {
        Download current = download;
        if (current == null) {
            return;
        }
        sink.send(LocalClientFrameType.PUBLIC_CAPABILITY_CHUNK_REQUEST, current.command().commandId(),
                new LocalClientPayloads.PublicCapabilityChunkRequest(
                        current.command().commandId(), sink.clientInstanceId(), sink.generation(),
                        current.command().bundleDigest(), sequence));
    }

    private void report(Download current, String status, String errorCode) {
        sink.send(LocalClientFrameType.PUBLIC_CAPABILITY_UPDATE_STATUS, current.command().commandId(),
                new LocalClientPayloads.PublicCapabilityUpdateStatus(
                        current.command().commandId(), sink.clientInstanceId(), sink.generation(),
                        current.command().sourceCommit(), current.command().bundleDigest(), status, errorCode,
                        Instant.now()));
    }

    private void requireCoordinates(String clientInstanceId, long generation) {
        if (!sink.clientInstanceId().equals(clientInstanceId) || sink.generation() != generation) {
            throw new IllegalArgumentException("公共能力客户端坐标或 generation 无效");
        }
    }

    private static void requireDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("公共能力 SHA-256 摘要无效");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String requestId(String prefix) {
        return prefix + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private record Download(
            LocalClientPayloads.PublicCapabilityUpdateCommand command,
            Path archive,
            long nextSequence,
            long receivedBytes) {
    }
}
