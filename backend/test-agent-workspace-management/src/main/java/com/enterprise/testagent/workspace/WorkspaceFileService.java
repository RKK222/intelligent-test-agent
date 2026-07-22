package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Workspace 文件访问服务，集中做 root 归一化和越权路径拦截，Controller 不直接操作文件系统。
 */
@Service
public class WorkspaceFileService {

    private static final String FILE_TARGET_EXISTS_MESSAGE = "目标文件已存在";
    private static final String MOVE_TARGET_EXISTS_MESSAGE = "目标文件或目录已存在";
    private static final String UPLOAD_TEMP_PREFIX = ".test-agent-upload-";
    private static final String UPLOAD_TEMP_SUFFIX = ".part";
    private static final int DEFAULT_UPLOAD_CHUNK_BYTES = 256 * 1024;
    private static final int MAX_UPLOAD_CHUNK_BYTES = 4 * 1024 * 1024;
    private static final Duration STALE_UPLOAD_AGE = Duration.ofHours(24);

    private final long maxPreviewBytes;
    private final int uploadChunkBytes;
    private final int maxDirectoryEntries;
    // 搜索相关配置
    private final int maxSearchResults;
    private final int maxSearchDepth;
    private final long searchTimeoutMillis;
    private final Runnable beforeSecureMove;
    // 黑名单目录：搜索时跳过这些目录
    private static final Set<String> BLACKLISTED_DIRECTORIES = Set.of(
            ".git", "node_modules", ".idea", "target", "build", ".gradle", "__pycache__", ".venv", "venv"
    );

    /**
     * 使用默认文件大小和目录项上限构造服务，适用于本地测试和未显式配置的运行环境。
     */
    public WorkspaceFileService() {
        this(5L * 1024L * 1024L, 1000, 200, 20, 5000L, DEFAULT_UPLOAD_CHUNK_BYTES, () -> {});
    }

    /**
     * 兼容测试路径：仅指定文件大小和目录项上限，搜索相关参数取默认值。
     */
    public WorkspaceFileService(long maxPreviewBytes, int maxDirectoryEntries) {
        this(maxPreviewBytes, maxDirectoryEntries, 200, 20, 5000L, DEFAULT_UPLOAD_CHUNK_BYTES, () -> {});
    }

    /**
     * 测试专用构造器，在最终安全移动前注入一次文件系统变化，用于验证路径竞态必须失败关闭。
     */
    WorkspaceFileService(long maxPreviewBytes, int maxDirectoryEntries, Runnable beforeSecureMove) {
        this(maxPreviewBytes, maxDirectoryEntries, 200, 20, 5000L, DEFAULT_UPLOAD_CHUNK_BYTES, beforeSecureMove);
    }

    /**
     * 构造文件服务并校验预览、分片和目录项安全上限。
     */
    @Autowired
    public WorkspaceFileService(
            @Value("${test-agent.files.max-preview-bytes:${test-agent.files.max-file-bytes:5242880}}") long maxPreviewBytes,
            @Value("${test-agent.files.max-directory-entries:1000}") int maxDirectoryEntries,
            @Value("${test-agent.files.max-search-results:200}") int maxSearchResults,
            @Value("${test-agent.files.max-search-depth:20}") int maxSearchDepth,
            @Value("${test-agent.files.search-timeout-millis:5000}") long searchTimeoutMillis,
            @Value("${test-agent.files.upload-chunk-bytes:262144}") int uploadChunkBytes) {
        this(maxPreviewBytes, maxDirectoryEntries, maxSearchResults, maxSearchDepth, searchTimeoutMillis, uploadChunkBytes, () -> {});
    }

