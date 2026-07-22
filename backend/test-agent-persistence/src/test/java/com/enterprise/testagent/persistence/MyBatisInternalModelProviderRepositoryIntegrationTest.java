package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.InternalModelToken;
import com.enterprise.testagent.domain.configuration.InternalModelTokenRepository;
import com.enterprise.testagent.persistence.mybatis.InternalModelProviderMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelProviderRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisInternalModelTokenRepository;
import java.time.Instant;
import java.util.List;
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

/**
 * 验证内部模型供应商与可复用 Token 定义通过 MyBatis XML 持久化。
 */
class MyBatisInternalModelProviderRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-08T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private InternalModelProviderRepository providerRepository;
    private InternalModelTokenRepository tokenRepository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_internal_model_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
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
        seedLegacyConfiguration();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260722180000__add_internal_model_token_definitions.sql"))
                .execute(dataSource);

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        InternalModelProviderMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(InternalModelProviderMapper.class);
        providerRepository = new MyBatisInternalModelProviderRepository(mapper);
        tokenRepository = new MyBatisInternalModelTokenRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void legacyGlobalTokenMigratesToOneSharedTokenDefinition() {
        List<InternalModelProvider> providers = providerRepository.findAll();
        List<InternalModelToken> tokens = tokenRepository.findAll();

        assertThat(tokens).singleElement().satisfies(token -> {
            assertThat(token.name()).isEqualTo("默认 Token");
            assertThat(token.referencedProviderCount()).isEqualTo(1);
        });
        assertThat(providers).singleElement().satisfies(provider -> {
            assertThat(provider.providerId()).isEqualTo("legacy-provider");
            assertThat(provider.tokenId()).isEqualTo(tokens.getFirst().tokenId());
            assertThat(provider.tokenName()).isEqualTo("默认 Token");
            assertThat(provider.tokenConfigured()).isTrue();
        });
        assertThat(providerRepository.findEnabledRuntimeConfigs())
                .singleElement()
                .satisfies(config -> {
                    assertThat(config.provider().providerId()).isEqualTo("legacy-provider");
                    assertThat(config.authToken()).isEqualTo("legacy-token");
                });
        assertThat(providerRepository.findAuthToken()).contains("legacy-token");
    }

    @Test
    void tokenRotationKeepsProviderAssociationAndReferencedTokenCannotBeDeleted() {
        InternalModelToken shared = tokenRepository.create("共享 Token", "token-one", NOW.plusSeconds(10));
        providerRepository.replaceProviders(List.of(
                        provider("enterprise-a", "ENTERPRISE A", "http://a.example/v1", true, 10, shared.tokenId()),
                        provider("enterprise-b", "ENTERPRISE B", "http://b.example/v1", true, 20, shared.tokenId())),
                NOW.plusSeconds(20));

        InternalModelToken updated = tokenRepository.update(
                shared.tokenId(), "共享 Token 已改名", "token-two", NOW.plusSeconds(30)).orElseThrow();

        assertThat(updated.tokenId()).isEqualTo(shared.tokenId());
        assertThat(updated.name()).isEqualTo("共享 Token 已改名");
        assertThat(providerRepository.findEnabledRuntimeConfigs())
                .extracting(InternalModelProviderRuntimeConfig::authToken)
                .containsExactly("token-two", "token-two");
        assertThat(tokenRepository.deleteIfUnreferenced(shared.tokenId())).isFalse();

        providerRepository.replaceProviders(List.of(), NOW.plusSeconds(40));

        assertThat(tokenRepository.deleteIfUnreferenced(shared.tokenId())).isTrue();
        assertThat(tokenRepository.findById(shared.tokenId())).isEmpty();
    }

    @Test
    void updatingOnlyTokenNamePreservesExternalTokenValue() {
        InternalModelToken token = tokenRepository.create("原名称", "external-token", NOW.plusSeconds(10));
        providerRepository.replaceProviders(List.of(
                        provider("enterprise-a", "ENTERPRISE A", "http://a.example/v1", true, 10, token.tokenId())),
                NOW.plusSeconds(20));

        tokenRepository.update(token.tokenId(), "新名称", null, NOW.plusSeconds(30)).orElseThrow();

        assertThat(providerRepository.findEnabledRuntimeConfigs())
                .singleElement()
                .extracting(InternalModelProviderRuntimeConfig::authToken)
                .isEqualTo("external-token");
        assertThat(providerRepository.findAll())
                .singleElement()
                .extracting(InternalModelProvider::tokenName)
                .isEqualTo("新名称");
    }

    private void seedLegacyConfiguration() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into internal_model_providers (
                    provider_id, name, base_url, enabled, sort_order, created_at, updated_at
                ) values (
                    'legacy-provider', 'Legacy Provider', 'http://legacy.example/v1', true, 1, :now, :now
                )
                """)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                insert into internal_model_proxy_settings (
                    setting_id, enterprise_openai_auth_token, created_at, updated_at
                ) values ('default', 'legacy-token', :now, :now)
                """)
                .param("now", NOW)
                .update();
    }

    private static InternalModelProvider provider(
            String providerId,
            String name,
            String baseUrl,
            boolean enabled,
            int sortOrder,
            Long tokenId) {
        return new InternalModelProvider(
                providerId,
                name,
                baseUrl,
                enabled,
                sortOrder,
                tokenId,
                null,
                false,
                NOW,
                NOW);
    }

    /**
     * 直接构造 MyBatis-Spring 测试仓储，确保 XML mapper 不依赖完整应用上下文也能加载。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
