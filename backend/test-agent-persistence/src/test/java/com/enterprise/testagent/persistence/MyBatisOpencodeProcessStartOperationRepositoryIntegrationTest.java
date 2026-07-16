package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperation;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisOpencodeProcessStartOperationRepository;
import com.enterprise.testagent.persistence.mybatis.OpencodeProcessStartOperationMapper;
import java.time.Instant;
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
 * 验证 opencode 进程初始化进度仓储通过 MyBatis XML 访问数据库。
 */
class MyBatisOpencodeProcessStartOperationRepositoryIntegrationTest {

    private static final UserId USER_ID = new UserId("usr_opi_mybatis");
    private static final UserId OTHER_USER_ID = new UserId("usr_opi_other");
    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");

    private SingleConnectionDataSource dataSource;
    private OpencodeProcessStartOperationRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_mybatis_opi_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        insertUser(USER_ID);
        insertUser(OTHER_USER_ID);

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        OpencodeProcessStartOperationMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(OpencodeProcessStartOperationMapper.class);
        repository = new MyBatisOpencodeProcessStartOperationRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void operationLifecycleIsPersistedAndScopedByUser() {
        OpencodeProcessStartOperation started = repository.start(
                "opi_mybatis_1234567890",
                USER_ID,
                "opencode",
                "trace_opi_mybatis",
                NOW);

        assertThat(started.status()).isEqualTo(OpencodeProcessStartOperationStatus.RUNNING);
        assertThat(repository.findById("opi_mybatis_1234567890", OTHER_USER_ID)).isEmpty();

        repository.markStep(
                "opi_mybatis_1234567890",
                OpencodeProcessStartOperationStep.HEALTH_CHECKING,
                NOW.plusSeconds(1));
        assertThat(repository.findById("opi_mybatis_1234567890", USER_ID))
                .get()
                .satisfies(operation -> {
                    assertThat(operation.currentStep()).isEqualTo(OpencodeProcessStartOperationStep.HEALTH_CHECKING);
                    assertThat(operation.updatedAt()).isEqualTo(NOW.plusSeconds(1));
                });

        repository.markFailed(
                "opi_mybatis_1234567890",
                OpencodeProcessStartOperationStep.HEALTH_CHECKING,
                "OPENCODE_UNAVAILABLE",
                "健康检查失败",
                NOW.plusSeconds(2));

        assertThat(repository.findById("opi_mybatis_1234567890", USER_ID))
                .get()
                .satisfies(operation -> {
                    assertThat(operation.status()).isEqualTo(OpencodeProcessStartOperationStatus.FAILED);
                    assertThat(operation.errorCode()).isEqualTo("OPENCODE_UNAVAILABLE");
                    assertThat(operation.errorMessage()).isEqualTo("健康检查失败");
                });
    }

    @Test
    void succeededOperationStoresProcessAndServiceAddress() {
        repository.start("opi_mybatis_success_123", USER_ID, "opencode", "trace_opi_success", NOW);

        repository.markSucceeded(
                "opi_mybatis_success_123",
                "ops_mybatis_123",
                "10.8.0.21:4100",
                NOW.plusSeconds(3));

        assertThat(repository.findById("opi_mybatis_success_123", USER_ID))
                .get()
                .satisfies(operation -> {
                    assertThat(operation.status()).isEqualTo(OpencodeProcessStartOperationStatus.SUCCEEDED);
                    assertThat(operation.currentStep()).isEqualTo(OpencodeProcessStartOperationStep.COMPLETED);
                    assertThat(operation.processId()).isEqualTo("ops_mybatis_123");
                    assertThat(operation.serviceAddress()).isEqualTo("10.8.0.21:4100");
                });
    }

    private void insertUser(UserId userId) {
        JdbcClient.create(dataSource)
                .sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, :authId, :username, :passwordHash, 'ACTIVE', :now, :now)
                        """)
                .param("userId", userId.value())
                .param("authId", "auth_" + userId.value())
                .param("username", "name_" + userId.value())
                .param("passwordHash", "noop")
                .param("now", NOW)
                .update();
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