    private WorkspaceFileService(
            long maxPreviewBytes,
            int maxDirectoryEntries,
            int maxSearchResults,
            int maxSearchDepth,
            long searchTimeoutMillis,
            int uploadChunkBytes,
            Runnable beforeSecureMove) {
        if (maxPreviewBytes < 1) {
            throw new IllegalArgumentException("maxPreviewBytes must be positive");
        }
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        if (uploadChunkBytes < 1 || uploadChunkBytes > MAX_UPLOAD_CHUNK_BYTES) {
            throw new IllegalArgumentException("uploadChunkBytes must be between 1 and 4194304");
        }
        this.maxPreviewBytes = maxPreviewBytes;
        this.uploadChunkBytes = uploadChunkBytes;
        this.maxDirectoryEntries = maxDirectoryEntries;
        this.maxSearchResults = maxSearchResults;
        this.maxSearchDepth = maxSearchDepth;
        this.searchTimeoutMillis = searchTimeoutMillis;
        this.beforeSecureMove = beforeSecureMove == null ? () -> {} : beforeSecureMove;
    }

    /**
     * 读取 rootPath 内的 UTF-8 文本文件；拒绝目录、缺失文件、越权路径和超过大小上限的文件。
     */
    public FileContentResponse readContent(String rootPath, String relativePath) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        if (!Files.isRegularFile(target)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        try {
            long size = Files.size(target);
            if (size > maxPreviewBytes) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "文件超过预览大小限制",
                        Map.of(
                                "path", safePath(relativePath),
                                "size", size,
                                "maxPreviewBytes", maxPreviewBytes,
                                "reason", "PREVIEW_TOO_LARGE"));
            }
            return new FileContentResponse(normalizeRelativePath(relativePath), Files.readString(target), size);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取文件失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    /**
     * 分段读取超大 UTF-8 文件；不限制最终预览总量，每次只在固定内存中读取一段。
     */
    public FilePreviewChunkResponse readContentChunk(
            String rootPath,
            String relativePath,
            long offset,
            Long expectedSize,
            Long expectedLastModifiedMillis) {
        Path target = resolveReadablePreviewFile(rootPath, relativePath);
        return Utf8FilePreviewReader.read(
                target,
                normalizeRelativePath(relativePath),
                offset,
                expectedSize,
                expectedLastModifiedMillis,
                maxPreviewBytes);
    }

