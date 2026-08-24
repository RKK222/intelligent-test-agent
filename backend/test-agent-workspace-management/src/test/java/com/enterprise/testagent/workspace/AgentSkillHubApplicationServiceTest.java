package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Asset;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetSummary;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.AssetType;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.BuiltinRevision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkill;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkillPackage;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushedSnapshot;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushReferenceAction;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.PushReferenceDecision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Reference;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.Revision;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillCategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadFile;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadProgress;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadRequest;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadSubmission;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillSubcategory;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SourceKind;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.UpdateOperation;
import com.enterprise.testagent.domain.hub.AgentSkillHubRepository;
import com.enterprise.testagent.domain.hub.SkillHubGateway;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

class AgentSkillHubApplicationServiceTest {

    @Test
    void externalCatalogAcceptsFieldTechnicalIdsAndPreservesExactNames() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        SkillHubGateway gateway = mock(SkillHubGateway.class);
        Instant createdAt = Instant.parse("2026-07-31T02:13:20Z");
        List<ExternalSkill> fieldSkills = List.of(
                externalSkill(219, "SLB_ENV_DEEPCHECK", "0", createdAt),
                externalSkill(220, "bin-file_compare", "1", createdAt),
                externalSkill(221, "threadSafe-Refactor", "3", createdAt));
        when(gateway.enabled()).thenReturn(true);
        when(gateway.listSkills()).thenReturn(fieldSkills);
        AgentSkillHubApplicationService service = service(repository);
        service.setSkillHubGateway(gateway);

        var response = service.syncExternalSkillHubCatalog();

