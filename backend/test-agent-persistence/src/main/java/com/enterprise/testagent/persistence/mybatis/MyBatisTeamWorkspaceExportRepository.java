package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportItem;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportJob;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 团队整组导出任务的 MyBatis 实现。 */
@Repository
public class MyBatisTeamWorkspaceExportRepository implements TeamWorkspaceExportRepository {

    private final TeamWorkspaceExportMapper mapper;

    public MyBatisTeamWorkspaceExportRepository(TeamWorkspaceExportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertJob(TeamWorkspaceExportJob job) {
        mapper.insertJob(job);
    }

    @Override
    public void insertItem(TeamWorkspaceExportItem item) {
        mapper.insertItem(item);
    }

    @Override
    public Optional<TeamWorkspaceExportJob> findJob(String exportId) {
        return Optional.ofNullable(mapper.findJob(exportId));
    }

    @Override
    public List<TeamWorkspaceExportItem> findItems(String exportId) {
        return mapper.findItems(exportId);
    }

    @Override
    public void updateJob(TeamWorkspaceExportJob job) {
        if (mapper.updateJob(job) != 1) throw missing(job.exportId());
    }

    @Override
    public void updateItem(TeamWorkspaceExportItem item) {
        if (mapper.updateItem(item) != 1) throw missing(item.exportId());
    }

    @Override
    public boolean claimItem(
            String exportItemId, String leaseOwner, String leaseToken, Instant now, Instant leaseExpiresAt) {
        return mapper.claimItem(exportItemId, leaseOwner, leaseToken, now, leaseExpiresAt) == 1;
    }

    @Override
    public boolean cancel(String exportId, String actorUserId, Instant now) {
        return mapper.cancel(exportId, actorUserId, now) == 1;
    }

    @Override
    public List<TeamWorkspaceExportJob> findActiveByTeamMember(String ownerUserId, String memberUserId) {
        return mapper.findActiveByTeamMember(ownerUserId, memberUserId);
    }

    @Override
    public List<TeamWorkspaceExportJob> findExpiredReady(
            String coordinatorLinuxServerId, Instant now, int limit) {
        return mapper.findExpiredReady(coordinatorLinuxServerId, now, limit);
    }

    private PlatformException missing(String exportId) {
        return new PlatformException(ErrorCode.NOT_FOUND, "团队导出任务不存在", java.util.Map.of("exportId", exportId));
    }
}
