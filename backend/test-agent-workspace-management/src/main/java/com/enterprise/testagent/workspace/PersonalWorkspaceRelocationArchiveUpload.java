package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单连接搬迁归档上传会话；按字节计数和摘要双重 fencing，且只能 complete/abort 一次。 */
public final class PersonalWorkspaceRelocationArchiveUpload {

    private final PersonalWorkspaceRelocationReceiveService service;
    private final String relocationId;
    private final String expectedSha256;
    private final long expectedSize;
    private final String traceId;
    private final Path archive;
    private final OutputStream output;
    private final MessageDigest digest;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private long received;

    PersonalWorkspaceRelocationArchiveUpload(
            PersonalWorkspaceRelocationReceiveService service,
            String relocationId,
            String expectedSha256,
            long expectedSize,
            String traceId,
            Path archive,
            OutputStream output,
            MessageDigest digest) {
        this.service = Objects.requireNonNull(service);
        this.relocationId = relocationId;
        this.expectedSha256 = expectedSha256;
        this.expectedSize = expectedSize;
        this.traceId = traceId;
        this.archive = Objects.requireNonNull(archive);
        this.output = Objects.requireNonNull(output);
        this.digest = Objects.requireNonNull(digest);
    }

    public synchronized void append(byte[] bytes) {
        if (terminal.get()) {
            throw invalidState();
        }
        try {
            long next = Math.addExact(received, bytes.length);
            if (next > expectedSize || next > PersonalWorkspaceSnapshotService.MAX_ARCHIVE_BYTES) {
                throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, "个人工作区搬迁归档超过允许大小");
            }
            output.write(bytes);
            digest.update(bytes);
            received = next;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "写入个人工作区搬迁归档失败", Map.of(), exception);
        }
    }

    public synchronized PersonalWorkspaceRelocationReceiveService.ReceiveResult complete() {
        if (!terminal.compareAndSet(false, true)) {
            throw invalidState();
        }
        try {
            output.close();
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (received != expectedSize || !expectedSha256.equals(actualSha256)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "个人工作区搬迁归档传输不完整",
                        Map.of("reason", "TRANSFER_DIGEST_MISMATCH"));
            }
            return service.apply(relocationId, archive, expectedSha256, expectedSize, traceId);
        } catch (PlatformException exception) {
            deleteQuietly();
            throw exception;
        } catch (Exception exception) {
            deleteQuietly();
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "完成个人工作区搬迁归档失败", Map.of(), exception);
        }
    }

    public synchronized void abort() {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        try {
            output.close();
        } catch (Exception ignored) {
            // 删除临时归档仍会继续执行。
        }
        deleteQuietly();
    }

    private PlatformException invalidState() {
        return new PlatformException(ErrorCode.CONFLICT, "个人工作区搬迁上传状态已结束");
    }

    private void deleteQuietly() {
        try {
            Files.deleteIfExists(archive);
        } catch (Exception ignored) {
            // 受控临时文件由后续同 ID 重试覆盖或系统临时目录清理兜底。
        }
    }
}
