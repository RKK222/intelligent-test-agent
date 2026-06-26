package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
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

    /**
     * 使用默认文件大小和目录项上限构造服务，适用于本地测试和未显式配置的运行环境。
     */
    public WorkspaceFileService() {
        this(1024 * 1024, 1000);
    }

    /**
     * 构造文件服务并校验安全上限；maxFileBytes 和 maxDirectoryEntries 必须为正数。
     */
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

    /**
     * 写入 rootPath 内的 UTF-8 文本文件；null content 按空文件处理，写入前会创建缺失父目录。
     */
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

    /**
     * 删除 rootPath 内的普通文件；目录删除不在文件 WebSocket v1 开放，避免误删目录树。
     */
    public void deleteFile(String rootPath, String relativePath) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        if (Files.isDirectory(target)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录删除暂不支持", Map.of("path", safePath(relativePath)));
        }
        if (!Files.isRegularFile(target)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        try {
            Files.delete(target);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "删除文件失败", Map.of("path", safePath(relativePath)), exception);
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

    /**
     * 将单个文件系统路径转换为目录列表项，大小字段仅对普通文件有效，目录大小固定为 0。
     */
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

    /**
     * 将相对路径解析到真实 root 内部；解析结果不在 root 下时拒绝访问，防止路径穿越。
     */
    private Path resolveInsideRoot(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        Path target = root.resolve(normalizeRelativePath(relativePath)).normalize();
        if (!target.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "文件路径超出工作区根目录", Map.of("path", safePath(relativePath)));
        }
        return target;
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

    /**
     * 返回可放入错误 details 的安全路径字符串，避免 null 进入统一错误响应。
     */
    private String safePath(String relativePath) {
        return relativePath == null ? "" : relativePath;
    }
}
