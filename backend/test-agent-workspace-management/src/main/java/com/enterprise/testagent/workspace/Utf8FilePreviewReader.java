package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

/** 在固定内存上限内读取一段 UTF-8 文件，供工作区与引用视图复用。 */
final class Utf8FilePreviewReader {

    static final int CHUNK_BYTES = 512 * 1024;
    private static final int UTF8_BOUNDARY_LOOKAHEAD_BYTES = 3;

    private Utf8FilePreviewReader() {
    }

    static FilePreviewChunkResponse read(
            Path target,
            String responsePath,
            long offset,
            Long expectedSize,
            Long expectedLastModifiedMillis,
            long warningThresholdBytes) {
        if (offset < 0) {
            throw invalidOffset(responsePath, offset);
        }
        try {
            BasicFileAttributes before = Files.readAttributes(
                    target,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile()) {
                throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", responsePath));
            }
            long size = before.size();
            long modified = before.lastModifiedTime().toMillis();
            requireExpectedSnapshot(responsePath, size, modified, expectedSize, expectedLastModifiedMillis);
            if (offset > size) {
                throw invalidOffset(responsePath, offset);
            }
            if (offset == size) {
                return new FilePreviewChunkResponse(
                        responsePath, "", offset, offset, size, true, warningThresholdBytes, modified);
            }

            int requestedBytes = (int) Math.min(CHUNK_BYTES, size - offset);
            int lookaheadBytes = (int) Math.min(
                    UTF8_BOUNDARY_LOOKAHEAD_BYTES,
                    size - offset - requestedBytes);
            ByteBuffer buffer = ByteBuffer.allocate(requestedBytes + lookaheadBytes);
            try (SeekableByteChannel channel = Files.newByteChannel(target, StandardOpenOption.READ)) {
                channel.position(offset);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // SeekableByteChannel 允许短读，持续读取到本段缓冲区填满或 EOF。
                }
            }
            int bytesRead = buffer.position();
            if (bytesRead < requestedBytes) {
                throw previewChanged(responsePath);
            }
            byte[] bytes = buffer.array();
            int contentBytes = requestedBytes;
            // 分片正好落在多字节字符中间时，最多向后包含 3 个 continuation byte。
            while (contentBytes < bytesRead && isUtf8Continuation(bytes[contentBytes])) {
                contentBytes++;
            }

            String content;
            try {
                content = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, 0, contentBytes))
                        .toString();
            } catch (Exception exception) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "文件不是有效的 UTF-8 文本，无法预览",
                        Map.of("path", responsePath),
                        exception);
            }

            BasicFileAttributes after = Files.readAttributes(
                    target,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (after.size() != size || after.lastModifiedTime().toMillis() != modified) {
                throw previewChanged(responsePath);
            }
            long nextOffset = offset + contentBytes;
            return new FilePreviewChunkResponse(
                    responsePath,
                    content,
                    offset,
                    nextOffset,
                    size,
                    nextOffset >= size,
                    warningThresholdBytes,
                    modified);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "读取文件预览分段失败",
                    Map.of("path", responsePath),
                    exception);
        }
    }

    private static void requireExpectedSnapshot(
            String path,
            long size,
            long modified,
            Long expectedSize,
            Long expectedLastModifiedMillis) {
        if ((expectedSize != null && expectedSize != size)
                || (expectedLastModifiedMillis != null && expectedLastModifiedMillis != modified)) {
            throw previewChanged(path);
        }
    }

    private static boolean isUtf8Continuation(byte value) {
        return (value & 0xC0) == 0x80;
    }

    private static PlatformException invalidOffset(String path, long offset) {
        return new PlatformException(
                ErrorCode.VALIDATION_ERROR,
                "文件预览偏移无效",
                Map.of("path", path, "offset", offset));
    }

    private static PlatformException previewChanged(String path) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "文件在渐进预览期间发生变化，请重新打开",
                Map.of("path", path, "reason", "PREVIEW_CHANGED"));
    }
}
