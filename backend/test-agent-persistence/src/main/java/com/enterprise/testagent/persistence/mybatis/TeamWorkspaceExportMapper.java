package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.team.TeamWorkspaceExportItem;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportJob;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 团队整组导出任务 SQL 映射。 */
@Mapper
public interface TeamWorkspaceExportMapper {

    void insertJob(TeamWorkspaceExportJob job);

    void insertItem(TeamWorkspaceExportItem item);

    TeamWorkspaceExportJob findJob(@Param("exportId") String exportId);

    List<TeamWorkspaceExportItem> findItems(@Param("exportId") String exportId);

    int updateJob(TeamWorkspaceExportJob job);

    int updateItem(TeamWorkspaceExportItem item);

    int claimItem(
            @Param("exportItemId") String exportItemId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now,
            @Param("leaseExpiresAt") Instant leaseExpiresAt);

    int cancel(
            @Param("exportId") String exportId,
            @Param("actorUserId") String actorUserId,
            @Param("now") Instant now);

    List<TeamWorkspaceExportJob> findActiveByTeamMember(
            @Param("ownerUserId") String ownerUserId,
            @Param("memberUserId") String memberUserId);

    List<TeamWorkspaceExportJob> findExpiredReady(
            @Param("coordinatorLinuxServerId") String coordinatorLinuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);
}
