package com.icbc.testagent.workspace;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Workspace 应用服务，负责工作区注册、查询和文件服务编排，避免 Controller 直接访问 Repository 或文件系统。
 */
@Service
public class WorkspaceApplicationService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceFileService fileService;

    /**
     * 构造 Workspace 应用服务，注入领域 Repository 端口和文件服务，避免 Controller 直接访问底层资源。
     */
    public WorkspaceApplicationService(WorkspaceRepository workspaceRepository, WorkspaceFileService fileService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
    }

    /**
     * 注册工作区；rootPath 必须是已存在目录，traceId 会写入领域对象用于审计和排障。
     */
    public Workspace createWorkspace(String name, String rootPath, String traceId) {
        Path root = validateRootPath(rootPath);
        Instant now = Instant.now();
        Workspace workspace = new Workspace(
                new WorkspaceId(RuntimeIdGenerator.workspaceId()),
                name,
                root.toString(),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                traceId);
        return workspaceRepository.save(workspace);
    }

    /**
     * 分页查询工作区列表；分页边界由 PageRequest 统一校验。
     */
    public PageResponse<Workspace> listWorkspaces(PageRequest pageRequest) {
        return workspaceRepository.findPage(pageRequest);
    }

    /**
     * 根据 WorkspaceId 查询工作区；不存在时返回平台 NOT_FOUND 错误，供 API 层统一映射。
     */
    public Workspace getWorkspace(WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", workspaceId.value())));
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
     * 查询工作区内文件或目录状态；不存在时返回 exists=false，而不是抛出 NOT_FOUND。
     */
    public FileStatusResponse fileStatus(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.status(workspace.rootPath(), path);
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
}
