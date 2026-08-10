package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordQuery;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelTtftDistribution;
import com.enterprise.testagent.persistence.mybatis.InternalModelObservabilityMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelCallRecordRepository;
import java.time.Instant;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 PostgreSQL 验证已部署首 token 基线升级、流完成列和 ON CONFLICT 聚合。 */
@Testcontainers(disabledWithoutDocker = true)
class InternalModelObservabilityPostgresqlIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-07T12:34:56Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static JdbcClient jdbc;
    private static InternalModelCallRecordRepository repository;
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        Flyway baseline = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260808143301")
                .load();
        baseline.migrate();
        jdbc = JdbcClient.create(dataSource);
        assertThat(columnExists("internal_model_call_records", "stream_complete_ms")).isFalse();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertThat(columnExists("internal_model_call_records", "stream_complete_ms")).isTrue();
        assertThat(jdbc.sql("""
                        select success from flyway_schema_history
                        where version = '20260808143302'
                        """)
                .query(Boolean.class)
                .single()).isTrue();

        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory(dataSource));
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionTestConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(
                PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(
                MyBatisInternalModelCallRecordRepository.class,
                () -> new MyBatisInternalModelCallRecordRepository(
                        template.getMapper(InternalModelObservabilityMapper.class)));
        context.refresh();
        repository = context.getBean(InternalModelCallRecordRepository.class);
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
    }

    @AfterAll
    static void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void recordsAndAggregatesOnlyCompletedStreams() {
        repository.record(record(120L));
        repository.record(record(null));

        assertThat(jdbc.sql("""
                        select stream_complete_ms from internal_model_call_records
                        where provider_id = 'provider-pg'
                        order by id
                        """)
                .query(Long.class)
                .list()).containsExactly(120L, null);

        var stat = repository.queryHourlyStats(
                        "provider-pg", InternalModelCallSource.USER_CALL,
                        STARTED_AT.minusSeconds(3600), STARTED_AT.plusSeconds(3600))
                .getFirst();
        assertThat(stat.requestCount()).isEqualTo(2L);
        assertThat(stat.firstTokenCount()).isEqualTo(2L);
        assertThat(stat.streamCompleteMillisSum()).isEqualTo(120L);
        assertThat(stat.streamCompleteMillisMax()).isEqualTo(120L);
        assertThat(stat.streamCompleteCount()).isEqualTo(1L);
    }

    @Test
    void recordsPreForwardFailureWithUnknownDimensionsThroughSpringTransactionProxy() {
        InternalModelCallRecord failure = new InternalModelCallRecord(
                null,
                "unknown",
                "unknown",
                "/chat/completions",
                InternalModelCallSource.USER_CALL,
                InternalModelCallOutcome.PROXY_AUTH_FAILED,
                null,
                "PlatformException",
                false,
                1L,
                null,
                null,
                null,
                "trace_pg_stage_failure",
                null,
                STARTED_AT.plusSeconds(120));

        repository.record(failure);

        var page = repository.query(new InternalModelCallRecordQuery(
                "unknown",
                java.util.List.of(InternalModelCallOutcome.PROXY_AUTH_FAILED),
                InternalModelCallSource.USER_CALL,
                STARTED_AT.minusSeconds(60),
                STARTED_AT.plusSeconds(3600),
                new PageRequest(1, 20)));
        assertThat(page.items()).singleElement().satisfies(record -> {
            assertThat(record.providerId()).isEqualTo("unknown");
            assertThat(record.model()).isEqualTo("unknown");
        });
        assertThat(repository.queryHourlyStats(
                        "unknown",
                        InternalModelCallSource.USER_CALL,
                        STARTED_AT.minusSeconds(3600),
                        STARTED_AT.plusSeconds(3600)))
                .singleElement()
                .satisfies(stat -> assertThat(stat.requestCount()).isEqualTo(1L));
    }

    @Test
    void calculatesTtftQuartilesWithPostgresqlPercentileCont() {
        repository.record(distributionRecord(100L, 1));
        repository.record(distributionRecord(200L, 2));
        repository.record(distributionRecord(300L, 3));
        repository.record(distributionRecord(400L, 4));

        InternalModelTtftDistribution distribution = repository.queryTtftDistribution(
                "provider-pg-distribution",
                java.util.List.of(InternalModelCallOutcome.SUCCESS),
                InternalModelCallSource.USER_CALL,
                STARTED_AT.minusSeconds(1),
                STARTED_AT.plusSeconds(10));

        assertThat(distribution.sampleCount()).isEqualTo(4);
        assertThat(distribution.minimumMillis()).isEqualTo(100.0);
        assertThat(distribution.firstQuartileMillis()).isEqualTo(175.0);
        assertThat(distribution.medianMillis()).isEqualTo(250.0);
        assertThat(distribution.thirdQuartileMillis()).isEqualTo(325.0);
        assertThat(distribution.maximumMillis()).isEqualTo(400.0);
    }

    private static InternalModelCallRecord record(Long streamCompleteMillis) {
        return new InternalModelCallRecord(
                null,
                "provider-pg",
                "model-pg",
                "/chat/completions",
                InternalModelCallSource.USER_CALL,
                InternalModelCallOutcome.CLIENT_DISCONNECTED,
                200,
                null,
                true,
                150L,
                10L,
                40L,
                streamCompleteMillis,
                "trace_pg",
                "ucid_pg",
                STARTED_AT);
    }

    private static InternalModelCallRecord distributionRecord(long firstTokenMillis, long offsetSeconds) {
        return new InternalModelCallRecord(
                null,
                "provider-pg-distribution",
                "model-pg",
                "/chat/completions",
                InternalModelCallSource.USER_CALL,
                InternalModelCallOutcome.SUCCESS,
                200,
                null,
                true,
                1000L,
                10L,
                firstTokenMillis,
                firstTokenMillis,
                "trace_pg_distribution_" + offsetSeconds,
                "ucid_pg",
                STARTED_AT.plusSeconds(offsetSeconds));
    }

    private static boolean columnExists(String tableName, String columnName) {
        return jdbc.sql("""
                        select count(*) from information_schema.columns
                        where table_schema = 'public'
                          and table_name = :tableName
                          and column_name = :columnName
                        """)
                .param("tableName", tableName)
                .param("columnName", columnName)
                .query(Long.class)
                .single() > 0;
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setDatabaseIdProvider(databaseIdProvider);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factory.getObject();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionTestConfiguration {
    }
}
