package com.enterprise.testagent.domain.team;

import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.ApplicationView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.MemberContributionView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.PersonalWorkspaceView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.WorkspaceTemplateView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.WorkspaceVersionView;
import com.enterprise.testagent.domain.user.UserId;
import java.util.List;
import java.util.Optional;

/** 团队应用、版本和个人 worktree 的只读组合查询端口。 */
public interface TeamWorkspaceQueryRepository {

    List<ApplicationView> findApplications(boolean globalScope, UserId ownerUserId);

    List<WorkspaceTemplateView> findWorkspaceTemplates(boolean globalScope, UserId ownerUserId, String appId);

    List<WorkspaceVersionView> findWorkspaceVersions(
            boolean globalScope, UserId ownerUserId, String applicationWorkspaceId);

    List<MemberContributionView> findContributions(
            boolean globalScope, UserId ownerUserId, String versionId);

    Optional<PersonalWorkspaceView> findPersonalWorkspace(
            boolean globalScope, UserId ownerUserId, PersonalWorkspaceId personalWorkspaceId);
}
