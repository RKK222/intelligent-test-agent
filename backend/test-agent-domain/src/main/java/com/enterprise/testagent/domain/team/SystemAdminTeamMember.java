package com.enterprise.testagent.domain.team;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 系统管理员维护的人员关系；它与应用成员关系相互独立。 */
public record SystemAdminTeamMember(
        UserId ownerUserId,
        UserId memberUserId,
        UserId addedByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public SystemAdminTeamMember {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(memberUserId, "memberUserId must not be null");
        Objects.requireNonNull(addedByUserId, "addedByUserId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (ownerUserId.equals(memberUserId)) {
            throw new IllegalArgumentException("team owner cannot be a member of the same team");
        }
    }

    public boolean active() {
        return deletedAt == null;
    }
}
