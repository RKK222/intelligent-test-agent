package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.user.UserDeletionRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 用户删除领域端口的 MyBatis 实现。
 *
 * <p>账号附属表按外键依赖顺序清理；共享工作区、会话、运行进程和审计配置只做阻断检查，
 * 不在用户管理入口中级联删除。
 */
@Repository
public class MyBatisUserDeletionRepository implements UserDeletionRepository {

    private final UserDeletionMapper mapper;

    /**
     * 注入用户删除 XML mapper。
     */
    public MyBatisUserDeletionRepository(UserDeletionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<UserId> lockExistingUserIds(List<UserId> userIds) {
        List<String> values = values(userIds);
        return mapper.lockExistingUserIds(values).stream().map(UserId::new).toList();
    }

    @Override
    public List<UserId> findDeletionBlockedUserIds(List<UserId> userIds) {
        List<String> values = values(userIds);
        return mapper.findDeletionBlockedUserIds(values).stream().map(UserId::new).toList();
    }

    @Override
    public int deleteUsers(List<UserId> userIds) {
        List<String> values = values(userIds);
        mapper.deleteAnalyticsHourly(values);
        mapper.deleteAnalyticsDaily(values);
        mapper.deleteAiMessageFeedbacks(values);
        mapper.deleteWorkspaceSyncRecords(values);
        mapper.deleteWorkspaceBranchPreferences(values);
        mapper.deleteApplicationWorkspacePreferences(values);
        mapper.deleteGlobalWorkspacePreferences(values);
        mapper.deleteUserSshKeys(values);
        mapper.deleteApplicationMembers(values);
        mapper.deleteUserLoginLogs(values);
        mapper.deleteUserRoles(values);
        return mapper.deleteUsers(values);
    }

    private List<String> values(List<UserId> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds must not be empty");
        }
        return userIds.stream().map(UserId::value).toList();
    }
}
