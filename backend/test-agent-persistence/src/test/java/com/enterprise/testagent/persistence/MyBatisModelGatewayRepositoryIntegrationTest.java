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
import java.time.Instant;
import java.time.LocalDate;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

class MyBatisModelGatewayRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private InternalModelProviderModelRepository models;
    private ModelGatewayUsageDailyRepository usage;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_model_gateway_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260716143000")
                .load()
                .migrate();
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V20260722180000__add_internal_model_token_definitions.sql"),
                new ClassPathResource("db/migration/V20260730090000__add_lobehub_model_gateway.sql"))
                .execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        seedProvider();

        SqlSessionFactory factory = sqlSessionFactory();
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        models = new MyBatisInternalModelProviderModelRepository(
                template.getMapper(InternalModelProviderModelMapper.class));
        usage = new MyBatisModelGatewayUsageDailyRepository(
                template.getMapper(ModelGatewayUsageDailyMapper.class));
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void replacesModelCatalogAndPersistsCapabilityProbeWithoutRawErrors() {
        models.replaceForProvider("provider-a", List.of(model()), NOW);
        assertThat(models.findByProviderId("provider-a"))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.declaredCapabilities())
                            .containsExactlyInAnyOrder(ModelCapability.CHAT, ModelCapability.VISION);
                    assertThat(saved.probedCapabilities()).isEmpty();
                });

        models.saveProbeResult(new ModelProbeResult(
                "provider-a", "enterprise-chat", ModelCapability.CHAT, true, NOW.plusSeconds(10)));

        assertThat(models.findByModelId("enterprise-chat"))
                .get()
                .extracting(InternalModelProviderModel::probedCapabilities)
                .isEqualTo(Set.of(ModelCapability.CHAT));
        assertThat(jdbc.sql("select count(*) from internal_model_provider_model_probes")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void aggregatesDailyUsageWithoutRequestContentOrTraceColumns() throws Exception {
        ModelGatewayUsageDelta success = new ModelGatewayUsageDelta(
                LocalDate.of(2026, 7, 30),
                "lobehub",
                "usr_lobehub",
                "provider-a",
                "enterprise-chat",
                "/chat/completions",
                true,
                10,
                5,
                15,
                120);
        ModelGatewayUsageDelta failure = new ModelGatewayUsageDelta(
                success.usageDate(),
                success.sourceClient(),
                success.userId(),
                success.providerId(),
                success.modelId(),
                success.endpoint(),
                false,
                0,
                0,
                0,
                30);

        usage.increment(success);
        usage.increment(failure);

        assertThat(jdbc.sql("""
                        select request_count, success_count, failure_count,
                               input_tokens, output_tokens, total_tokens, duration_ms
                        from model_gateway_usage_daily
                        """)
                .query((rs, row) -> List.of(
                        rs.getLong("request_count"),
                        rs.getLong("success_count"),
                        rs.getLong("failure_count"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        rs.getLong("total_tokens"),
                        rs.getLong("duration_ms")))
                .single()).containsExactly(2L, 1L, 1L, 10L, 5L, 15L, 150L);

        assertThat(tableColumns("model_gateway_usage_daily"))
                .noneMatch(column -> Set.of("prompt", "answer", "ucid", "trace_id", "raw_error")
                        .contains(column.toLowerCase()));
    }

    private void seedProvider() {
        jdbc.sql("""
                        insert into internal_model_tokens(name, token_value, created_at, updated_at)
                        values ('Token A', 'secret', :now, :now)
                        """)
                .param("now", NOW)
                .update();
        Long tokenId = jdbc.sql("select token_id from internal_model_tokens where name = 'Token A'")
                .query(Long.class).single();
        jdbc.sql("""
                        insert into internal_model_providers(
                            provider_id, name, base_url, enabled, sort_order, token_id, created_at, updated_at
                        ) values ('provider-a', 'Provider A', 'http://models.internal/v1', true, 0, :tokenId, :now, :now)
                        """)
                .param("tokenId", tokenId)
                .param("now", NOW)
                .update();
    }

    private static InternalModelProviderModel model() {
        return new InternalModelProviderModel(
                "provider-a",
                "enterprise-chat",
                "upstream-chat",
                "企业对话",
                128_000L,
                true,
                Set.of(ModelCapability.CHAT, ModelCapability.VISION),
                Set.of(),
                null,
                NOW,
                NOW);
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }

    private List<String> tableColumns(String tableName) throws Exception {
        List<String> columns = new ArrayList<>();
        try (ResultSet result = dataSource.getConnection().getMetaData().getColumns(null, null, null, null)) {
            while (result.next()) {
                if (tableName.equalsIgnoreCase(result.getString("TABLE_NAME"))) {
                    columns.add(result.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }
}
