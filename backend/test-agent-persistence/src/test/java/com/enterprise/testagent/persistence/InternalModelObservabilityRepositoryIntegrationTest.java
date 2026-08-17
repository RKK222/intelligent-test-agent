package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcomeGroup;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordQuery;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelLatencyDistribution;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatus;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatusRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelThroughputDistribution;
import com.enterprise.testagent.persistence.mybatis.InternalModelObservabilityMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelCallRecordRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelProbeStatusRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 验证内部模型可观测性 migration、明细+聚合同事务落库、分页查询与探活状态 upsert。 */
class InternalModelObservabilityRepositoryIntegrationTest {

    private static final String PROVIDER = "enterprise-deepseek";
    private static final Instant T0 = Instant.parse("2026-08-07T12:00:00Z");
    private SingleConnectionDataSource dataSource;
    private InternalModelCallRecordRepository callRepository;
    private InternalModelProbeStatusRepository probeRepository;
    private static final Set<String> INTERNAL_MODEL_MIGRATIONS = Set.of(
            "V20260808143300__create_internal_model_observability.sql",
            "V20260808143301__add_internal_model_first_token_metrics.sql",
            "V20260808143302__add_internal_model_stream_complete_metrics.sql",
            "V20260810234154__internal_model_call_records_add_token_latency_inputs.sql");

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_imo_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        // baselineOnMigrate 只对非空库生效；先建占位表触发基线，避免 Flyway 从 V1 全量执行 PostgreSQL 专用 SQL。
        JdbcClient.create(dataSource).sql(
                "create table observability_test_placeholder (id bigint primary key)").update();
        migrateInternalModelSchema();
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        InternalModelObservabilityMapper mapper =
                new SqlSessionTemplate(sessionFactory).getMapper(InternalModelObservabilityMapper.class);
        callRepository = new MyBatisInternalModelCallRecordRepository(mapper);
        probeRepository = new MyBatisInternalModelProbeStatusRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    /** 精简 H2 库只装载本模块迁移；完整跨业务升级链由 PostgreSQL 兼容性测试负责。 */
    private void migrateInternalModelSchema() {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("20260808143259")
                .target("20260810234154");
        ResourceProvider defaultProvider = new Scanner<>(
                JavaMigration.class, configuration, configuration.getLocations());
        configuration.resourceProvider(new ResourceProvider() {
            @Override
            public LoadableResource getResource(String name) {
                LoadableResource resource = defaultProvider.getResource(name);
                return resource != null && isInternalModelMigration(resource) ? resource : null;
            }

            @Override
            public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
                return defaultProvider.getResources(prefix, suffixes).stream()
                        .filter(InternalModelObservabilityRepositoryIntegrationTest::isInternalModelMigration)
                        .toList();
            }
        });
        configuration.load().migrate();
    }

    private static boolean isInternalModelMigration(LoadableResource resource) {
        String path = resource.getAbsolutePath().replace('\\', '/');
        return INTERNAL_MODEL_MIGRATIONS.stream().anyMatch(path::endsWith);
    }

    @Test
    void recordsDetailAndHourlyStatInSameTransaction() {
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 1200L, null, T0));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "UPSTREAM_HTTP_ERROR", 500, 800L, "UpstreamException", T0.plusSeconds(60)));

        var page = callRepository.query(new InternalModelCallRecordQuery(
                PROVIDER, List.of(), null, T0.minus(1, ChronoUnit.HOURS), T0.plus(1, ChronoUnit.HOURS), new PageRequest(1, 20)));
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(InternalModelCallRecord::outcome)
                .containsExactly(InternalModelCallOutcome.UPSTREAM_HTTP_ERROR, InternalModelCallOutcome.SUCCESS);

        var stats = callRepository.queryHourlyStats(PROVIDER, T0.minus(1, ChronoUnit.HOURS), T0.plus(2, ChronoUnit.HOURS));
        // 同一小时段内聚合为一行，request_count=2，duration 求和。
        assertThat(stats).hasSize(2);
        var success = stats.stream().filter(s -> "SUCCESS".equals(s.outcome())).findFirst().orElseThrow();
        assertThat(success.requestCount()).isEqualTo(1);
        assertThat(success.durationMillisSum()).isEqualTo(1200L);
        assertThat(success.durationMillisMax()).isEqualTo(1200L);
        assertThat(success.firstTokenMillisSum()).isEqualTo(75L);
        assertThat(success.firstTokenMillisMax()).isEqualTo(75L);
        assertThat(success.firstTokenCount()).isEqualTo(1L);
        assertThat(success.streamCompleteMillisSum()).isEqualTo(75L);
        assertThat(success.streamCompleteMillisMax()).isEqualTo(75L);
        assertThat(success.streamCompleteCount()).isEqualTo(1L);
    }

    @Test
    void aggregatesSameHourIntoOneRow() {
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 100L, null, T0));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 300L, null, T0.plusSeconds(30)));

        var stats = callRepository.queryHourlyStats(PROVIDER, T0.minus(1, ChronoUnit.HOURS), T0.plus(2, ChronoUnit.HOURS));
        var success = stats.stream().filter(s -> "SUCCESS".equals(s.outcome())).findFirst().orElseThrow();
        assertThat(success.requestCount()).isEqualTo(2);
        assertThat(success.durationMillisSum()).isEqualTo(400L);
        assertThat(success.durationMillisMax()).isEqualTo(300L);
        assertThat(success.firstTokenMillisSum()).isEqualTo(150L);
        assertThat(success.firstTokenMillisMax()).isEqualTo(75L);
        assertThat(success.firstTokenCount()).isEqualTo(2L);
        assertThat(success.streamCompleteMillisSum()).isEqualTo(150L);
        assertThat(success.streamCompleteCount()).isEqualTo(2L);
    }

    @Test
    void filtersByOutcomeAndSource() {
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 100L, null, T0));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/responses", "PROVIDER_UNAVAILABLE", null, 10L, "RegistryException", T0.plusSeconds(5)));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "UPSTREAM_HTTP_ERROR", 503, 80L, "UpstreamException", T0.plusSeconds(6)));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "UPSTREAM_CONNECT_FAILED", null, 60L, "ConnectException", T0.plusSeconds(7)));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 200L, null,
                T0.plusSeconds(10), InternalModelCallSource.PROBE, null));

        var failures = callRepository.query(new InternalModelCallRecordQuery(
                null, List.of(InternalModelCallOutcome.PROVIDER_UNAVAILABLE), null, null, null, new PageRequest(1, 20)));
        assertThat(failures.total()).isEqualTo(1);
        assertThat(failures.items().getFirst().endpoint()).isEqualTo("/responses");
        assertThat(failures.items().getFirst().httpStatus()).isNull();

        var upstreamFailures = callRepository.query(new InternalModelCallRecordQuery(
                null, InternalModelCallOutcomeGroup.UPSTREAM_FAILURE.outcomes(), null,
                null, null, new PageRequest(1, 20)));
        assertThat(upstreamFailures.total()).isEqualTo(2);
        assertThat(upstreamFailures.items()).extracting(InternalModelCallRecord::outcome)
                .containsExactlyInAnyOrder(
                        InternalModelCallOutcome.UPSTREAM_HTTP_ERROR,
                        InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED);

        var probeOnly = callRepository.query(new InternalModelCallRecordQuery(
                null, List.of(), InternalModelCallSource.PROBE, null, null, new PageRequest(1, 20)));
        assertThat(probeOnly.total()).isEqualTo(1);

        var userOnly = callRepository.query(new InternalModelCallRecordQuery(
                null, List.of(), InternalModelCallSource.USER_CALL, "CID_T",
                null, null, new PageRequest(1, 2)));
        assertThat(userOnly.total()).isEqualTo(4);
        assertThat(userOnly.items()).hasSize(2).allSatisfy(item ->
                assertThat(item.ucid()).isEqualTo("ucid_test"));

        assertThat(callRepository.queryHourlyStats(
                PROVIDER, InternalModelCallSource.USER_CALL,
                T0.minus(1, ChronoUnit.HOURS), T0.plus(2, ChronoUnit.HOURS))).hasSize(4);
        var probeStats = callRepository.queryHourlyStats(
                PROVIDER, InternalModelCallSource.PROBE,
                T0.minus(1, ChronoUnit.HOURS), T0.plus(2, ChronoUnit.HOURS));
        assertThat(probeStats).hasSize(1);
        assertThat(probeStats.getFirst().firstTokenCount()).isZero();
        assertThat(probeStats.getFirst().streamCompleteCount()).isZero();
    }

    @Test
    void calculatesTtftFiveNumberSummaryFromFilteredCallRecords() {
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200,
                5000L, null, T0, InternalModelCallSource.USER_CALL, 100L));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200,
                5000L, null, T0.plusSeconds(1), InternalModelCallSource.USER_CALL, 200L));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200,
                5000L, null, T0.plusSeconds(2), InternalModelCallSource.USER_CALL, 300L));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200,
                5000L, null, T0.plusSeconds(3), InternalModelCallSource.USER_CALL, 400L));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "UPSTREAM_HTTP_ERROR", 500,
                5000L, "UpstreamException", T0.plusSeconds(4), InternalModelCallSource.USER_CALL, 1000L));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200,
                5000L, null, T0.plusSeconds(5), InternalModelCallSource.PROBE, 2000L));
        callRepository.record(record("other-provider", "deepseek-v4", "/chat/completions", "SUCCESS", 200,
                5000L, null, T0.plusSeconds(6), InternalModelCallSource.USER_CALL, 3000L));

        InternalModelLatencyDistribution distribution = callRepository.queryTtftDistribution(
                PROVIDER,
                List.of(InternalModelCallOutcome.SUCCESS),
                InternalModelCallSource.USER_CALL,
                T0.minusSeconds(1),
                T0.plusSeconds(10));

        assertThat(distribution.sampleCount()).isEqualTo(4);
        assertThat(distribution.averageMillis()).isEqualTo(250.0);
        assertThat(distribution.minimumMillis()).isEqualTo(100.0);
        assertThat(distribution.firstQuartileMillis()).isEqualTo(175.0);
        assertThat(distribution.medianMillis()).isEqualTo(250.0);
        assertThat(distribution.thirdQuartileMillis()).isEqualTo(325.0);
        assertThat(distribution.maximumMillis()).isEqualTo(400.0);
    }

    @Test
    void returnsEmptyTtftDistributionWhenNoCallReachedFirstToken() {
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "UPSTREAM_CONNECT_FAILED", null,
                100L, "ConnectException", T0, InternalModelCallSource.USER_CALL, null));

        InternalModelLatencyDistribution distribution = callRepository.queryTtftDistribution(
                PROVIDER, List.of(), InternalModelCallSource.USER_CALL,
                T0.minusSeconds(1), T0.plusSeconds(1));

        assertThat(distribution.sampleCount()).isZero();
        assertThat(distribution.averageMillis()).isNull();
        assertThat(distribution.minimumMillis()).isNull();
        assertThat(distribution.firstQuartileMillis()).isNull();
        assertThat(distribution.medianMillis()).isNull();
        assertThat(distribution.thirdQuartileMillis()).isNull();
        assertThat(distribution.maximumMillis()).isNull();
    }

    @Test
    void calculatesItlFiveNumberSummaryOnlyFromExactTokenUsage() {
        callRepository.record(itlRecord(10L, 1));
        callRepository.record(itlRecord(20L, 2));
        callRepository.record(itlRecord(30L, 3));
        callRepository.record(itlRecord(40L, 4));
        // 缺少准确用量的记录不会进入样本。
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200,
                5000L, null, T0.plusSeconds(5), InternalModelCallSource.USER_CALL, 100L));

        InternalModelLatencyDistribution distribution = callRepository.queryItlDistribution(
                PROVIDER, List.of(InternalModelCallOutcome.SUCCESS), InternalModelCallSource.USER_CALL,
                T0.minusSeconds(1), T0.plusSeconds(10));

        assertThat(distribution.sampleCount()).isEqualTo(4);
        assertThat(distribution.averageMillis()).isEqualTo(25.0);
        assertThat(distribution.minimumMillis()).isEqualTo(10.0);
        assertThat(distribution.firstQuartileMillis()).isEqualTo(17.5);
        assertThat(distribution.medianMillis()).isEqualTo(25.0);
        assertThat(distribution.thirdQuartileMillis()).isEqualTo(32.5);
        assertThat(distribution.maximumMillis()).isEqualTo(40.0);
    }

    @Test
    void calculatesOutputTpsDistributionFromEachCompleteStream() {
        callRepository.record(tpsRecord(100L, 11));
        callRepository.record(tpsRecord(50L, 12));
        callRepository.record(tpsRecord(40L, 13));
        callRepository.record(tpsRecord(20L, 14));

        InternalModelThroughputDistribution distribution = callRepository.queryTpsDistribution(
                PROVIDER, List.of(InternalModelCallOutcome.SUCCESS), InternalModelCallSource.USER_CALL,
                T0.plusSeconds(10), T0.plusSeconds(20));

        assertThat(distribution.sampleCount()).isEqualTo(4);
        assertThat(distribution.averageTokensPerSecond()).isEqualTo(26.25);
        assertThat(distribution.minimumTokensPerSecond()).isEqualTo(10.0);
        assertThat(distribution.firstQuartileTokensPerSecond()).isEqualTo(17.5);
        assertThat(distribution.medianTokensPerSecond()).isEqualTo(22.5);
        assertThat(distribution.thirdQuartileTokensPerSecond()).isEqualTo(31.25);
        assertThat(distribution.maximumTokensPerSecond()).isEqualTo(50.0);
    }

    @Test
    void purgesRecordsAndStatsBeforeCutoff() {
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 100L, null, T0.minus(40, ChronoUnit.DAYS)));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 100L, null, T0));

        assertThat(callRepository.deleteRecordsBefore(T0.minus(30, ChronoUnit.DAYS))).isEqualTo(1);
        // 聚合独立于明细保留期：删除同样早于 30 天的聚合后只剩近期一行。
        assertThat(callRepository.deleteHourlyStatsBefore(T0.minus(30, ChronoUnit.DAYS))).isEqualTo(1);
        assertThat(callRepository.queryHourlyStats(null, null, null)).hasSize(1);
    }

    @Test
    void upsertProbeStatusTracksConsecutiveFailures() {
        probeRepository.upsert(probeStatus(PROVIDER, "UPSTREAM_HTTP_ERROR", 500, "UpstreamException", T0));
        probeRepository.upsert(probeStatus(PROVIDER, "UPSTREAM_CONNECT_FAILED", null, "ConnectException", T0.plusSeconds(60)));

        List<InternalModelProbeStatus> all = probeRepository.findAll();
        assertThat(all).hasSize(1);
        InternalModelProbeStatus status = all.getFirst();
        assertThat(status.consecutiveFailures()).isEqualTo(2);
        assertThat(status.lastOutcome()).isEqualTo(InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED);
        assertThat(status.lastSuccessAt()).isNull();

        probeRepository.upsert(probeStatus(PROVIDER, "SUCCESS", 200, null, T0.plusSeconds(120)));
        InternalModelProbeStatus recovered = probeRepository.findAll().getFirst();
        assertThat(recovered.consecutiveFailures()).isZero();
        assertThat(recovered.lastSuccessAt()).isEqualTo(T0.plusSeconds(120));
    }

    private InternalModelCallRecord record(
            String providerId, String model, String endpoint, String outcome, Integer status,
            long duration, String errorClass, Instant startedAt) {
        return record(providerId, model, endpoint, outcome, status, duration, errorClass, startedAt,
                InternalModelCallSource.USER_CALL, 75L);
    }

    private InternalModelCallRecord record(
            String providerId, String model, String endpoint, String outcome, Integer status,
            long duration, String errorClass, Instant startedAt, InternalModelCallSource source,
            Long firstTokenMillis) {
        return new InternalModelCallRecord(
                null, providerId, model, endpoint, source,
                InternalModelCallOutcome.valueOf(outcome), status, errorClass, true, duration, 50L, firstTokenMillis,
                firstTokenMillis, firstTokenMillis, null,
                "trace_imo_test", "ucid_test", startedAt);
    }

    private InternalModelCallRecord itlRecord(long itlMillis, long offsetSeconds) {
        long firstTokenMillis = 100L;
        long outputTokenCount = 11L;
        long lastTokenMillis = firstTokenMillis + itlMillis * (outputTokenCount - 1);
        return new InternalModelCallRecord(
                null, PROVIDER, "deepseek-v4", "/chat/completions", InternalModelCallSource.USER_CALL,
                InternalModelCallOutcome.SUCCESS, 200, null, true, 5000L, 50L, firstTokenMillis,
                lastTokenMillis, lastTokenMillis, outputTokenCount,
                "trace_imo_itl_" + offsetSeconds, "ucid_test", T0.plusSeconds(offsetSeconds));
    }

    private InternalModelCallRecord tpsRecord(long outputSpanMillis, long offsetSeconds) {
        long firstTokenMillis = 100L;
        return new InternalModelCallRecord(
                null, PROVIDER, "deepseek-v4", "/chat/completions", InternalModelCallSource.USER_CALL,
                InternalModelCallOutcome.SUCCESS, 200, null, true, 5000L, 50L, firstTokenMillis,
                firstTokenMillis + outputSpanMillis, firstTokenMillis + outputSpanMillis, 2L,
                "trace_imo_tps_" + offsetSeconds, "ucid_test", T0.plusSeconds(offsetSeconds));
    }

    private InternalModelProbeStatus probeStatus(
            String providerId, String outcome, Integer status, String errorClass, Instant probedAt) {
        return new InternalModelProbeStatus(
                providerId, InternalModelCallOutcome.valueOf(outcome), status, errorClass,
                300L, probedAt, null, 0, "trace_imo_probe");
    }
}
