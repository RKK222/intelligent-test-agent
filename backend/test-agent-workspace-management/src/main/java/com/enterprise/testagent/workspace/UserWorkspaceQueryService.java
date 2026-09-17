package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.UserWorkspaceQueryRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 用户关联工作区只读查询服务，为普通用户对象级鉴权和排查只读入口提供同一范围定义。
 */
@Service
public class UserWorkspaceQueryService {

    private final UserWorkspaceQueryRepository repository;
    private final WorkspaceRepository workspaceRepository;
    private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;
    private final ManagedWorkspacePathResolver pathResolver;
    private final ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer;

    @Autowired
    public UserWorkspaceQueryService(
            UserWorkspaceQueryRepository repository,
            WorkspaceRepository workspaceRepository,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            ManagedWorkspacePathResolver pathResolver,
            ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.experienceWorkspaceAccessAuthorizer = Objects.requireNonNull(
                experienceWorkspaceAccessAuthorizer,
                "experienceWorkspaceAccessAuthorizer must not be null");
    }

    /** 兼容不涉及体验 Workspace 的单元测试和嵌入式构造路径。 */
    public UserWorkspaceQueryService(
            UserWorkspaceQueryRepository repository,
            WorkspaceRepository workspaceRepository,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            ManagedWorkspacePathResolver pathResolver) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.experienceWorkspaceAccessAuthorizer = null;
    }

    /** 兼容只验证体验 Workspace 实时鉴权的单元测试。 */
    public UserWorkspaceQueryService(
            UserWorkspaceQueryRepository repository,
            ManagedWorkspacePathResolver pathResolver,
            ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.workspaceRepository = null;
        this.workspaceAccessAuthorizer = null;
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.experienceWorkspaceAccessAuthorizer = Objects.requireNonNull(
                experienceWorkspaceAccessAuthorizer,
                "experienceWorkspaceAccessAuthorizer must not be null");
    }

    /** 用户范围过滤完成后仍解析托管逻辑路径，保持 Workspace API 返回物理绝对路径。 */
    public PageResponse<Workspace> listUserWorkspaces(UserId userId, PageRequest pageRequest) {
        return listUserWorkspaces(userId, null, pageRequest);
    }

    /** 排查列表可按工作区名称或 ID 过滤，且仍复用同一用户归因范围。 */
    public PageResponse<Workspace> listUserWorkspaces(
            UserId userId,
            String query,
            PageRequest pageRequest) {
        PageResponse<Workspace> page = repository.findUserWorkspaces(userId, query, pageRequest);
        return new PageResponse<>(
                page.items().stream().map(pathResolver::withResolvedRootPath).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    /**
     * 详情查询与列表使用同一物理路径响应语义；首次打开源码快照尚无会话引用时，
     * 仅允许通过现有 APP_SOURCE 权威鉴权的 Runtime Workspace 受控回退到工作区主表。
     */
    public Workspace requireUserWorkspace(UserId userId, WorkspaceId workspaceId) {
        if (experienceWorkspaceAccessAuthorizer != null
                && experienceWorkspaceAccessAuthorizer.isExperienceWorkspace(workspaceId)) {
            return experienceWorkspaceAccessAuthorizer.requireAccess(userId, workspaceId);
        }
        var direct = repository.findUserWorkspace(userId, workspaceId);
        if (direct.isPresent()) {
            return pathResolver.withResolvedRootPath(direct.get());
        }

        if (workspaceAccessAuthorizer == null || workspaceRepository == null) {
            throw workspaceNotFound(workspaceId);
        }
        // 这里只借用分类结果；允许未映射项返回 STANDARD 后继续按 NOT_FOUND 收口，不授予任何 STANDARD 详情读取。
        var kind = workspaceAccessAuthorizer.requireClassifiedFileAccess(userId, workspaceId, true);
        if (kind != ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind.APP_SOURCE) {
            throw workspaceNotFound(workspaceId);
        }
        return workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.status() == WorkspaceStatus.ACTIVE)
                .map(pathResolver::withResolvedRootPath)
                .orElseThrow(() -> workspaceNotFound(workspaceId));
    }

    private PlatformException workspaceNotFound(WorkspaceId workspaceId) {
        return new PlatformException(
                ErrorCode.NOT_FOUND,
                "Workspace 不存在",
                Map.of("workspaceId", workspaceId.value()));
    }
}
