package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
     * 把浏览器上传的 Base64 内容写入工作区新文件；拒绝覆盖已有条目，并沿用文本文件相同的大小上限。
     */
    public void uploadFile(String rootPath, String relativePath, String contentBase64) {
        Path target = resolveNewFileTarget(rootPath, relativePath);
        String encoded = contentBase64 == null ? "" : contentBase64;
        long maxEncodedLength = 4L * ((maxFileBytes + 2L) / 3L);
        if (encoded.length() > maxEncodedLength) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "上传文件超过大小限制",
                    Map.of("path", safePath(relativePath), "maxFileBytes", maxFileBytes));
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
        if (bytes.length > maxFileBytes) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "上传文件超过大小限制",
                    Map.of("path", safePath(relativePath), "maxFileBytes", maxFileBytes));
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
     * 在工作区内跨目录移动普通文件；源文件和目标路径都必须位于同一工作区，且不覆盖已有条目。
     */
    public void moveFile(String rootPath, String sourcePath, String targetPath) {
        Path source = requireRegularFile(rootPath, sourcePath);
        Path target = resolveInsideRoot(rootPath, targetPath);
        if (source.equals(target)) {
            return;
        }
        target = resolveNewFileTarget(rootPath, targetPath);
        try {
            Files.move(source, target);
        } catch (FileAlreadyExistsException exception) {
            throw targetConflict(targetPath, exception);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "移动文件失败",
                    Map.of("sourcePath", safePath(sourcePath), "targetPath", safePath(targetPath)),
                    exception);
        }
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
     * 删除 rootPath 内的普通文件；目录删除按平台文件安全规范拒绝。
     */
    public void deleteFile(String rootPath, String relativePath) {
        Path target = resolveInsideRoot(rootPath, relativePath);
        if (!Files.exists(target)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "文件不存在", Map.of("path", safePath(relativePath)));
        }
        if (!Files.isRegularFile(target)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "仅支持删除普通文件", Map.of("path", safePath(relativePath)));
        }
        try {
            Files.delete(target);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "删除失败", Map.of("path", safePath(relativePath)), exception);
        }
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
        Path target = root.resolve(normalizeRelativePath(relativePath)).normalize();
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
     * 校验新文件目标：不能是工作区根目录、不能覆盖已有条目，父目录必须真实存在于工作区内。
     */
    private Path resolveNewFileTarget(String rootPath, String relativePath) {
        Path root = rootRealPath(rootPath);
        Path target = resolveInsideRoot(rootPath, relativePath);
        if (target.equals(root) || target.getFileName() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目标文件路径不能为空", Map.of("path", safePath(relativePath)));
        }
        if (Files.exists(target)) {
            throw targetConflict(relativePath, null);
        }
        Path parent = target.getParent();
        try {
            if (parent == null || !Files.isDirectory(parent) || !parent.toRealPath().startsWith(root)) {
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
        if (cause == null) {
            return new PlatformException(
                    ErrorCode.CONFLICT,
                    "目标文件已存在",
                    Map.of("path", safePath(relativePath)));
        }
        return new PlatformException(
                ErrorCode.CONFLICT,
                "目标文件已存在",
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
