package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.configuration.InternalModelProvider;
import com.icbc.testagent.domain.configuration.InternalModelProviderRepository;
import com.icbc.testagent.persistence.mybatis.InternalModelProviderMapper;
import com.icbc.testagent.persistence.mybatis.MyBatisInternalModelProviderRepository;
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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证内部模型供应商配置通过 MyBatis XML 持久化，供 Java 内部代理启动加载和刷新使用。
 */
class MyBatisInternalModelProviderRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-08T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private InternalModelProviderRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_internal_model_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        InternalModelProviderMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(InternalModelProviderMapper.class);
        repository = new MyBatisInternalModelProviderRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void providersAndAuthTokenArePersistedThroughMyBatisXmlMapper() {
        repository.replaceProviders(List.of(
                        provider("icbc-z", "ICBC Z", "http://z.example/v1", true, 20),
                        provider("icbc-a", "ICBC A", "http://a.example/v1", true, 10),
                        provider("icbc-off", "ICBC Off", "http://off.example/v1", false, 0)),
                NOW);
        repository.saveAuthToken("token-one", NOW);

        assertThat(repository.findEnabled())
                .extracting(InternalModelProvider::providerId)
                .containsExactly("icbc-a", "icbc-z");
        assertThat(repository.findAuthToken()).contains("token-one");

        repository.replaceProviders(List.of(
                        provider("icbc-a", "ICBC A Updated", "http://a2.example/v1", true, 5)),
                NOW.plusSeconds(60));
        repository.saveAuthToken("token-two", NOW.plusSeconds(60));

        assertThat(repository.findAll())
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.providerId()).isEqualTo("icbc-a");
                    assertThat(saved.name()).isEqualTo("ICBC A Updated");
                    assertThat(saved.baseUrl()).isEqualTo("http://a2.example/v1");
                    assertThat(saved.sortOrder()).isEqualTo(5);
                    assertThat(saved.createdAt()).isEqualTo(NOW);
                    assertThat(saved.updatedAt()).isEqualTo(NOW.plusSeconds(60));
                });
        assertThat(repository.findAuthToken()).contains("token-two");
    }

    private static InternalModelProvider provider(
            String providerId,
            String name,
            String baseUrl,
            boolean enabled,
            int sortOrder) {
        return new InternalModelProvider(providerId, name, baseUrl, enabled, sortOrder, NOW, NOW);
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
