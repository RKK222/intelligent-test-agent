package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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

    private final long maxFileBytes;
    private final int maxDirectoryEntries;
    // 搜索相关配置
    private final int maxSearchResults;
    private final int maxSearchDepth;
    private final long searchTimeoutMillis;
    // 黑名单目录：搜索时跳过这些目录
    private static final Set<String> BLACKLISTED_DIRECTORIES = Set.of(
            ".git", "node_modules", ".idea", "target", "build", ".gradle", "__pycache__", ".venv", "venv"
    );

    /**
     * 使用默认文件大小和目录项上限构造服务，适用于本地测试和未显式配置的运行环境。
     */
    public WorkspaceFileService() {
        this(1024 * 1024, 1000, 200, 20, 5000L);
    }

    /**
     * 兼容测试路径：仅指定文件大小和目录项上限，搜索相关参数取默认值。
     */
    public WorkspaceFileService(long maxFileBytes, int maxDirectoryEntries) {
        this(maxFileBytes, maxDirectoryEntries, 200, 20, 5000L);
    }

    /**
     * 构造文件服务并校验安全上限；maxFileBytes 和 maxDirectoryEntries 必须为正数。
     */
    @Autowired
    public WorkspaceFileService(
            @Value("${test-agent.files.max-file-bytes:1048576}") long maxFileBytes,
            @Value("${test-agent.files.max-directory-entries:1000}") int maxDirectoryEntries,
            @Value("${test-agent.files.max-search-results:200}") int maxSearchResults,
            @Value("${test-agent.files.max-search-depth:20}") int maxSearchDepth,
            @Value("${test-agent.files.search-timeout-millis:5000}") long searchTimeoutMillis) {
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxDirectoryEntries = maxDirectoryEntries;
        this.maxSearchResults = maxSearchResults;
        this.maxSearchDepth = maxSearchDepth;
        this.searchTimeoutMillis = searchTimeoutMillis;
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

    /**
     * 在 rootPath 下递归搜索文件名包含 query（不区分大小写）的文件。
     * 忽略黑名单目录，结果按文件名排序，最多返回 maxSearchResults 条。
     * 搜索有超时保护，超时后返回已收集的结果。
     */
    public List<FileSearchResultResponse> searchFiles(String rootPath, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Path root = rootRealPath(rootPath);
        String normalizedQuery = query.trim().toLowerCase();
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
                // 跳过黑名单目录
                if (Files.isDirectory(path) && BLACKLISTED_DIRECTORIES.contains(name)) {
                    return;
                }
                if (Files.isDirectory(path)) {
                    // 递归搜索子目录
                    searchDirectory(root, path, query, results, depth + 1);
                } else if (Files.isRegularFile(path)) {
                    // 文件名匹配（不区分大小写）
                    if (name.toLowerCase().contains(query)) {
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
