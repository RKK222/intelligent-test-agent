package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户删除 MyBatis mapper；SQL 统一维护在 XML 中，接口只声明批量入参与受影响行数。
 */
@Mapper
public interface UserDeletionMapper {

    List<String> lockExistingUserIds(@Param("userIds") List<String> userIds);

    List<String> findDeletionBlockedUserIds(@Param("userIds") List<String> userIds);

    int deleteAnalyticsHourly(@Param("userIds") List<String> userIds);

    int deleteAnalyticsDaily(@Param("userIds") List<String> userIds);

    int deleteAiMessageFeedbacks(@Param("userIds") List<String> userIds);

    int deleteWorkspaceSyncRecords(@Param("userIds") List<String> userIds);

    int deleteWorkspaceBranchPreferences(@Param("userIds") List<String> userIds);

    int deleteApplicationWorkspacePreferences(@Param("userIds") List<String> userIds);

    int deleteGlobalWorkspacePreferences(@Param("userIds") List<String> userIds);

    int deleteUserSshKeys(@Param("userIds") List<String> userIds);

    int deleteApplicationMembers(@Param("userIds") List<String> userIds);

    int deleteUserLoginLogs(@Param("userIds") List<String> userIds);

    int deleteUserRoles(@Param("userIds") List<String> userIds);

    int deleteUsers(@Param("userIds") List<String> userIds);
}
