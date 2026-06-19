package com.example.testagent.workspace;

import com.example.testagent.common.id.RuntimeIdGenerator;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import com.example.testagent.domain.workspace.WorkspaceStatus;
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

    public WorkspaceApplicationService(WorkspaceRepository workspaceRepository, WorkspaceFileService fileService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
    }

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

    public PageResponse<Workspace> listWorkspaces(PageRequest pageRequest) {
        return workspaceRepository.findPage(pageRequest);
    }

    public Workspace getWorkspace(WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", workspaceId.value())));
    }

    public List<FileTreeEntryResponse> listFiles(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.listDirectory(workspace.rootPath(), path);
    }

    public FileContentResponse readFile(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.readContent(workspace.rootPath(), path);
    }

    public void writeFile(WorkspaceId workspaceId, String path, String content) {
        Workspace workspace = getWorkspace(workspaceId);
        fileService.writeContent(workspace.rootPath(), path, content);
    }

    public FileStatusResponse fileStatus(WorkspaceId workspaceId, String path) {
        Workspace workspace = getWorkspace(workspaceId);
        return fileService.status(workspace.rootPath(), path);
    }

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
