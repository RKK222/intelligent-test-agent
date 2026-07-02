package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.persistence.mybatis.DatabaseIdentityMapper;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证 DatabaseIdentityMapper 的 restartIdentity 在 H2(PostgreSQL 模式) 下可用。
 *
 * <p>queryIdentityStatus 依赖 PostgreSQL 系统目录（pg_get_serial_sequence / pg_sequences），
 * 由真实 PostgreSQL 环境验证，不在此断言其返回值。
 */
class MyBatisDatabaseIdentityMapperIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private DatabaseIdentityMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_mybatis_identity_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        SqlSessionFactory factory = sqlSessionFactory();
        mapper = new SqlSessionTemplate(factory).getMapper(DatabaseIdentityMapper.class);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void restartIdentityMovesSequenceForwardOnH2() throws Exception {
        // 先插入一行以确认 users 表 identity 正常工作
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at) "
                    + "values ('usr_test', 'AUTH_TEST', 'test', 'hash', 'ACTIVE', now(), now())");
            var rs = stmt.executeQuery("select max(id) from users");
            assertThat(rs.next()).isTrue();
            long firstId = rs.getLong(1);
            assertThat(firstId).isPositive();

            // 重启 identity 到更大的值
            mapper.restartIdentity("users", 1000000L);

            // 再插入一行，ID 应 >= 1000000
            stmt.execute("insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at) "
                    + "values ('usr_test2', 'AUTH_TEST2', 'test2', 'hash', 'ACTIVE', now(), now())");
            rs = stmt.executeQuery("select max(id) from users");
            assertThat(rs.next()).isTrue();
            long secondId = rs.getLong(1);
            assertThat(secondId).isGreaterThanOrEqualTo(1000000L);
        }
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mybatis/**/*.xml"));
        factoryBean.getObject().getConfiguration().setMapUnderscoreToCamelCase(true);
        return factoryBean.getObject();
    }
}
