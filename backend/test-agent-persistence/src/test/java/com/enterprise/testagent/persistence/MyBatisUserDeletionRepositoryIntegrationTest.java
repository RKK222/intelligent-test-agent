package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.user.UserDeletionRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserDeletionRepository;
import com.enterprise.testagent.persistence.mybatis.UserDeletionMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证用户删除 MyBatis XML 能识别受保护业务引用，并清理可安全随账号删除的附属表。
 */
class MyBatisUserDeletionRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private UserDeletionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_user_deletion_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=true"
                        .formatted(UUID.randomUUID().toString().replace("-", ""))
                        + ";INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
                "sa",
                "",
                true);
        // 后续 rollout migration 使用 PostgreSQL partial expression index，H2 无法解析；先迁移到稳定基线，
        // 再补齐本 mapper 只读引用的后续表最小结构。完整 migration 链由 PostgreSQL 部署校验负责。
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260715213000")
                .load()
                .migrate();
        jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("create table reference_repository_states (credential_user_id varchar(128))").update();
        jdbcClient.sql("create table night_execution_tasks (owner_user_id varchar(128))").update();
        jdbcClient.sql("create table night_execution_session_locks (owner_user_id varchar(128))").update();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        UserDeletionMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(UserDeletionMapper.class);
        repository = new MyBatisUserDeletionRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void deletesAccountAttachmentsForUnusedUsers() {
        UserId userId = insertUser("usr_delete", "AUTH_DELETE", "待删除用户");
        jdbcClient.sql("""
                        insert into user_roles(user_id, dict_id, created_at)
                        values (:userId, 'dict_role_user', :createdAt)
                        """)
                .param("userId", userId.value())
                .param("createdAt", Timestamp.from(NOW))
                .update();
        jdbcClient.sql("""
                        insert into user_login_logs(log_id, user_id, login_at, login_result)
                        values ('log_delete', :userId, :loginAt, 'SUCCESS')
                        """)
                .param("userId", userId.value())
                .param("loginAt", Timestamp.from(NOW))
                .update();
        jdbcClient.sql("""
                        insert into applications(app_id, app_name, enabled, created_at, updated_at)
                        values ('app_delete_test', '删除测试应用', true, :createdAt, :updatedAt)
                        """)
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
        jdbcClient.sql("""
                        insert into application_members(app_id, user_id, created_at, updated_at)
                        values ('app_delete_test', :userId, :createdAt, :updatedAt)
                        """)
                .param("userId", userId.value())
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();

        assertThat(repository.lockExistingUserIds(List.of(userId))).containsExactly(userId);
        assertThat(repository.findDeletionBlockedUserIds(List.of(userId))).isEmpty();
        assertThat(repository.deleteUsers(List.of(userId))).isEqualTo(1);

        assertThat(count("users", userId)).isZero();
        assertThat(count("user_roles", userId)).isZero();
        assertThat(count("user_login_logs", userId)).isZero();
        assertThat(count("application_members", userId)).isZero();
    }

    @Test
    void reportsUsersReferencedByProtectedBusinessRecords() {
        UserId safeUserId = insertUser("usr_safe", "AUTH_SAFE", "安全用户");
        UserId blockedUserId = insertUser("usr_blocked", "AUTH_BLOCKED", "受保护用户");
        jdbcClient.sql("""
                        insert into opencode_process_start_operations(
                            operation_id, requested_by_user_id, agent_id, status, current_step,
                            trace_id, created_at, updated_at)
                        values ('op_start_blocked', :userId, 'opencode', 'SUCCEEDED', 'COMPLETED',
                            'trace_user_delete_test', :createdAt, :updatedAt)
                        """)
                .param("userId", blockedUserId.value())
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();

        assertThat(repository.findDeletionBlockedUserIds(List.of(safeUserId, blockedUserId)))
                .containsExactly(blockedUserId);
    }

    private UserId insertUser(String userId, String unifiedAuthId, String username) {
        jdbcClient.sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, :unifiedAuthId, :username, '$2a$10$hashedvalue', 'ACTIVE', :createdAt, :updatedAt)
                        """)
                .param("userId", userId)
                .param("unifiedAuthId", unifiedAuthId)
                .param("username", username)
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
        return new UserId(userId);
    }

    private long count(String table, UserId userId) {
        return jdbcClient.sql("select count(*) from " + table + " where user_id = :userId")
                .param("userId", userId.value())
                .query(Long.class)
                .single();
    }

    /**
     * 直接加载全部 XML mapper，确保测试与生产使用相同的 MyBatis SQL。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
