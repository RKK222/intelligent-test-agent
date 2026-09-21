package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.team.TeamOversightAuditEvent;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 系统管理员团队关系与团队审计 SQL mapper。 */
@Mapper
public interface SystemAdminTeamMapper {

    List<SystemAdminTeamMemberRow> findMembers(
            @Param("ownerUserId") String ownerUserId,
            @Param("keywordPattern") String keywordPattern,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countMembers(@Param("ownerUserId") String ownerUserId, @Param("keywordPattern") String keywordPattern);

    List<SystemAdminTeamUserRow> findCandidates(
            @Param("ownerUserId") String ownerUserId,
            @Param("keywordPattern") String keywordPattern,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countCandidates(@Param("ownerUserId") String ownerUserId, @Param("keywordPattern") String keywordPattern);

    SystemAdminTeamMemberRow findRelation(
            @Param("ownerUserId") String ownerUserId,
            @Param("memberUserId") String memberUserId);

    List<String> findActiveMemberIds(@Param("ownerUserId") String ownerUserId);

    boolean existsActiveMember(
            @Param("ownerUserId") String ownerUserId,
            @Param("memberUserId") String memberUserId);

    void upsertMember(
            @Param("ownerUserId") String ownerUserId,
            @Param("memberUserId") String memberUserId,
            @Param("addedByUserId") String addedByUserId,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    int deactivateMember(
            @Param("ownerUserId") String ownerUserId,
            @Param("memberUserId") String memberUserId,
            @Param("deletedAt") Instant deletedAt);

    void insertTeamAudit(TeamOversightAuditEvent event);
}