    /** 渐进预览不得通过末端或中间符号链接逃逸工作区真实根目录。 */
    private Path resolveReadablePreviewFile(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        Path target = resolveInsideRoot(rootPath, relativePath);
        try {
            if (Files.isSymbolicLink(target)) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "大文件预览不支持符号链接",
                        Map.of("path", safePath(relativePath)));
            }
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(root)) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "文件路径超出工作区根目录",
                        Map.of("path", safePath(relativePath)));
            }
            return realTarget;
        } catch (PlatformException exception) {
            throw exception;
        } catch (NoSuchFileException exception) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "文件不存在",
                    Map.of("path", safePath(relativePath)),
                    exception);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "文件路径无法安全解析",
                    Map.of("path", safePath(relativePath)),
                    exception);
        }
    }

    /**
     * 写入 rootPath 内的 UTF-8 文本文件；null content 按空文件处理，写入前会创建缺失父目录。
     */
    public void writeContent(String rootPath, String relativePath, String content) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxPreviewBytes) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "文件超过可编辑大小限制",
                    Map.of("path", safePath(relativePath), "maxPreviewBytes", maxPreviewBytes));
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "写入文件失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    /**
     * 兼容旧客户端的一次性 Base64 上传；新客户端应使用 beginUpload 分片上传。
     * 旧操作仍受预览大小约束，避免单条 WebSocket 消息重新占用无界内存。
     */
    public void uploadFile(String rootPath, String relativePath, String contentBase64) {
        Path target = resolveNewFileTarget(rootPath, relativePath);
        String encoded = contentBase64 == null ? "" : contentBase64;
        long maxEncodedLength = 4L * ((maxPreviewBytes + 2L) / 3L);
        if (encoded.length() > maxEncodedLength) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "旧版上传文件超过预览大小限制，请使用分片上传",
                    Map.of("path", safePath(relativePath), "maxPreviewBytes", maxPreviewBytes));
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "上传文件内容不是有效的 Base64",
                    Map.of("path", safePath(relativePath)),
                    exception);
        }
        if (bytes.length > maxPreviewBytes) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "旧版上传文件超过预览大小限制，请使用分片上传",
                    Map.of("path", safePath(relativePath), "maxPreviewBytes", maxPreviewBytes));
        }
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException exception) {
            throw targetConflict(relativePath, exception);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "上传文件失败",
                    Map.of("path", safePath(relativePath)),
                    exception);
        }
    }

    /**
     * 创建不限制文件总大小的分片上传会话。声明大小只用于完成时校验；每次调用仅解码一个有界分片。
     */
    public WorkspaceFileUpload beginUpload(String rootPath, String relativePath, long expectedBytes) {
        if (expectedBytes < 0) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "上传文件大小无效",
                    Map.of("path", safePath(relativePath), "size", expectedBytes));
        }
        Path target = resolveNewFileTarget(rootPath, relativePath);
        Path realRoot = rootRealPath(rootPath);
        Path temporary = null;
        try {
            Path realParent = target.getParent().toRealPath();
            if (!realParent.startsWith(realRoot)) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "文件路径超出工作区根目录",
                        Map.of("path", safePath(relativePath)));
            }
            cleanupStaleUploads(realParent);
            temporary = Files.createTempFile(realParent, UPLOAD_TEMP_PREFIX, UPLOAD_TEMP_SUFFIX).toRealPath();
            OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.WRITE);
            Path realTarget = realParent.resolve(target.getFileName()).normalize();
            return new StreamingWorkspaceFileUpload(
                    realRoot,
                    temporary,
                    realTarget,
                    normalizeRelativePath(relativePath),
                    expectedBytes,
                    output);
        } catch (PlatformException exception) {
            deleteUploadTemporaryQuietly(temporary);
            throw exception;
        } catch (Exception exception) {
            deleteUploadTemporaryQuietly(temporary);
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "创建分片上传失败",
                    Map.of("path", safePath(relativePath)),
                    exception);
        }
    }

    /** 创建上传会话中途失败时删除已生成的临时文件，避免等待过期清理。 */
    private void deleteUploadTemporaryQuietly(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (Exception ignored) {
            // 删除失败仍会由过期清理兜底，且临时文件始终对目录和搜索接口隐藏。
        }
    }

    /** 超过一天的同目录上传残留可安全清理；活跃连接使用的新文件不会命中该窗口。 */
    private void cleanupStaleUploads(Path directory) {
        Instant cutoff = Instant.now().minus(STALE_UPLOAD_AGE);
        try (var stream = Files.list(directory)) {
            stream.filter(this::isUploadTemporaryFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);
                        } catch (Exception ignored) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // 清理失败不应阻断新的上传；文件仍会继续被目录与搜索接口隐藏。
                        }
                    });
        } catch (Exception ignored) {
            // 同上，残留清理是尽力而为，上传主流程仍由临时文件与连接关闭清理保证。
        }
    }

    /**
     * 单个上传对象只持有一个有界分片和一个临时文件流；所有状态方法同步，避免关闭连接与完成请求竞态。
     */
    private final class StreamingWorkspaceFileUpload implements WorkspaceFileUpload {

        private final Path realRoot;
        private final Path temporary;
        private final Path target;
        private final String relativePath;
        private final long expectedBytes;
        private OutputStream output;
        private long uploadedBytes;
        private long nextChunkIndex;
        private boolean finished;

        private StreamingWorkspaceFileUpload(
                Path realRoot,
                Path temporary,
                Path target,
                String relativePath,
                long expectedBytes,
                OutputStream output) {
            this.realRoot = realRoot;
            this.temporary = temporary;
            this.target = target;
            this.relativePath = relativePath;
            this.expectedBytes = expectedBytes;
            this.output = output;
        }

        @Override
        public int chunkBytes() {
            return uploadChunkBytes;
        }

        @Override
        public long expectedBytes() {
            return expectedBytes;
        }

        @Override
        public synchronized long uploadedBytes() {
            return uploadedBytes;
        }

        @Override
        public synchronized void append(long index, String contentBase64) {
            requireOpen();
            if (index != nextChunkIndex) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "上传分片序号不连续",
                        Map.of("path", relativePath, "expectedIndex", nextChunkIndex, "actualIndex", index));
            }
            String encoded = contentBase64 == null ? "" : contentBase64;
            long maxEncodedLength = 4L * ((uploadChunkBytes + 2L) / 3L);
            if (encoded.length() > maxEncodedLength) {
                throw chunkTooLarge();
            }
            final byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException exception) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "上传分片不是有效的 Base64",
                        Map.of("path", relativePath, "index", index),
                        exception);
            }
            if (bytes.length == 0) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "上传分片不能为空",
                        Map.of("path", relativePath, "index", index));
            }
            if (bytes.length > uploadChunkBytes) {
                throw chunkTooLarge();
            }
            if (uploadedBytes > expectedBytes || bytes.length > expectedBytes - uploadedBytes) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "上传内容超过声明的文件大小",
                        Map.of(
                                "path", relativePath,
                                "uploadedBytes", uploadedBytes,
                                "chunkBytes", bytes.length,
                                "expectedBytes", expectedBytes));
            }
            try {
                output.write(bytes);
                uploadedBytes += bytes.length;
                nextChunkIndex++;
            } catch (IOException exception) {
                abort();
                throw new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "写入上传分片失败",
                        Map.of("path", relativePath, "index", index),
                        exception);
            }
        }

        @Override
        public synchronized long complete() {
            requireOpen();
            if (uploadedBytes != expectedBytes) {
                long actualBytes = uploadedBytes;
                abort();
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "上传文件大小与声明不一致",
                        Map.of("path", relativePath, "expectedBytes", expectedBytes, "uploadedBytes", actualBytes));
            }
            try {
                output.close();
                output = null;
                beforeSecureMove.run();
                SecureWorkspaceMover.move(realRoot, temporary, target);
                finished = true;
                return uploadedBytes;
            } catch (FileAlreadyExistsException exception) {
                throw targetConflict(relativePath, exception);
            } catch (NoSuchFileException exception) {
                throw new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "上传目标目录不存在",
                        Map.of("path", relativePath),
                        exception);
            } catch (FileSystemException exception) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "上传路径发生变化或包含符号链接",
                        Map.of("path", relativePath),
                        exception);
            } catch (PlatformException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "完成分片上传失败",
                        Map.of("path", relativePath),
                        exception);
            } finally {
                if (!finished) {
                    closeAndDeleteTemporary();
                    finished = true;
                }
            }
        }

        @Override
        public synchronized void abort() {
            if (finished) {
                return;
            }
            closeAndDeleteTemporary();
            finished = true;
        }

        private void requireOpen() {
            if (finished || output == null) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "上传会话已结束",
                        Map.of("path", relativePath));
            }
        }

        private PlatformException chunkTooLarge() {
            return new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "上传分片超过大小限制",
                    Map.of("path", relativePath, "maxChunkBytes", uploadChunkBytes));
        }

        private void closeAndDeleteTemporary() {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                    // 继续删除临时文件；中止操作必须保持幂等并尽量完成清理。
                }
                output = null;
            }
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
                // 超过一天的残留会在同目录下一次 begin 时再次清理。
            }
        }
    }

    /**
     * 在工作区内复制普通文件到新的相对路径；不覆盖目标文件，目录复制不在本接口范围内。
     */
    public void copyFile(String rootPath, String sourcePath, String targetPath) {
        Path source = requireRegularFile(rootPath, sourcePath);
        Path target = resolveNewFileTarget(rootPath, targetPath);
        try {
            Files.copy(source, target);
        } catch (FileAlreadyExistsException exception) {
            throw targetConflict(targetPath, exception);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "复制文件失败",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)),
                    exception);
        }
    }

    /**
     * 在工作区内跨目录移动普通文件或目录；源和目标必须位于同一工作区，且不覆盖已有条目。
     */
    public void moveFile(String rootPath, String sourcePath, String targetPath) {
        Path source = requireMovableSource(rootPath, sourcePath);
        Path target = resolveInsideRoot(rootPath, targetPath);
        if (source.equals(target)) {
            return;
        }
        // 目录不能移动到自身后代，否则底层重命名会产生依赖文件系统实现的异常。
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && target.startsWith(source)) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "目标目录不能位于源目录内部",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)));
        }
        target = resolveNewMoveTarget(rootPath, targetPath);
        Path realRoot = rootRealPath(rootPath);
        Path realSource;
        Path realTarget;
        try {
            // 目标父目录可能经工作区内符号链接落入源目录后代，移动必须使用本次校验得到的真实路径。
            realSource = source.toRealPath();
            realTarget = target.getParent().toRealPath().resolve(target.getFileName()).normalize();
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "解析移动目标失败",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)),
                    exception);
        }
        if (!realSource.startsWith(realRoot) || !realTarget.startsWith(realRoot)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "文件路径超出工作区根目录",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)));
        }
        if (Files.isDirectory(realSource, LinkOption.NOFOLLOW_LINKS) && realTarget.startsWith(realSource)) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "目标目录不能位于源目录内部",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)));
        }
        try {
            moveAtomicallyWithDirectoryDescriptors(realRoot, realSource, realTarget, sourcePath, targetPath);
        } catch (FileAlreadyExistsException exception) {
            throw targetConflict(targetPath, exception, MOVE_TARGET_EXISTS_MESSAGE);
        } catch (NoSuchFileException exception) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "文件或目标目录不存在",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)),
                    exception);
        } catch (FileSystemException exception) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "移动路径发生变化或包含符号链接",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)),
                    exception);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "移动文件失败",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)),
                    exception);
        }
    }

    /**
     * 相对已打开的工作区目录句柄执行原子移动，目标父目录即使在校验后被替换为符号链接也不会越界。
     */
    private void moveAtomicallyWithDirectoryDescriptors(
            Path realRoot,
            Path realSource,
            Path realTarget,
            String sourcePath,
            String targetPath) throws IOException {
        if (realSource.getParent() == null || realTarget.getParent() == null) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "移动路径缺少父目录",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)));
        }
        beforeSecureMove.run();
        SecureWorkspaceMover.move(realRoot, realSource, realTarget);
    }

    /**
     * 在同一父目录内重命名普通文件或目录；新名称只允许是单个名称，避免借此接口移动条目或穿越工作区。
     */
    public void renameFile(String rootPath, String relativePath, String newName) {
        String normalizedName = newName == null ? "" : newName.trim();
        if (normalizedName.isBlank()
                || ".".equals(normalizedName)
                || "..".equals(normalizedName)
                || normalizedName.contains("/")
                || normalizedName.contains("\\")) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "文件名无效",
                    Map.of("path", safePath(relativePath), "name", normalizedName));
        }

        Path source = resolveInsideRoot(rootPath, relativePath);
        if (!Files.exists(source)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        if (!Files.isRegularFile(source) && !Files.isDirectory(source)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "仅支持重命名普通文件或目录", Map.of("path", safePath(relativePath)));
        }
        if (normalizedName.equals(source.getFileName().toString())) {
            return;
        }

        Path root = rootRealPath(rootPath);
        Path target = source.resolveSibling(normalizedName).normalize();
        if (!target.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "文件路径超出工作区根目录", Map.of("name", normalizedName));
        }
        if (Files.exists(target)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "目标文件或目录已存在",
                    Map.of("path", safePath(relativePath), "name", normalizedName));
        }
        try {
            Files.move(source, target);
        } catch (FileAlreadyExistsException exception) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "目标文件或目录已存在",
                    Map.of("path", safePath(relativePath), "name", normalizedName),
                    exception);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "重命名文件或目录失败",
                    Map.of("path", safePath(relativePath), "name", normalizedName),
                    exception);
        }
    }

    /**
     * 删除 rootPath 内的普通文件或目录树；拒绝删除工作区根目录，遍历目录时不跟随符号链接。
     */
    public void deleteFile(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        Path target = resolveInsideRoot(rootPath, relativePath);
        if (target.equals(root)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "禁止删除工作区根目录", Map.of("path", safePath(relativePath)));
        }
        if (containsGitMetadataSegment(relativePath)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "禁止删除版本库元数据", Map.of("path", safePath(relativePath)));
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        try {
            if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(target);
                return;
            }
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws java.io.IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, java.io.IOException exception) throws java.io.IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "删除失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    /**
     * 目录递归删除不得触碰任意层级的 .git 元数据，避免个人 worktree 因一次文件操作失去版本库结构。
     */
    private boolean containsGitMetadataSegment(String relativePath) {
        return List.of(normalizeRelativePath(relativePath).split("/"))
                .stream()
                .anyMatch(".git"::equals);
    }

    /**
     * 在 rootPath 内创建目录；已存在时不报错，不存在时递归创建父目录。
     */
    public void createDirectory(String rootPath, String relativePath) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        try {
            Files.createDirectories(target);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建目录失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    /**
     * 查询 rootPath 内文件或目录状态；目标不存在时返回 exists=false，其他 IO 错误统一转为平台内部错误。
     */
    public FileStatusResponse status(String rootPath, String relativePath) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        try {
            if (!Files.exists(target)) {
                return new FileStatusResponse(normalizeRelativePath(relativePath), false, false, 0L, null);
            }
            return new FileStatusResponse(
                    normalizeRelativePath(relativePath),
                    true,
                    Files.isDirectory(target),
                    Files.isDirectory(target) ? 0L : Files.size(target),
                    Files.getLastModifiedTime(target).toInstant());
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取文件状态失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    /**
     * 列出指定目录的一层子项，按文件名排序并限制最大返回数量，避免递归扫描大仓库。
     */
    public List<FileTreeEntryResponse> listDirectory(String rootPath, String relativePath) {
        return listDirectory(rootPath, relativePath, maxDirectoryEntries);
    }

    /**
     * 组合视图需要在多来源归并后统一应用上限，因此允许调用方多取一项用于精确判断 truncated。
     */
    public List<FileTreeEntryResponse> listDirectory(String rootPath, String relativePath, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Path root = rootRealPath(rootPath);
        Path directory = resolveInsideRoot(rootPath, relativePath);
        if (!Files.isDirectory(directory)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "目录不存在", Map.of("path", safePath(relativePath)));
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> !isUploadTemporaryFile(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(limit)
                    .map(path -> entry(root, path))
                    .toList();
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取目录失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    /**
     * 将单个文件系统路径转换为目录列表项，大小字段仅对普通文件有效，目录大小固定为 0。
     */
    private FileTreeEntryResponse entry(Path root, Path path) {
        try {
            boolean directory = Files.isDirectory(path);
            return new FileTreeEntryResponse(
                    root.relativize(path).toString(),
                    path.getFileName().toString(),
                    directory,
                    directory ? 0L : Files.size(path),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取目录项失败", Map.of("path", path.getFileName().toString()), exception);
        }
    }

    /**
     * 将相对路径解析到真实 root 内部；解析结果不在 root 下时拒绝访问，防止路径穿越。
     */
    private Path resolveInsideRoot(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        String normalizedPath = normalizeRelativePath(relativePath);
        if (containsUploadTemporarySegment(normalizedPath)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "文件路径属于平台上传临时区",
                    Map.of("path", safePath(relativePath)));
        }
        Path target = root.resolve(normalizedPath).normalize();
        if (!target.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "文件路径超出工作区根目录", Map.of("path", safePath(relativePath)));
        }
        return target;
    }

    /**
     * 校验源路径存在且为普通文件，避免复制、移动接口意外递归处理目录树或特殊文件。
     */
    private Path requireRegularFile(String rootPath, String relativePath) {
        Path source = resolveInsideRoot(rootPath, relativePath);
        if (!Files.exists(source)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "仅支持复制或移动普通文件", Map.of("path", safePath(relativePath)));
        }
        return source;
    }

    /**
     * 校验移动源必须是工作区内的普通文件或普通目录；符号链接和特殊文件均不跟随，避免越界或意外操作设备文件。
     */
    private Path requireMovableSource(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        Path source = resolveInsideRoot(rootPath, relativePath);
        if (source.equals(root)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "禁止移动工作区根目录", Map.of("path", safePath(relativePath)));
        }
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "仅支持移动普通文件或目录",
                    Map.of("path", safePath(relativePath)));
        }
        try {
            // 中间路径可以含有符号链接，但源条目的真实路径仍必须留在工作区 root 内。
            if (!source.toRealPath().startsWith(root)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "文件路径超出工作区根目录", Map.of("path", safePath(relativePath)));
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "文件路径超出工作区根目录",
                    Map.of("path", safePath(relativePath)),
                    exception);
        }
        return source;
    }

    /**
     * 校验新文件目标：不能是工作区根目录、不能覆盖已有条目，父目录必须真实存在于工作区内。
     */
    private Path resolveNewFileTarget(String rootPath, String relativePath) {
        return resolveNewTarget(rootPath, relativePath, false, false, FILE_TARGET_EXISTS_MESSAGE);
    }

    /**
     * 校验移动目标：目标条目按不跟随符号链接方式判断存在，且真实父目录越界时必须拒绝移动。
     */
    private Path resolveNewMoveTarget(String rootPath, String relativePath) {
        return resolveNewTarget(rootPath, relativePath, true, true, MOVE_TARGET_EXISTS_MESSAGE);
    }

    /**
     * 统一校验新目标，保留上传/复制的既有错误语义，并允许移动操作强化符号链接边界校验。
     */
    private Path resolveNewTarget(
            String rootPath,
            String relativePath,
            boolean targetExistsWithoutFollowingLinks,
            boolean rejectExternalRealParent,
            String targetExistsMessage) {
        Path root = rootRealPath(rootPath);
        Path target = resolveInsideRoot(rootPath, relativePath);
        if (target.equals(root) || target.getFileName() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目标文件路径不能为空", Map.of("path", safePath(relativePath)));
        }
        boolean targetExists = targetExistsWithoutFollowingLinks
                ? Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                : Files.exists(target);
        if (targetExists) {
            throw targetConflict(relativePath, null, targetExistsMessage);
        }
        Path parent = target.getParent();
        try {
            if (parent == null || !Files.isDirectory(parent)) {
                throw new PlatformException(ErrorCode.NOT_FOUND, "目标目录不存在", Map.of("path", safePath(relativePath)));
            }
            // 父目录可能是符号链接；移动必须拒绝借此写出工作区，复制/上传保持既有 NOT_FOUND 语义。
            if (!parent.toRealPath().startsWith(root)) {
                if (rejectExternalRealParent) {
                    throw new PlatformException(ErrorCode.FORBIDDEN, "文件路径超出工作区根目录", Map.of("path", safePath(relativePath)));
                }
                throw new PlatformException(ErrorCode.NOT_FOUND, "目标目录不存在", Map.of("path", safePath(relativePath)));
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目标目录不存在", Map.of("path", safePath(relativePath)), exception);
        }
        return target;
    }

    /**
     * 统一构造不覆盖目标条目的冲突错误，details 只返回工作区相对路径。
     */
    private PlatformException targetConflict(String relativePath, Exception cause) {
        return targetConflict(relativePath, cause, FILE_TARGET_EXISTS_MESSAGE);
    }

    /**
     * 按具体文件操作构造冲突错误，避免移动目录的文案改变上传和复制的既有契约。
     */
    private PlatformException targetConflict(String relativePath, Exception cause, String message) {
        if (cause == null) {
            return new PlatformException(
                    ErrorCode.CONFLICT,
                    message,
                    Map.of("path", safePath(relativePath)));
        }
        return new PlatformException(
                ErrorCode.CONFLICT,
                message,
                Map.of("path", safePath(relativePath)),
                cause);
    }

    /**
     * 解析并校验工作区根目录真实路径，确保根目录存在且是目录。
     */
    private Path rootRealPath(String rootPath) {
        try {
            Path root = Path.of(rootPath).toRealPath();
            if (!Files.isDirectory(root)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作区根目录不存在", Map.of("rootPath", rootPath));
            }
            return root;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作区根目录不存在", Map.of("rootPath", rootPath), exception);
        }
    }

    /**
     * 统一相对路径分隔符；空路径表示工作区根目录。
     */
    private String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        return relativePath.replace('\\', '/');
    }

    /** 平台隐藏上传文件名为保留命名空间，不能由普通文件 RPC 读取或覆盖。 */
    private boolean containsUploadTemporarySegment(String relativePath) {
        for (String segment : relativePath.split("/")) {
            if (isUploadTemporaryName(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUploadTemporaryFile(Path path) {
        return path.getFileName() != null && isUploadTemporaryName(path.getFileName().toString());
    }

    private boolean isUploadTemporaryName(String name) {
        return name.startsWith(UPLOAD_TEMP_PREFIX) && name.endsWith(UPLOAD_TEMP_SUFFIX);
    }

    /**
     * 返回可放入错误 details 的安全路径字符串，避免 null 进入统一错误响应。
     */
    private String safePath(String relativePath) {
        return relativePath == null ? "" : relativePath;
    }

    /**
     * 在 rootPath 下递归搜索相对路径包含 query（不区分大小写）的文件。
     * 空关键字用于对话 @ 文件候选，仍受深度、数量和超时上限保护；忽略黑名单目录，
     * 结果按文件名排序，最多返回 maxSearchResults 条。
     */
    public List<FileSearchResultResponse> searchFiles(String rootPath, String query) {
        Path root = rootRealPath(rootPath);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<FileSearchResultResponse> results = new ArrayList<>();

        // 使用 CompletableFuture 实现超时保护
        CompletableFuture<Void> searchFuture = CompletableFuture.runAsync(() -> {
            searchDirectory(root, root, normalizedQuery, results, 0);
        });

        try {
            searchFuture.get(searchTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            // 超时后取消搜索，返回已收集的结果
            searchFuture.cancel(true);
        } catch (Exception exception) {
            // 其他异常（中断、执行异常）也返回已收集的结果
        }

        // 按文件名排序
        results.sort(Comparator.comparing(FileSearchResultResponse::name));
        // 限制返回数量
        if (results.size() > maxSearchResults) {
            return results.subList(0, maxSearchResults);
        }
        return results;
    }

    /**
     * 递归搜索目录，收集匹配的文件。
     */
    private void searchDirectory(
            Path root, Path directory, String query, List<FileSearchResultResponse> results, int depth) {
        // 超过深度限制或结果已满，停止搜索
        if (depth > maxSearchDepth || results.size() >= maxSearchResults) {
            return;
        }
        try (var stream = Files.list(directory)) {
            stream.forEach(path -> {
                // 结果已满，停止处理
                if (results.size() >= maxSearchResults) {
                    return;
                }
                String name = path.getFileName().toString();
                if (isUploadTemporaryFile(path)) {
                    return;
                }
                // 跳过黑名单目录
                if (Files.isDirectory(path) && BLACKLISTED_DIRECTORIES.contains(name)) {
                    return;
                }
                if (Files.isDirectory(path)) {
                    // 递归搜索子目录
                    searchDirectory(root, path, query, results, depth + 1);
                } else if (Files.isRegularFile(path)) {
                    // 匹配工作区相对路径，使对话 # 能按 01-需求/子条目结构检索真实文件。
                    String relativePath = root.relativize(path).toString().replace('\\', '/');
                    if (relativePath.toLowerCase().contains(query)) {
                        results.add(searchResultEntry(root, path));
                    }
                }
            });
        } catch (Exception exception) {
            // 目录读取失败，跳过该目录继续搜索其他目录
        }
    }

    /**
     * 将单个匹配文件转换为搜索结果条目。
     */
    private FileSearchResultResponse searchResultEntry(Path root, Path path) {
        try {
            String relativePath = root.relativize(path).toString().replace('\\', '/');
            String name = path.getFileName().toString();
            // 父目录相对路径
            String directory = "";
            int lastSlash = relativePath.lastIndexOf('/');
            if (lastSlash > 0) {
                directory = relativePath.substring(0, lastSlash);
            }
            return new FileSearchResultResponse(
                    relativePath, name, directory,
                    Files.size(path),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取搜索结果文件信息失败", Map.of("path", path.getFileName().toString()), exception);
        }
    }
}
