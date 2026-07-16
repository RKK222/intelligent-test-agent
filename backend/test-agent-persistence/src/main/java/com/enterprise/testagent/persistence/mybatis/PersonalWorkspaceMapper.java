package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 个人工作区 MyBatis mapper；新增 SQL 统一维护在 XML 中。
 */
@Mapper
public interface PersonalWorkspaceMapper {

    int updateLocation(
            @Param("personalWorkspaceId") String personalWorkspaceId,
            @Param("branch") String branch,
            @Param("repoRootPath") String repoRootPath,
            @Param("workspaceRootPath") String workspaceRootPath,
            @Param("baseCommit") String baseCommit,
            @Param("updatedAt") Instant updatedAt);
}
