package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserLoginLog;
import com.icbc.testagent.domain.user.UserLoginLogRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 用户登录日志 JDBC Repository。
 */
@Repository
public class JdbcUserLoginLogRepository extends JdbcRepositorySupport implements UserLoginLogRepository {

    private final JdbcClient jdbcClient;

    /**
     * 注入 JdbcClient。
     */
    public JdbcUserLoginLogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存登录日志。
     */
    @Override
    public void save(UserLoginLog loginLog) {
        jdbcClient.sql("""
                        insert into user_login_logs(log_id, user_id, login_at, ip_address, user_agent, login_result)
                        values (:logId, :userId, :loginAt, :ipAddress, :userAgent, :loginResult)
                        """)
                .param("logId", loginLog.logId())
                .param("userId", loginLog.userId().value())
                .param("loginAt", timestamp(loginLog.loginAt()))
                .param("ipAddress", loginLog.ipAddress())
                .param("userAgent", loginLog.userAgent())
                .param("loginResult", loginLog.loginResult().name())
                .update();
    }
}
