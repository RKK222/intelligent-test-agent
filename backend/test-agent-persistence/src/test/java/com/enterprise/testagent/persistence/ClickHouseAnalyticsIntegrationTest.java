package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.persistence.clickhouse.ClickHouseAnalyticsRepository;
import com.enterprise.testagent.persistence.clickhouse.ClickHouseAnalyticsMapper;
import com.enterprise.testagent.persistence.clickhouse.ClickHouseInstantTypeHandler;
import com.enterprise.testagent.persistence.clickhouse.ClickHouseSchemaMigrator;
import com.enterprise.testagent.persistence.clickhouse.ClickHouseTraceCatalogMapper;
import com.enterprise.testagent.persistence.mybatis.AnalyticsMapper;
import com.enterprise.testagent.domain.trace.TraceModels;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.mockito.Mockito.mock;

/** 使用真实 ClickHouse 验证迁移幂等、事实替换和能力调用分类。 */
@Testcontainers(disabledWithoutDocker = true)
class ClickHouseAnalyticsIntegrationTest {

    @Container
    private static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer(
            DockerImageName.parse("clickhouse/clickhouse-server:26.3.17.56").asCompatibleSubstituteFor("clickhouse"));

    private static ClickHouseAnalyticsMapper mapper;
    private static ClickHouseTraceCatalogMapper traceMapper;
    private static JdbcClient jdbc;

    @BeforeAll
    static void setUp() throws Exception {
        DataSource dataSource = dataSource();
        ClickHouseSchemaMigrator migrator = new ClickHouseSchemaMigrator(dataSource);
        migrator.migrate();
        migrator.migrate();
        SqlSessionTemplate session = new SqlSessionTemplate(sqlSessionFactory(dataSource));
        mapper = session.getMapper(ClickHouseAnalyticsMapper.class);
        traceMapper = session.getMapper(ClickHouseTraceCatalogMapper.class);
        jdbc = JdbcClient.create(dataSource);
    }

