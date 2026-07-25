package com.enterprise.testagent.app.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationMember;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Reference;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Revision;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.persistence.JdbcManagedWorkspaceRepository;
import com.enterprise.testagent.persistence.JdbcWorkspaceRepository;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubMapper;
import com.enterprise.testagent.persistence.mybatis.ConfigurationManagementMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAgentSkillHubRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisConfigurationManagementRepository;
import com.enterprise.testagent.persistence.mybatis.PersonalWorkspaceMapper;
import com.enterprise.testagent.workspace.AgentSkillHubApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 仅由本地显式脚本启用的 Hub 演示数据夹具，不参与常规测试，也不向 Flyway 写入演示数据。
 */
@EnabledIfSystemProperty(named = "testagent.hub.demo.enabled", matches = "true")
class AgentSkillHubDemoDataFixtureTest {

    private static final ApplicationId SOURCE_APP_ID = new ApplicationId("app_hub_demo");
    private static final CodeRepositoryId SOURCE_REPOSITORY_ID = new CodeRepositoryId("repo_hub_demo");
    private static final ApplicationWorkspaceId SOURCE_WORKSPACE_ID = new ApplicationWorkspaceId("awp_hub_demo");
    private static final WorkspaceId SOURCE_RUNTIME_ID = new WorkspaceId("wrk_hub_demo");
    private static final WorkspaceId PERSONAL_RUNTIME_ID = new WorkspaceId("wrk_hub_demo_personal");
    private static final PersonalWorkspaceId PERSONAL_WORKSPACE_ID = new PersonalWorkspaceId("per_hub_demo");
    private static final ApplicationWorkspaceVersionId SOURCE_VERSION_ID =
            new ApplicationWorkspaceVersionId("awv_hub_demo");
    private static final String AGENT_ID = "hub-demo-reviewer";
    private static final String SKILL_ID = "hub-demo-api-check";
    private static final String UNPUBLISHED_SKILL_ID = "hub-demo-unpublished";

