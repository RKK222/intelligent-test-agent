package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.ConfigurationManagementMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisConfigurationManagementRepository;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
        insertLegacyWorkspace();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260702180000")
                .load()
                .migrate();
        // H2 不支持后续 PostgreSQL 部分索引语法；本测试只执行并验证本功能迁移。
        new ResourceDatabasePopulator(
                        new ClassPathResource("db/migration/V20260723145200__add_application_workspace_enabled.sql"))
                .execute(dataSource);
        new ResourceDatabasePopulator(
                        new ClassPathResource("db/migration/V20260812204207__dictionaries_add_automation_code_repository.sql"))
                .execute(dataSource);

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
                        CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value(),
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
    void automationRepositoriesPersistRepositoryTypeThroughMyBatisXmlMapper() {
        CodeRepository automationRepository = new CodeRepository(
                new CodeRepositoryId("repo_automation"),
                "git@gitee.com:demo/automation.git",
                "自动化代码库",
                "automation",
                CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.INTERNAL.value(),
                true,
                NOW,
                NOW);

        repository.saveRepository(automationRepository);

        assertThat(repository.findRepositoryByEnglishName("automation"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.repositoryType()).isEqualTo(CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value());
                    assertThat(saved.deploymentMode()).isEqualTo(CodeRepositoryDeploymentMode.INTERNAL.value());
                    assertThat(saved.standard()).isFalse();
                });
    }

    @Test
    void repositorySearchMatchesNameEnglishNameGitUrlAndIdWithFilteredTotal() {
        repository.saveRepository(new CodeRepository(
                new CodeRepositoryId("repo_search_identity"),
                "git@gitee.com:search/git-marker.git",
                "中文检索名称",
                "english-marker",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.EXTERNAL.value(),
                false,
                NOW.plusSeconds(10),
                NOW.plusSeconds(10)));

        assertSingleSearchResult("REPO_SEARCH_IDENTITY", "repo_search_identity");
        assertSingleSearchResult("中文检索", "repo_search_identity");
        assertSingleSearchResult("ENGLISH-MARKER", "repo_search_identity");
        assertSingleSearchResult("git-marker.git", "repo_search_identity");
        assertThat(repository.findRepositories("missing", new PageRequest(1, 20)).total()).isZero();
    }

    private void assertSingleSearchResult(String keyword, String repositoryId) {
        assertThat(repository.findRepositories(keyword, new PageRequest(1, 20)))
                .satisfies(page -> {
                    assertThat(page.total()).isEqualTo(1);
                    assertThat(page.items()).extracting(item -> item.repositoryId().value())
                            .containsExactly(repositoryId);
                });
    }

    @Test
    void applicationsPersistThroughMyBatisXmlMapper() {
        ApplicationDefinition application = new ApplicationDefinition(
                new ApplicationId("F-MYBATIS"),
                "MyBatis 新应用",
                true,
                NOW,
                NOW);

        repository.saveApplication(application);

        assertThat(repository.findApplication(application.appId()))
                .contains(application);
    }

    @Test
    void enabledApplicationMembershipExistsQueryIgnoresDeletedMembersAndDisabledApplications() {
        insertUser("usr_no_app");
        insertUser("usr_active_app");
        insertUser("usr_deleted_member");
        insertUser("usr_disabled_app");
        insertApplicationAndMember("app_active", true, "usr_active_app", null);
        insertApplicationAndMember("app_deleted", true, "usr_deleted_member", NOW.plusSeconds(1));
        insertApplicationAndMember("app_disabled", false, "usr_disabled_app", null);

        assertThat(repository.hasEnabledApplicationMembership(new UserId("usr_active_app"))).isTrue();
        assertThat(repository.hasEnabledApplicationMembership(new UserId("usr_no_app"))).isFalse();
        assertThat(repository.hasEnabledApplicationMembership(new UserId("usr_deleted_member"))).isFalse();
        assertThat(repository.hasEnabledApplicationMembership(new UserId("usr_disabled_app"))).isFalse();
    }

    @Test
    void workspaceEnabledMigrationDefaultsLegacyRowsAndMyBatisPersistsChanges() {
        assertThat(repository.hasApplicationWorkspaceHistory(new CodeRepositoryId("repo_legacy_standard"))).isTrue();
        assertThat(repository.hasApplicationWorkspaceHistory(new CodeRepositoryId("repo_legacy_application"))).isFalse();

        ApplicationWorkspace legacy = repository.findWorkspace(new ApplicationWorkspaceId("awp_legacy"))
                .orElseThrow();
        assertThat(legacy.enabled()).isTrue();

        ApplicationWorkspace disabled = legacy.withEnabled(false, NOW.plusSeconds(10));
        repository.updateWorkspace(disabled);

        assertThat(repository.findWorkspace(legacy.workspaceId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.enabled()).isFalse();
                    assertThat(saved.workspaceName()).isEqualTo("历史工作空间");
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

    private void insertLegacyWorkspace() {
        jdbcClient.sql("""
                        insert into applications(app_id, app_name, enabled, created_at, updated_at)
                        values (:appId, :appName, true, :createdAt, :updatedAt)
                        """)
                .param("appId", "app_legacy")
                .param("appName", "历史应用")
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
        jdbcClient.sql("""
                        insert into application_workspaces(
                            workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at
                        ) values (
                            :workspaceId, :appId, :repositoryId, :branch, :directoryPath, :workspaceName, :createdAt, :updatedAt
                        )
                        """)
                .param("workspaceId", "awp_legacy")
                .param("appId", "app_legacy")
                .param("repositoryId", "repo_legacy_standard")
                .param("branch", "feature_testagent_20260701")
                .param("directoryPath", "F-LEGACY/W1")
                .param("workspaceName", "历史工作空间")
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
    }

    private void insertUser(String userId) {
        jdbcClient.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, :authId, :username, 'hash', 'ACTIVE', :createdAt, :updatedAt)
                        """)
                .param("userId", userId)
                .param("authId", "auth_" + userId)
                .param("username", "name_" + userId)
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
    }

    private void insertApplicationAndMember(String appId, boolean enabled, String userId, Instant deletedAt) {
        jdbcClient.sql("""
                        insert into applications(app_id, app_name, enabled, created_at, updated_at)
                        values (:appId, :appName, :enabled, :createdAt, :updatedAt)
                        """)
                .param("appId", appId)
                .param("appName", appId)
                .param("enabled", enabled)
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
        var statement = jdbcClient.sql("""
                        insert into application_members(app_id, user_id, created_at, updated_at, deleted_at)
                        values (:appId, :userId, :createdAt, :updatedAt, :deletedAt)
                        """)
                .param("appId", appId)
                .param("userId", userId)
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW));
        statement.param(
                "deletedAt",
                deletedAt == null ? null : Timestamp.from(deletedAt),
                java.sql.Types.TIMESTAMP).update();
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
