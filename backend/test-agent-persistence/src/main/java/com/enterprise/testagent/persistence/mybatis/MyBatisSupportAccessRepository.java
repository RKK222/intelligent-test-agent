package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.supportaccess.SupportAccessAuditEvent;
import com.enterprise.testagent.domain.supportaccess.SupportAccessAuditQuery;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrant;
import com.enterprise.testagent.domain.supportaccess.SupportAccessRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 排查授权与审计 MyBatis Repository。 */
@Repository
public class MyBatisSupportAccessRepository implements SupportAccessRepository {

    private final SupportAccessMapper mapper;

    public MyBatisSupportAccessRepository(SupportAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SupportAccessGrant saveGrant(SupportAccessGrant grant) {
        mapper.insertGrant(toRow(grant));
        return grant;
    }

    @Override
    public Optional<SupportAccessGrant> findGrant(String grantId) {
        return Optional.ofNullable(mapper.findGrant(grantId)).map(this::toDomain);
    }

    @Override
    public Optional<String> findLatestIncidentId(UserId actorUserId) {
        return Optional.ofNullable(mapper.findLatestIncidentId(actorUserId.value()));
    }

    @Override
    public void revokeGrant(String grantId, Instant revokedAt, String reason) {
        mapper.revokeGrant(grantId, revokedAt, reason);
    }

    @Override
    public void appendAuditEvent(SupportAccessAuditEvent event) {
        mapper.insertAuditEvent(toRow(event));
    }

    @Override
    public PageResponse<SupportAccessAuditEvent> findAuditEvents(
            SupportAccessAuditQuery query,
            PageRequest pageRequest) {
        var rows = mapper.findAuditEvents(
                query.actorUserId(), query.targetUserId(), query.incidentId(), query.outcome(),
                pageRequest.size(), pageRequest.offset());
        long total = mapper.countAuditEvents(
                query.actorUserId(), query.targetUserId(), query.incidentId(), query.outcome());
        return new PageResponse<>(
                rows.stream().map(this::toDomain).toList(),
                pageRequest.page(),
                pageRequest.size(),
                total);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        int auditDeleted = mapper.deleteAuditEventsBefore(cutoff);
        return auditDeleted + mapper.deleteEndedGrantsBefore(cutoff);
    }

    private SupportAccessGrantRow toRow(SupportAccessGrant grant) {
        return new SupportAccessGrantRow(
                grant.grantId(), grant.actorUserId() == null ? null : grant.actorUserId().value(),
                grant.actorUsername(), grant.incidentId(), grant.reason(),
                grant.sessionDigest(), grant.issuedAt(), grant.expiresAt(), grant.revokedAt(), grant.revokeReason(), grant.traceId());
    }

    private SupportAccessGrant toDomain(SupportAccessGrantRow row) {
        return new SupportAccessGrant(
                row.grantId(), row.actorUserId() == null ? null : new UserId(row.actorUserId()),
                row.actorUsername(), row.incidentId(), row.reason(),
                row.sessionDigest(), row.issuedAt(), row.expiresAt(), row.revokedAt(), row.revokeReason(), row.traceId());
    }

    private SupportAccessAuditEventRow toRow(SupportAccessAuditEvent event) {
        return new SupportAccessAuditEventRow(
                event.eventId(), event.grantId(), event.actorUserId(), event.actorUsername(),
                event.targetUserId(), event.targetUsername(), event.incidentId(), event.reason(), event.action(),
                event.resourceType(), event.resourceId(), event.pathDigest(), event.outcome(), event.errorCode(),
                event.traceId(), event.ipAddress(), event.userAgentDigest(), event.occurredAt());
    }

    private SupportAccessAuditEvent toDomain(SupportAccessAuditEventRow row) {
        return new SupportAccessAuditEvent(
                row.eventId(), row.grantId(), row.actorUserId(), row.actorUsername(), row.targetUserId(),
                row.targetUsername(), row.incidentId(), row.reason(), row.action(), row.resourceType(), row.resourceId(),
                row.pathDigest(), row.outcome(), row.errorCode(), row.traceId(), row.ipAddress(),
                row.userAgentDigest(), row.occurredAt());
    }
}
