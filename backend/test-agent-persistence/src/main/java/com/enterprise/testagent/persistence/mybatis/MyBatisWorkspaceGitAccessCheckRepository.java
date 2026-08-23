package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheck;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheckRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 工作空间 Git 权限巡检 MyBatis 仓储实现。 */
@Repository
public class MyBatisWorkspaceGitAccessCheckRepository implements WorkspaceGitAccessCheckRepository {

    private final WorkspaceGitAccessCheckMapper mapper;

    public MyBatisWorkspaceGitAccessCheckRepository(WorkspaceGitAccessCheckMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ApplicationWorkspaceCandidate> findApplicationWorkspaceCandidatesAfter(
            String afterUserId,
            String afterWorkspaceId,
            int limit) {
        return mapper.findApplicationCandidatesAfter(afterUserId, afterWorkspaceId, limit).stream()
                .map(row -> new ApplicationWorkspaceCandidate(
                        new UserId(row.userId()), row.applicationWorkspaceId(), row.versionId()))
                .toList();
    }

    @Override
    public List<LocalWorkspaceCandidate> findLocalWorkspaceCandidatesAfter(
            String afterUserId,
            String afterWorkspaceId,
            int limit) {
        return mapper.findLocalCandidatesAfter(afterUserId, afterWorkspaceId, limit).stream()
                .map(row -> new LocalWorkspaceCandidate(
                        new UserId(row.userId()),
                        new WorkspaceId(row.workspaceId()),
                        row.clientInstanceId(),
                        row.rootDigest()))
                .toList();
    }

    @Override
    public Optional<WorkspaceGitAccessCheck> find(
            UserId userId,
            WorkspaceGitAccessCheck.TargetKind targetKind,
            String targetId) {
        WorkspaceGitAccessCheckRow row = switch (targetKind) {
            case APPLICATION_WORKSPACE -> mapper.findApplicationCheck(userId.value(), targetId);
            case LOCAL_WORKSPACE -> mapper.findLocalCheck(userId.value(), targetId);
        };
        return Optional.ofNullable(row).map(value -> toDomain(value, targetKind));
    }

    @Override
    public void save(WorkspaceGitAccessCheck check) {
        WorkspaceGitAccessCheckRow row = new WorkspaceGitAccessCheckRow(
                check.userId().value(),
                check.targetId(),
                check.status().name(),
                check.reason(),
                check.message(),
                check.checkedAt());
        int updated = switch (check.targetKind()) {
            case APPLICATION_WORKSPACE -> mapper.upsertApplicationCheck(row, check.checkedAt());
            case LOCAL_WORKSPACE -> mapper.upsertLocalCheck(row, check.checkedAt());
        };
        if (updated < 1) {
            throw new IllegalStateException("workspace Git access check was not persisted");
        }
    }

    private WorkspaceGitAccessCheck toDomain(
            WorkspaceGitAccessCheckRow row,
            WorkspaceGitAccessCheck.TargetKind targetKind) {
        return new WorkspaceGitAccessCheck(
                new UserId(row.userId()),
                targetKind,
                row.targetId(),
                WorkspaceGitAccessCheck.Status.valueOf(row.status()),
                row.reason(),
                row.message(),
                row.checkedAt());
    }
}
