package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareAccessStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShareAuditEvent;
import com.enterprise.testagent.domain.sessionshare.SessionShareCandidate;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembershipStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShareRepository;
import com.enterprise.testagent.domain.sessionshare.SessionShareStatus;
import com.enterprise.testagent.domain.sessionshare.SharedSessionListItem;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 会话协作分享 MyBatis 仓储实现。 */
@Repository
public class MyBatisSessionShareRepository implements SessionShareRepository {

    private final SessionShareMapper mapper;

    public MyBatisSessionShareRepository(SessionShareMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public SessionShare insert(SessionShare share) {
        mapper.insertShare(toRow(share));
        for (SessionShareMembership membership : share.memberships()) {
            mapper.insertMembership(toRow(share.shareId(), membership));
        }
        return share;
    }

    @Override
    @Transactional
    public boolean update(SessionShare share, long expectedVersion) {
        if (mapper.updateShare(toRow(share), expectedVersion) == 0) {
            return false;
        }
        for (SessionShareMembership membership : share.memberships()) {
            SessionShareMembershipRow row = toRow(share.shareId(), membership);
            if (mapper.updateMembership(row) == 0) {
                mapper.insertMembership(row);
            }
        }
        return true;
    }

    @Override
    public Optional<SessionShare> findBySessionId(SessionId sessionId) {
        return Optional.ofNullable(mapper.findBySessionId(sessionId.value())).map(this::toDomain);
    }

    @Override
    public Optional<SessionShare> findByShareId(SessionShareId shareId) {
        return Optional.ofNullable(mapper.findByShareId(shareId.value())).map(this::toDomain);
    }

    @Override
    public PageResponse<SharedSessionListItem> findSharedWith(UserId userId, PageRequest pageRequest) {
        List<SharedSessionListItem> items = mapper.findSharedWith(
                        userId.value(), pageRequest.size(), pageRequest.offset()).stream()
                .map(this::toListItem)
                .toList();
        return new PageResponse<>(
                items, pageRequest.page(), pageRequest.size(), mapper.countSharedWith(userId.value()));
    }

    @Override
    public PageResponse<SessionShareCandidate> findCandidates(
            UserId excludedUserId,
            String keyword,
            PageRequest pageRequest) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String pattern = normalized.isBlank() ? null : "%" + normalized + "%";
        List<SessionShareCandidate> items = mapper.findCandidates(
                        excludedUserId.value(), pattern, pageRequest.size(), pageRequest.offset()).stream()
                .map(row -> new SessionShareCandidate(
                        new UserId(row.userId()), row.unifiedAuthId(), row.username()))
                .toList();
        return new PageResponse<>(
                items,
                pageRequest.page(),
                pageRequest.size(),
                mapper.countCandidates(excludedUserId.value(), pattern));
    }

    /** 通过单条条件 UPDATE 安全认领 created_by_user_id 为空的旧会话。 */
    @Override
    public boolean claimLegacySessionOwner(SessionId sessionId, UserId actor) {
        return mapper.claimLegacySessionOwner(sessionId.value(), actor.value()) == 1;
    }

    @Override
    public void appendAudit(SessionShareAuditEvent event) {
        mapper.insertAudit(event);
    }

    @Override
    public int deleteAuditEventsBefore(Instant cutoff) {
        return mapper.deleteAuditEventsBefore(java.util.Objects.requireNonNull(cutoff, "cutoff must not be null"));
    }

    private SessionShare toDomain(SessionShareRow row) {
        return new SessionShare(
                new SessionShareId(row.shareId()),
                new SessionId(row.sessionId()),
                new WorkspaceId(row.workspaceId()),
                new UserId(row.ownerUserId()),
                SessionShareStatus.valueOf(row.status()),
                row.expiresAt(),
                row.version().longValue(),
                mapper.findMemberships(row.shareId()).stream().map(this::toDomain).toList(),
                row.createdAt(),
                row.updatedAt(),
                row.revokedAt(),
                row.traceId());
    }

    private SessionShareMembership toDomain(SessionShareMembershipRow row) {
        return new SessionShareMembership(
                new UserId(row.userId()),
                row.unifiedAuthId(),
                row.username(),
                Boolean.TRUE.equals(row.canChat()),
                SessionShareMembershipStatus.valueOf(row.status()),
                row.sharedAt(),
                row.updatedAt(),
                row.removedAt());
    }

    private SharedSessionListItem toListItem(SharedSessionListRow row) {
        return new SharedSessionListItem(
                new SessionShareId(row.shareId()),
                new SessionId(row.sessionId()),
                new WorkspaceId(row.workspaceId()),
                row.sessionTitle(),
                new UserId(row.ownerUserId()),
                row.ownerUnifiedAuthId(),
                row.ownerUsername(),
                row.sharedAt(),
                row.expiresAt(),
                Boolean.TRUE.equals(row.canChat()),
                SessionShareAccessStatus.valueOf(row.accessStatus()));
    }

    private SessionShareRow toRow(SessionShare share) {
        return new SessionShareRow(
                share.shareId().value(), share.sessionId().value(), share.workspaceId().value(),
                share.ownerUserId().value(), share.status().name(), share.expiresAt(), share.version(),
                share.traceId(), share.createdAt(), share.updatedAt(), share.revokedAt());
    }

    private SessionShareMembershipRow toRow(SessionShareId shareId, SessionShareMembership membership) {
        return new SessionShareMembershipRow(
                shareId.value(), membership.userId().value(), membership.unifiedAuthId(), membership.username(),
                membership.canChat(), membership.status().name(), membership.sharedAt(),
                membership.updatedAt(), membership.removedAt());
    }
}
