package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 排查授权与审计 MyBatis mapper；SQL 统一维护在 XML。 */
@Mapper
public interface SupportAccessMapper {

    int insertGrant(SupportAccessGrantRow row);

    SupportAccessGrantRow findGrant(@Param("grantId") String grantId);

    String findLatestIncidentId(@Param("actorUserId") String actorUserId);

    int revokeGrant(
            @Param("grantId") String grantId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);

    int insertAuditEvent(SupportAccessAuditEventRow row);

    List<SupportAccessAuditEventRow> findAuditEvents(
            @Param("actorUserId") String actorUserId,
            @Param("targetUserId") String targetUserId,
            @Param("incidentId") String incidentId,
            @Param("outcome") String outcome,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countAuditEvents(
            @Param("actorUserId") String actorUserId,
            @Param("targetUserId") String targetUserId,
            @Param("incidentId") String incidentId,
            @Param("outcome") String outcome);

    int deleteAuditEventsBefore(@Param("cutoff") Instant cutoff);

    int deleteEndedGrantsBefore(@Param("cutoff") Instant cutoff);
}
