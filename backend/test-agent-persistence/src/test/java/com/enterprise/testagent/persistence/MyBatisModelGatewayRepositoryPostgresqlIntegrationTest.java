package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.configuration.ModelProbeResult;
import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDailyRepository;
import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDelta;
import com.enterprise.testagent.persistence.mybatis.InternalModelProviderModelMapper;
import com.enterprise.testagent.persistence.mybatis.ModelGatewayUsageDailyMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelProviderModelRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisModelGatewayUsageDailyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 PostgreSQL 验证模型目录迁移、方言分支和每日聚合并发原子性。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisModelGatewayRepositoryPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");
    private static final int WORKERS = 8;
    private static final int INCREMENTS_PER_WORKER = 25;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static DataSource dataSource;
    private static JdbcClient jdbc;
    private static InternalModelProviderModelRepository models;
    private static ModelGatewayUsageDailyRepository usage;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        postgresDataSource.setURL(POSTGRES.getJdbcUrl());
        postgresDataSource.setUser(POSTGRES.getUsername());
        postgresDataSource.setPassword(POSTGRES.getPassword());
        dataSource = postgresDataSource;

        // 模拟目标环境已经执行当前仓库此前最高版本，再按默认顺序升级到 LobeHub migration。
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260728210000")
                .load()
                .migrate();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        jdbc = JdbcClient.create(dataSource);
        seedProviderAndUser();
        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory(dataSource));
        models = new MyBatisInternalModelProviderModelRepository(
                template.getMapper(InternalModelProviderModelMapper.class));
        usage = new MyBatisModelGatewayUsageDailyRepository(
                template.getMapper(ModelGatewayUsageDailyMapper.class));
    }

    @Test
    void postgresqlProbeUpsertReplacesPreviousResult() {
        models.replaceForProvider("provider-lobehub", List.of(model()), NOW);
        models.saveProbeResult(new ModelProbeResult(
                "provider-lobehub", "enterprise-chat", ModelCapability.CHAT, false, NOW.plusSeconds(1)));
        models.saveProbeResult(new ModelProbeResult(
                "provider-lobehub", "enterprise-chat", ModelCapability.CHAT, true, NOW.plusSeconds(2)));

        assertThat(models.findByModelId("enterprise-chat"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.probedCapabilities()).containsExactly(ModelCapability.CHAT);
                    assertThat(saved.lastProbedAt()).isEqualTo(NOW.plusSeconds(2));
                });
        assertThat(jdbc.sql("select count(*) from internal_model_provider_model_probes")
                .query(Long.class)
                .single()).isEqualTo(1L);
    }

    @Test
    void concurrentUsageIncrementsRemainAtomic() throws Exception {
        ModelGatewayUsageDelta delta = new ModelGatewayUsageDelta(
                LocalDate.of(2026, 7, 30),
                "lobehub",
                "usr_lobehub_pg",
                "provider-lobehub",
                "enterprise-chat",
                "/chat/completions",
                true,
                3,
                2,
                5,
                7);
        CountDownLatch ready = new CountDownLatch(WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        try {
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, WORKERS)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        for (int index = 0; index < INCREMENTS_PER_WORKER; index++) {
                            usage.increment(delta);
                        }
                        return null;
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        long expected = (long) WORKERS * INCREMENTS_PER_WORKER;
        assertThat(jdbc.sql("""
                        select request_count, success_count, input_tokens, output_tokens,
                               total_tokens, duration_ms
                        from model_gateway_usage_daily
                        where user_id = 'usr_lobehub_pg'
                        """)
                .query((result, row) -> List.of(
                        result.getLong("request_count"),
                        result.getLong("success_count"),
                        result.getLong("input_tokens"),
                        result.getLong("output_tokens"),
                        result.getLong("total_tokens"),
                        result.getLong("duration_ms")))
                .single()).containsExactly(expected, expected, expected * 3, expected * 2, expected * 5, expected * 7);
    }

    private static void seedProviderAndUser() {
        jdbc.sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at
                        ) values (
                            'usr_lobehub_pg', 'auth_lobehub_pg', 'lobehub-pg', 'hash', 'ACTIVE', :now, :now
                        )
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        jdbc.sql("""
                        insert into internal_model_tokens(name, token_value, created_at, updated_at)
                        values ('LobeHub token', 'secret', :now, :now)
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        Long tokenId = jdbc.sql("select token_id from internal_model_tokens where name = 'LobeHub token'")
                .query(Long.class)
                .single();
        jdbc.sql("""
                        insert into internal_model_providers(
                            provider_id, name, base_url, enabled, sort_order, token_id, created_at, updated_at
                        ) values (
                            'provider-lobehub', 'LobeHub Provider', 'http://models.internal/v1',
                            true, 0, :tokenId, :now, :now
                        )
                        """)
                .param("tokenId", tokenId)
                .param("now", Timestamp.from(NOW))
                .update();
    }

    private static InternalModelProviderModel model() {
        return new InternalModelProviderModel(
                "provider-lobehub",
                "enterprise-chat",
                "upstream-chat",
                "企业对话",
                128_000L,
                true,
                Set.of(ModelCapability.CHAT),
                Set.of(),
                null,
                NOW,
                NOW);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setDatabaseIdProvider(databaseIdProvider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
