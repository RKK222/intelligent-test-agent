package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 工作区目录选择服务，只允许在配置的本机根目录内浏览一层子目录，避免 Web UI 任意扫描磁盘。
 */
@Service
public class WorkspaceDirectoryService {

    private final List<String> allowedRootPaths;
    private final int maxDirectoryEntries;

    /**
     * 绑定目录选择器允许根目录和单次目录项上限；allowedRoots 为空时默认使用当前用户的 workspace 目录。
     */
    @Autowired
    public WorkspaceDirectoryService(
            @Value("${test-agent.workspace-picker.allowed-roots:}") String allowedRoots,
            @Value("${test-agent.files.max-directory-entries:1000}") int maxDirectoryEntries) {
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        List<String> parsedRoots = parseAllowedRoots(allowedRoots);
        this.allowedRootPaths = parsedRoots.isEmpty() ? List.of(defaultAllowedRoot()) : parsedRoots;
        this.maxDirectoryEntries = maxDirectoryEntries;
    }

    /**
     * 列出当前可选择目录的一层子目录；path 为空时进入第一个允许根目录。
     */
    public WorkspaceDirectoryListResponse listDirectories(String path) {
        Path directory = isBlank(path) ? realDirectory(allowedRootPaths.getFirst()) : realDirectory(path);
        Path allowedRoot = allowedRootFor(directory);
        String parentPath = parentInsideAllowedRoot(directory, allowedRoot);
        try (var stream = Files.list(directory)) {
            List<WorkspaceDirectoryEntryResponse> entries = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(child -> child.getFileName().toString()))
                    .limit(maxDirectoryEntries)
                    .map(child -> new WorkspaceDirectoryEntryResponse(
                            child.getFileName().toString(),
                            child.toAbsolutePath().normalize().toString()))
                    .toList();
            return new WorkspaceDirectoryListResponse(directory.toString(), parentPath, entries);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "读取可选工作区目录失败",
                    Map.of("path", directory.toString()),
                    exception);
        }
    }

    /**
     * 将候选目录解析为真实路径，并把缺失、不可访问或非目录统一归为请求参数错误。
     */
    private Path realDirectory(String path) {
        try {
            Path directory = Path.of(expandTilde(path)).toRealPath();
            if (!Files.isDirectory(directory)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录不存在或不可访问", Map.of("path", path));
            }
            return directory;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录不存在或不可访问", Map.of("path", path), exception);
        }
    }

    /**
     * 查找包含当前目录的允许根目录，找不到时拒绝访问，防止越权浏览。
     */
    private Path allowedRootFor(Path directory) {
        return allowedRootPaths.stream()
                .map(this::safeAllowedRoot)
                .filter(root -> root != null && (directory.equals(root) || directory.startsWith(root)))
                .findFirst()
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "目录超出允许的工作区选择范围",
                        Map.of("path", directory.toString())));
    }

    /**
     * 允许根目录可能被删除或不可访问，匹配时跳过这类无效配置，避免启动期直接失败。
     */
    private Path safeAllowedRoot(String rootPath) {
        try {
            Path root = Path.of(expandTilde(rootPath)).toRealPath();
            return Files.isDirectory(root) ? root : null;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 只允许返回仍在允许根目录内的父目录；根目录本身不提供向上跳转。
     */
    private String parentInsideAllowedRoot(Path directory, Path allowedRoot) {
        if (directory.equals(allowedRoot)) {
            return null;
        }
        Path parent = directory.getParent();
        return parent != null && (parent.equals(allowedRoot) || parent.startsWith(allowedRoot))
                ? parent.toString()
                : null;
    }

    private List<String> parseAllowedRoots(String allowedRoots) {
        if (isBlank(allowedRoots)) {
            return List.of();
        }
        return List.of(allowedRoots.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String expandTilde(String path) {
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        if (path != null && path.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), path.substring(2)).toString();
        }
        return path;
    }

    private String defaultAllowedRoot() {
        return Path.of(System.getProperty("user.home"), "workspace").toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
