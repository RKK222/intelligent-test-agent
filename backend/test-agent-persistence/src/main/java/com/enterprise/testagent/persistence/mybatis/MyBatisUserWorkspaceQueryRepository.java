package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.UserWorkspaceQueryRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 用户关联工作区 MyBatis Repository。 */
@Repository
public class MyBatisUserWorkspaceQueryRepository implements UserWorkspaceQueryRepository {

    private final UserWorkspaceQueryMapper mapper;

    public MyBatisUserWorkspaceQueryRepository(UserWorkspaceQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResponse<Workspace> findUserWorkspaces(UserId userId, PageRequest pageRequest) {
        var rows = mapper.findUserWorkspaces(userId.value(), pageRequest.size(), pageRequest.offset());
        return new PageResponse<>(
                rows.stream().map(this::toDomain).toList(),
                pageRequest.page(),
                pageRequest.size(),
                mapper.countUserWorkspaces(userId.value()));
    }

    @Override
    public Optional<Workspace> findUserWorkspace(UserId userId, WorkspaceId workspaceId) {
        return Optional.ofNullable(mapper.findUserWorkspace(userId.value(), workspaceId.value()))
                .map(this::toDomain);
    }

    private Workspace toDomain(UserWorkspaceRow row) {
        Instant updatedAt = row.updatedAt().isBefore(row.createdAt()) ? row.createdAt() : row.updatedAt();
        return new Workspace(
                new WorkspaceId(row.workspaceId()), row.name(), row.rootPath(), WorkspaceStatus.valueOf(row.status()),
                row.createdAt(), updatedAt, row.linuxServerId(), row.traceId());
    }
}
