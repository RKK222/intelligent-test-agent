package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
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
    private final WorkspaceRepository workspaceRepository;
    private final ManagedWorkspacePathResolver pathResolver;
    private final WorkspaceServerIdentity serverIdentity;

    /**
     * 绑定单次目录项上限，目录入口由目标服务器的 WebSocket ticket 控制。
     */
    @Autowired
    public WorkspaceDirectoryService(
            @Value("${test-agent.files.max-directory-entries:1000}") int maxDirectoryEntries,
            WorkspaceRepository workspaceRepository,
            ManagedWorkspacePathResolver pathResolver,
            WorkspaceServerIdentity serverIdentity) {
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        this.maxDirectoryEntries = maxDirectoryEntries;
        this.workspaceRepository = workspaceRepository;
        this.pathResolver = pathResolver;
        this.serverIdentity = serverIdentity;
    }

    /** 兼容既有单元测试，不执行已注册工作区标记。 */
    public WorkspaceDirectoryService(int maxDirectoryEntries) {
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        this.maxDirectoryEntries = maxDirectoryEntries;
        this.workspaceRepository = null;
        this.pathResolver = null;
        this.serverIdentity = null;
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
        Map<Path, String> registered = registeredWorkspaceIds();
        try (var stream = Files.list(directory)) {
            List<WorkspaceDirectoryEntryResponse> entries = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(child -> child.getFileName().toString()))
                    .limit(maxDirectoryEntries)
                    .map(child -> new WorkspaceDirectoryEntryResponse(
                            child.getFileName().toString(),
                            child.toAbsolutePath().normalize().toString(),
                            registered.get(realPathOrNormalized(child))))
                    .toList();
            return new WorkspaceDirectoryListResponse(
                    directory.toString(), parentPath, registered.get(realPathOrNormalized(directory)), entries);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "读取可选工作区目录失败",
                    Map.of("path", directory.toString()),
                    exception);
        }
    }

    /** 目录选择器直接返回已注册 ID，不再要求普通 Workspace API 暴露根路径供前端比对。 */
    private Map<Path, String> registeredWorkspaceIds() {
        if (workspaceRepository == null || pathResolver == null || serverIdentity == null) return Map.of();
        Map<Path, String> result = new HashMap<>();
        int page = 1;
        while (page <= 100) {
            var workspaces = workspaceRepository.findPage(new PageRequest(page, 200));
            for (Workspace workspace : workspaces.items()) {
                if (workspace.linuxServerId() != null
                        && !serverIdentity.linuxServerId().equals(workspace.linuxServerId())) continue;
                try {
                    result.put(realPathOrNormalized(pathResolver.resolve(workspace.rootPath())), workspace.workspaceId().value());
                } catch (RuntimeException ignored) {
                    // 配置缺失或目录已移除的旧记录不能阻断管理员浏览其它目录。
                }
            }
            if ((long) page * workspaces.size() >= workspaces.total() || workspaces.items().isEmpty()) break;
            page++;
        }
        return result;
    }

    private Path realPathOrNormalized(Path path) {
        try {
            return path.toRealPath();
        } catch (Exception ignored) {
            return path.toAbsolutePath().normalize();
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