    @Test
    void duplicateEventUsesLatestVersionAndClassifiesSkillOutsideTool() {
        Instant occurredAt = Instant.parse("2026-08-12T16:30:00Z");
        AnalyticsEventOutboxRepository.Event first = event(
                "message:1", 1, occurredAt,
                "{\"eventType\":\"MESSAGE\",\"userId\":\"usr-1\",\"username\":\"张三\","
                        + "\"organization\":\"总行\",\"rdDepartment\":\"研发一部\","
                        + "\"department\":\"平台部\",\"userMessageCount\":1}");
        AnalyticsEventOutboxRepository.Event replacement = event(
                "message:1", 2, occurredAt,
                "{\"eventType\":\"MESSAGE\",\"userId\":\"usr-1\",\"username\":\"张三\","
                        + "\"organization\":\"总行\",\"rdDepartment\":\"研发一部\","
                        + "\"department\":\"平台部\",\"userMessageCount\":2}");
        AnalyticsEventOutboxRepository.Event skill = event(
                "run-event:skill-start", 3, occurredAt.plusSeconds(1),
                "{\"eventType\":\"TOOL_STARTED\",\"userId\":\"usr-1\",\"runId\":\"run-1\","
                        + "\"sessionId\":\"root\",\"callId\":\"call-1\",\"toolName\":\"skill\","
                        + "\"title\":\"Loaded skill: playwright\"}");
        AnalyticsEventOutboxRepository.Event skillFinished = event(
                "run-event:skill-finish", 3, occurredAt.plusSeconds(2),
                "{\"eventType\":\"TOOL_FINISHED\",\"userId\":\"usr-1\",\"runId\":\"run-1\","
                        + "\"sessionId\":\"root\",\"callId\":\"call-1\",\"toolName\":\"skill\","
                        + "\"title\":\"Loaded skill: playwright\",\"status\":\"SUCCEEDED\"}");
        AnalyticsEventOutboxRepository.Event genericTask = event(
                "run-event:task-start", 4, occurredAt.plusSeconds(3),
                "{\"eventType\":\"TOOL_STARTED\",\"userId\":\"usr-1\",\"runId\":\"run-1\","
                        + "\"sessionId\":\"root\",\"callId\":\"call-task\",\"toolName\":\"task\","
                        + "\"title\":\"检查接口实现\"}");
        AnalyticsEventOutboxRepository.Event childAgent = event(
                "run-event:child-agent", 5, occurredAt.plusSeconds(4),
                "{\"eventType\":\"SESSION_CHILD_DISCOVERED\",\"userId\":\"usr-1\",\"runId\":\"run-1\","
                        + "\"sessionId\":\"child-1\",\"parentSessionId\":\"root\","
                        + "\"taskCallId\":\"call-task\",\"agentName\":\"review\"}");
        AnalyticsEventOutboxRepository.Event agentStarted = event(
                "run-started:run-1", 6, occurredAt.plusSeconds(5),
                "{\"eventType\":\"RUN_STARTED\",\"userId\":\"usr-1\",\"runId\":\"run-1\","
                        + "\"sessionId\":\"root\",\"capabilityName\":\"coding-agent\"}");
        AnalyticsEventOutboxRepository.Event agentFailed = event(
                "run-terminal:run-1", 7, occurredAt.plusSeconds(6),
                "{\"eventType\":\"RUN_TERMINAL\",\"userId\":\"usr-1\",\"runId\":\"run-1\","
                        + "\"sessionId\":\"root\",\"capabilityName\":\"coding-agent\",\"status\":\"FAILED\"}");
        AnalyticsEventOutboxRepository.Event activeUser = event(
                "user-dimension:usr-1", 8, occurredAt.plusSeconds(7),
                "{\"eventType\":\"USER_DIMENSION\",\"userId\":\"usr-1\",\"username\":\"张三\","
                        + "\"organization\":\"总行\",\"rdDepartment\":\"研发一部\","
                        + "\"department\":\"平台部\",\"status\":\"ACTIVE\"}");
        AnalyticsEventOutboxRepository.Event inactiveUser = event(
                "user-dimension:usr-2", 9, occurredAt.plusSeconds(8),
                "{\"eventType\":\"USER_DIMENSION\",\"userId\":\"usr-2\",\"username\":\"李四\","
                        + "\"organization\":\"总行\",\"rdDepartment\":\"研发一部\","
                        + "\"department\":\"质量部\",\"status\":\"INACTIVE\"}");

        mapper.insertEvents(
                List.of(first, replacement, skill, skillFinished, genericTask, childAgent, agentStarted, agentFailed,
                        activeUser, inactiveUser),
                occurredAt.plusSeconds(9));

        long messages = jdbc.sql("select sum(user_message_count) from analytics_activity_facts final")
                .query(Long.class).single();
        String capabilityType = jdbc.sql("""
                        select capability_type from analytics_capability_facts final
                        where capability_name = 'playwright'
                        """).query(String.class).single();
        long toolCount = jdbc.sql("""
                        select count() from analytics_capability_facts final
                        where capability_type = 'TOOL' and capability_name = 'playwright'
                        """).query(Long.class).single();
        long migrationCount = jdbc.sql("""
                        select count() from analytics_schema_history final
                        where version = '20260813150000'
                """).query(Long.class).single();
        String skillStatus = jdbc.sql("""
                        select status from analytics_capability_facts final
                        where capability_name = 'playwright'
                        """).query(String.class).single();
        long agentCount = jdbc.sql("""
                        select count() from analytics_capability_facts final
                        where capability_type = 'AGENT' and capability_name = 'coding-agent'
                        """).query(Long.class).single();
        String agentStatus = jdbc.sql("""
                        select status from analytics_capability_facts final
                        where capability_type = 'AGENT' and capability_name = 'coding-agent'
                        """).query(String.class).single();
        long childAgentCount = jdbc.sql("""
                        select count() from analytics_capability_facts final
                        where capability_type = 'AGENT' and capability_name = 'review'
                        """).query(Long.class).single();
        long genericTaskCount = jdbc.sql("""
                        select count() from analytics_capability_facts final
                        where capability_name in ('task', '检查接口实现')
                        """).query(Long.class).single();
        AnalyticsModels.Filter filter = filter(occurredAt.minusSeconds(1), occurredAt.plusSeconds(10), "总行");
        List<AnalyticsModels.CapabilityUsageRow> capabilities = mapper.capabilityUsage(filter);
        List<AnalyticsModels.FilterOption> organizations = mapper.organizations();

        assertThat(messages).isEqualTo(2L);
        assertThat(capabilityType).isEqualTo("SKILL");
        assertThat(toolCount).isZero();
        assertThat(migrationCount).isEqualTo(1L);
        assertThat(skillStatus).isEqualTo("SUCCEEDED");
        assertThat(agentCount).isEqualTo(1L);
        assertThat(agentStatus).isEqualTo("FAILED");
        assertThat(childAgentCount).isEqualTo(1L);
        assertThat(genericTaskCount).isZero();
        assertThat(capabilities).extracting(AnalyticsModels.CapabilityUsageRow::name)
                .containsExactlyInAnyOrder("playwright", "review", "coding-agent");
        assertThat(organizations).contains(new AnalyticsModels.FilterOption("总行", "总行"));
        assertThat(mapper.countRegisteredUsers(filter)).isEqualTo(2);
        assertThat(mapper.countEnabledUsers(filter)).isEqualTo(1);
        assertThat(mapper.departments("总行", "研发一部"))
                .extracting(AnalyticsModels.FilterOption::value)
                .containsExactlyInAnyOrder("平台部", "质量部");
        long dimensionActivityFacts = jdbc.sql("""
                        select count() from analytics_activity_facts final
                        where user_id = 'usr-2'
                        """).query(Long.class).single();
        assertThat(dimensionActivityFacts).isZero();
        assertThat(mapper.countUserDimensionFactsByPrefix("user-dimension:")).isEqualTo(2);
    }

