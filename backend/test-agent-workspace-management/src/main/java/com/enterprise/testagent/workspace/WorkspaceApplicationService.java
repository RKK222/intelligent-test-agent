package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.domain.workspace.TrustedWorkspaceResolver;
import com.enterprise.testagent.domain.workspace.TrustedWorkspaceResolution;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextWorkspaceMutation;
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
public class WorkspaceApplicationService implements TrustedWorkspaceResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceApplicationService.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceFileService fileService;
    private final WorkspaceServerIdentity serverIdentity;
    private final ManagedWorkspacePathResolver pathResolver;
    private final ConversationContextStore conversationContextStore;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;

    /**
     * 构造 Workspace 应用服务，注入领域 Repository 端口和文件服务，避免 Controller 直接访问底层资源。
     */
    @Autowired
    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity,
            ManagedWorkspacePathResolver pathResolver,
            ConversationContextStore conversationContextStore,
            ManagedWorkspaceRepository managedWorkspaceRepository) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.conversationContextStore = Objects.requireNonNull(
                conversationContextStore,
                "conversationContextStore must not be null");
        this.managedWorkspaceRepository = Objects.requireNonNull(
                managedWorkspaceRepository,
                "managedWorkspaceRepository must not be null");
    }

    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity,
            ManagedWorkspacePathResolver pathResolver) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.conversationContextStore = null;
        this.managedWorkspaceRepository = null;
    }

    /** 兼容带会话上下文的测试/嵌入式构造路径。 */
    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity,
            ManagedWorkspacePathResolver pathResolver,
            ConversationContextStore conversationContextStore) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.conversationContextStore = conversationContextStore;
        this.managedWorkspaceRepository = null;
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
     * 校验文件 WebSocket 是否可以写入托管工作区。个人 worktree 只允许 owner 写入；
     * 应用版本副本是发布端只读输入，仅应用管理员可通过显式管理流程修改。
     */
    public void requireWorkspaceWriteAccess(WorkspaceId workspaceId, UserId userId, boolean appAdmin) {
        if (managedWorkspaceRepository == null) {
            return;
        }
        PersonalWorkspace personalWorkspace = managedWorkspaceRepository
                .findPersonalWorkspaceByRuntimeWorkspace(workspaceId)
                .orElse(null);
        if (personalWorkspace != null) {
            if (!personalWorkspace.userId().equals(userId)) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "个人工作区只允许拥有者编辑",
                        Map.of("workspaceId", workspaceId.value()));
            }
            return;
        }
        if (managedWorkspaceRepository.findVersionReplicaByRuntimeWorkspace(workspaceId).isPresent()
                || managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspaceId).isPresent()) {
            if (!appAdmin) {
                throw new PlatformException(
                        ErrorCode.FORBIDDEN,
                        "应用版本副本为只读，请在个人工作区中编辑",
                        Map.of("workspaceId", workspaceId.value()));
            }
        }
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
        return resolveTrustedWorkspace(workspaceId, traceId).workspace();
    }

    /**
     * 返回可信工作区并显式标记历史 server 回填，供上下文签发只对该自失效场景做一次有界重签。
     */
    @Override
    public TrustedWorkspaceResolution resolveTrustedWorkspace(WorkspaceId workspaceId, String traceId) {
        Workspace workspace = rawWorkspace(workspaceId);
        String currentLinuxServerId = serverIdentity.linuxServerId();
        if (workspace.linuxServerId() == null) {
            validateRootPath(resolvedRootPath(workspace));
            Workspace bound = workspace.withLinuxServerId(currentLinuxServerId, traceId, Instant.now());
            if (conversationContextStore == null) {
                return new TrustedWorkspaceResolution(workspaceForResponse(workspaceRepository.save(bound)), true);
            }
            ConversationContextWorkspaceMutation mutation =
                    conversationContextStore.beginWorkspaceMutation(workspaceId);
            Workspace saved;
            try {
                saved = workspaceRepository.save(bound);
            } catch (RuntimeException exception) {
                abortWorkspaceMutation(mutation, exception);
                throw exception;
            }
            conversationContextStore.completeWorkspaceMutation(mutation);
            return new TrustedWorkspaceResolution(workspaceForResponse(saved), true);
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
        return new TrustedWorkspaceResolution(workspaceForResponse(workspace), false);
    }

    private void abortWorkspaceMutation(
            ConversationContextWorkspaceMutation mutation,
            RuntimeException original) {
        try {
            conversationContextStore.abortWorkspaceMutation(mutation);
        } catch (RuntimeException abortFailure) {
            original.addSuppressed(abortFailure);
        }
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

    /** 渐进读取工作区大文件，调用方使用响应中的 nextOffset 连续请求。 */
    public FilePreviewChunkResponse readFilePreviewChunk(
            WorkspaceId workspaceId,
            String path,
            long offset,
            Long expectedSize,
            Long expectedLastModifiedMillis) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.readContentChunk(
                workspace.rootPath(), path, offset, expectedSize, expectedLastModifiedMillis);
    }

    /**
     * 写入工作区内 UTF-8 文本文件；缺失父目录会按文件服务规则自动创建。
     */
    public void writeFile(WorkspaceId workspaceId, String path, String content) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.writeContent(workspace.rootPath(), path, content);
    }

    /**
     * 上传 Base64 文件内容到工作区新路径；二进制解码、大小和冲突校验由文件服务统一处理。
     */
    public void uploadFile(WorkspaceId workspaceId, String path, String contentBase64) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.uploadFile(workspace.rootPath(), path, contentBase64);
    }

    /** 创建工作区文件分片上传会话；总大小只做完整性校验，不作为业务上限。 */
    public WorkspaceFileUpload beginFileUpload(WorkspaceId workspaceId, String path, long expectedBytes) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.beginUpload(workspace.rootPath(), path, expectedBytes);
    }

    /**
     * 在工作区内复制普通文件，不覆盖已有目标。
     */
    public void copyFile(WorkspaceId workspaceId, String sourcePath, String targetPath) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.copyFile(workspace.rootPath(), sourcePath, targetPath);
    }

    /**
     * 在工作区内移动普通文件，不覆盖已有目标。
     */
    public void moveFile(WorkspaceId workspaceId, String sourcePath, String targetPath) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.moveFile(workspace.rootPath(), sourcePath, targetPath);
    }

    /**
     * 在工作区同一父目录内重命名普通文件或目录，名称校验和路径安全由文件服务统一处理。
     */
    public void renameFile(WorkspaceId workspaceId, String path, String name) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.renameFile(workspace.rootPath(), path, name);
    }

    /**
     * 删除工作区内普通文件；目录删除当前不开放，避免 Web 入口误删目录树。
     */
    public void deleteFile(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.deleteFile(workspace.rootPath(), path);
    }

    /**
     * 在工作区内创建目录；已存在时不报错，不存在时递归创建父目录。
     */
    public void createDirectory(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.createDirectory(workspace.rootPath(), path);
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
