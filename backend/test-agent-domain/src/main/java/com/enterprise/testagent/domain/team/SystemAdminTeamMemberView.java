package com.enterprise.testagent.domain.team;

import com.enterprise.testagent.domain.user.User;
import java.time.Instant;
import java.util.Objects;

/** 团队成员列表使用的用户资料与关系时间快照。 */
public record SystemAdminTeamMemberView(User user, Instant addedAt, Instant updatedAt) {
    public SystemAdminTeamMemberView {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(addedAt, "addedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
