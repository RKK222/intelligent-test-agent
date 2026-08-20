package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Artifact;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinPushedRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkill;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedAsset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushReferenceAction;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushReferenceDecision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Reference;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Revision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillSubcategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SourceKind;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.persistence.mybatis.AgentSkillHubMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAgentSkillHubRepository;
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
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 使用真实 Flyway 与 MyBatis XML 固化 Hub 快照、发布、引用和更新查询。 */
class MyBatisAgentSkillHubRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private AgentSkillHubRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_hub_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS timestamptz AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        // 后续存量 migration 含 H2 不支持的 PostgreSQL partial expression index；
        // 本测试迁移到其前一稳定基线，再单独执行本功能 migration。完整链由真实 PostgreSQL 启动验证。
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715213000").load().migrate();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260725143000__create_agent_skill_hub.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260725230000__support_hub_reference_removal.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260806143000__classify_skill_hub_assets.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260806190000__persist_public_skill_hub_snapshots.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260806190500__classify_public_skill_hub_snapshots.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260820153926__agent_skill_hub_assets_add_skillhub_source.sql")).execute(dataSource);
        seedRequiredParents(JdbcClient.create(dataSource));
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        AgentSkillHubMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(AgentSkillHubMapper.class);
        repository = new MyBatisAgentSkillHubRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void immutablePushesPublishAndExposeApplicationWideUpdate() {
        repository.replacePushedSnapshot(snapshot("a".repeat(40), "1".repeat(64), NOW));
        var first = repository.listAssets(AssetType.AGENT, null, "usr_hub", null, false, 0, 10).getFirst();
        repository.publish(first.asset().assetId(), first.pushedRevision().revisionId(), "usr_hub", List.of(), NOW);
        repository.saveReferences(List.of(new Reference(
                "hub_ref_test", first.asset().assetId(), "app_hub", "aw_hub",
                ".opencode/agents/reviewer.md", "reviewer", first.pushedRevision().revisionId(),
                null, null, "ACTIVE", "usr_hub", NOW, NOW)));

        repository.replacePushedSnapshot(snapshot("b".repeat(40), "2".repeat(64), NOW.plusSeconds(60)));
        var second = repository.listAssets(AssetType.AGENT, null, "usr_hub", null, false, 0, 10).getFirst();
        repository.publish(second.asset().assetId(), second.pushedRevision().revisionId(),
                "usr_hub", List.of(), NOW.plusSeconds(60));

        assertThat(second.pushedRevision().revisionId()).isNotEqualTo(first.pushedRevision().revisionId());
        assertThat(repository.countUpdates("usr_hub", null)).isEqualTo(1);
        assertThat(repository.listUpdates("usr_hub", null, 0, 10)).singleElement().satisfies(update -> {
            assertThat(update.reference().activeRevisionId()).isEqualTo(first.pushedRevision().revisionId());
            assertThat(update.latestRevision().revisionId()).isEqualTo(second.pushedRevision().revisionId());
            assertThat(update.sourceAppName()).isEqualTo("Hub 来源应用");
        });

        repository.saveReference(new Reference(
                "hub_ref_test", first.asset().assetId(), "app_hub", "aw_hub",
                ".opencode/agents/reviewer.md", "reviewer", second.pushedRevision().revisionId(),
                null, null, "ACTIVE", "usr_hub", NOW, NOW.plusSeconds(60)));
        repository.replacePushedSnapshot(snapshot("c".repeat(40), "2".repeat(64), NOW.plusSeconds(120)));
        var unchanged = repository.listAssets(AssetType.AGENT, null, "usr_hub", null, false, 0, 10).getFirst();
        repository.publish(unchanged.asset().assetId(), unchanged.pushedRevision().revisionId(),
                "usr_hub", List.of(), NOW.plusSeconds(120));

        assertThat(unchanged.updateAvailable()).isFalse();
        assertThat(repository.countUpdates("usr_hub", null)).isZero();
    }

    @Test
    void distinguishesPendingActiveAndPendingRemovalReferenceStates() {
        repository.replacePushedSnapshot(snapshot("a".repeat(40), "1".repeat(64), NOW));
        var asset = repository.listAssets(AssetType.AGENT, null, "usr_hub", "aw_hub", false, 0, 10).getFirst();
        repository.publish(asset.asset().assetId(), asset.pushedRevision().revisionId(), "usr_hub", List.of(), NOW);
        assertThat(repository.findArtifact("1".repeat(64))).get().satisfies(artifact ->
                assertThat(artifact.content()).containsExactly(1, 2, 3));

        Reference pending = new Reference(
                "hub_ref_state", asset.asset().assetId(), "app_hub", "aw_hub",
                ".opencode/agents/reviewer.md", "reviewer", null, asset.pushedRevision().revisionId(),
                "f".repeat(64), "PENDING_PUSH", "usr_hub", NOW, NOW);
        repository.saveReference(pending);
        assertThat(repository.listAssets(AssetType.AGENT, null, "usr_hub", "aw_hub", false, 0, 10))
                .singleElement().extracting("referenceStatus").isEqualTo("PENDING_PUSH");
        assertThat(repository.listAssets(null, null, "usr_hub", "aw_hub", true, 0, 10))
                .singleElement().satisfies(summary -> {
                    assertThat(summary.referenceStatus()).isEqualTo("PENDING_PUSH");
                    assertThat(summary.referenceCount()).isZero();
                });
        assertThat(repository.listReferenceConsumers(asset.asset().assetId(), "usr_hub"))
                .singleElement().satisfies(consumer -> {
                    assertThat(consumer.targetAppName()).isEqualTo("Hub 来源应用");
                    assertThat(consumer.targetWorkspaceName()).isEqualTo("Hub 来源工作空间");
                    assertThat(consumer.reference().status()).isEqualTo("PENDING_PUSH");
                });

        Reference active = new Reference(
                pending.referenceId(), pending.assetId(), pending.targetAppId(), pending.targetApplicationWorkspaceId(),
                pending.targetPath(), pending.aliasTechnicalId(), asset.pushedRevision().revisionId(), null, null,
                "ACTIVE", pending.createdByUserId(), pending.createdAt(), NOW.plusSeconds(1));
        repository.saveReference(active);
        assertThat(repository.listAssets(AssetType.AGENT, null, "usr_hub", "aw_hub", false, 0, 10))
                .singleElement().satisfies(summary -> {
                    assertThat(summary.referenceStatus()).isEqualTo("ACTIVE");
                    assertThat(summary.referenceCount()).isEqualTo(1);
                });
        assertThat(repository.countAssets(null, null, "aw_hub", true)).isEqualTo(1);

        Reference removing = new Reference(
                active.referenceId(), active.assetId(), active.targetAppId(), active.targetApplicationWorkspaceId(),
                active.targetPath(), active.aliasTechnicalId(), active.activeRevisionId(), null, null,
                "PENDING_REMOVE", active.createdByUserId(), active.createdAt(), NOW.plusSeconds(2));
        repository.saveReference(removing);
        assertThat(repository.countUpdates("usr_hub", "aw_hub")).isZero();
        assertThat(repository.findPendingReferences("aw_hub"))
                .singleElement().extracting("status").isEqualTo("PENDING_REMOVE");
        assertThat(repository.listAssets(AssetType.AGENT, null, "usr_hub", "aw_hub", false, 0, 10))
                .singleElement().satisfies(summary -> {
                    assertThat(summary.referenceStatus()).isEqualTo("PENDING_REMOVE");
                    assertThat(summary.referenceCount()).isZero();
                });
        assertThat(repository.listAssets(null, null, "usr_hub", "aw_hub", true, 0, 10)).isEmpty();
        assertThat(repository.listReferenceConsumers(asset.asset().assetId(), "usr_hub")).isEmpty();

        Reference reactivated = new Reference(
                removing.referenceId(), removing.assetId(), removing.targetAppId(),
                removing.targetApplicationWorkspaceId(), ".opencode/agents/reviewer-v2.md", "reviewer-v2",
                removing.activeRevisionId(), asset.pushedRevision().revisionId(), "e".repeat(64),
                "PENDING_PUSH", removing.createdByUserId(), removing.createdAt(), NOW.plusSeconds(3));
        repository.saveReference(reactivated);
        assertThat(repository.findReferencesByTargetAsset("aw_hub", active.assetId()))
                .singleElement().satisfies(reference -> {
                    assertThat(reference.referenceId()).isEqualTo(removing.referenceId());
                    assertThat(reference.targetPath()).isEqualTo(".opencode/agents/reviewer-v2.md");
                    assertThat(reference.aliasTechnicalId()).isEqualTo("reviewer-v2");
                    assertThat(reference.status()).isEqualTo("PENDING_PUSH");
                });
        assertThat(repository.listAssets(null, null, "usr_hub", "aw_hub", true, 0, 10))
                .singleElement().extracting("referenceStatus").isEqualTo("PENDING_PUSH");
    }

    @Test
    void newSkillDefaultsToOtherAndKeepsAdminClassificationAcrossPushes() {
        repository.replacePushedSnapshot(skillSnapshot("a".repeat(40), "3".repeat(64), NOW));
        var unclassified = repository.listAssets(
                AssetType.SKILL, SkillCategory.OTHER, null,
                null, "usr_hub", null, false, 0, 10).getFirst();
        assertThat(unclassified.asset().skillCategory()).isEqualTo(SkillCategory.OTHER);
        assertThat(unclassified.asset().skillSubcategory()).isNull();

        repository.updateSkillClassification(
                unclassified.asset().assetId(), SkillCategory.TEST, SkillSubcategory.TEST_DATA_CONSTRUCTION,
                "usr_hub", NOW.plusSeconds(1));
        repository.replacePushedSnapshot(skillSnapshot(
                "b".repeat(40), "4".repeat(64), NOW.plusSeconds(60)));

        assertThat(repository.countAssets(
                AssetType.SKILL, SkillCategory.OTHER, null, null, null, false)).isZero();
        assertThat(repository.listAssets(
                AssetType.SKILL, SkillCategory.TEST, SkillSubcategory.TEST_DATA_CONSTRUCTION,
                null, "usr_hub", null, false, 0, 10))
                .singleElement().satisfies(summary -> {
                    assertThat(summary.asset().skillCategory()).isEqualTo(SkillCategory.TEST);
                    assertThat(summary.asset().skillSubcategory()).isEqualTo(SkillSubcategory.TEST_DATA_CONSTRUCTION);
                    assertThat(summary.pushedRevision().sourceCommitHash()).isEqualTo("b".repeat(40));
                });
    }

    @Test
    void publicBuiltinSnapshotUsesCommitCasAndKeepsExactHistoricalRevision() {
        String firstCommit = "d".repeat(40);
        String secondCommit = "e".repeat(40);
        BuiltinSnapshot first = builtinSnapshot(firstCommit, "5".repeat(64), NOW);
        BuiltinSnapshot second = builtinSnapshot(secondCommit, "6".repeat(64), NOW.plusSeconds(60));

        assertThat(repository.replaceBuiltinSnapshot(null, first)).isTrue();
        assertThat(repository.findBuiltinSnapshotCommit()).contains(firstCommit);
        assertThat(repository.listCurrentBuiltinRevisions())
                .singleElement().satisfies(revision -> {
                    assertThat(revision.assetType()).isEqualTo(AssetType.AGENT);
                    assertThat(revision.technicalId()).isEqualTo("public-reviewer");
                    assertThat(revision.sourceCommitHash()).isEqualTo(firstCommit);
                });
        assertThat(repository.replaceBuiltinSnapshot(null, second)).isFalse();
        assertThat(repository.findBuiltinSnapshotCommit()).contains(firstCommit);

        assertThat(repository.replaceBuiltinSnapshot(firstCommit, second)).isTrue();
        assertThat(repository.findBuiltinSnapshotCommit()).contains(secondCommit);
        assertThat(repository.listCurrentBuiltinRevisions())
                .singleElement().extracting("sourceCommitHash").isEqualTo(secondCommit);
        assertThat(repository.findBuiltinRevision(first.revisions().getFirst().revision().revisionId())).isPresent();
        assertThat(repository.findArtifact("5".repeat(64))).isPresent();
    }

    @Test
    void publicSkillClassificationSurvivesTheNextGitSnapshot() {
        String firstCommit = "7".repeat(40);
        String secondCommit = "8".repeat(40);
        BuiltinSnapshot first = builtinSkillSnapshot(firstCommit, "9".repeat(64), NOW);
        BuiltinSnapshot second = builtinSkillSnapshot(secondCommit, "a".repeat(64), NOW.plusSeconds(60));

        assertThat(repository.replaceBuiltinSnapshot(null, first)).isTrue();
        var firstRevision = repository.listCurrentBuiltinRevisions().getFirst();
        assertThat(firstRevision.skillCategory()).isEqualTo(SkillCategory.OTHER);

        repository.updateBuiltinSkillClassification(
                firstRevision.assetId(), SkillCategory.CODE, SkillSubcategory.WHITE_BOX_ANALYSIS,
                "usr_hub", NOW.plusSeconds(30));
        assertThat(repository.replaceBuiltinSnapshot(firstCommit, second)).isTrue();

        assertThat(repository.listCurrentBuiltinRevisions()).singleElement().satisfies(revision -> {
            assertThat(revision.sourceCommitHash()).isEqualTo(secondCommit);
            assertThat(revision.skillCategory()).isEqualTo(SkillCategory.CODE);
            assertThat(revision.skillSubcategory()).isEqualTo(SkillSubcategory.WHITE_BOX_ANALYSIS);
        });
        assertThat(repository.findBuiltinRevision(firstRevision.revisionId())).get().satisfies(revision -> {
            assertThat(revision.skillCategory()).isEqualTo(SkillCategory.CODE);
            assertThat(revision.skillSubcategory()).isEqualTo(SkillSubcategory.WHITE_BOX_ANALYSIS);
        });
    }

    @Test
    void externalCatalogIsMetadataOnlyAndDelistedReferenceRemainsVisible() {
        ExternalSkill external = externalSkill();
        repository.replaceExternalCatalog(List.of(external), NOW);

        var summary = repository.listAssets(
                AssetType.SKILL, null, null, SourceKind.SKILLHUB,
                null, "usr_hub", null, false, 0, 10).getFirst();
        assertThat(summary.asset().sourceKind()).isEqualTo(SourceKind.SKILLHUB);
        assertThat(summary.pushedRevision()).isNull();
        assertThat(summary.asset().sourceAvailable()).isTrue();
        assertThat(repository.countAssets(
                AssetType.SKILL, null, null, SourceKind.PLATFORM, null, null, false)).isZero();

        Revision externalRevision = materializeExternal(summary.asset().assetId(), external);
        repository.saveReference(new Reference(
                "hub_ref_external", summary.asset().assetId(), "app_hub", "aw_hub",
                ".opencode/skills/case-design/", "case-design", externalRevision.revisionId(),
                null, null, "ACTIVE", "usr_hub", NOW, NOW));
        repository.replaceExternalCatalog(List.of(), NOW.plusSeconds(60));

        assertThat(repository.listAssets(
                AssetType.SKILL, null, null, SourceKind.SKILLHUB,
                null, "usr_hub", null, false, 0, 10)).isEmpty();
        assertThat(repository.listAssets(
                AssetType.SKILL, null, null, null,
                null, "usr_hub", "aw_hub", true, 0, 10)).singleElement().satisfies(item -> {
                    assertThat(item.asset().sourceAvailable()).isFalse();
                    assertThat(item.referenceStatus()).isEqualTo("ACTIVE");
                });
    }

    @Test
    void externalRevisionRejectsDifferentContentForTheSameIdAndVersion() {
        ExternalSkill external = externalSkill();
        repository.replaceExternalCatalog(List.of(external), NOW);
        String assetId = repository.listAssets(
                AssetType.SKILL, null, null, SourceKind.SKILLHUB,
                null, "usr_hub", null, false, 0, 10).getFirst().asset().assetId();
        materializeExternal(assetId, external);
        byte[] compressed = new byte[]{6, 6, 6};
        Artifact conflicting = new Artifact(
                "f".repeat(64), "GZIP_JSON_V1", compressed, "[]", 12,
                compressed.length, 1, NOW.plusSeconds(1));

        assertThatThrownBy(() -> repository.saveExternalRevision(
                assetId, external.id(), external.version(), conflicting, "f".repeat(64),
                external.displayName(), external.description(), NOW.plusSeconds(1)))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("不同内容");
        assertThat(repository.findExternalRevision(assetId, external.id(), external.version()))
                .get().extracting(Revision::contentSha256).isEqualTo("e".repeat(64));
    }

    @Test
    void exactExternalPushKeepsSourceWhileChangedPushCreatesUnpublishedPlatformFork() {
        ExternalSkill external = externalSkill();
        repository.replaceExternalCatalog(List.of(external), NOW);
        var externalAsset = repository.listAssets(
                AssetType.SKILL, null, null, SourceKind.SKILLHUB,
                null, "usr_hub", null, false, 0, 10).getFirst().asset();
        Revision externalRevision = materializeExternal(externalAsset.assetId(), external);

        repository.saveReference(new Reference(
                "hub_ref_exact", externalAsset.assetId(), "app_hub", "aw_hub",
                ".opencode/skills/case-design/", "case-design", null, externalRevision.revisionId(),
                externalRevision.contentSha256(), "PENDING_PUSH", "usr_hub", NOW, NOW));
        repository.replacePushedSnapshot(
                new PushedSnapshot("app_hub", "aw_hub", "ver_hub", "b".repeat(40),
                        NOW.plusSeconds(10), List.of()),
                List.of(new PushReferenceDecision(
                        "hub_ref_exact", PushReferenceAction.KEEP_SOURCE, AssetType.SKILL,
                        "case-design", null, null)));
        assertThat(repository.findReference("hub_ref_exact")).get().satisfies(reference -> {
            assertThat(reference.assetId()).isEqualTo(externalAsset.assetId());
            assertThat(reference.activeRevisionId()).isEqualTo(externalRevision.revisionId());
            assertThat(reference.status()).isEqualTo("ACTIVE");
        });

        repository.saveReference(new Reference(
                "hub_ref_fork", externalAsset.assetId(), "app_hub", "aw_hub",
                ".opencode/skills/forked-case-design/", "forked-case-design", null,
                externalRevision.revisionId(), externalRevision.contentSha256(),
                "PENDING_PUSH", "usr_hub", NOW, NOW.plusSeconds(20)));
        PushedSnapshot forkSnapshot = skillSnapshot("c".repeat(40), "4".repeat(64), NOW.plusSeconds(30));
        PushedAsset forkAsset = new PushedAsset(
                AssetType.SKILL, "forked-case-design", forkSnapshot.assets().getFirst().artifact(),
                "4".repeat(64), "派生测试设计", "Forked case design", "已修改的外部 Skill");
        repository.replacePushedSnapshot(
                new PushedSnapshot("app_hub", "aw_hub", "ver_hub", "c".repeat(40),
                        NOW.plusSeconds(30), List.of(forkAsset)),
                List.of(new PushReferenceDecision(
                        "hub_ref_fork", PushReferenceAction.FORK_TO_PLATFORM, AssetType.SKILL,
                        "forked-case-design", externalAsset.assetId(), externalRevision.revisionId())));

        var forkReference = repository.findReference("hub_ref_fork").orElseThrow();
        var platformFork = repository.findAsset(forkReference.assetId()).orElseThrow();
        assertThat(platformFork.sourceKind()).isEqualTo(SourceKind.PLATFORM);
        assertThat(platformFork.forkedFromAssetId()).isEqualTo(externalAsset.assetId());
        assertThat(platformFork.forkedFromRevisionId()).isEqualTo(externalRevision.revisionId());
        assertThat(platformFork.latestPublishedRevisionId()).isNull();
        assertThat(forkReference.status()).isEqualTo("ACTIVE");
        assertThat(forkReference.activeRevisionId()).isEqualTo(platformFork.latestPushedRevisionId());
    }

    private ExternalSkill externalSkill() {
        return new ExternalSkill(
                42, "case-design", "1.2.0", "测试设计", "生成结构化测试案例",
                "official", "test", "stable", "稳定", "team", NOW, 7);
    }

    private Revision materializeExternal(String assetId, ExternalSkill external) {
        byte[] compressed = new byte[]{9, 8, 7};
        Artifact artifact = new Artifact(
                "e".repeat(64), "GZIP_JSON_V1", compressed, "[]", 12,
                compressed.length, 1, NOW);
        return repository.saveExternalRevision(
                assetId, external.id(), external.version(), artifact, "e".repeat(64),
                external.displayName(), external.description(), NOW);
    }

    private PushedSnapshot snapshot(String commit, String artifactSha, Instant pushedAt) {
        byte[] compressed = new byte[]{1, 2, 3};
        Artifact artifact = new Artifact(artifactSha, "GZIP_JSON_V1", compressed, "[]", 12,
                compressed.length, 1, pushedAt);
        PushedAsset asset = new PushedAsset(AssetType.AGENT, "reviewer", artifact, artifactSha,
                "评审专家", "Reviewer", "评审测试设计");
        return new PushedSnapshot("app_hub", "aw_hub", "ver_hub", commit, pushedAt, List.of(asset));
    }

    private PushedSnapshot skillSnapshot(String commit, String artifactSha, Instant pushedAt) {
        byte[] compressed = new byte[]{4, 5, 6};
        Artifact artifact = new Artifact(artifactSha, "GZIP_JSON_V1", compressed, "[]", 12,
                compressed.length, 1, pushedAt);
        PushedAsset asset = new PushedAsset(AssetType.SKILL, "case-design", artifact, artifactSha,
                "测试案例设计", "Test case design", "生成结构化测试案例");
        return new PushedSnapshot("app_hub", "aw_hub", "ver_hub", commit, pushedAt, List.of(asset));
    }

    private BuiltinSnapshot builtinSnapshot(String commit, String artifactSha, Instant pushedAt) {
        byte[] compressed = new byte[]{7, 8, 9};
        Artifact artifact = new Artifact(
                artifactSha, "GZIP_JSON_V1", compressed, "[]", 12, compressed.length, 1, pushedAt);
        String assetId = "hub_builtin_AGENT_cHVibGljLXJldmlld2Vy";
        BuiltinRevision revision = new BuiltinRevision(
                "hub_builtin_rev_" + commit + "_AGENT_cHVibGljLXJldmlld2Vy",
                assetId, AssetType.AGENT, "public-reviewer", commit, artifactSha, artifactSha,
                "公共评审", "Public reviewer", "公共评审 Agent", SkillCategory.OTHER, null, pushedAt);
        return new BuiltinSnapshot(
                commit, pushedAt, List.of(new BuiltinPushedRevision(revision, artifact)));
    }

    private BuiltinSnapshot builtinSkillSnapshot(String commit, String artifactSha, Instant pushedAt) {
        byte[] compressed = new byte[]{10, 11, 12};
        Artifact artifact = new Artifact(
                artifactSha, "GZIP_JSON_V1", compressed, "[]", 12, compressed.length, 1, pushedAt);
        String assetId = "hub_builtin_SKILL_d2hpdGUtYm94LWFuYWx5c2lz";
        BuiltinRevision revision = new BuiltinRevision(
                "hub_builtin_rev_" + commit + "_SKILL_d2hpdGUtYm94LWFuYWx5c2lz",
                assetId, AssetType.SKILL, "white-box-analysis", commit, artifactSha, artifactSha,
                "白盒分析", "White-box analysis", "分析代码实现", SkillCategory.OTHER, null, pushedAt);
        return new BuiltinSnapshot(
                commit, pushedAt, List.of(new BuiltinPushedRevision(revision, artifact)));
    }

    private void seedRequiredParents(JdbcClient jdbc) {
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values('usr_hub', 'hub-user', 'hub-user', 'hash', 'ACTIVE', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into applications(app_id, app_name, enabled, created_at, updated_at)
                values('app_hub', 'Hub 来源应用', true, :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into application_members(app_id, user_id, created_at, updated_at)
                values('app_hub', 'usr_hub', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into code_repositories(repository_id, git_url, name, standard, created_at, updated_at)
                values('repo_hub', 'ssh://git/hub.git', 'hub', true, :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into application_workspaces(
                    workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at)
                values('aw_hub', 'app_hub', 'repo_hub', 'main', '/', 'Hub 来源工作空间', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values('wrk_hub', 'Hub runtime', '/tmp/hub', 'ACTIVE', 'trace_hub', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into application_workspace_versions(
                    version_id, application_workspace_id, app_id, repository_id, version, branch,
                    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
                    status, target_commit_hash, target_commit_updated_at, created_at, updated_at)
                values('ver_hub', 'aw_hub', 'app_hub', 'repo_hub', '20260725', 'main',
                    '/repo/hub', '/repo/hub', 'wrk_hub', 'usr_hub', 'ACTIVE', :commit, :now, :now, :now)
                """).param("commit", "a".repeat(40)).param("now", NOW).update();
    }
}
