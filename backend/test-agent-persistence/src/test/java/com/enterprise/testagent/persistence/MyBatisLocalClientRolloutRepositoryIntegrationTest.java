package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.localclient.LocalClientRolloutEntry;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.LocalClientMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientRolloutRepository;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 使用 H2 PostgreSQL 模式验证灰度名单的 MyBatis XML 查询、幂等启用和保留审计的禁用。 */
class MyBatisLocalClientRolloutRepositoryIntegrationTest {

    private static final UserId TARGET = new UserId("usr_rollout_target");
    private static final UserId ADMIN = new UserId("usr_rollout_admin");
    private static final Instant CREATED_AT = Instant.parse("2026-08-17T10:00:00Z");
    private SingleConnectionDataSource dataSource;
    private MyBatisLocalClientRolloutRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_local_client_rollout_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("create table users(user_id varchar(128) primary key)").update();
        jdbc.sql("""
                create table local_client_rollout_users(
                    user_id varchar(128) primary key references users(user_id),
                    enabled boolean not null,
                    updated_by_user_id varchar(128) not null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """).update();
        jdbc.sql("insert into users(user_id) values (?), (?)")
                .params(TARGET.value(), ADMIN.value()).update();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        LocalClientMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(LocalClientMapper.class);
        repository = new MyBatisLocalClientRolloutRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void enableListAndDisableAreFailClosedAndKeepAuditRow() {
        assertThat(repository.isEnabled(TARGET)).isFalse();

        repository.save(new LocalClientRolloutEntry(TARGET, true, ADMIN, CREATED_AT, CREATED_AT));
        assertThat(repository.isEnabled(TARGET)).isTrue();
        assertThat(repository.countEnabled()).isEqualTo(1);
        assertThat(repository.findEnabledPage(0, 50)).extracting(entry -> entry.userId().value())
                .containsExactly(TARGET.value());

        Instant disabledAt = CREATED_AT.plusSeconds(60);
        assertThat(repository.disable(TARGET, ADMIN, disabledAt)).isTrue();
        assertThat(repository.isEnabled(TARGET)).isFalse();
        assertThat(repository.countEnabled()).isZero();
        assertThat(repository.findByUserId(TARGET)).get().satisfies(entry -> {
            assertThat(entry.enabled()).isFalse();
            assertThat(entry.createdAt()).isEqualTo(CREATED_AT);
            assertThat(entry.updatedAt()).isEqualTo(disabledAt);
        });
    }
}
