package com.enterprise.testagent.system.management.team;

import com.enterprise.testagent.domain.team.TeamScopeMode;
import java.time.Instant;
import java.util.List;

/** 系统管理员团队管理对 API 暴露的稳定响应。 */
public final class SystemAdminTeamResponses {

    private SystemAdminTeamResponses() {
    }

    public record TeamUserResponse(
            String userId,
            String unifiedAuthId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String status,
            List<String> roles,
            Instant addedAt) {
    }

    public record TeamScope(TeamScopeMode mode, String ownerUserId, boolean global) {
    }

    public record TeamMutationResponse(String ownerUserId, String memberUserId, boolean active) {
    }
}