    @Test
    void repositoryWritesDailyRowsWithLocalDateBucket() {
        Instant dimensionTime = Instant.parse("2026-08-13T00:59:00Z");
        mapper.insertEvents(List.of(event(
                "daily-fixture-dimension:usr-daily", 10, dimensionTime,
                "{\"eventType\":\"USER_DIMENSION\",\"userId\":\"usr-daily\",\"username\":\"李四\","
                        + "\"organization\":\"分行\",\"rdDepartment\":\"研发一部\","
                        + "\"department\":\"平台部\",\"status\":\"ACTIVE\"}")), dimensionTime);
        AnalyticsModels.ActivityRollupRow daily = new AnalyticsModels.ActivityRollupRow(
                null, LocalDate.of(2026, 8, 13), "usr-daily", "李四", "总行", "研发一部", "平台部",
                "", "", "", 0, 0, 0, 0, 0, 3, 1, 1, 1, 0, 0, 0, 1,
                0, 0, 0, 0, 0, 10, 20, 5, 2, 1, 35, 100, 1,
                Instant.parse("2026-08-12T16:00:00Z"), Instant.parse("2026-08-12T17:00:00Z"));
        ClickHouseAnalyticsRepository repository = new ClickHouseAnalyticsRepository(mapper, mock(AnalyticsMapper.class));

        repository.insertDaily(List.of(daily), Instant.parse("2026-08-13T01:00:00Z"));

        List<AnalyticsModels.ActivityRollupRow> rows = repository.queryRollups(filter(
                Instant.parse("2026-08-12T16:00:00Z"), Instant.parse("2026-08-13T16:00:00Z")));
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.activityDate()).isEqualTo(LocalDate.of(2026, 8, 13));
            assertThat(row.bucketStart()).isEqualTo(Instant.parse("2026-08-12T16:00:00Z"));
            assertThat(row.tokensTotal()).isEqualTo(35);
        });
    }

    @Test
    void pluginCapabilityFactOverridesLegacyCallAndPublishesCoverageWithoutRawPayload() {
        Instant occurredAt = Instant.parse("2026-08-22T06:00:00Z");
        mapper.insertEvents(List.of(
                event(
                        "plugin-dimension:usr-plugin", 30, occurredAt.minusSeconds(1),
                        "{\"eventType\":\"USER_DIMENSION\",\"userId\":\"usr-plugin\","
                                + "\"username\":\"王五\",\"organization\":\"插件组织\","
                                + "\"rdDepartment\":\"研发二部\",\"department\":\"测试平台\","
                                + "\"status\":\"ACTIVE\"}"),
                event(
                        "plugin-legacy:start", 31, occurredAt,
                        "{\"eventType\":\"TOOL_STARTED\",\"userId\":\"usr-plugin\","
                                + "\"runId\":\"run-plugin\",\"sessionId\":\"ses-plugin\","
                                + "\"callId\":\"call-test-design\",\"toolName\":\"skill\","
                                + "\"title\":\"Loaded skill: test-design\"}"),
                event(
                        "plugin-legacy:finish", 32, occurredAt.plusSeconds(1),
                        "{\"eventType\":\"TOOL_FINISHED\",\"userId\":\"usr-plugin\","
                                + "\"runId\":\"run-plugin\",\"sessionId\":\"ses-plugin\","
                                + "\"callId\":\"call-test-design\",\"toolName\":\"skill\","
                                + "\"title\":\"Loaded skill: test-design\",\"status\":\"SUCCEEDED\"}")),
                occurredAt.plusSeconds(2));

        traceMapper.insertCapabilityFacts(List.of(
                new TraceModels.CapabilityFact(
                        "capability:run-plugin:ses-plugin:call-test-design",
                        101,
                        occurredAt.plusSeconds(1),
                        "usr-plugin",
                        "王五",
                        "插件组织",
                        "研发二部",
                        "测试平台",
                        "ses-plugin",
                        "run-plugin",
                        "call-test-design",
                        "SKILL",
                        "test-design",
                        "SUCCEEDED",
                        29,
                        "OPENCODE_PLUGIN"),
                new TraceModels.CapabilityFact(
                        "capability:run-plugin:ses-plugin:call-test-design-cancelled",
                        102,
                        occurredAt.plusSeconds(2),
                        "usr-plugin",
                        "王五",
                        "插件组织",
                        "研发二部",
                        "测试平台",
                        "ses-plugin",
                        "run-plugin",
                        "call-test-design-cancelled",
                        "SKILL",
                        "test-design",
                        "CANCELLED",
                        31,
                        "OPENCODE_PLUGIN")), occurredAt.plusSeconds(2));
        TraceModels.Catalog catalog = new TraceModels.Catalog(
                "trc_11111111111111111111111111111111",
                "usr-plugin",
                "王五",
                "插件组织",
                "研发二部",
                "测试平台",
                "LOCAL_CLIENT",
                "OPENCODE_PLUGIN",
                "",
                "lci-plugin",
                "bjp-plugin",
                "linux-plugin",
                "ses-plugin",
                "run-plugin",
                "test-design-agent",
                "COMPLETED",
                "INCOMPLETE",
                occurredAt,
                occurredAt.plusSeconds(3),
                occurredAt,
                101,
                100,
                4096,
                1,
                0,
                false,
                true);
        traceMapper.insertCatalog(catalog, 101);
        traceMapper.insertSpans(List.of(new TraceModels.Span(
                catalog.traceId(),
                "evt_000000000000000000000000000000000101",
                "ASSISTANT_STEP_METRICS",
                "MODEL",
                "message",
                occurredAt.plusSeconds(2),
                occurredAt.plusSeconds(1),
                101,
                10,
                "ses-plugin",
                "run-plugin",
                "turn-plugin",
                "step-plugin",
                "message-plugin",
                null,
                null,
                "",
                "",
                "COMPLETED",
                1_000,
                100,
                20,
                7,
                60,
                5,
                127,
                30L,
                970L,
                20,
                0.125D,
                "stop",
                "OPENCODE_PLUGIN")));
        mapper.insertEvents(List.of(
                event(
                        "plugin-legacy:before-cutover", 33, occurredAt.minusSeconds(1),
                        "{\"eventType\":\"TOOL_FINISHED\",\"userId\":\"usr-plugin\","
                                + "\"runId\":\"run-plugin-before\",\"sessionId\":\"ses-plugin\","
                                + "\"callId\":\"call-before-cutover\",\"toolName\":\"bash\","
                                + "\"status\":\"SUCCEEDED\"}"),
                event(
                        "plugin-legacy:after-cutover", 34, occurredAt.plusSeconds(4),
                        "{\"eventType\":\"TOOL_FINISHED\",\"userId\":\"usr-plugin\","
                                + "\"runId\":\"run-plugin-after\",\"sessionId\":\"ses-plugin\","
                                + "\"callId\":\"call-after-cutover\",\"toolName\":\"bash\","
                                + "\"status\":\"SUCCEEDED\"}")),
                occurredAt.plusSeconds(5));

        AnalyticsModels.Filter analyticsFilter = filter(
                occurredAt.minusSeconds(2), occurredAt.plusSeconds(5), "插件组织");
        AnalyticsModels.CapabilityUsageRow row = mapper.capabilityUsage(analyticsFilter).stream()
                .filter(candidate -> candidate.name().equals("test-design"))
                .findFirst()
                .orElseThrow();
        AnalyticsModels.CapabilityCoverage coverage = mapper.capabilityCoverage(analyticsFilter);
        TraceModels.Filter traceFilter = new TraceModels.Filter(
                occurredAt.minusSeconds(2), occurredAt.plusSeconds(5), null, "插件组织", null,
                null, null, "INCOMPLETE", null, null, 1, 20);

        assertThat(row.invocationCount()).isEqualTo(2);
        assertThat(row.userCount()).isEqualTo(1);
        assertThat(row.succeededCount()).isEqualTo(1);
        assertThat(row.cancelledCount()).isEqualTo(1);
        assertThat(jdbc.sql("""
                        select count() from analytics_capability_facts final
                        where call_id = 'call-before-cutover'
                        """).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                        select count() from analytics_capability_facts final
                        where call_id = 'call-after-cutover'
                        """).query(Long.class).single()).isZero();
        assertThat(coverage.source()).isEqualTo("OPENCODE_PLUGIN");
        assertThat(coverage.coverageStartAt()).isEqualTo(occurredAt);
        assertThat(coverage.rolloutCompleteness()).isZero();
        assertThat(traceMapper.events(catalog.traceId(), 0, 10)).singleElement().satisfies(span -> {
            assertThat(span.tokensTotal()).isEqualTo(127);
            assertThat(span.decodeTokens()).isEqualTo(20);
            assertThat(span.cost()).isEqualTo(0.125D);
            assertThat(span.finishReason()).isEqualTo("stop");
        });
        assertThat(traceMapper.search(traceFilter, 20, 0))
                .extracting(TraceModels.Catalog::traceId)
                .containsExactly(catalog.traceId());
        assertThat(jdbc.sql("""
                        select count() from system.columns
                        where database = currentDatabase()
                          and table in ('analytics_trace_catalog', 'analytics_trace_spans',
                                        'analytics_plugin_capability_facts')
                          and name in ('payload', 'prompt', 'reasoning', 'tool_input',
                                       'tool_output', 'archive_path')
                        """)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                        select count() from system.columns
                        where database = currentDatabase()
                          and table = 'analytics_trace_spans'
                          and name in ('record_kind', 'started_at', 'tokens_cache_read',
                                       'tokens_cache_write', 'ttft_ms', 'decode_ms')
                        """)
                .query(Long.class).single()).isEqualTo(6L);
        assertThat(jdbc.sql("""
                        select count() from system.columns
                        where database = currentDatabase()
                          and table = 'analytics_trace_spans'
                          and name in ('tokens_total', 'decode_tokens', 'cost', 'finish_reason')
                        """)
                .query(Long.class).single()).isEqualTo(4L);
        assertThat(jdbc.sql("""
                        select count() from analytics_schema_history final
                        where version in ('20260822215123', '20260823000346', '20260823001128') and success = 1
                        """)
                .query(Long.class).single()).isEqualTo(3L);
    }

    @Test
    void pluginCapabilityIsVisibleBeforeUserDimensionArrives() {
        Instant occurredAt = Instant.parse("2026-08-22T07:00:00Z");
        traceMapper.insertCapabilityFacts(List.of(new TraceModels.CapabilityFact(
                "capability:run-plugin-early:ses-plugin-early:call-test-design-early",
                201,
                occurredAt,
                "usr-plugin-early",
                "维表未同步用户",
                "插件先到组织",
                "研发三部",
                "效能平台",
                "ses-plugin-early",
                "run-plugin-early",
                "call-test-design-early",
                "SKILL",
                "test-design",
                "SUCCEEDED",
                17,
                "OPENCODE_PLUGIN")), occurredAt.plusSeconds(1));

        AnalyticsModels.Filter analyticsFilter = filter(
                occurredAt.minusSeconds(1), occurredAt.plusSeconds(2), "插件先到组织");
        AnalyticsModels.CapabilityUsageRow row = mapper.capabilityUsage(analyticsFilter).stream()
                .filter(candidate -> candidate.name().equals("test-design"))
                .findFirst()
                .orElseThrow();

        assertThat(jdbc.sql("""
                        select count() from analytics_user_dimensions final
                        where user_id = 'usr-plugin-early'
                        """).query(Long.class).single()).isZero();
        assertThat(row.invocationCount()).isEqualTo(1);
        assertThat(row.userCount()).isEqualTo(1);
        assertThat(row.succeededCount()).isEqualTo(1);
    }

    private static AnalyticsModels.Filter filter(Instant start, Instant end) {
        return filter(start, end, null);
    }

    private static AnalyticsModels.Filter filter(Instant start, Instant end, String organization) {
        return new AnalyticsModels.Filter(
                start, end, AnalyticsModels.Granularity.DAY,
                organization, null, null, null, null, null, null,
                20, 1, 50, "userMessageCount,desc");
    }

    private static AnalyticsEventOutboxRepository.Event event(
            String eventId,
            long version,
            Instant occurredAt,
            String payload) {
        return new AnalyticsEventOutboxRepository.Event(
                version, eventId, version, "TEST", eventId, occurredAt, payload);
    }

    private static DataSource dataSource() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", CLICKHOUSE.getUsername());
        properties.setProperty("password", CLICKHOUSE.getPassword());
        properties.setProperty("compress", "false");
        return new ClickHouseDataSource(CLICKHOUSE.getJdbcUrl(), properties);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeHandlers(new ClickHouseInstantTypeHandler());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis-clickhouse/**/*.xml"));
        SqlSessionFactory result = factory.getObject();
        result.getConfiguration().setMapUnderscoreToCamelCase(true);
        return result;
    }
}
