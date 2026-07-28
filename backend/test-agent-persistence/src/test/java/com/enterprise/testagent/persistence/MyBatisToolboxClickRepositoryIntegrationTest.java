package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.toolbox.ToolboxClickEvent;
import com.enterprise.testagent.domain.toolbox.ToolboxClickRepository;
import com.enterprise.testagent.domain.toolbox.ToolboxClickTotal;
import com.enterprise.testagent.domain.toolbox.ToolboxClickWriteResult;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisToolboxClickRepository;
import com.enterprise.testagent.persistence.mybatis.ToolboxClickEventRow;
import com.enterprise.testagent.persistence.mybatis.ToolboxClickMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 使用 H2 PostgreSQL 模式验证工具点击 Flyway 与 MyBatis XML 基本语义。 */
class MyBatisToolboxClickRepositoryIntegrationTest {

    private static final String USER_ID = "usr_toolbox_mybatis";
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    private SingleConnectionDataSource dataSource;
    private ToolboxClickRepository repository;
    private ToolboxClickMapper mapper;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_toolbox_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS timestamptz AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        // H2 无法解析后续历史迁移中的 PostgreSQL partial/expression index；先执行仓库既有
        // H2 基线，再单独验证本功能迁移。完整迁移链由 PostgreSQL Testcontainers 用例覆盖。
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260715213000")
                .load()
                .migrate();
        new ResourceDatabasePopulator(
                        new ClassPathResource("db/migration/V20260727203500__create_toolbox_click_tracking.sql"))
                .execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        insertUser(USER_ID);

        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory());
        mapper = template.getMapper(ToolboxClickMapper.class);
        repository = new MyBatisToolboxClickRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void eventIdIsIdempotentAndThirtySecondWindowControlsOnlyTheTotal() {
        ToolboxClickWriteResult first = repository.record(event("evt_first", NOW), Duration.ofSeconds(30));
        ToolboxClickWriteResult repeated = repository.record(event("evt_repeat", NOW.plusSeconds(29)), Duration.ofSeconds(30));
        ToolboxClickWriteResult boundary = repository.record(event("evt_boundary", NOW.plusSeconds(30)), Duration.ofSeconds(30));
        ToolboxClickWriteResult duplicate = repository.record(event("evt_boundary", NOW.plusSeconds(60)), Duration.ofSeconds(30));

        assertThat(first).isEqualTo(new ToolboxClickWriteResult(1, true, true));
        assertThat(repeated).isEqualTo(new ToolboxClickWriteResult(1, true, false));
        assertThat(boundary).isEqualTo(new ToolboxClickWriteResult(2, true, true));
        assertThat(duplicate).isEqualTo(new ToolboxClickWriteResult(2, false, false));
        assertThat(mapper.findEvent("evt_first").counted()).isTrue();
        assertThat(mapper.findEvent("evt_repeat").counted()).isFalse();
        assertThat(mapper.countEvents()).isEqualTo(3);
    }

    @Test
    void totalsAreReadInOneBatchAndMissingToolsStayAbsent() {
        repository.record(event("evt_total", NOW), Duration.ofSeconds(30));

        Map<String, ToolboxClickTotal> totals = repository.findTotals(
                List.of("it-tools.hash-text", "omni-tools.string.statistics"));

        assertThat(totals).containsOnlyKeys("it-tools.hash-text");
        assertThat(totals.get("it-tools.hash-text").clickCount()).isEqualTo(1);
        assertThat(repository.findTotals(List.of())).isEmpty();
    }

    @Test
    void deletingUserAnonymizesEventsCascadesStateAndKeepsTotals() {
        repository.record(event("evt_delete", NOW), Duration.ofSeconds(30));

        jdbc.sql("delete from users where user_id = :userId")
                .param("userId", USER_ID)
                .update();

        ToolboxClickEventRow event = mapper.findEvent("evt_delete");
        assertThat(event.userId()).isNull();
        assertThat(mapper.countStates()).isZero();
        assertThat(repository.findTotals(List.of("it-tools.hash-text")))
                .extractingByKey("it-tools.hash-text")
                .extracting(ToolboxClickTotal::clickCount)
                .isEqualTo(1L);
    }

    private ToolboxClickEvent event(String eventId, Instant clickedAt) {
        return new ToolboxClickEvent(
                eventId,
                "it-tools.hash-text",
                "IT_TOOLS",
                new UserId(USER_ID),
                "trace_toolbox_mybatis",
                clickedAt,
                false);
    }

    private void insertUser(String userId) {
        jdbc.sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at
                        ) values (
                            :userId, :authId, :username, 'hash', 'ACTIVE', :now, :now
                        )
                        """)
                .param("userId", userId)
                .param("authId", "auth_" + userId)
                .param("username", "name_" + userId)
                .param("now", NOW)
                .update();
    }

    /** 复用生产 XML，并显式配置 databaseId 选择 H2 兼容语句。 */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIds.setProperty("H2", "h2");
        provider.setProperties(databaseIds);
        factoryBean.setDatabaseIdProvider(provider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
