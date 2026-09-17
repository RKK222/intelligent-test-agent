package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户关联工作区 MyBatis mapper；SQL 统一维护在 XML。 */
@Mapper
public interface UserWorkspaceQueryMapper {

    List<UserWorkspaceRow> findUserWorkspaces(
            @Param("userId") String userId,
            @Param("queryPattern") String queryPattern,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countUserWorkspaces(
            @Param("userId") String userId,
            @Param("queryPattern") String queryPattern);

    UserWorkspaceRow findUserWorkspace(
            @Param("userId") String userId,
            @Param("workspaceId") String workspaceId);
}
