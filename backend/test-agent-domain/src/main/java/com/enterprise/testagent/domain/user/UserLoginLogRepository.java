package com.enterprise.testagent.domain.user;

/**
 * 用户登录日志仓储接口。
 */
public interface UserLoginLogRepository {

    /**
     * 保存登录日志。
     */
    void save(UserLoginLog loginLog);
}
