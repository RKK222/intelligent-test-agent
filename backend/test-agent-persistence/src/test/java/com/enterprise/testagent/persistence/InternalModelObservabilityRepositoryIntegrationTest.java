package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordQuery;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatus;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatusRepository;
import com.enterprise.testagent.persistence.mybatis.InternalModelObservabilityMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelCallRecordRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelProbeStatusRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** 验证内部模型可观测性 migration、明细+聚合同事务落库、分页查询与探活状态 upsert。 */
class InternalModelObservabilityRepositoryIntegrationTest {

    private static final String PROVIDER = "enterprise-deepseek";
    private static final Instant T0 = Instant.parse("2026-08-07T12:00:00Z");
    private SingleConnectionDataSource dataSource;
    private InternalModelCallRecordRepository callRepository;
    private InternalModelProbeStatusRepository probeRepository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_imo_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        // baselineOnMigrate 只对非空库生效；先建占位表触发基线，避免 Flyway 从 V1 全量执行 PostgreSQL 专用 SQL。
        JdbcClient.create(dataSource).sql(
                "create table observability_test_placeholder (id bigint primary key)").update();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("20260807130133")
                .target("20260807222227").load().migrate();
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

    @Test
    void recordsDetailAndHourlyStatInSameTransaction() {
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 1200L, null, T0));
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "UPSTREAM_HTTP_ERROR", 500, 800L, "UpstreamException", T0.plusSeconds(60)));

        var page = callRepository.query(new InternalModelCallRecordQuery(
                PROVIDER, null, null, T0.minus(1, ChronoUnit.HOURS), T0.plus(1, ChronoUnit.HOURS), new PageRequest(1, 20)));
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
        callRepository.record(record(PROVIDER, "deepseek-v4", "/chat/completions", "SUCCESS", 200, 200L, null,
                T0.plusSeconds(10), InternalModelCallSource.PROBE, null));

        var failures = callRepository.query(new InternalModelCallRecordQuery(
                null, InternalModelCallOutcome.PROVIDER_UNAVAILABLE, null, null, null, new PageRequest(1, 20)));
        assertThat(failures.total()).isEqualTo(1);
        assertThat(failures.items().getFirst().endpoint()).isEqualTo("/responses");
        assertThat(failures.items().getFirst().httpStatus()).isNull();

        var probeOnly = callRepository.query(new InternalModelCallRecordQuery(
                null, null, InternalModelCallSource.PROBE, null, null, new PageRequest(1, 20)));
        assertThat(probeOnly.total()).isEqualTo(1);

        assertThat(callRepository.queryHourlyStats(
                PROVIDER, InternalModelCallSource.USER_CALL,
                T0.minus(1, ChronoUnit.HOURS), T0.plus(2, ChronoUnit.HOURS))).hasSize(2);
        var probeStats = callRepository.queryHourlyStats(
                PROVIDER, InternalModelCallSource.PROBE,
                T0.minus(1, ChronoUnit.HOURS), T0.plus(2, ChronoUnit.HOURS));
        assertThat(probeStats).hasSize(1);
        assertThat(probeStats.getFirst().firstTokenCount()).isZero();
        assertThat(probeStats.getFirst().streamCompleteCount()).isZero();
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
                firstTokenMillis,
                "trace_imo_test", "ucid_test", startedAt);
    }

    private InternalModelProbeStatus probeStatus(
            String providerId, String outcome, Integer status, String errorClass, Instant probedAt) {
        return new InternalModelProbeStatus(
                providerId, InternalModelCallOutcome.valueOf(outcome), status, errorClass,
                300L, probedAt, null, 0, "trace_imo_probe");
    }
}
