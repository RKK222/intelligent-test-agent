package com.enterprise.testagent.domain.sessionshare;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 平台会话协作分享聚合。
 *
 * <p>分享 ID 与 Session 永久一对一；成员更新采用全量语义，但缺失成员只会软移除。
 */
public record SessionShare(
        SessionShareId shareId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        UserId ownerUserId,
        SessionShareStatus status,
        Instant expiresAt,
        long version,
        List<SessionShareMembership> memberships,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt,
        String traceId) {

    public static final int MAX_MEMBERS = 50;
    public static final Duration MAX_VALIDITY = Duration.ofDays(7);

    public SessionShare {
        Objects.requireNonNull(shareId, "shareId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == SessionShareStatus.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("revoked share requires revokedAt");
        }
        memberships = immutableValidatedMemberships(ownerUserId, memberships);
    }

    /** 首次创建唯一分享链接。 */
    public static SessionShare create(
            SessionShareId shareId,
            SessionId sessionId,
            WorkspaceId workspaceId,
            UserId ownerUserId,
            Instant expiresAt,
            List<SessionShareMembership> memberships,
            Instant now,
            String traceId) {
        validateExpiry(expiresAt, now);
        return new SessionShare(
                shareId, sessionId, workspaceId, ownerUserId, SessionShareStatus.ACTIVE,
                expiresAt, 0, memberships, now, now, null, traceId);
    }

    /** 全量更新成员与有效期；历史成员仍保留在聚合中并标记为 REMOVED。 */
    public SessionShare update(
            Instant nextExpiresAt,
            List<SessionShareMembership> requestedMemberships,
            Instant now,
            String nextTraceId) {
        if (status != SessionShareStatus.ACTIVE) {
            throw new IllegalStateException("revoked share must be reactivated before update");
        }
        validateExpiry(nextExpiresAt, now);
        return copy(
                SessionShareStatus.ACTIVE,
                nextExpiresAt,
                mergeMemberships(requestedMemberships, now),
                now,
                null,
                nextTraceId);
    }

    /** 取消分享但永久保留 shareId 和成员历史。 */
    public SessionShare revoke(Instant now, String nextTraceId) {
        if (status == SessionShareStatus.REVOKED) {
            return this;
        }
        return copy(status == SessionShareStatus.ACTIVE ? SessionShareStatus.REVOKED : status,
                expiresAt, memberships, now, now, nextTraceId);
    }

    /** 使用原 shareId 重新启用已取消或过期的分享。 */
    public SessionShare reactivate(
            Instant nextExpiresAt,
            List<SessionShareMembership> requestedMemberships,
            Instant now,
            String nextTraceId) {
        validateExpiry(nextExpiresAt, now);
        return copy(
                SessionShareStatus.ACTIVE,
                nextExpiresAt,
                mergeMemberships(requestedMemberships, now),
                now,
                null,
                nextTraceId);
    }

    /** 当前链接是否有效；所属会话状态由访问服务额外校验。 */
    public boolean activeAt(Instant now) {
        return status == SessionShareStatus.ACTIVE && expiresAt.isAfter(now);
    }

    /** 按成员 ID 读取历史或当前授权。 */
    public Optional<SessionShareMembership> membership(UserId userId) {
        return memberships.stream().filter(member -> member.userId().equals(userId)).findFirst();
    }

    private SessionShare copy(
            SessionShareStatus nextStatus,
            Instant nextExpiresAt,
            List<SessionShareMembership> nextMemberships,
            Instant now,
            Instant nextRevokedAt,
            String nextTraceId) {
        return new SessionShare(
                shareId, sessionId, workspaceId, ownerUserId, nextStatus, nextExpiresAt,
                version + 1, nextMemberships, createdAt, now, nextRevokedAt, nextTraceId);
    }

    private List<SessionShareMembership> mergeMemberships(
            List<SessionShareMembership> requestedMemberships,
            Instant now) {
        List<SessionShareMembership> requested = immutableValidatedMemberships(ownerUserId, requestedMemberships);
        Map<UserId, SessionShareMembership> requestedByUser = new LinkedHashMap<>();
        requested.forEach(member -> requestedByUser.put(member.userId(), member));

        Map<UserId, SessionShareMembership> merged = new LinkedHashMap<>();
        for (SessionShareMembership existing : memberships) {
            SessionShareMembership replacement = requestedByUser.remove(existing.userId());
            merged.put(existing.userId(), replacement == null
                    ? existing.remove(now)
                    : existing.activateFrom(replacement, now));
        }
        requestedByUser.forEach(merged::put);
        return List.copyOf(merged.values());
    }

    private static List<SessionShareMembership> immutableValidatedMemberships(
            UserId ownerUserId,
            List<SessionShareMembership> memberships) {
        Objects.requireNonNull(memberships, "memberships must not be null");
        List<SessionShareMembership> copy = List.copyOf(new ArrayList<>(memberships));
        long activeCount = copy.stream()
                .filter(member -> member.status() == SessionShareMembershipStatus.ACTIVE)
                .count();
        if (activeCount > MAX_MEMBERS) {
            throw new IllegalArgumentException("active membership count must not exceed 50");
        }
        Map<UserId, SessionShareMembership> unique = new LinkedHashMap<>();
        for (SessionShareMembership member : copy) {
            Objects.requireNonNull(member, "membership must not be null");
            if (ownerUserId.equals(member.userId())) {
                throw new IllegalArgumentException("owner must not be a share member");
            }
            if (unique.put(member.userId(), member) != null) {
                throw new IllegalArgumentException("duplicate share member: " + member.userId().value());
            }
        }
        return copy;
    }

    private static void validateExpiry(Instant expiresAt, Instant now) {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be after now");
        }
        if (expiresAt.isAfter(now.plus(MAX_VALIDITY))) {
            throw new IllegalArgumentException("expiresAt must not be more than 7 days after operation time");
        }
    }
}
