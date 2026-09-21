package com.enterprise.testagent.domain.team;

import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.user.User;
import java.util.List;

/** 团队代码视图的只读领域投影。 */
public final class TeamWorkspaceViews {

    private TeamWorkspaceViews() {
    }

    public record ApplicationView(
            String appId,
            String appName,
            boolean enabled,
            long currentMemberCount,
            long historicalMemberCount) {
    }

    public record WorkspaceTemplateView(
            String workspaceId,
            String appId,
            String workspaceName,
            String branch,
            String directoryPath,
            boolean enabled) {
    }

    public record WorkspaceVersionView(ApplicationWorkspaceVersion version) {
    }

    public record PersonalWorkspaceView(PersonalWorkspace workspace, String linuxServerId) {
    }

    public record MemberContributionView(
            User user,
            TeamMembershipState membershipState,
            List<PersonalWorkspaceView> personalWorkspaces) {
    }
}
