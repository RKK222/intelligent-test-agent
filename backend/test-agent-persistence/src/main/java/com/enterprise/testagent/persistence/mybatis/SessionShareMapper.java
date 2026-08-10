package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.sessionshare.SessionShareAuditEvent;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话协作分享 MyBatis mapper；全部关系型 SQL 维护在 XML 中。 */
@Mapper
public interface SessionShareMapper {

    int insertShare(@Param("row") SessionShareRow row);

    int updateShare(@Param("row") SessionShareRow row, @Param("expectedVersion") long expectedVersion);

    SessionShareRow findBySessionId(@Param("sessionId") String sessionId);

    SessionShareRow findByShareId(@Param("shareId") String shareId);

    int insertMembership(@Param("row") SessionShareMembershipRow row);

    int updateMembership(@Param("row") SessionShareMembershipRow row);

    List<SessionShareMembershipRow> findMemberships(@Param("shareId") String shareId);

    List<SharedSessionListRow> findSharedWith(
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countSharedWith(@Param("userId") String userId);

    List<SessionShareCandidateRow> findCandidates(
            @Param("excludedUserId") String excludedUserId,
            @Param("pattern") String pattern,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countCandidates(
            @Param("excludedUserId") String excludedUserId,
            @Param("pattern") String pattern);

    int claimLegacySessionOwner(
            @Param("sessionId") String sessionId,
            @Param("actorUserId") String actorUserId);

    int insertAudit(@Param("event") SessionShareAuditEvent event);

    int deleteAuditEventsBefore(@Param("cutoff") Instant cutoff);
}
