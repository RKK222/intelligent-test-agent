package com.enterprise.testagent.domain.user;

import java.util.List;

/**
 * 用户删除持久化端口，负责锁定目标用户、识别不可安全删除的业务关联并清理账号附属数据。
 *
 * <p>删除只面向未承载会话、工作区、运行进程等业务资产的存量账号；具体关系型 SQL
 * 由 persistence 模块通过 MyBatis XML 实现。
 */
public interface UserDeletionRepository {

    /**
     * 锁定并返回实际存在的目标用户 ID，供批量删除在同一事务内做全有或全无校验。
     */
    List<UserId> lockExistingUserIds(List<UserId> userIds);

    /**
     * 返回仍被业务资产或审计配置引用、因而不能直接物理删除的用户 ID。
     */
    List<UserId> findDeletionBlockedUserIds(List<UserId> userIds);

    /**
     * 清理可安全随账号移除的附属数据并删除用户主记录。
     *
     * @return 实际删除的用户主记录数
     */
    int deleteUsers(List<UserId> userIds);
}
