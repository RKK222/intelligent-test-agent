package com.icbc.testagent.workspace;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Workspace 应用服务，负责工作区注册、查询和文件服务编排，避免 Controller 直接访问 Repository 或文件系统。
 */
@Service
public class WorkspaceApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceApplicationService.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceFileService fileService;
    private final WorkspaceServerIdentity serverIdentity;
    private final ManagedWorkspacePathResolver pathResolver;

    /**
     * 构造 Workspace 应用服务，注入领域 Repository 端口和文件服务，避免 Controller 直接访问底层资源。
     */
    @Autowired
    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity,
            ManagedWorkspacePathResolver pathResolver) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
    }

    /**
     * 兼容旧测试构造路径，未显式注入服务器身份时使用本地默认值。
     */
    public WorkspaceApplicationService(WorkspaceRepository workspaceRepository, WorkspaceFileService fileService) {
        this(workspaceRepository, fileService, new WorkspaceServerIdentity("127.0.0.1"), ManagedWorkspacePathResolver.legacyOnly());
    }

    /**
     * 兼容旧测试构造路径，未显式注入路径解析器时只处理普通绝对路径。
     */
    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity) {
        this(workspaceRepository, fileService, serverIdentity, ManagedWorkspacePathResolver.legacyOnly());
    }

    /**
     * 注册工作区；可选 linuxServerId 必须与当前后端身份一致，用于跨服务器目录选择后的目标端创建。
     */
    public Workspace createWorkspace(String name, String rootPath, String linuxServerId, String traceId) {
        LOGGER.info("Creating workspace, name={}, rootPath={}, traceId={}", name, rootPath, traceId);
        String resolvedLinuxServerId = resolveLinuxServerId(linuxServerId);
        Path root = validateRootPath(rootPath);
        Instant now = Instant.now();
        Workspace workspace = new Workspace(
                new WorkspaceId(RuntimeIdGenerator.workspaceId()),
                name,
                root.toString(),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                resolvedLinuxServerId,
                traceId);
        Workspace saved = workspaceRepository.save(workspace);
        LOGGER.info("Workspace created, workspaceId={}, name={}, traceId={}", saved.workspaceId().value(), name, traceId);
        return saved;
    }

    /**
     * 前端传入服务器 ID 时必须与当前后端一致，避免把远端路径注册到错误服务器。
     */
    private String resolveLinuxServerId(String requestedLinuxServerId) {
        String current = serverIdentity.linuxServerId();
        if (requestedLinuxServerId == null || requestedLinuxServerId.isBlank()) {
            return current;
        }
        if (!current.equals(requestedLinuxServerId.trim())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "工作空间与 agent 不在同一服务器",
                    Map.of("workspaceLinuxServerId", requestedLinuxServerId.trim(), "currentLinuxServerId", current));
        }
        return current;
    }

    /**
     * 分页查询工作区列表；分页边界由 PageRequest 统一校验。
     */
    public PageResponse<Workspace> listWorkspaces(PageRequest pageRequest) {
        PageResponse<Workspace> page = workspaceRepository.findPage(pageRequest);
        return new PageResponse<>(
                page.items().stream().map(this::workspaceForResponse).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    /**
     * 根据 WorkspaceId 查询工作区；不存在时返回平台 NOT_FOUND 错误，供 API 层统一映射。
     */
    public Workspace getWorkspace(WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .map(this::workspaceForResponse)
                .orElseThrow(() -> {
                    LOGGER.warn("Workspace not found, workspaceId={}", workspaceId.value());
                    return new PlatformException(
                            ErrorCode.NOT_FOUND,
                            "Workspace 不存在",
                            Map.of("workspaceId", workspaceId.value()));
                });
    }

    /**
     * 校验工作区属于当前后端服务器；历史空服务器字段在根目录可访问时回填当前服务器。
     */
    public Workspace requireWorkspaceOnCurrentServer(WorkspaceId workspaceId, String traceId) {
        Workspace workspace = rawWorkspace(workspaceId);
        String currentLinuxServerId = serverIdentity.linuxServerId();
        if (workspace.linuxServerId() == null) {
            validateRootPath(resolvedRootPath(workspace));
            Workspace bound = workspace.withLinuxServerId(currentLinuxServerId, traceId, Instant.now());
            return workspaceForResponse(workspaceRepository.save(bound));
        }
        if (!currentLinuxServerId.equals(workspace.linuxServerId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "工作空间与 agent 不在同一服务器",
                    Map.of(
                            "workspaceId", workspaceId.value(),
                            "workspaceLinuxServerId", workspace.linuxServerId(),
                            "currentLinuxServerId", currentLinuxServerId));
        }
        validateRootPath(resolvedRootPath(workspace));
        return workspaceForResponse(workspace);
    }

    /**
     * 当前后端绑定的 Linux 服务器 ID。
     */
    public String currentLinuxServerId() {
        return serverIdentity.linuxServerId();
    }

    /**
     * 当前 Java 进程运行目录，用作远端服务器目录选择默认路径。
     */
    public String defaultDirectory() {
        return serverIdentity.defaultDirectory();
    }

    /**
     * 列出工作区内指定相对路径的一层目录项，不做递归扫描。
     */
    public List<FileTreeEntryResponse> listFiles(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.listDirectory(workspace.rootPath(), path);
    }

    /**
     * 读取工作区内 UTF-8 文本文件内容，路径越权和大小限制由 WorkspaceFileService 统一处理。
     */
    public FileContentResponse readFile(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.readContent(workspace.rootPath(), path);
    }

    /**
     * 写入工作区内 UTF-8 文本文件；缺失父目录会按文件服务规则自动创建。
     */
    public void writeFile(WorkspaceId workspaceId, String path, String content) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.writeContent(workspace.rootPath(), path, content);
    }

    /**
     * 删除工作区内普通文件；目录删除当前不开放，避免 Web 入口误删目录树。
     */
    public void deleteFile(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.deleteFile(workspace.rootPath(), path);
    }

    /**
     * 查询工作区内文件或目录状态；不存在时返回 exists=false，而不是抛出 NOT_FOUND。
     */
    public FileStatusResponse fileStatus(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.status(workspace.rootPath(), path);
    }

    /**
     * 在工作区内递归搜索文件名包含 query（不区分大小写）的文件。
     * 忽略黑名单目录，结果按文件名排序，有数量和超时限制。
     */
    public List<FileSearchResultResponse> searchFiles(WorkspaceId workspaceId, String query) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.searchFiles(workspace.rootPath(), query);
    }

    /**
     * 校验并归一化工作区根目录，返回真实路径，避免后续文件服务基于符号链接产生越权判断误差。
     */
    private Path validateRootPath(String rootPath) {
        try {
            Path root = Path.of(rootPath).toRealPath();
            if (!Files.isDirectory(root)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作区根路径必须是目录", Map.of("rootPath", rootPath));
            }
            return root;
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作区根路径不存在", Map.of("rootPath", rootPath), exception);
        }
    }

    private Workspace workspaceForResponse(Workspace workspace) {
        return pathResolver.withResolvedRootPath(workspace);
    }

    private String resolvedRootPath(Workspace workspace) {
        return pathResolver.resolve(workspace.rootPath()).toString();
    }

    private Workspace rawWorkspace(WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> {
                    LOGGER.warn("Workspace not found, workspaceId={}", workspaceId.value());
                    return new PlatformException(
                            ErrorCode.NOT_FOUND,
                            "Workspace 不存在",
                            Map.of("workspaceId", workspaceId.value()));
                });
    }
}
