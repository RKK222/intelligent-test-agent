package com.enterprise.testagent.domain.team;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 团队整组导出任务的 MyBatis 持久化端口。 */
public interface TeamWorkspaceExportRepository {

    void insertJob(TeamWorkspaceExportJob job);

    void insertItem(TeamWorkspaceExportItem item);

    Optional<TeamWorkspaceExportJob> findJob(String exportId);

    List<TeamWorkspaceExportItem> findItems(String exportId);

    void updateJob(TeamWorkspaceExportJob job);

    void updateItem(TeamWorkspaceExportItem item);

    /** 原子领取一个待处理 worktree，防止协调器重试时重复打包。 */
    boolean claimItem(String exportItemId, String leaseOwner, String leaseToken, Instant now, Instant leaseExpiresAt);

    boolean cancel(String exportId, String actorUserId, Instant now);

    /** 查找仍可用且包含指定团队成员的导出，用于成员移除后立即撤销下载权限。 */
    List<TeamWorkspaceExportJob> findActiveByTeamMember(String ownerUserId, String memberUserId);

    /** 只返回指定协调节点应负责清理的过期任务，避免共享数据库下由其它节点误标记。 */
    List<TeamWorkspaceExportJob> findExpiredReady(String coordinatorLinuxServerId, Instant now, int limit);
}
