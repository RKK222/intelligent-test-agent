package com.enterprise.testagent.domain.sessionshare;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 单个被分享人的授权及展示身份快照。 */
public record SessionShareMembership(
        UserId userId,
        String unifiedAuthId,
        String username,
        boolean canChat,
        SessionShareMembershipStatus status,
        Instant sharedAt,
        Instant updatedAt,
        Instant removedAt) {

    public SessionShareMembership {
        Objects.requireNonNull(userId, "userId must not be null");
        unifiedAuthId = DomainValidation.requireText(unifiedAuthId, "unifiedAuthId");
        username = DomainValidation.requireText(username, "username");
        Objects.requireNonNull(status, "status must not be null");
        sharedAt = DomainValidation.requireInstant(sharedAt, "sharedAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(sharedAt)) {
            throw new IllegalArgumentException("updatedAt must not be before sharedAt");
        }
        if (status == SessionShareMembershipStatus.REMOVED && removedAt == null) {
            throw new IllegalArgumentException("removed membership requires removedAt");
        }
        if (status == SessionShareMembershipStatus.ACTIVE && removedAt != null) {
            throw new IllegalArgumentException("active membership must not have removedAt");
        }
    }

    /** 创建一条有效成员授权。 */
    public static SessionShareMembership active(
            UserId userId,
            String unifiedAuthId,
            String username,
            boolean canChat,
            Instant now) {
        return new SessionShareMembership(
                userId, unifiedAuthId, username, canChat,
                SessionShareMembershipStatus.ACTIVE, now, now, null);
    }

    /** 使用最新用户资料和权限激活成员；重新加入时刷新分享时间。 */
    public SessionShareMembership activateFrom(SessionShareMembership requested, Instant now) {
        Instant nextSharedAt = status == SessionShareMembershipStatus.ACTIVE ? sharedAt : now;
        return new SessionShareMembership(
                userId,
                requested.unifiedAuthId,
                requested.username,
                requested.canChat,
                SessionShareMembershipStatus.ACTIVE,
                nextSharedAt,
                now,
                null);
    }

    /** 软移除成员，保留身份和最近权限用于审计与失效列表。 */
    public SessionShareMembership remove(Instant now) {
        if (status == SessionShareMembershipStatus.REMOVED) {
            return this;
        }
        return new SessionShareMembership(
                userId, unifiedAuthId, username, canChat,
                SessionShareMembershipStatus.REMOVED, sharedAt, now, now);
    }
}
