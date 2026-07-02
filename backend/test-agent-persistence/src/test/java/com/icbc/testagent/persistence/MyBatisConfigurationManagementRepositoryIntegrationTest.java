package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.CodeRepositoryType;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.persistence.mybatis.ConfigurationManagementMapper;
import com.icbc.testagent.persistence.mybatis.MyBatisConfigurationManagementRepository;
import java.sql.Timestamp;
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
 * 验证配置管理仓储通过 MyBatis XML SQL 读写版本库类型，并覆盖历史数据 migration 回填。
 */
class MyBatisConfigurationManagementRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-02T08:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private ConfigurationManagementRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_mybatis_configuration_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260702120000")
                .load()
                .migrate();
        jdbcClient = JdbcClient.create(dataSource);
        insertLegacyRepository("repo_legacy_standard", "git@gitee.com:demo/standard.git", true);
        insertLegacyRepository("repo_legacy_application", "git@gitee.com:demo/application.git", false);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        ConfigurationManagementMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(ConfigurationManagementMapper.class);
        repository = new MyBatisConfigurationManagementRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void migrationSeedsRepositoryTypeDictionaryAndBackfillsExistingRepositories() {
        assertThat(jdbcClient.sql("""
                        select dict_value from dictionaries
                        where dict_key = :dictKey
                        order by sort_order
                        """)
                .param("dictKey", Dictionary.DICT_KEY_REPOSITORY_TYPE)
                .query(String.class)
                .list())
                .containsExactly(
                        CodeRepositoryType.TEST_WORK_REPOSITORY.value(),
                        CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                        CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value());

        assertThat(repository.findRepository(new CodeRepositoryId("repo_legacy_standard")))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.repositoryType()).isEqualTo(CodeRepositoryType.TEST_WORK_REPOSITORY.value());
                    assertThat(saved.deploymentMode()).isEqualTo(CodeRepositoryDeploymentMode.EXTERNAL.value());
                    assertThat(saved.standard()).isTrue();
                });
        assertThat(repository.findRepository(new CodeRepositoryId("repo_legacy_application")))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.repositoryType()).isEqualTo(CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value());
                    assertThat(saved.deploymentMode()).isEqualTo(CodeRepositoryDeploymentMode.EXTERNAL.value());
                    assertThat(saved.standard()).isFalse();
                });
    }

    @Test
    void repositoriesPersistRepositoryTypeThroughMyBatisXmlMapper() {
        CodeRepository assetRepository = new CodeRepository(
                new CodeRepositoryId("repo_asset"),
                "git@gitee.com:demo/asset.git",
                "资产库",
                "assetrepo",
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.INTERNAL.value(),
                true,
                NOW,
                NOW);

        repository.saveRepository(assetRepository);

        assertThat(repository.findRepositoryByEnglishName("assetrepo"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.repositoryType()).isEqualTo(CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value());
                    assertThat(saved.deploymentMode()).isEqualTo(CodeRepositoryDeploymentMode.INTERNAL.value());
                    assertThat(saved.standard()).isFalse();
                });
    }

    private void insertLegacyRepository(String repositoryId, String gitUrl, boolean standard) {
        jdbcClient.sql("""
                        insert into code_repositories(repository_id, git_url, name, english_name, standard, created_at, updated_at)
                        values (:repositoryId, :gitUrl, :name, :englishName, :standard, :createdAt, :updatedAt)
                        """)
                .param("repositoryId", repositoryId)
                .param("gitUrl", gitUrl)
                .param("name", repositoryId)
                .param("englishName", repositoryId.replace("repo_", ""))
                .param("standard", standard)
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
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
