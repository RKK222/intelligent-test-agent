package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 工作空间 Git 权限巡检 MyBatis mapper；SQL 统一维护在 XML。 */
@Mapper
public interface WorkspaceGitAccessCheckMapper {

    List<ApplicationWorkspaceGitAccessCandidateRow> findApplicationCandidatesAfter(
            @Param("afterUserId") String afterUserId,
            @Param("afterWorkspaceId") String afterWorkspaceId,
            @Param("limit") int limit);

    List<LocalWorkspaceGitAccessCandidateRow> findLocalCandidatesAfter(
            @Param("afterUserId") String afterUserId,
            @Param("afterWorkspaceId") String afterWorkspaceId,
            @Param("limit") int limit);

    WorkspaceGitAccessCheckRow findApplicationCheck(
            @Param("userId") String userId,
            @Param("targetId") String targetId);

    WorkspaceGitAccessCheckRow findLocalCheck(
            @Param("userId") String userId,
            @Param("targetId") String targetId);

    int upsertApplicationCheck(
            @Param("row") WorkspaceGitAccessCheckRow row,
            @Param("now") Instant now);

    int upsertLocalCheck(
            @Param("row") WorkspaceGitAccessCheckRow row,
            @Param("now") Instant now);
}
