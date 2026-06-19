package com.example.testagent.workspace;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Workspace 文件访问服务，集中做 root 归一化和越权路径拦截，Controller 不直接操作文件系统。
 */
@Service
public class WorkspaceFileService {

    private final long maxFileBytes;
    private final int maxDirectoryEntries;

    public WorkspaceFileService() {
        this(1024 * 1024, 1000);
    }

    @Autowired
    public WorkspaceFileService(
            @Value("${test-agent.files.max-file-bytes:1048576}") long maxFileBytes,
            @Value("${test-agent.files.max-directory-entries:1000}") int maxDirectoryEntries) {
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxDirectoryEntries = maxDirectoryEntries;
    }

    public FileContentResponse readContent(String rootPath, String relativePath) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        if (!Files.isRegularFile(target)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        try {
            long size = Files.size(target);
            if (size > maxFileBytes) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "文件超过读取大小限制",
                        Map.of("path", safePath(relativePath), "maxFileBytes", maxFileBytes));
            }
            return new FileContentResponse(normalizeRelativePath(relativePath), Files.readString(target), size);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取文件失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    public void writeContent(String rootPath, String relativePath, String content) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxFileBytes) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "文件超过写入大小限制",
                    Map.of("path", safePath(relativePath), "maxFileBytes", maxFileBytes));
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

    public List<FileTreeEntryResponse> listDirectory(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        Path directory = resolveInsideRoot(rootPath, relativePath);
        if (!Files.isDirectory(directory)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "目录不存在", Map.of("path", safePath(relativePath)));
        }
        try (var stream = Files.list(directory)) {
            return stream.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(maxDirectoryEntries)
                    .map(path -> entry(root, path))
                    .toList();
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取目录失败", Map.of("path", safePath(relativePath)), exception);
        }
    }

    private FileTreeEntryResponse entry(Path root, Path path) {
        try {
            return new FileTreeEntryResponse(
                    root.relativize(path).toString(),
                    path.getFileName().toString(),
                    Files.isDirectory(path),
                    Files.isDirectory(path) ? 0L : Files.size(path),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取目录项失败", Map.of("path", path.getFileName().toString()), exception);
        }
    }

    private Path resolveInsideRoot(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        Path target = root.resolve(normalizeRelativePath(relativePath)).normalize();
        if (!target.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "文件路径超出工作区根目录", Map.of("path", safePath(relativePath)));
        }
        return target;
    }

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

    private String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        return relativePath.replace('\\', '/');
    }

    private String safePath(String relativePath) {
        return relativePath == null ? "" : relativePath;
    }
}
