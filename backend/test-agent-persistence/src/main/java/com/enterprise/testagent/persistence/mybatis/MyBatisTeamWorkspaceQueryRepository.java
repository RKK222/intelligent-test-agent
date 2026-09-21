package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.team.TeamMembershipState;
import com.enterprise.testagent.domain.team.TeamWorkspaceQueryRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.ApplicationView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.MemberContributionView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.PersonalWorkspaceView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.WorkspaceTemplateView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.WorkspaceVersionView;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 团队代码视图组合查询的 MyBatis 实现。 */
@Repository
public class MyBatisTeamWorkspaceQueryRepository implements TeamWorkspaceQueryRepository {

    private final TeamWorkspaceQueryMapper mapper;

    public MyBatisTeamWorkspaceQueryRepository(TeamWorkspaceQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ApplicationView> findApplications(boolean globalScope, UserId ownerUserId) {
        return mapper.findApplications(globalScope, value(ownerUserId)).stream()
                .map(row -> new ApplicationView(
                        row.appId(), row.appName(), row.enabled(), row.currentMemberCount(), row.historicalMemberCount()))
                .toList();
    }

    @Override
    public List<WorkspaceTemplateView> findWorkspaceTemplates(
            boolean globalScope, UserId ownerUserId, String appId) {
        return mapper.findWorkspaceTemplates(globalScope, value(ownerUserId), appId).stream()
                .map(row -> new WorkspaceTemplateView(
                        row.workspaceId(), row.appId(), row.workspaceName(), row.branch(),
                        row.directoryPath(), row.enabled()))
                .toList();
    }

    @Override
    public List<WorkspaceVersionView> findWorkspaceVersions(
            boolean globalScope, UserId ownerUserId, String applicationWorkspaceId) {
        return mapper.findWorkspaceVersions(globalScope, value(ownerUserId), applicationWorkspaceId).stream()
                .map(row -> new WorkspaceVersionView(version(row))).toList();
    }

    @Override
    public List<MemberContributionView> findContributions(
            boolean globalScope, UserId ownerUserId, String versionId) {
        LinkedHashMap<String, MutableContribution> grouped = new LinkedHashMap<>();
        for (TeamWorkspaceContributionRow row : mapper.findContributions(globalScope, value(ownerUserId), versionId)) {
            MutableContribution contribution = grouped.computeIfAbsent(row.userId(), ignored -> new MutableContribution(
                    user(row), TeamMembershipState.valueOf(row.membershipState()), new ArrayList<>()));
            if (row.personalWorkspaceId() != null) {
                contribution.workspaces.add(personal(row));
            }
        }
        return grouped.values().stream()
                .map(value -> new MemberContributionView(
                        value.user, value.state, List.copyOf(value.workspaces)))
                .toList();
    }

    @Override
    public Optional<PersonalWorkspaceView> findPersonalWorkspace(
            boolean globalScope, UserId ownerUserId, PersonalWorkspaceId personalWorkspaceId) {
        return Optional.ofNullable(mapper.findPersonalWorkspace(
                globalScope, value(ownerUserId), personalWorkspaceId.value())).map(this::personal);
    }

    private ApplicationWorkspaceVersion version(TeamWorkspaceVersionRow row) {
        return new ApplicationWorkspaceVersion(
                new ApplicationWorkspaceVersionId(row.versionId()),
                new ApplicationWorkspaceId(row.applicationWorkspaceId()),
                new ApplicationId(row.appId()),
                new CodeRepositoryId(row.repositoryId()),
                row.version(), row.branch(), row.repoRootPath(), row.workspaceRootPath(),
                new WorkspaceId(row.runtimeWorkspaceId()), new UserId(row.createdByUserId()),
                ManagedWorkspaceStatus.valueOf(row.status()), row.targetCommitHash(), row.targetCommitUpdatedAt(),
                row.createdAt(), row.updatedAt());
    }

    private User user(TeamWorkspaceContributionRow row) {
        return new User(
                new UserId(row.userId()), row.unifiedAuthId(), row.username(), row.passwordHash(),
                row.organization(), row.rdDepartment(), row.department(), UserStatus.valueOf(row.userStatus()),
                row.userCreatedAt(), row.userUpdatedAt());
    }

    private PersonalWorkspaceView personal(TeamWorkspaceContributionRow row) {
        PersonalWorkspace workspace = new PersonalWorkspace(
                new PersonalWorkspaceId(row.personalWorkspaceId()),
                new ApplicationWorkspaceVersionId(row.versionId()), new ApplicationId(row.appId()),
                new ApplicationWorkspaceId(row.applicationWorkspaceId()), new UserId(row.userId()),
                row.workspaceName(), row.branch(), row.repoRootPath(), row.workspaceRootPath(),
                new WorkspaceId(row.runtimeWorkspaceId()), row.baseCommit(),
                ManagedWorkspaceStatus.valueOf(row.workspaceStatus()),
                row.workspaceCreatedAt(), row.workspaceUpdatedAt());
        return new PersonalWorkspaceView(workspace, row.linuxServerId());
    }

    private String value(UserId userId) {
        return userId == null ? null : userId.value();
    }

    private static final class MutableContribution {
        private final User user;
        private final TeamMembershipState state;
        private final List<PersonalWorkspaceView> workspaces;

        private MutableContribution(User user, TeamMembershipState state, List<PersonalWorkspaceView> workspaces) {
            this.user = user;
            this.state = state;
            this.workspaces = workspaces;
        }
    }
}
