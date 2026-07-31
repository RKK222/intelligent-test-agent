package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.Map;

/** 在固定内存上限内读取文件原始字节，供工作区和引用视图下载复用。 */
final class BinaryFileChunkReader {

    static final int CHUNK_BYTES = Utf8FilePreviewReader.CHUNK_BYTES;

    private BinaryFileChunkReader() {
    }

    /**
     * 按字节偏移读取一段 Base64；后续请求必须回传首段的大小和修改时间，禁止拼接不同版本。
     */
    static FileBinaryChunkResponse read(
            Path target,
            String responsePath,
            long offset,
            Long expectedSize,
            Long expectedLastModifiedMillis) {
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
                return new FileBinaryChunkResponse(
                        responsePath, "", offset, offset, size, true, modified);
            }

            int requestedBytes = (int) Math.min(CHUNK_BYTES, size - offset);
            ByteBuffer buffer = ByteBuffer.allocate(requestedBytes);
            try (SeekableByteChannel channel = Files.newByteChannel(target, StandardOpenOption.READ)) {
                channel.position(offset);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // SeekableByteChannel 允许短读，持续读取到本段缓冲区填满或 EOF。
                }
            }
            if (buffer.position() != requestedBytes) {
                throw downloadChanged(responsePath);
            }

            BasicFileAttributes after = Files.readAttributes(
                    target,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (after.size() != size || after.lastModifiedTime().toMillis() != modified) {
                throw downloadChanged(responsePath);
            }
            long nextOffset = offset + requestedBytes;
            return new FileBinaryChunkResponse(
                    responsePath,
                    Base64.getEncoder().encodeToString(buffer.array()),
                    offset,
                    nextOffset,
                    size,
                    nextOffset >= size,
                    modified);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "读取文件下载分段失败",
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
            throw downloadChanged(path);
        }
    }

    private static PlatformException invalidOffset(String path, long offset) {
        return new PlatformException(
                ErrorCode.VALIDATION_ERROR,
                "文件下载偏移无效",
                Map.of("path", path, "offset", offset));
    }

    private static PlatformException downloadChanged(String path) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "文件在下载期间发生变化，请重新下载",
                Map.of("path", path, "reason", "DOWNLOAD_CHANGED"));
    }
}
