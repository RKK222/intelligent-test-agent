package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 服务器工作空间目录选择服务，仅服务 SUPER_ADMIN 跨服务器目录选择器。
 */
@Service
public class WorkspaceDirectoryService {

    private final int maxDirectoryEntries;

    /**
     * 绑定单次目录项上限，目录入口由目标服务器的 WebSocket ticket 控制。
     */
    @Autowired
    public WorkspaceDirectoryService(
            @Value("${test-agent.files.max-directory-entries:1000}") int maxDirectoryEntries) {
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        this.maxDirectoryEntries = maxDirectoryEntries;
    }

    /**
     * 超级管理员服务器工作空间选择器使用的目录浏览入口；默认从目标后端 Java 进程运行目录开始。
     */
    public WorkspaceDirectoryListResponse listServerDirectories(String path, String defaultPath) {
        Path directory = isBlank(path) ? realDirectory(defaultPath) : realDirectory(path);
        Path parent = directory.getParent();
        return listDirectoryEntries(directory, parent == null ? null : parent.toString());
    }

    /**
     * 列出目录的一层子目录并构造统一响应。
     */
    private WorkspaceDirectoryListResponse listDirectoryEntries(Path directory, String parentPath) {
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

    private String expandTilde(String path) {
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        if (path != null && path.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), path.substring(2)).toString();
        }
        return path;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