    @Test
    void seedPublishedReferencedAndUpdateAvailableData() throws Exception {
        Map<String, String> localEnvironment = readEnvironmentFile(
                Path.of(requiredProperty("testagent.hub.demo.env-file")));
        String host = requiredValue(localEnvironment, "TEST_AGENT_TEST_DB_HOST");
        String port = localEnvironment.getOrDefault("TEST_AGENT_TEST_DB_PORT", "5432");
        String database = requiredValue(localEnvironment, "TEST_AGENT_TEST_DB_NAME");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://%s:%s/%s".formatted(host, port, database),
                requiredValue(localEnvironment, "TEST_AGENT_TEST_DB_USERNAME"),
                requiredValue(localEnvironment, "TEST_AGENT_TEST_DB_PASSWORD"));

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIds.setProperty("H2", "h2");
        databaseIdProvider.setProperties(databaseIds);
        factory.setDatabaseIdProvider(databaseIdProvider);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sessionFactory);
        var configuration = new MyBatisConfigurationManagementRepository(
                template.getMapper(ConfigurationManagementMapper.class));
        var managed = new JdbcManagedWorkspaceRepository(
                JdbcClient.create(dataSource), new ObjectMapper(), template.getMapper(PersonalWorkspaceMapper.class));
        var workspaces = new JdbcWorkspaceRepository(JdbcClient.create(dataSource));
        AgentSkillHubRepository hub = new MyBatisAgentSkillHubRepository(
                template.getMapper(AgentSkillHubMapper.class));

        Path repoRoot = Path.of(requiredProperty("testagent.hub.demo.repo-root")).toAbsolutePath().normalize();
        Path personalRoot = Path.of(requiredProperty("testagent.hub.demo.personal-root")).toAbsolutePath().normalize();
        Path remoteRoot = Path.of(requiredProperty("testagent.hub.demo.remote-root")).toAbsolutePath().normalize();
        String oldCommit = requiredProperty("testagent.hub.demo.old-commit");
        String newCommit = requiredProperty("testagent.hub.demo.new-commit");
        UserId userId = new UserId(System.getProperty("testagent.hub.demo.user-id", "usr_test_dev"));
        Instant now = Instant.now();

        seedSourceParents(configuration, managed, workspaces, repoRoot, personalRoot,
                remoteRoot, userId, newCommit, now);
        CommonParameterValues emptyParameters = mock(CommonParameterValues.class);
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                hub, configuration, managed, emptyParameters, new ObjectMapper());

        ApplicationWorkspaceVersion oldVersion = version(repoRoot, userId, oldCommit, now.minusSeconds(60));
        service.indexSuccessfulPush(oldVersion, repoRoot, repoRoot, oldCommit);
        Asset oldAgent = requireAsset(hub, AGENT_ID);
        Asset oldSkill = requireAsset(hub, SKILL_ID);
        Revision oldAgentRevision = requireRevision(hub, oldAgent.latestPushedRevisionId());
        Revision oldSkillRevision = requireRevision(hub, oldSkill.latestPushedRevisionId());
        publishIfRequired(service, hub, oldAgent, List.of(), userId);
        publishIfRequired(service, hub, oldSkill, List.of(oldAgent.assetId()), userId);

        // ACTIVE 引用代表目标应用已经确认并 push 过旧修订；固定 ID 使夹具可重复执行。
        hub.saveReferences(List.of(
                activeReference("hub_ref_demo_agent", oldAgent, oldAgentRevision,
                        ".opencode/agents/hub-demo-reviewer.md", userId, now),
                activeReference("hub_ref_demo_skill", oldSkill, oldSkillRevision,
                        ".opencode/skills/hub-demo-api-check", userId, now)));

        ApplicationWorkspaceVersion newVersion = version(repoRoot, userId, newCommit, now);
        service.indexSuccessfulPush(newVersion, repoRoot, repoRoot, newCommit);
        Asset newAgent = requireAsset(hub, AGENT_ID);
        Asset newSkill = requireAsset(hub, SKILL_ID);
        publishIfRequired(service, hub, newAgent, List.of(), userId);
        publishIfRequired(service, hub, newSkill, List.of(newAgent.assetId()), userId);
        managed.updateVersionTargetCommit(SOURCE_VERSION_ID, newCommit, now);

        assertThat(hub.listAssets(null, "Hub 演示", userId.value(), null, false, 0, 20))
                .extracting(summary -> summary.asset().technicalId())
                .contains(AGENT_ID, SKILL_ID, UNPUBLISHED_SKILL_ID);
        assertThat(hub.findAsset(requireAsset(hub, UNPUBLISHED_SKILL_ID).assetId()).orElseThrow()
                .latestPublishedRevisionId()).isNull();
        assertThat(hub.findArtifact(requireRevision(hub, newSkill.latestPushedRevisionId()).artifactSha256())
                .orElseThrow().content()).isNotEmpty();
        assertThat(hub.listUpdates(userId.value(), null, 0, 100))
                .extracting(update -> update.reference().referenceId())
                .contains("hub_ref_demo_agent", "hub_ref_demo_skill");
    }

    private void seedSourceParents(
            MyBatisConfigurationManagementRepository configuration,
            JdbcManagedWorkspaceRepository managed,
            JdbcWorkspaceRepository workspaces,
            Path repoRoot,
            Path personalRoot,
            Path remoteRoot,
            UserId userId,
            String newCommit,
            Instant now) {
        if (configuration.findApplication(SOURCE_APP_ID).isEmpty()) {
            configuration.saveApplication(new ApplicationDefinition(
                    SOURCE_APP_ID, "Hub 演示应用", true, now, now));
        }
        configuration.saveMember(ApplicationMember.active(SOURCE_APP_ID, userId, now));
        if (configuration.findRepository(SOURCE_REPOSITORY_ID).isEmpty()) {
            configuration.saveRepository(new CodeRepository(
                    SOURCE_REPOSITORY_ID, remoteRoot.toUri().toString(), "Hub 演示仓库",
                    "hub-demo-repository", false, now, now));
        }
        configuration.linkRepository(SOURCE_APP_ID, SOURCE_REPOSITORY_ID);
        if (configuration.findWorkspace(SOURCE_WORKSPACE_ID).isEmpty()) {
            configuration.saveWorkspace(new ApplicationWorkspace(
                    SOURCE_WORKSPACE_ID, SOURCE_APP_ID, SOURCE_REPOSITORY_ID, "main", "/",
                    "Hub 演示工作空间", true, now, now));
        }
        workspaces.save(new Workspace(
                SOURCE_RUNTIME_ID, "Hub 演示运行目录", repoRoot.toString(), WorkspaceStatus.ARCHIVED,
                now, now, null, "trace_hub_demo_fixture"));
        if (managed.findVersion(SOURCE_VERSION_ID).isEmpty()) {
            managed.saveVersion(version(repoRoot, userId, newCommit, now));
        }
        workspaces.save(new Workspace(
                PERSONAL_RUNTIME_ID, "Hub 演示个人工作区", personalRoot.toString(), WorkspaceStatus.ACTIVE,
                now, now, null, "trace_hub_demo_personal_fixture"));
        if (managed.findPersonalWorkspace(PERSONAL_WORKSPACE_ID).isEmpty()) {
            managed.savePersonalWorkspace(new PersonalWorkspace(
                    PERSONAL_WORKSPACE_ID, SOURCE_VERSION_ID, SOURCE_APP_ID, SOURCE_WORKSPACE_ID, userId,
                    "Hub 演示个人工作区", "feature-hub-demo-user", personalRoot.toString(), personalRoot.toString(),
                    PERSONAL_RUNTIME_ID, newCommit, ManagedWorkspaceStatus.ACTIVE, now, now));
        }
    }

    private ApplicationWorkspaceVersion version(Path repoRoot, UserId userId, String commit, Instant updatedAt) {
        return new ApplicationWorkspaceVersion(
                SOURCE_VERSION_ID, SOURCE_WORKSPACE_ID, SOURCE_APP_ID, SOURCE_REPOSITORY_ID,
                "20260725", "main", repoRoot.toString(), repoRoot.toString(), SOURCE_RUNTIME_ID,
                userId, ManagedWorkspaceStatus.UNAVAILABLE, commit, updatedAt, updatedAt, updatedAt);
    }

    private Asset requireAsset(AgentSkillHubRepository hub, String technicalId) {
        return hub.listAssets(null, technicalId, "usr_test_dev", null, false, 0, 20).stream()
                .map(summary -> summary.asset())
                .filter(asset -> SOURCE_APP_ID.value().equals(asset.sourceAppId()))
                .filter(asset -> technicalId.equals(asset.technicalId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Hub 演示资产不存在: " + technicalId));
    }

    private Revision requireRevision(AgentSkillHubRepository hub, String revisionId) {
        return hub.findRevision(revisionId).orElseThrow();
    }

    private void publishIfRequired(
            AgentSkillHubApplicationService service,
            AgentSkillHubRepository hub,
            Asset asset,
            List<String> dependencyAssetIds,
            UserId userId) {
        Revision pushed = requireRevision(hub, asset.latestPushedRevisionId());
        if (pushed.publishedAt() == null) {
            service.publish(asset.assetId(), dependencyAssetIds, userId);
        }
    }

    private Reference activeReference(
            String referenceId,
            Asset asset,
            Revision revision,
            String targetPath,
            UserId userId,
            Instant now) {
        return new Reference(
                referenceId, asset.assetId(), SOURCE_APP_ID.value(), SOURCE_WORKSPACE_ID.value(), targetPath,
                asset.technicalId(), revision.revisionId(), null, null, "ACTIVE", userId.value(), now, now);
    }

    /** 只解析本地 fixture 所需的简单 KEY=VALUE，不执行配置文件中的 shell 内容。 */
    private static Map<String, String> readEnvironmentFile(Path path) throws Exception {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String value = trimmed.substring(separator + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(trimmed.substring(0, separator).trim(), value);
        }
        return values;
    }

    private static String requiredValue(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("本地环境文件缺少配置: " + name);
        }
        return value;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少系统属性: " + name);
        }
        return value;
    }
}