        ArgumentCaptor<List<ExternalSkill>> catalog = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceExternalCatalog(catalog.capture(), any(Instant.class));
        assertThat(response.assetCount()).isEqualTo(3);
        assertThat(catalog.getValue()).extracting(ExternalSkill::name)
                .containsExactly("SLB_ENV_DEEPCHECK", "bin-file_compare", "threadSafe-Refactor");
    }

    @Test
    void externalCatalogStillRejectsUnsafePathName() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        SkillHubGateway gateway = mock(SkillHubGateway.class);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.listSkills()).thenReturn(List.of(
                externalSkill(219, "unsafe/name", "0", Instant.parse("2026-07-31T02:13:20Z"))));
        AgentSkillHubApplicationService service = service(repository);
        service.setSkillHubGateway(gateway);

        assertThatThrownBy(service::syncExternalSkillHubCatalog)
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("条目标识无效");
        verify(repository, never()).replaceExternalCatalog(any(), any());
    }

    @Test
    void externalUploadValidatesPackageAndReturnsDocumentedResult() throws Exception {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        SkillHubGateway gateway = mock(SkillHubGateway.class);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.upload(any())).thenReturn(new SkillHubUploadSubmission("user001_1723526400000"));
        when(gateway.uploadProgress("user001_1723526400000"))
                .thenReturn(new SkillHubUploadProgress(100, "完成"));
        AgentSkillHubApplicationService service = service(repository);
        service.setSkillHubGateway(gateway);
        SkillHubUploadRequest request = uploadRequest(" 研发团队 ", "04", zip("SKILL.md", "name: api-check"));

        assertThat(service.uploadExternalSkillHub(request)).isEqualTo("user001_1723526400000");
        assertThat(service.externalSkillHubUploadProgress("user001_1723526400000"))
                .isEqualTo(new SkillHubUploadProgress(100, "完成"));

        ArgumentCaptor<SkillHubUploadRequest> uploaded = ArgumentCaptor.forClass(SkillHubUploadRequest.class);
        verify(gateway).upload(uploaded.capture());
        assertThat(uploaded.getValue().source()).isEqualTo("研发团队");
        assertThat(uploaded.getValue().phase()).isEqualTo("04");
        verify(gateway).uploadProgress("user001_1723526400000");
    }

    @Test
    void externalUploadRejectsUndocumentedPhaseBeforeCallingRemote() throws Exception {
        SkillHubGateway gateway = mock(SkillHubGateway.class);
        when(gateway.enabled()).thenReturn(true);
        AgentSkillHubApplicationService service = service(mock(AgentSkillHubRepository.class));
        service.setSkillHubGateway(gateway);

        assertThatThrownBy(() -> service.uploadExternalSkillHub(
                uploadRequest("研发团队", "07", zip("SKILL.md", "name: api-check"))))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("00 至 06");
        verify(gateway, never()).upload(any());
    }

    @Test
    void publicBuiltinReconciliationDefaultsToTenMinutes() throws Exception {
        Scheduled scheduled = AgentSkillHubApplicationService.class
                .getDeclaredMethod("reconcilePublicBuiltinSnapshots")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${test-agent.agent-skill-hub.builtin-reconcile-delay:PT10M}");
    }

    @Test
    void localSnapshotReconciliationSkipsDisabledHistoricalWorkspaceTemplates() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        ApplicationId appId = new ApplicationId("app_1");
        ApplicationWorkspace disabled = new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_1"),
                appId,
                new CodeRepositoryId("repo_1"),
                "feature_testagent_20260725",
                "service/pay",
                "历史自动化工作区",
                false,
                now,
                now);
        when(configuration.findApplications(true))
                .thenReturn(List.of(new ApplicationDefinition(appId, "应用", true, now, now)));
        when(configuration.findWorkspaces(appId)).thenReturn(List.of(disabled));
        when(managed.findVersionsByApplication(appId)).thenReturn(List.of(version("a".repeat(40))));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository,
                configuration,
                managed,
                mock(CommonParameterValues.class),
                git,
                new ObjectMapper());

        service.reconcileLocalSnapshots();

        verify(managed, never()).findVersionReplicaByRuntimeWorkspace(any());
        verifyNoInteractions(git);
    }

    @Test
    void externalMaterializationRejectsZipTraversalBeforePersistence() throws Exception {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        SkillHubGateway gateway = mock(SkillHubGateway.class);
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        Asset external = new Asset(
                "hub_asset_external", null, null, AssetType.SKILL, "case-design",
                SkillCategory.OTHER, null, null, null, now, now,
                SourceKind.SKILLHUB, true, 42L, "1.2.0", "official", "test",
                "stable", "稳定", "team", 7L, "测试设计", "生成案例", null, null);
        when(repository.findAsset(external.assetId())).thenReturn(Optional.of(external));
        when(gateway.enabled()).thenReturn(true);
        when(gateway.download(42, "1.2.0")).thenReturn(
                new ExternalSkillPackage(42, "1.2.0", zip("../SKILL.md", "name: case-design")));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class), mock(CommonParameterValues.class),
                mock(GitWorkspaceService.class), new ObjectMapper());
        service.setSkillHubGateway(gateway);

        assertThatThrownBy(() -> service.materializeExternalAsset(
                external.assetId(), null, new UserId("usr_1")))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("路径无效");
        verify(repository, never()).saveExternalRevision(
                anyString(), anyLong(), anyString(), any(), anyString(), any(), any(), any());
    }

    private byte[] zip(String path, String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private SkillHubUploadRequest uploadRequest(String source, String phase, byte[] packageBytes) {
        return new SkillHubUploadRequest(
                source,
                phase,
                new SkillHubUploadFile("api-check.zip", "application/zip", packageBytes),
                new SkillHubUploadFile("safety.png", "image/png", new byte[]{1}),
                new SkillHubUploadFile("directory.png", "image/png", new byte[]{2}),
                new SkillHubUploadFile("running.png", "image/png", new byte[]{3}));
    }

    private ExternalSkill externalSkill(long id, String name, String version, Instant createdAt) {
        return new ExternalSkill(
                id, name, version, name, "现场目录条目", "杭州产品部", null,
                "04", "测试", "000831611", createdAt, 23);
    }

    private AgentSkillHubApplicationService service(AgentSkillHubRepository repository) {
        return new AgentSkillHubApplicationService(
                repository,
                mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class),
                mock(CommonParameterValues.class),
                mock(GitWorkspaceService.class),
                new ObjectMapper());
    }

    @Test
    void successfulPushBuildsOneAgentAndOneWholeSkillArtifactFromCommit() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        Path repoRoot = Path.of("/repo");
        Path workspaceRoot = Path.of("/repo/service/pay");
        String commit = "a".repeat(40);
        List<String> files = List.of(
                "service/pay/.opencode/agents/checkout.md",
                "service/pay/.opencode/skills/api-check/SKILL.md",
                "service/pay/.opencode/skills/api-check/templates/request.md",
                "service/pay/.opencode/opencode.jsonc");
        when(git.listFilesAtCommit(repoRoot, commit, "service/pay/.opencode")).thenReturn(files);
        when(git.readFileAtCommit(any(), any(), any())).thenAnswer(invocation -> switch ((String) invocation.getArgument(2)) {
            case "service/pay/.opencode/agents/checkout.md" -> "---\ndescription: Checkout（结账检查）。\n---\n# Checkout".getBytes(StandardCharsets.UTF_8);
            case "service/pay/.opencode/skills/api-check/SKILL.md" -> "---\nname: api-check\nmetadata:\n  display-name-zh: 接口检查\n---".getBytes(StandardCharsets.UTF_8);
            case "service/pay/.opencode/skills/api-check/templates/request.md" -> "template".getBytes(StandardCharsets.UTF_8);
            default -> "{}".getBytes(StandardCharsets.UTF_8);
        });
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository,
                mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class),
                mock(CommonParameterValues.class),
                git,
                new ObjectMapper());

        service.indexSuccessfulPush(version(commit), repoRoot, workspaceRoot, commit);

        ArgumentCaptor<PushedSnapshot> snapshot = ArgumentCaptor.forClass(PushedSnapshot.class);
        verify(repository).replacePushedSnapshot(snapshot.capture());
        assertThat(snapshot.getValue().sourceCommitHash()).isEqualTo(commit);
        assertThat(snapshot.getValue().assets()).hasSize(2);
        assertThat(snapshot.getValue().assets()).anySatisfy(asset -> {
            assertThat(asset.assetType()).isEqualTo(AssetType.AGENT);
            assertThat(asset.technicalId()).isEqualTo("checkout");
            assertThat(asset.displayName()).isEqualTo("结账检查");
            assertThat(asset.artifact().fileCount()).isEqualTo(1);
            assertThat(asset.artifact().sha256()).hasSize(64);
        });
        assertThat(snapshot.getValue().assets()).anySatisfy(asset -> {
            assertThat(asset.assetType()).isEqualTo(AssetType.SKILL);
            assertThat(asset.technicalId()).isEqualTo("api-check");
            assertThat(asset.displayName()).isEqualTo("接口检查");
            assertThat(asset.artifact().fileCount()).isEqualTo(2);
        });
    }

    @Test
    void successfulPushFinalizesPendingReferenceRemoval() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        String commit = "d".repeat(40);
        Path repoRoot = Path.of("/repo");
        Path workspaceRoot = Path.of("/repo/service/pay");
        when(git.listFilesAtCommit(repoRoot, commit, "service/pay/.opencode")).thenReturn(List.of());
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Reference removing = new Reference(
                "hub_ref_remove", "hub_asset_1", "app_1", "awp_1",
                ".opencode/agents/reviewer.md", "reviewer", "hub_rev_1", null, null,
                "PENDING_REMOVE", "usr_1", now, now);
        when(repository.findPendingReferences("awp_1")).thenReturn(List.of(removing));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository,
                mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class),
                mock(CommonParameterValues.class),
                git,
                new ObjectMapper());

        service.indexSuccessfulPush(version(commit), repoRoot, workspaceRoot, commit);

        ArgumentCaptor<List<PushReferenceDecision>> decisions = ArgumentCaptor.forClass(List.class);
        verify(repository).replacePushedSnapshot(any(PushedSnapshot.class), decisions.capture());
        assertThat(decisions.getValue()).singleElement().satisfies(decision -> {
            assertThat(decision.referenceId()).isEqualTo("hub_ref_remove");
            assertThat(decision.action()).isEqualTo(PushReferenceAction.REMOVE);
        });
    }

    @Test
    void exposesPublicConfigAsReadOnlyBuiltinAtExactCommit() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        Path publicRoot = Path.of("/public-config");
        String commit = "b".repeat(40);
        when(parameters.resolvedValue("OPENCODE_PUBLIC_CONFIG_GIT_ROOT")).thenReturn(Optional.of(publicRoot.toString()));
        when(git.isGitRepository(publicRoot)).thenReturn(true);
        when(git.headCommit(publicRoot)).thenReturn(commit);
        when(git.listFilesAtCommit(publicRoot, commit, "opencode"))
                .thenReturn(List.of(
                        "opencode/agents/reviewer.md",
                        "opencode/skills/review/SKILL.md",
                        "opencode/skills/review-api/SKILL.md"));
        when(git.readFileAtCommit(publicRoot, commit, "opencode/agents/reviewer.md"))
                .thenReturn(("---\ndescription: Reviewer（评审专家）。\n---\n# Reviewer\n"
                        + "加载 `review` skill。").getBytes(StandardCharsets.UTF_8));
        when(git.readFileAtCommit(publicRoot, commit, "opencode/skills/review/SKILL.md"))
                .thenReturn("---\nname: review\n---\n# Review".getBytes(StandardCharsets.UTF_8));
        when(git.readFileAtCommit(publicRoot, commit, "opencode/skills/review-api/SKILL.md"))
                .thenReturn("---\nname: review-api\n---\n# Review API".getBytes(StandardCharsets.UTF_8));
        AtomicReference<BuiltinSnapshot> stored = new AtomicReference<>();
        when(repository.findBuiltinSnapshotCommit()).thenAnswer(invocation -> Optional.ofNullable(stored.get())
                .map(BuiltinSnapshot::sourceCommitHash));
        when(repository.replaceBuiltinSnapshot(nullable(String.class), any(BuiltinSnapshot.class)))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(1));
                    return true;
                });
        when(repository.listCurrentBuiltinRevisions()).thenAnswer(invocation -> stored.get() == null
                ? List.of()
                : stored.get().revisions().stream().map(item -> item.revision()).toList());
        when(repository.listBuiltinRevisionsByCommit(anyString())).thenAnswer(invocation -> stored.get() == null
                ? List.of()
                : stored.get().revisions().stream().map(item -> item.revision())
                .filter(item -> item.sourceCommitHash().equals(invocation.getArgument(0))).toList());
        when(repository.findCurrentBuiltinRevision(anyString())).thenAnswer(invocation -> stored.get().revisions()
                .stream().map(item -> item.revision())
                .filter(item -> item.assetId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findBuiltinRevision(anyString())).thenAnswer(invocation -> stored.get().revisions()
                .stream().map(item -> item.revision())
                .filter(item -> item.revisionId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findArtifact(anyString())).thenAnswer(invocation -> stored.get().revisions().stream()
                .map(item -> item.artifact())
                .filter(item -> item.sha256().equals(invocation.getArgument(0))).findFirst());
        when(repository.listAssets(any(), nullable(SkillCategory.class), nullable(SkillSubcategory.class),
                nullable(String.class), anyString(), nullable(String.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, mock(ConfigurationManagementRepository.class), mock(ManagedWorkspaceRepository.class),
                parameters, git, new ObjectMapper());

        service.reconcilePublicBuiltinSnapshots();
        service.reconcilePublicBuiltinSnapshots();
        verify(git, times(1)).listFilesAtCommit(publicRoot, commit, "opencode");
        clearInvocations(git);
        var page = service.listAssets("AGENT", null, 1, 100, new UserId("usr_1"));

        assertThat(page.total()).isEqualTo(1);
        var protectedCatalog = service.listCatalog(
                new UserId("usr_1"), new WorkspaceId("wrk_protected_catalog"));
        assertThat(protectedCatalog).singleElement().satisfies(item -> {
            assertThat(item.selectionId()).startsWith("protected:");
            assertThat(item.displayName()).isEqualTo("评审专家");
            assertThat(item.toString()).doesNotContain("# Reviewer", "AGENT.md");
            var definition = service.resolve(
                    new UserId("usr_1"),
                    new WorkspaceId("wrk_protected_catalog"),
                    item.revisionId());
            assertThat(definition.agentFiles().get("AGENT.md")).contains("# Reviewer");
            assertThat(definition.skills()).singleElement().satisfies(skill -> {
                assertThat(skill.technicalId()).isEqualTo("review");
                assertThat(skill.files()).containsEntry("SKILL.md", "---\nname: review\n---\n# Review");
            });
        });
        assertThat(page.items()).singleElement().satisfies(asset -> {
            assertThat(asset.builtin()).isTrue();
            assertThat(asset.displayName()).isEqualTo("评审专家");
            assertThat(asset.sourceAppName()).isEqualTo("平台内置");
            var detail = service.getAsset(asset.assetId(), null, new UserId("usr_1"));
            assertThat(detail.files()).extracting("path").containsExactly("AGENT.md");
            assertThat(service.readFile(detail.selectedRevisionId(), "AGENT.md").content()).contains("# Reviewer");
            assertThatThrownBy(() -> service.publish(asset.assetId(), List.of(), new UserId("usr_1")))
                    .hasMessageContaining("无需发布");
        });
        verifyNoInteractions(git);
    }

    @Test
    void publicSnapshotReconcileDoesNotLetStaleServerMoveCatalogBackward() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        Path publicRoot = Path.of("/public-config");
        String oldCommit = "a".repeat(40);
        String indexedCommit = "b".repeat(40);
        when(parameters.resolvedValue("OPENCODE_PUBLIC_CONFIG_GIT_ROOT"))
                .thenReturn(Optional.of(publicRoot.toString()));
        when(git.isGitRepository(publicRoot)).thenReturn(true);
        when(git.headCommit(publicRoot)).thenReturn(oldCommit);
        when(git.isAncestor(publicRoot, oldCommit, indexedCommit)).thenReturn(true);
        when(repository.findBuiltinSnapshotCommit()).thenReturn(Optional.of(indexedCommit));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, mock(ConfigurationManagementRepository.class), mock(ManagedWorkspaceRepository.class),
                parameters, git, new ObjectMapper());

        service.reconcilePublicBuiltinSnapshots();

        verify(git, never()).listFilesAtCommit(any(), anyString(), anyString());
        verify(repository, never()).replaceBuiltinSnapshot(nullable(String.class), any(BuiltinSnapshot.class));
    }

    @Test
    void publicSnapshotReconcileFindsAPushFromAnotherLocalClone() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        Path publicRoot = Path.of("/public-config");
        String localCommit = "1".repeat(40);
        String remoteCommit = "2".repeat(40);
        when(parameters.resolvedValue("OPENCODE_PUBLIC_CONFIG_GIT_ROOT"))
                .thenReturn(Optional.of(publicRoot.toString()));
        when(git.isGitRepository(publicRoot)).thenReturn(true);
        when(git.headCommit(publicRoot)).thenReturn(localCommit);
        when(git.currentBranch(publicRoot)).thenReturn("main");
        when(git.resolveCommit(publicRoot, "origin/main")).thenReturn(remoteCommit);
        when(git.listFilesAtCommit(publicRoot, remoteCommit, "opencode"))
                .thenReturn(List.of("opencode/agents/remote-reviewer.md"));
        when(git.readFileAtCommit(publicRoot, remoteCommit, "opencode/agents/remote-reviewer.md"))
                .thenReturn("---\ndescription: 远端评审\n---\n# Reviewer".getBytes(StandardCharsets.UTF_8));
        when(repository.replaceBuiltinSnapshot(nullable(String.class), any(BuiltinSnapshot.class))).thenReturn(true);
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, mock(ConfigurationManagementRepository.class), mock(ManagedWorkspaceRepository.class),
                parameters, git, new ObjectMapper());

        service.reconcilePublicBuiltinSnapshots();

        verify(git).fetch(publicRoot, null);
        ArgumentCaptor<BuiltinSnapshot> snapshot = ArgumentCaptor.forClass(BuiltinSnapshot.class);
        verify(repository).replaceBuiltinSnapshot(nullable(String.class), snapshot.capture());
        assertThat(snapshot.getValue().sourceCommitHash()).isEqualTo(remoteCommit);
        assertThat(snapshot.getValue().revisions()).singleElement().satisfies(item ->
                assertThat(item.revision().technicalId()).isEqualTo("remote-reviewer"));
    }

    @Test
    void pendingPushIsNotReportedAsEffectiveReference() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Asset asset = new Asset("hub_asset_1", "app_source", "aw_source", AssetType.AGENT, "reviewer",
                "hub_rev_1", "hub_rev_1", now, now);
        Revision revision = new Revision(
                "hub_rev_1", asset.assetId(), "ver_1", "a".repeat(40), "1".repeat(64), "1".repeat(64),
                "Reviewer", null, null, false, now, now, "usr_1");
        when(repository.listAssets(any(), nullable(SkillCategory.class), nullable(SkillSubcategory.class),
                nullable(String.class), anyString(), nullable(String.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of(new AssetSummary(
                        asset, revision, revision, "来源应用", "来源工作空间", false, "PENDING_PUSH", 0)));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository,
                mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class),
                mock(CommonParameterValues.class),
                mock(GitWorkspaceService.class),
                new ObjectMapper());

        var response = service.listAssets("AGENT", null, 1, 10, new UserId("usr_1")).items().getFirst();

        assertThat(response.referenceStatus()).isEqualTo("PENDING_PUSH");
        assertThat(response.referenced()).isFalse();
    }

    @Test
    void pendingRemovalIsNotReportedAsEffectiveReference() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Asset asset = new Asset("hub_asset_1", "app_source", "aw_source", AssetType.SKILL, "api-check",
                "hub_rev_1", "hub_rev_1", now, now);
        Revision revision = new Revision(
                "hub_rev_1", asset.assetId(), "ver_1", "a".repeat(40), "1".repeat(64), "1".repeat(64),
                "API Check", null, null, false, now, now, "usr_1");
        when(repository.listAssets(any(), nullable(SkillCategory.class), nullable(SkillSubcategory.class),
                nullable(String.class), anyString(), nullable(String.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of(new AssetSummary(
                        asset, revision, revision, "来源应用", "来源工作空间", false, "PENDING_REMOVE", 0)));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository,
                mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class),
                mock(CommonParameterValues.class),
                mock(GitWorkspaceService.class),
                new ObjectMapper());

        var response = service.listAssets("SKILL", null, 1, 10, new UserId("usr_1")).items().getFirst();

        assertThat(response.referenceStatus()).isEqualTo("PENDING_REMOVE");
        assertThat(response.referenced()).isFalse();
    }

    @Test
    void superAdminClassificationUsesControlledSkillTaxonomy() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        Asset asset = new Asset("hub_asset_skill", "app_source", "aw_source", AssetType.SKILL, "case-design",
                "hub_rev_1", "hub_rev_1", now, now);
        when(repository.findAsset(asset.assetId())).thenReturn(Optional.of(asset));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository,
                mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class),
                mock(CommonParameterValues.class),
                mock(GitWorkspaceService.class),
                new ObjectMapper());

        var response = service.classifySkill(
                asset.assetId(), "test", "test_design", new UserId("usr_admin"));

        assertThat(response.category()).isEqualTo("TEST");
        assertThat(response.subcategory()).isEqualTo("TEST_DESIGN");
        verify(repository).updateSkillClassification(
                asset.assetId(), SkillCategory.TEST, SkillSubcategory.TEST_DESIGN,
                "usr_admin", response.classifiedAt());
        assertThatThrownBy(() -> service.classifySkill(
                asset.assetId(), "CODE", "TEST_EXECUTION", new UserId("usr_admin")))
                .hasMessageContaining("不匹配");
    }

    @Test
    void superAdminCanClassifyPublicGitSkill() {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        String assetId = "hub_builtin_SKILL_d2hpdGUtYm94LWFuYWx5c2lz";
        BuiltinRevision revision = new BuiltinRevision(
                "hub_builtin_rev_" + "a".repeat(40) + "_SKILL_d2hpdGUtYm94LWFuYWx5c2lz",
                assetId, AssetType.SKILL, "white-box-analysis", "a".repeat(40), "b".repeat(64),
                "c".repeat(64), "白盒分析", "White-box analysis", "分析代码实现",
                SkillCategory.OTHER, null, now);
        when(repository.findCurrentBuiltinRevision(assetId)).thenReturn(Optional.of(revision));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository,
                mock(ConfigurationManagementRepository.class),
                mock(ManagedWorkspaceRepository.class),
                mock(CommonParameterValues.class),
                mock(GitWorkspaceService.class),
                new ObjectMapper());

        var response = service.classifySkill(
                assetId, "CODE", "WHITE_BOX_ANALYSIS", new UserId("usr_admin"));

        assertThat(response.assetId()).isEqualTo(assetId);
        verify(repository).updateBuiltinSkillClassification(
                assetId, SkillCategory.CODE, SkillSubcategory.WHITE_BOX_ANALYSIS,
                "usr_admin", response.classifiedAt());
    }

    @Test
    void conflictFreeUpdateWritesIncomingAndPersistsCompletedOperation(@TempDir Path workspaceRoot) throws Exception {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        when(parameters.resolvedValue(ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT))
                .thenReturn(Optional.of(workspaceRoot.toString()));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, configuration, managed, parameters, git, new ObjectMapper());
        Path sourceRepo = Path.of("/repo");
        Path sourceWorkspace = Path.of("/repo/service/pay");
        String baseCommit = "a".repeat(40);
        String incomingCommit = "b".repeat(40);
        String sourcePath = "service/pay/.opencode/agents/reviewer.md";
        when(git.listFilesAtCommit(sourceRepo, baseCommit, "service/pay/.opencode"))
                .thenReturn(List.of(sourcePath));
        when(git.listFilesAtCommit(sourceRepo, incomingCommit, "service/pay/.opencode"))
                .thenReturn(List.of(sourcePath));
        when(git.readFileAtCommit(sourceRepo, baseCommit, sourcePath))
                .thenReturn("---\ndescription: Reviewer\n---\nbase\n".getBytes(StandardCharsets.UTF_8));
        when(git.readFileAtCommit(sourceRepo, incomingCommit, sourcePath))
                .thenReturn("---\ndescription: Reviewer\n---\nincoming\n".getBytes(StandardCharsets.UTF_8));
        service.indexSuccessfulPush(version(baseCommit), sourceRepo, sourceWorkspace, baseCommit);
        service.indexSuccessfulPush(version(incomingCommit), sourceRepo, sourceWorkspace, incomingCommit);
        ArgumentCaptor<PushedSnapshot> snapshots = ArgumentCaptor.forClass(PushedSnapshot.class);
        verify(repository, org.mockito.Mockito.times(2)).replacePushedSnapshot(snapshots.capture());
        var baseArtifact = snapshots.getAllValues().get(0).assets().getFirst().artifact();
        var incomingArtifact = snapshots.getAllValues().get(1).assets().getFirst().artifact();

        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        String assetId = "hub_asset_reviewer";
        String baseRevisionId = "hub_rev_base";
        String incomingRevisionId = "hub_rev_incoming";
        Asset asset = new Asset(assetId, "app_source", "aw_source", AssetType.AGENT, "reviewer",
                incomingRevisionId, incomingRevisionId, now, now);
        Revision base = new Revision(baseRevisionId, assetId, "ver_1", baseCommit, baseArtifact.sha256(),
                baseArtifact.sha256(), "Reviewer", null, null, false, now, now, "usr_1");
        Revision incoming = new Revision(incomingRevisionId, assetId, "ver_1", incomingCommit,
                incomingArtifact.sha256(), incomingArtifact.sha256(), "Reviewer", null, null,
                false, now.plusSeconds(60), now.plusSeconds(60), "usr_1");
        Reference reference = new Reference("hub_ref_1", assetId, "app_target", "awp_target",
                ".opencode/agents/reviewer.md", "reviewer", baseRevisionId, null, null,
                "ACTIVE", "usr_1", now, now);
        PersonalWorkspace personal = personalWorkspace();
        Path target = workspaceRoot.resolve("hub-user/.opencode/agents/reviewer.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "---\ndescription: Reviewer\n---\nbase\n");

        when(managed.findPersonalWorkspaceByRuntimeWorkspace(new WorkspaceId("wrk_target")))
                .thenReturn(Optional.of(personal));
        when(configuration.isActiveMember(new ApplicationId("app_target"), new UserId("usr_1"))).thenReturn(true);
        when(repository.findReference("hub_ref_1")).thenReturn(Optional.of(reference));
        when(repository.findAsset(assetId)).thenReturn(Optional.of(asset));
        when(repository.findRevision(baseRevisionId)).thenReturn(Optional.of(base));
        when(repository.findRevision(incomingRevisionId)).thenReturn(Optional.of(incoming));
        when(repository.findArtifact(baseArtifact.sha256())).thenReturn(Optional.of(baseArtifact));
        when(repository.findArtifact(incomingArtifact.sha256())).thenReturn(Optional.of(incomingArtifact));
        when(repository.saveReferenceAndUpdateOperation(any(Reference.class), any(UpdateOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.startUpdate("hub_ref_1", "wrk_target", new UserId("usr_1"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.worktreeChanged()).isTrue();
        assertThat(Files.readString(target)).contains("incoming").doesNotContain("\nbase\n");
        ArgumentCaptor<UpdateOperation> operation = ArgumentCaptor.forClass(UpdateOperation.class);
        verify(repository).saveReferenceAndUpdateOperation(any(Reference.class), operation.capture());
        assertThat(operation.getValue().status()).isEqualTo("COMPLETED");
    }

    @Test
    void removeReferenceDeletesWorktreeFileAndWaitsForPush(@TempDir Path workspaceRoot) throws Exception {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        when(parameters.resolvedValue(ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT))
                .thenReturn(Optional.of(workspaceRoot.toString()));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, configuration, managed, parameters, mock(GitWorkspaceService.class), new ObjectMapper());
        PersonalWorkspace personal = personalWorkspace();
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Asset asset = new Asset("hub_asset_1", "app_source", "aw_source", AssetType.AGENT, "reviewer",
                "hub_rev_1", "hub_rev_1", now, now);
        Reference reference = new Reference(
                "hub_ref_1", asset.assetId(), "app_target", "awp_target",
                ".opencode/agents/reviewer.md", "reviewer", "hub_rev_1", null, null,
                "ACTIVE", "usr_1", now, now);
        Path target = workspaceRoot.resolve("hub-user/.opencode/agents/reviewer.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "# reviewer\n");
        when(managed.findPersonalWorkspaceByRuntimeWorkspace(new WorkspaceId("wrk_target")))
                .thenReturn(Optional.of(personal));
        when(configuration.isActiveMember(new ApplicationId("app_target"), new UserId("usr_1"))).thenReturn(true);
        when(repository.findAsset(asset.assetId())).thenReturn(Optional.of(asset));
        when(repository.findReferencesByTargetAsset("awp_target", asset.assetId())).thenReturn(List.of(reference));
        when(repository.saveReference(any(Reference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.removeReference(asset.assetId(), "wrk_target", new UserId("usr_1"));

        assertThat(result.status()).isEqualTo("PENDING_REMOVE");
        assertThat(result.activeRevisionId()).isEqualTo("hub_rev_1");
        assertThat(result.pendingRevisionId()).isNull();
        assertThat(target).doesNotExist();
        assertThat(result.message()).contains("推送后正式解除引用");
    }

    @Test
    void createReferenceReactivatesPendingRemovalInTheSameReference(@TempDir Path workspaceRoot) throws Exception {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        when(parameters.resolvedValue(ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT))
                .thenReturn(Optional.of(workspaceRoot.toString()));
        Path sourceRepo = Path.of("/repo");
        Path sourceWorkspace = Path.of("/repo/service/pay");
        String commit = "f".repeat(40);
        String agentPath = "service/pay/.opencode/agents/reviewer.md";
        when(git.listFilesAtCommit(sourceRepo, commit, "service/pay/.opencode")).thenReturn(List.of(agentPath));
        when(git.readFileAtCommit(sourceRepo, commit, agentPath)).thenReturn(
                "---\ndescription: Reviewer\n---\n# Reviewer\n".getBytes(StandardCharsets.UTF_8));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, configuration, managed, parameters, git, new ObjectMapper());
        service.indexSuccessfulPush(version(commit), sourceRepo, sourceWorkspace, commit);
        ArgumentCaptor<PushedSnapshot> snapshot = ArgumentCaptor.forClass(PushedSnapshot.class);
        verify(repository).replacePushedSnapshot(snapshot.capture());
        var artifact = snapshot.getValue().assets().getFirst().artifact();

        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Asset asset = new Asset("hub_asset_agent", "app_source", "aw_source", AssetType.AGENT, "reviewer",
                "hub_rev_agent", "hub_rev_agent", now, now);
        Revision revision = new Revision(
                "hub_rev_agent", asset.assetId(), "ver_1", commit, artifact.sha256(), artifact.sha256(),
                "Reviewer", null, null, false, now, now, "usr_1");
        Reference removing = new Reference(
                "hub_ref_existing", asset.assetId(), "app_target", "awp_target",
                ".opencode/agents/reviewer.md", "reviewer", "hub_rev_old", null, null,
                "PENDING_REMOVE", "usr_1", now, now.plusSeconds(1));
        when(managed.findPersonalWorkspaceByRuntimeWorkspace(new WorkspaceId("wrk_target")))
                .thenReturn(Optional.of(personalWorkspace()));
        when(configuration.isActiveMember(new ApplicationId("app_target"), new UserId("usr_1"))).thenReturn(true);
        when(repository.findAsset(asset.assetId())).thenReturn(Optional.of(asset));
        when(repository.findRevision(revision.revisionId())).thenReturn(Optional.of(revision));
        when(repository.findArtifact(artifact.sha256())).thenReturn(Optional.of(artifact));
        when(repository.findReferencesByTargetAsset("awp_target", asset.assetId())).thenReturn(List.of(removing));
        when(repository.saveReferences(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createReference(asset.assetId(), "wrk_target", null, new UserId("usr_1"));

        assertThat(result.referenceId()).isEqualTo(removing.referenceId());
        assertThat(result.status()).isEqualTo("PENDING_PUSH");
        assertThat(result.activeRevisionId()).isEqualTo("hub_rev_old");
        assertThat(result.pendingRevisionId()).isEqualTo(revision.revisionId());
        assertThat(result.message()).contains("重新引用");
        assertThat(workspaceRoot.resolve("hub-user/.opencode/agents/reviewer.md"))
                .hasContent("---\ndescription: Reviewer\n---\n# Reviewer\n");
        verify(repository).saveReferences(org.mockito.ArgumentMatchers.argThat(references ->
                references.size() == 1
                        && references.getFirst().referenceId().equals(removing.referenceId())
                        && references.getFirst().status().equals("PENDING_PUSH")));
    }

    @Test
    void createReferenceRejectsExistingSkillRootWithoutOverwritingFiles(@TempDir Path workspaceRoot) throws Exception {
        AgentSkillHubRepository repository = mock(AgentSkillHubRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        when(parameters.resolvedValue(ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT))
                .thenReturn(Optional.of(workspaceRoot.toString()));
        Path sourceRepo = Path.of("/repo");
        Path sourceWorkspace = Path.of("/repo/service/pay");
        String commit = "e".repeat(40);
        String skillPath = "service/pay/.opencode/skills/api-check/SKILL.md";
        when(git.listFilesAtCommit(sourceRepo, commit, "service/pay/.opencode")).thenReturn(List.of(skillPath));
        when(git.readFileAtCommit(sourceRepo, commit, skillPath)).thenReturn(
                "---\nname: api-check\ndescription: API check\n---\n".getBytes(StandardCharsets.UTF_8));
        AgentSkillHubApplicationService service = new AgentSkillHubApplicationService(
                repository, configuration, managed, parameters, git, new ObjectMapper());
        service.indexSuccessfulPush(version(commit), sourceRepo, sourceWorkspace, commit);
        ArgumentCaptor<PushedSnapshot> snapshot = ArgumentCaptor.forClass(PushedSnapshot.class);
        verify(repository).replacePushedSnapshot(snapshot.capture());
        var artifact = snapshot.getValue().assets().getFirst().artifact();

        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Asset asset = new Asset("hub_asset_skill", "app_source", "aw_source", AssetType.SKILL, "api-check",
                "hub_rev_skill", "hub_rev_skill", now, now);
        Revision revision = new Revision(
                "hub_rev_skill", asset.assetId(), "ver_1", commit, artifact.sha256(), artifact.sha256(),
                "API check", null, null, false, now, now, "usr_1");
        PersonalWorkspace personal = personalWorkspace();
        when(managed.findPersonalWorkspaceByRuntimeWorkspace(new WorkspaceId("wrk_target")))
                .thenReturn(Optional.of(personal));
        when(configuration.isActiveMember(new ApplicationId("app_target"), new UserId("usr_1"))).thenReturn(true);
        when(repository.findAsset(asset.assetId())).thenReturn(Optional.of(asset));
        when(repository.findRevision(revision.revisionId())).thenReturn(Optional.of(revision));
        when(repository.findArtifact(artifact.sha256())).thenReturn(Optional.of(artifact));
        Path customFile = workspaceRoot.resolve("hub-user/.opencode/skills/api-check/custom.txt");
        Files.createDirectories(customFile.getParent());
        Files.writeString(customFile, "keep me");

        assertThatThrownBy(() -> service.createReference(asset.assetId(), "wrk_target", null, new UserId("usr_1")))
                .hasMessageContaining("目标 Agent/Skill 已存在");
        assertThat(customFile).exists();
        assertThat(Files.readString(customFile)).isEqualTo("keep me");
    }

    private ApplicationWorkspaceVersion version(String commit) {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return new ApplicationWorkspaceVersion(
                new ApplicationWorkspaceVersionId("ver_1"), new ApplicationWorkspaceId("awp_1"),
                new ApplicationId("app_1"), new CodeRepositoryId("repo_1"), "20260725",
                "feature_testagent_20260725", "/repo", "/repo/service/pay", new WorkspaceId("wrk_1"),
                new UserId("usr_1"), ManagedWorkspaceStatus.ACTIVE, commit, now, now, now);
    }

    private PersonalWorkspace personalWorkspace() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return new PersonalWorkspace(new PersonalWorkspaceId("per_1"),
                new ApplicationWorkspaceVersionId("ver_1"), new ApplicationId("app_target"),
                new ApplicationWorkspaceId("awp_target"), new UserId("usr_1"), "个人工作区", "feature-user",
                "personalworktree:hub-user", "personalworktree:hub-user", new WorkspaceId("wrk_target"),
                "a".repeat(40), ManagedWorkspaceStatus.ACTIVE, now, now);
    }
}
