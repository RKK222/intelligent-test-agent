package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationMember;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreeClaim;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreePending;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import com.enterprise.testagent.domain.configuration.SshKeyId;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplicaId;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.managedworkspace.UserWorkspaceBranchPreference;
import com.enterprise.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.enterprise.testagent.domain.managedworkspace.WorkspaceReplicaSyncStatus;
import com.enterprise.testagent.domain.managedworkspace.WorkspaceSyncDirection;
import com.enterprise.testagent.domain.managedworkspace.WorkspaceSyncRecord;
import com.enterprise.testagent.domain.managedworkspace.WorkspaceSyncStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextWorkspaceMutation;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedWorkspaceApplicationServiceTest {

    private final SshKeyTestFixtures sshKeyFixtures = new SshKeyTestFixtures();
    private static final String PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n";

    @TempDir
    Path root;

    @Test
    void personalWorkspaceCreationUsesOneSharedCreator() throws Exception {
        Path sourcePath = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/enterprise/testagent/workspace/ManagedWorkspaceApplicationService.java");
        String source = Files.readString(sourcePath);

        assertThat(source).doesNotContain("doCreatePersonalWorkspaceWithName(");
    }

    @Test
    void createsStandardApplicationVersionWorkspaceAndRecordsRecentUsage() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse response = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_1234567890abcdef");

        assertThat(response.version()).isEqualTo("20260707");
        assertThat(response.branch()).isEqualTo("feature_testagent_20260707");
        assertThat(response.runtimeWorkspace().rootPath()).endsWith("appworkspace/20260707/gcms/F-GCMS/workspace");
        assertThat(git.clonedBranch).isEqualTo("feature_testagent_20260707");
        assertThat(workspaces.saved).hasSize(1);
        assertThat(managed.versions).hasSize(1);
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_base");
        assertThat(managed.replicas).hasSize(1);
        assertThat(managed.replicas.get(0).linuxServerId()).isEqualTo("127.0.0.1");
        assertThat(managed.replicas.get(0).currentCommitHash()).isEqualTo("commit_base");
        assertThat(managed.globalPreference).isNotNull();
        assertThat(managed.applicationPreference).isNotNull();
    }

    @Test
    void versionGitAccessCheckReusesCurrentUserRepositoryIdentity() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version_access");

        ManagedWorkspaceResponses.GitRepositoryAccessResponse response = service.checkVersionGitAccess(
                version.versionId(), new UserId("usr_1"));

        assertThat(response.accessible()).isTrue();
        assertThat(response.repositoryId()).isEqualTo("repo_1");
        assertThat(response.repositoryName()).isEqualTo("gcms/gcms");
        assertThat(response.branch()).isEqualTo("feature_testagent_20260707");
        assertThat(response.reason()).isNull();
    }

    @Test
    void versionGitAccessCheckMapsAuthenticationFailureToPermissionApplication() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService creator = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = creator.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version_access_denied");
        GitRemoteService deniedRemote = new GitRemoteService() {
            @Override
            public List<String> listBranches(String gitUrl, String privateKey) {
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        "Git 远端认证失败",
                        Map.of("gitFailureType", "AUTHENTICATION_FAILED"));
            }
        };
        ManagedWorkspaceApplicationService service = serviceWithGitRemote(
                configuration, managed, workspaces, git, deniedRemote);

        ManagedWorkspaceResponses.GitRepositoryAccessResponse response = service.checkVersionGitAccess(
                version.versionId(), new UserId("usr_1"));

        assertThat(response.accessible()).isFalse();
        assertThat(response.reason()).isEqualTo("REPOSITORY_PERMISSION_REQUIRED");
        assertThat(response.repositoryName()).isEqualTo("gcms/gcms");
    }

    @Test
    void versionGitAccessCheckReportsMissingSshKeySeparately() {
        FakeConfigurationRepository externalConfiguration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService creator = service(externalConfiguration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = creator.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version_access_no_key");
        CodeRepository internalRepository = new CodeRepository(
                new CodeRepositoryId("repo_1"),
                "scm.example.com:29418/team/gcms.git",
                "GCMS 内部版本库",
                "gcms",
                "APPLICATION_CODE_REPOSITORY",
                CodeRepositoryDeploymentMode.INTERNAL.value(),
                false,
                Instant.now(),
                Instant.now());
        FakeConfigurationRepository internalConfiguration = new FakeConfigurationRepository(
                true, internalRepository, List.of());
        ManagedWorkspaceApplicationService service = serviceWithGitRemote(
                internalConfiguration,
                managed,
                workspaces,
                git,
                new FakeGitRemoteService(List.of("feature_testagent_20260707")));

        ManagedWorkspaceResponses.GitRepositoryAccessResponse response = service.checkVersionGitAccess(
                version.versionId(), new UserId("usr_1"));

        assertThat(response.accessible()).isFalse();
        assertThat(response.reason()).isEqualTo("SSH_KEY_MISSING");
        assertThat(response.repositoryName()).isEqualTo("GCMS 内部版本库");
    }

    @Test
    void versionGitAccessCheckDoesNotMisreportNetworkFailuresAsPermissionProblems() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService creator = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = creator.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version_access_network");
        GitRemoteService unavailableRemote = new GitRemoteService() {
            @Override
            public List<String> listBranches(String gitUrl, String privateKey) {
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        "Git 远端网络连接失败",
                        Map.of("gitFailureType", "NETWORK_UNAVAILABLE"));
            }
        };
        ManagedWorkspaceApplicationService service = serviceWithGitRemote(
                configuration, managed, workspaces, git, unavailableRemote);

        assertThatThrownBy(() -> service.checkVersionGitAccess(version.versionId(), new UserId("usr_1")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.GIT_UNAVAILABLE);
                    assertThat(exception.details()).containsEntry("gitFailureType", "NETWORK_UNAVAILABLE");
                });
    }

    @Test
    void createVersionRejectsExistingApplicationRepoDirectoryWithoutGitMetadata() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.strictGitRepositoryDetection = true;
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        Files.createDirectories(applicationRepoRoot().resolve("F-GCMS/workspace"));

        assertThatThrownBy(() -> service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_existing_plain_dir"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).contains("不是 Git 仓库");
                });
    }

    @Test
    void createVersionReclonesIncompleteApplicationRepoLeftByTimedOutClone() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.strictGitRepositoryDetection = true;
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        Path repoRoot = applicationRepoRoot();
        Files.createDirectories(repoRoot.resolve(".git"));
        git.gitRoots.add(repoRoot);
        git.invalidHeadCommitRoots.add(repoRoot);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse response = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_incomplete_clone");

        assertThat(response.runtimeWorkspace().rootPath()).endsWith("appworkspace/20260707/gcms/F-GCMS/workspace");
        assertThat(git.clonedBranch).isEqualTo("feature_testagent_20260707");
        assertThat(Files.isDirectory(repoRoot.resolve("F-GCMS/workspace"))).isTrue();
        assertThat(git.invalidHeadCommitRoots).doesNotContain(repoRoot);
    }

    @Test
    void workspaceCreateForTestRepositoryRequiresDirectChildOfApplicationRoot() {
        ManagedWorkspaceApplicationService service = service(
                new FakeConfigurationRepository(true),
                new FakeManagedWorkspaceRepository(),
                new FakeWorkspaceRepository(),
                new FakeGitWorkspaceService("F-GCMS/workspace/F1"));

        assertThatThrownBy(() -> service.createApplicationWorkspaceWithInitialVersion(
                "app_gcms",
                "repo_1",
                "feature_testagent_20260707",
                "F-GCMS/workspace/F1",
                "deep",
                false,
                null,
                null,
                new UserId("usr_1"),
                "127.0.0.1",
                "trace_direct_child"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void workspaceCreateCanCreateNewDirectChildDirectoryAfterClone() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/existing");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceCreateResponse response = service.createApplicationWorkspaceWithInitialVersion(
                "app_gcms",
                "repo_1",
                "feature_testagent_20260707",
                "F-GCMS/NewSpace",
                "新目录",
                true,
                null,
                null,
                new UserId("usr_1"),
                "127.0.0.1",
                "trace_new_dir");

        assertThat(response.directoryPath()).isEqualTo("F-GCMS/NewSpace");
        assertThat(Files.isDirectory(root.resolve("appworkspace/20260707/gcms/F-GCMS/NewSpace"))).isTrue();
    }

    @Test
    void workspaceCreateRejectsDuplicateWorkspaceAliasInSameApplication() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        ManagedWorkspaceApplicationService service = service(
                configuration,
                new FakeManagedWorkspaceRepository(),
                new FakeWorkspaceRepository(),
                new FakeGitWorkspaceService("F-GCMS/W1"));

        assertThatThrownBy(() -> service.createApplicationWorkspaceWithInitialVersion(
                "app_gcms",
                "repo_1",
                "feature_testagent_20260707",
                "F-GCMS/W1",
                "GCMS Workspace",
                false,
                null,
                null,
                new UserId("usr_1"),
                "127.0.0.1",
                "trace_duplicate_alias"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void internalRepositoryGitOperationsUseCurrentUsersEffectiveGitUrl() throws Exception {
        UserId userId = new UserId("usr_1");
        CodeRepository internalRepository = new CodeRepository(
                new CodeRepositoryId("repo_1"),
                "scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform",
                "接口平台",
                "hzefficiencytools-interfaceplatform",
                "APPLICATION_CODE_REPOSITORY",
                CodeRepositoryDeploymentMode.INTERNAL.value(),
                false,
                Instant.now(),
                Instant.now());
        UserSshKey sshKey = sshKeyFixtures.encryptedSshKey(
                new SshKeyId("ssh_1"), userId, "work", PRIVATE_KEY, Instant.now());
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true, internalRepository, List.of(sshKey));
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                "main",
                userId,
                "trace_internal_clone");
        git.originUrlValue = "ssh://009988776@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform";

        service.gitPullVersion(version.versionId(), userId, "127.0.0.1", "trace_internal_pull");

        String expectedGitUrl = "ssh://000857009@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform";
        assertThat(git.clonedGitUrl).isEqualTo(expectedGitUrl);
        assertThat(git.originUpdates)
                .extracting(OriginUpdate::gitUrl)
                .contains(expectedGitUrl);
        assertThat(git.pulledBranch).isEqualTo("main");
    }

    @Test
    void createsManagedWorkspaceRecordsWithLogicalPathsAndPhysicalResponsePaths() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        assertThat(version.repoRootPath().replace('\\', '/')).endsWith("/appworkspace/20260707/gcms");
        assertThat(version.workspaceRootPath().replace('\\', '/')).endsWith("/appworkspace/20260707/gcms/F-GCMS/workspace");
        assertThat(version.runtimeWorkspace().rootPath().replace('\\', '/')).endsWith("/appworkspace/20260707/gcms/F-GCMS/workspace");
        assertThat(managed.versions.get(0).repoRootPath()).isEqualTo("appworkspace:20260707/gcms");
        assertThat(managed.versions.get(0).workspaceRootPath()).isEqualTo("appworkspace:20260707/gcms/F-GCMS/workspace");
        assertThat(managed.replicas.get(0).repoRootPath()).isEqualTo("appworkspace:20260707/gcms");
        assertThat(managed.replicas.get(0).workspaceRootPath()).isEqualTo("appworkspace:20260707/gcms/F-GCMS/workspace");
        assertThat(workspaces.saved.get(0).rootPath()).isEqualTo("appworkspace:20260707/gcms/F-GCMS/workspace");
        assertThat(managed.personals.get(0).repoRootPath())
                .isEqualTo("personalworktree:20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default");
        assertThat(managed.personals.get(0).workspaceRootPath())
                .isEqualTo("personalworktree:20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace");
        assertThat(workspaces.findById(managed.personals.get(0).runtimeWorkspaceId())).get()
                .satisfies(workspace -> assertThat(workspace.rootPath())
                        .isEqualTo("personalworktree:20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace"));
        assertThat(personal.runtimeWorkspace().rootPath().replace('\\', '/'))
                .endsWith("/personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace");
    }

    @Test
    void createsVersionForTargetServerAndBroadcastsSyncRequest() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git, publisher);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse response = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "127.0.0.1",
                "trace_1234567890abcdef");

        assertThat(response.targetCommitHash()).isEqualTo("commit_base");
        assertThat(response.replicaCommitHash()).isEqualTo("commit_base");
        assertThat(response.replicaLinuxServerId()).isEqualTo("127.0.0.1");
        assertThat(response.replicaStatus()).isEqualTo(WorkspaceReplicaSyncStatus.READY.name());
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.get(0).type()).isEqualTo("workspace.version.sync-requested");
        assertThat(publisher.events.get(0).originInstanceId()).isEqualTo("instance-test");
        assertThat(publisher.events.get(0).originLinuxServerId()).isEqualTo("127.0.0.1");
        assertThat(publisher.events.get(0).payload()).containsEntry("versionId", response.versionId());
        assertThat(publisher.events.get(0).payload()).containsEntry("targetCommitHash", "commit_base");
    }

    @Test
    void recordsApplicationAgentPublishAndBroadcastsUpdatedFeatureHead() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git, publisher);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        publisher.events.clear();

        service.recordFeatureWorkspacePublished(
                version.runtimeWorkspace().workspaceId(),
                "commit_agent_config",
                new UserId("usr_1"),
                "trace_agent_publish");

        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_agent_config");
        assertThat(managed.replicas.get(0).currentCommitHash()).isEqualTo("commit_agent_config");
        assertThat(publisher.events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("workspace.version.sync-requested");
            assertThat(event.payload()).containsEntry("reason", "AGENT_CONFIG_PUBLISHED");
            assertThat(event.payload()).containsEntry("targetCommitHash", "commit_agent_config");
        });
    }

    @Test
    void createsApplicationVersionWithYearMonthFormat() throws Exception {
        // 「+新增版本」场景：version 字段允许原样保留为 yyyy年M月（"2024年1月"），
        // 但派生出来的分支名 / 路径要走 sanitizeVersionForBranchAndPath 转 yyyy-MM。
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = serviceWithBranches(
                configuration,
                managed,
                workspaces,
                git,
                List.of("feature_testagent_2024-01"));

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse response = service.createVersion(
                "app_gcms",
                "awp_1",
                "2024年1月",
                null,
                new UserId("usr_1"),
                "trace_year_month");

        assertThat(response.version()).isEqualTo("2024年1月");
        // 分支名从 "2024年1月" 转 "2024-01"，避免 git ref 出现中文 / 年月字面量
        assertThat(response.branch()).isEqualTo("feature_testagent_2024-01");
        assertThat(git.clonedBranch).isEqualTo("feature_testagent_2024-01");
        // 路径同样用 yyyy-MM；用 Path.endsWith 避免 Windows / Linux 路径分隔符差异
        assertThat(java.nio.file.Paths.get(response.runtimeWorkspace().rootPath()))
                .endsWith(java.nio.file.Paths.get("appworkspace", "2024-01", "gcms", "F-GCMS", "workspace"));
    }

    @Test
    void rejectsInvalidVersionFormat() {
        ManagedWorkspaceApplicationService service = service(
                new FakeConfigurationRepository(true),
                new FakeManagedWorkspaceRepository(),
                new FakeWorkspaceRepository(),
                new FakeGitWorkspaceService("F-GCMS/workspace"));

        assertThatThrownBy(() -> service.createVersion(
                "app_gcms",
                "awp_1",
                "v1.0",
                null,
                new UserId("usr_1"),
                "trace_invalid"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void rejectsApplicationWorkspaceOperationsForNonMembers() {
        ManagedWorkspaceApplicationService service = service(
                new FakeConfigurationRepository(false),
                new FakeManagedWorkspaceRepository(),
                new FakeWorkspaceRepository(),
                new FakeGitWorkspaceService("F-GCMS/workspace"));

        assertThatThrownBy(() -> service.listTemplates("app_gcms", new UserId("usr_1")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void recentWorkspaceForbiddenIncludesLoadingContext() {
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService creator = service(new FakeConfigurationRepository(true), managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = creator.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = creator.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");
        ManagedWorkspaceApplicationService reader = service(new FakeConfigurationRepository(false), managed, workspaces, git);

        assertThatThrownBy(() -> reader.recentWorkspace("app_gcms", new UserId("usr_1")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getMessage())
                            .contains("F-GCMS(app_gcms)")
                            .contains("20260707")
                            .contains("default 私人工作区:default");
                    assertThat(exception.details())
                            .containsEntry("loadingStage", "application-recent-workspace")
                            .containsEntry("appId", "app_gcms")
                            .containsEntry("appName", "F-GCMS")
                            .containsEntry("versionId", version.versionId())
                            .containsEntry("version", "20260707")
                            .containsEntry("applicationWorkspaceId", "awp_1")
                            .containsEntry("workspaceKind", "default 私人工作区")
                            .containsEntry("workspaceName", "default")
                            .containsEntry("workspaceId", personal.runtimeWorkspace().workspaceId())
                            .containsEntry("personalWorkspaceId", personal.personalWorkspaceId());
                });
    }

    @Test
    void globalRecentWorkspaceIsHiddenButRetainedAfterMemberRevocation() {
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService creator = service(
                new FakeConfigurationRepository(true), managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = creator.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        creator.ensureDefaultPersonalWorkspace(version.versionId(), new UserId("usr_1"), "trace_default");

        ManagedWorkspaceApplicationService revokedReader = service(
                new FakeConfigurationRepository(false), managed, workspaces, git);

        assertThat(revokedReader.recentWorkspace(new UserId("usr_1"))).isEmpty();
        assertThat(managed.personals).hasSize(1);
        assertThat(workspaces.saved).hasSize(2);
    }

    @Test
    void ensureDefaultPersonalWorkspaceForbiddenIncludesVersionAndDefaultContext() {
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService creator = service(new FakeConfigurationRepository(true), managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = creator.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceApplicationService reader = service(new FakeConfigurationRepository(false), managed, workspaces, git);

        assertThatThrownBy(() -> reader.ensureDefaultPersonalWorkspace(version.versionId(), new UserId("usr_1"), "trace_default"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getMessage())
                            .contains("F-GCMS(app_gcms)")
                            .contains("20260707")
                            .contains("default 私人工作区:default");
                    assertThat(exception.details())
                            .containsEntry("loadingStage", "ensure-default-personal-workspace")
                            .containsEntry("appId", "app_gcms")
                            .containsEntry("appName", "F-GCMS")
                            .containsEntry("versionId", version.versionId())
                            .containsEntry("version", "20260707")
                            .containsEntry("applicationWorkspaceId", "awp_1")
                            .containsEntry("workspaceKind", "default 私人工作区")
                            .containsEntry("workspaceName", "default");
                    assertThat(exception.details()).doesNotContainKeys("workspaceId", "personalWorkspaceId");
                });
    }

    @Test
    void createsPersonalWorkspaceFromApplicationVersionWorktree() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        ManagedWorkspaceResponses.PersonalWorkspaceResponse personal = service.createPersonalWorkspace(
                version.versionId(),
                "我的空间",
                new UserId("usr_1"),
                "trace_personal");

        assertThat(personal.workspaceName()).isEqualTo("我的空间");
        assertThat(personal.branch()).isEqualTo("feature_testagent_20260707_usr_1_____");
        assertThat(personal.runtimeWorkspace().rootPath()).contains("personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_____");
        assertThat(personal.runtimeWorkspace().rootPath()).endsWith("F-GCMS/workspace");
        assertThat(git.reusedWorktreeBranch).isEqualTo(personal.branch());
        assertThat(workspaces.saved).hasSize(2);
        assertThat(managed.personals).hasSize(1);
        assertThat(managed.globalPreference.workspaceId()).isEqualTo(managed.personals.get(0).runtimeWorkspaceId());
    }

    @Test
    void ensureDefaultPersonalWorkspaceUsesUserIdBranchAndReusesWorktreeConflict() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.failCreateWorktreeWithConflict = true;
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        assertThat(personal.personalWorkspaceName()).isEqualTo("default");
        assertThat(personal.personalWorkspaceBranch()).isEqualTo("feature_testagent_20260707_usr_1_default");
        assertThat(git.reusedWorktreeBranch).isEqualTo("feature_testagent_20260707_usr_1_default");
        assertThat(managed.personals).hasSize(1);
    }

    @Test
    void ensureDefaultPersonalWorkspaceEnsuresLocalReplicaInsteadOfUsingLegacyVersionPath() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        Instant now = Instant.now();
        WorkspaceId staleRuntimeId = new WorkspaceId("wrk_stale_legacy");
        workspaces.save(new Workspace(
                staleRuntimeId,
                "legacy",
                "/Users/rina/Desktop/mimo仓库/intelligent-test-agent/F-GCMS/workspace",
                WorkspaceStatus.ACTIVE,
                now,
                now,
                "legacy-server",
                "trace_legacy"));
        managed.versions.add(new ApplicationWorkspaceVersion(
                new ApplicationWorkspaceVersionId("awv_stale_legacy"),
                new ApplicationWorkspaceId("awp_1"),
                new ApplicationId("app_gcms"),
                new CodeRepositoryId("repo_1"),
                "20260707",
                "feature_testagent_20260707",
                "/Users/rina/Desktop/mimo仓库/intelligent-test-agent",
                "/Users/rina/Desktop/mimo仓库/intelligent-test-agent/F-GCMS/workspace",
                staleRuntimeId,
                new UserId("usr_1"),
                com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus.ACTIVE,
                "commit_base",
                now,
                now,
                now));

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                "awv_stale_legacy",
                new UserId("usr_1"),
                "trace_default");

        assertThat(git.createWorktreeBaseRepoRoot).isEqualTo(root.resolve("appworkspace/20260707/gcms").toAbsolutePath().normalize());
        assertThat(managed.replicas).singleElement().satisfies(replica -> {
            assertThat(replica.repoRootPath()).isEqualTo("appworkspace:20260707/gcms");
            assertThat(replica.workspaceRootPath()).isEqualTo("appworkspace:20260707/gcms/F-GCMS/workspace");
        });
        assertThat(personal.runtimeWorkspace().rootPath()).doesNotContain("/Users/rina/Desktop");
    }

    @Test
    void ensureDefaultPersonalWorkspaceRecreatesEmptyLeftoverDirectory() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        Files.createDirectories(root.resolve("personalworktree/20260707/usr_1/gcms/default"));

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        assertThat(personal.personalWorkspaceBranch()).isEqualTo("feature_testagent_20260707_usr_1_default");
        assertThat(git.reusedWorktreeBranch).isEqualTo("feature_testagent_20260707_usr_1_default");
        assertThat(Files.isDirectory(root.resolve("personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace"))).isTrue();
    }

    @Test
    void ensureDefaultPersonalWorkspaceUsesBranchAndUserIdInPhysicalDirectory() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        assertThat(personal.runtimeWorkspace().rootPath().replace('\\', '/'))
                .contains("/personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace");
    }

    @Test
    void ensureDefaultPersonalWorkspaceRecreatesMissingRecordedWorktree() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");
        Path repoRoot = root.resolve("personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default");
        deleteRecursively(repoRoot);
        git.reusedWorktreeBranch = null;

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse repaired = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_repair_missing");

        assertThat(repaired.personalWorkspaceId()).isEqualTo(personal.personalWorkspaceId());
        assertThat(git.reusedWorktreeBranch).isEqualTo("feature_testagent_20260707_usr_1_default");
        assertThat(Files.isDirectory(repoRoot.resolve("F-GCMS/workspace"))).isTrue();
        assertThat(repaired.runtimeWorkspace().rootPath().replace('\\', '/'))
                .contains("/personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace");
    }

    @Test
    void ensureDefaultPersonalWorkspaceReusesExistingValidWorktreeEvenWhenPathDiffersFromConfiguredRoot() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        PersonalWorkspace saved = managed.personals.get(0);
        Workspace runtime = workspaces.findById(saved.runtimeWorkspaceId()).orElseThrow();
        Path existingRepoRoot = root.resolve("existing-valid-worktree");
        Path existingWorkspaceRoot = existingRepoRoot.resolve("F-GCMS/workspace");
        Files.createDirectories(existingWorkspaceRoot);
        PersonalWorkspace relocated = new PersonalWorkspace(
                saved.personalWorkspaceId(),
                saved.versionId(),
                saved.appId(),
                saved.applicationWorkspaceId(),
                saved.userId(),
                saved.workspaceName(),
                saved.branch(),
                existingRepoRoot.toString(),
                existingWorkspaceRoot.toString(),
                saved.runtimeWorkspaceId(),
                saved.baseCommit(),
                saved.status(),
                saved.createdAt(),
                saved.updatedAt());
        managed.updatePersonalWorkspaceLocation(relocated);
        workspaces.save(new Workspace(
                runtime.workspaceId(),
                runtime.name(),
                existingWorkspaceRoot.toString(),
                runtime.status(),
                runtime.createdAt(),
                runtime.updatedAt(),
                runtime.linuxServerId(),
                runtime.traceId()));
        git.currentBranchValue = personal.personalWorkspaceBranch();
        git.reusedWorktreeBranch = null;

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse reused = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_reuse");

        assertThat(reused.personalWorkspaceId()).isEqualTo(personal.personalWorkspaceId());
        assertThat(reused.runtimeWorkspace().rootPath()).isEqualTo(existingWorkspaceRoot.toString());
        assertThat(git.reusedWorktreeBranch).isNull();
        assertThat(managed.personals.get(0).repoRootPath()).isEqualTo(existingRepoRoot.toString());
    }

    @Test
    void ensureDefaultPersonalWorkspaceRepairsLegacyDefaultRecordToNewBranchAndPath() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ConversationContextStore contextStore = org.mockito.Mockito.mock(ConversationContextStore.class);
        service.setConversationContextStore(contextStore);
        WorkspaceId runtimeId = new WorkspaceId("wrk_legacy_default");
        ConversationContextWorkspaceMutation mutation =
                new ConversationContextWorkspaceMutation(runtimeId, "mutation-personal-repair");
        org.mockito.Mockito.when(contextStore.beginWorkspaceMutation(runtimeId)).thenReturn(mutation);
        workspaces.save(new Workspace(
                runtimeId,
                "default",
                root.resolve("personalworktree/20260707/000857009/gcms/default/F-GCMS/workspace").toString(),
                com.enterprise.testagent.domain.workspace.WorkspaceStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                "127.0.0.1",
                "trace_legacy"));
        ApplicationWorkspaceVersion savedVersion = managed.versions.get(0);
        managed.personals.add(new PersonalWorkspace(
                new PersonalWorkspaceId("psw_legacy_default"),
                savedVersion.versionId(),
                savedVersion.appId(),
                savedVersion.applicationWorkspaceId(),
                new UserId("usr_1"),
                "default",
                "feature_testagent_20260707_000857009_psw_legacy_default",
                root.resolve("personalworktree/20260707/000857009/gcms/default").toString(),
                root.resolve("personalworktree/20260707/000857009/gcms/default/F-GCMS/workspace").toString(),
                runtimeId,
                "commit_legacy",
                com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus.ACTIVE,
                Instant.now(),
                Instant.now()));

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse repaired = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_repair");

        assertThat(repaired.personalWorkspaceId()).isEqualTo("psw_legacy_default");
        assertThat(repaired.personalWorkspaceBranch()).isEqualTo("feature_testagent_20260707_usr_1_default");
        assertThat(repaired.runtimeWorkspace().rootPath().replace('\\', '/'))
                .contains("/personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace");
        assertThat(managed.personals.get(0).branch()).isEqualTo("feature_testagent_20260707_usr_1_default");
        assertThat(workspaces.findById(runtimeId)).get()
                .satisfies(workspace -> assertThat(workspace.rootPath())
                        .isEqualTo("personalworktree:20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace"));
        org.mockito.Mockito.verify(contextStore).beginWorkspaceMutation(runtimeId);
        org.mockito.Mockito.verify(contextStore).completeWorkspaceMutation(mutation);
    }

    @Test
    void ensureDefaultPersonalWorkspaceRepairsMissingConfiguredDirectoryToRepoRoot() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ApplicationWorkspaceVersion savedVersion = managed.versions.get(0);
        String branch = "feature_testagent_20260707_usr_1_default";
        Path repoRoot = root.resolve("personalworktree/20260707/usr_1/gcms/" + branch);
        Path missingWorkspaceRoot = repoRoot.resolve("F-GCMS/workspace");
        Files.createDirectories(repoRoot);
        git.worktreeDirectoryPath = "other";
        WorkspaceId runtimeId = new WorkspaceId("wrk_missing_dir_default");
        workspaces.save(new Workspace(
                runtimeId,
                "default",
                missingWorkspaceRoot.toString(),
                com.enterprise.testagent.domain.workspace.WorkspaceStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                "127.0.0.1",
                "trace_missing_dir"));
        managed.personals.add(new PersonalWorkspace(
                new PersonalWorkspaceId("psw_missing_dir_default"),
                savedVersion.versionId(),
                savedVersion.appId(),
                savedVersion.applicationWorkspaceId(),
                new UserId("usr_1"),
                "default",
                branch,
                repoRoot.toString(),
                missingWorkspaceRoot.toString(),
                runtimeId,
                "commit_legacy",
                com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus.ACTIVE,
                Instant.now(),
                Instant.now()));

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse repaired = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_repair_missing_dir");

        String expectedRepoRoot = repoRoot.toAbsolutePath().normalize().toString();
        String expectedRepoValue = "personalworktree:20260707/usr_1/gcms/" + branch;
        assertThat(repaired.runtimeWorkspace().rootPath()).isEqualTo(expectedRepoRoot);
        assertThat(managed.personals.get(0).workspaceRootPath()).isEqualTo(expectedRepoValue);
        assertThat(workspaces.findById(runtimeId)).get()
                .satisfies(workspace -> assertThat(workspace.rootPath()).isEqualTo(expectedRepoValue));
    }

    @Test
    void workspaceGitDiffReturnsWorkspaceRelativeChinesePathAndPatch() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");
        git.nextStatusPorcelain = " M \"F-GCMS/workspace/需求/登录 Test.md\"\n";
        git.diffByFile.put(
                "F-GCMS/workspace/需求/登录 Test.md",
                "diff --git a/F-GCMS/workspace/需求/登录 Test.md b/F-GCMS/workspace/需求/登录 Test.md\n@@ -1 +1 @@\n-旧\n+新\n");

        ManagedWorkspaceResponses.WorkspaceGitDiffResponse diff = service.getWorkspaceGitDiff(
                personal.runtimeWorkspace().workspaceId(),
                new UserId("usr_1"));

        assertThat(diff.files()).hasSize(1);
        assertThat(diff.files().get(0).path()).isEqualTo("需求/登录 Test.md");
        assertThat(diff.files().get(0).patch()).contains("+新");
        assertThat(diff.files().get(0).additions()).isEqualTo(1);
        assertThat(diff.files().get(0).deletions()).isEqualTo(1);
        assertThat(git.lastDiffFile).isEqualTo("F-GCMS/workspace/需求/登录 Test.md");
    }

    @Test
    void workspaceGitDiffReturnsPseudoPatchForUntrackedOrAddedFiles() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        Path repoRoot = Path.of(personal.runtimeWorkspace().rootPath());
        Path fileFolder = repoRoot.resolve("需求");
        java.nio.file.Files.createDirectories(fileFolder);
        Path untrackedFile = fileFolder.resolve("untracked.txt");
        java.nio.file.Files.writeString(untrackedFile, "line1\nline2");

        git.nextStatusPorcelain = "?? \"F-GCMS/workspace/需求/untracked.txt\"\n";

        try {
            ManagedWorkspaceResponses.WorkspaceGitDiffResponse diff = service.getWorkspaceGitDiff(
                    personal.runtimeWorkspace().workspaceId(),
                    new UserId("usr_1"));

            assertThat(diff.files()).hasSize(1);
            assertThat(diff.files().get(0).path()).isEqualTo("需求/untracked.txt");
            assertThat(diff.files().get(0).status()).isEqualTo("untracked");
            assertThat(diff.files().get(0).patch()).contains("+line1");
            assertThat(diff.files().get(0).patch()).contains("+line2");
            assertThat(diff.files().get(0).additions()).isEqualTo(2);
        } finally {
            java.nio.file.Files.deleteIfExists(untrackedFile);
        }
    }

    @Test
    void workspaceGitDiffMergesStagedAndUnstagedPatchForSameFile() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");
        git.nextStatusPorcelain = "MM F-GCMS/workspace/src/App.java\n";
        git.diffByFileAndStage.put(
                "F-GCMS/workspace/src/App.java|true",
                "diff --git a/F-GCMS/workspace/src/App.java b/F-GCMS/workspace/src/App.java\n@@ -1 +1 @@\n-old\n+staged\n");
        git.diffByFileAndStage.put(
                "F-GCMS/workspace/src/App.java|false",
                "diff --git a/F-GCMS/workspace/src/App.java b/F-GCMS/workspace/src/App.java\n@@ -2 +2,2 @@\n-old2\n+unstaged\n+more\n");

        ManagedWorkspaceResponses.WorkspaceGitDiffResponse diff = service.getWorkspaceGitDiff(
                personal.runtimeWorkspace().workspaceId(),
                new UserId("usr_1"));

        assertThat(diff.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("src/App.java");
            assertThat(file.status()).isEqualTo("modified");
            assertThat(file.staged()).isTrue();
            assertThat(file.patch()).contains("+staged").contains("+unstaged").contains("+more");
            assertThat(file.additions()).isEqualTo(3);
            assertThat(file.deletions()).isEqualTo(2);
        });
    }

    @Test
    void applicationWorkspaceGitDiffUsesRepoRootAndWorkspaceRelativePath() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        git.nextStatusPorcelain = " M F-GCMS/workspace/docs/app.md\n";
        git.diffByFile.put(
                "F-GCMS/workspace/docs/app.md",
                "diff --git a/F-GCMS/workspace/docs/app.md b/F-GCMS/workspace/docs/app.md\n@@ -1 +1 @@\n-old\n+new\n");

        ManagedWorkspaceResponses.WorkspaceGitDiffResponse diff = service.getWorkspaceGitDiff(
                version.runtimeWorkspace().workspaceId(),
                new UserId("usr_1"));

        assertThat(git.statusRepoRoot).isEqualTo(applicationRepoRoot());
        assertThat(git.statusPathspec).isEqualTo("F-GCMS/workspace");
        assertThat(diff.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("docs/app.md");
            assertThat(file.patch()).contains("+new");
        });
    }

    @Test
    void discardWorkspaceGitFilesRestoresTrackedAndCleansNewFiles() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");
        git.nextStatusPorcelain = " M F-GCMS/workspace/需求/登录.md\nA  F-GCMS/workspace/需求/staged-new.md\n?? F-GCMS/workspace/需求/new.md\n";

        service.discardWorkspaceGitFiles(
                personal.runtimeWorkspace().workspaceId(),
                List.of("需求/登录.md", "需求/staged-new.md", "需求/new.md"),
                new UserId("usr_1"));

        assertThat(git.restoredFiles).containsExactly("F-GCMS/workspace/需求/登录.md");
        assertThat(git.unstagedFiles).containsExactly("F-GCMS/workspace/需求/staged-new.md");
        assertThat(git.cleanedFiles).containsExactly(
                "F-GCMS/workspace/需求/staged-new.md",
                "F-GCMS/workspace/需求/new.md");
    }

    @Test
    void applicationWorkspaceGitStageRejectsMutationOnFeatureReplica() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        git.nextStatusPorcelain = " M F-GCMS/workspace/docs/app.md\n";

        assertThatThrownBy(() -> service.stageWorkspaceGitFiles(
                version.runtimeWorkspace().workspaceId(),
                List.of("docs/app.md"),
                new UserId("usr_1")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void stagesAndUnstagesNonConflictFilesWhileRejectingConflictPaths() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_default");
        git.nextStatusPorcelain = """
                UU F-GCMS/workspace/src/Conflict.java
                ?? F-GCMS/workspace/src/Changed.java
                """;

        service.stageWorkspaceGitFiles(
                personal.runtimeWorkspace().workspaceId(),
                List.of("src/Changed.java"),
                new UserId("usr_1"));
        service.unstageWorkspaceGitFiles(
                personal.runtimeWorkspace().workspaceId(),
                List.of("src/Changed.java"),
                new UserId("usr_1"));

        assertThat(git.stagedFiles).containsExactly("F-GCMS/workspace/src/Changed.java");
        assertThat(git.unstagedFiles).containsExactly("F-GCMS/workspace/src/Changed.java");
        assertThatThrownBy(() -> service.stageWorkspaceGitFiles(
                personal.runtimeWorkspace().workspaceId(),
                List.of("src/Conflict.java"),
                new UserId("usr_1")))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("冲突文件");
    }

    @Test
    void syncsPersonalWorkspaceFilesToApplicationAndRecordsPush() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.commitStagedUpdatesHead = true;
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.PersonalWorkspaceResponse personal = service.createPersonalWorkspace(
                version.versionId(),
                "我的空间",
                new UserId("usr_1"),
                "trace_personal");
        Files.writeString(Path.of(personal.runtimeWorkspace().rootPath()).resolve("case.txt"), "from personal");
        git.calls.clear();

        ManagedWorkspaceResponses.WorkspaceSyncResponse result = service.syncPersonalToApplication(
                personal.personalWorkspaceId(),
                List.of("case.txt"),
                true,
                new UserId("usr_1"),
                "trace_sync");

        // 兼容同步接口现在复用 feature 发布链路，返回发布结果而非旧的复制状态。
        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(git.pushedBranch).isEqualTo("feature_testagent_20260707");
        assertThat(git.pushedForce).isFalse();
        Path applicationRepoRoot = applicationRepoRoot();
        assertThat(git.calls).containsSubsequence(
                "clean:" + applicationRepoRoot,
                "fetch:" + applicationRepoRoot,
                "pull:" + applicationRepoRoot + ":feature_testagent_20260707");
        assertThat(git.calls).contains(
                "push:" + applicationRepoRoot + ":feature_testagent_20260707:false",
                "head:" + applicationRepoRoot);
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_after_push");
        assertThat(managed.replicas.get(0).currentCommitHash()).isEqualTo("commit_after_push");
        assertThat(managed.syncRecords).hasSize(1);
        assertThat(managed.syncRecords.get(0).direction()).isEqualTo(WorkspaceSyncDirection.PERSONAL_TO_APPLICATION);
        assertThat(managed.syncRecords.get(0).traceId()).isEqualTo("trace_sync");
    }

    @Test
    void syncPersonalToApplicationCannotForcePublishSpecFiles() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.PersonalWorkspaceResponse personal = service.createPersonalWorkspace(
                version.versionId(), "我的空间", new UserId("usr_1"), "trace_personal");

        assertThatThrownBy(() -> service.syncPersonalToApplication(
                personal.personalWorkspaceId(),
                List.of("./spec/design.md"),
                true,
                new UserId("usr_1"),
                "trace_sync"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(git.pushedBranch).isNull();
        assertThat(managed.syncRecords).hasSize(1);
        assertThat(managed.syncRecords.get(0).status()).isEqualTo(WorkspaceSyncStatus.FAILED);
    }

    @Test
    void applicationAgentRolloutMergesFixedFeatureCommitIntoCleanPersonalWorktree() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        service.ensureDefaultPersonalWorkspace(version.versionId(), new UserId("usr_1"), "trace_personal");
        git.targetContainedInHead = false;
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        PublicAgentConfigRolloutSyncRequest request = applicationSyncRequest(version.versionId());
        when(coordinator.claimPendingSync("127.0.0.1", AgentConfigRolloutScope.APPLICATION))
                .thenReturn(Optional.of(request));
        when(coordinator.renewServerSync(request)).thenReturn(true);
        service.setAgentConfigRolloutCoordinator(coordinator);

        service.retryPendingApplicationConfigSync();

        assertThat(git.mergedCommit).isEqualTo("commit_application_agent");
        assertThat(git.mergedCommitRepoRoot).isEqualTo(
                personalRepoRoot("feature_testagent_20260707_usr_1_default"));
        assertThat(managed.syncRecords).singleElement().satisfies(record -> {
            assertThat(record.direction()).isEqualTo(WorkspaceSyncDirection.APPLICATION_TO_PERSONAL);
            assertThat(record.status()).isEqualTo(WorkspaceSyncStatus.SUCCEEDED);
        });
        verify(coordinator).markServerSyncedForUsers(request, Set.of("usr_1"), List.of());
    }

    @Test
    void applicationAgentRolloutCompletesServerAndPersistsDirtyWorktreeForUserResolution() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_personal");
        git.targetContainedInHead = false;
        git.dirtyRepoRoot = personalRepoRoot("feature_testagent_20260707_usr_1_default");
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        PublicAgentConfigRolloutSyncRequest request = applicationSyncRequest(version.versionId());
        when(coordinator.claimPendingSync("127.0.0.1", AgentConfigRolloutScope.APPLICATION))
                .thenReturn(Optional.of(request));
        when(coordinator.renewServerSync(request)).thenReturn(true);
        service.setAgentConfigRolloutCoordinator(coordinator);

        service.retryPendingApplicationConfigSync();

        assertThat(git.mergedCommit).isNull();
        assertThat(managed.syncRecords).isEmpty();
        verify(coordinator).markServerSyncedForUsers(
                request,
                Set.of(),
                List.of(new AgentConfigRolloutWorktreePending(
                        personal.personalWorkspaceId(), "usr_1", "LOCAL_CHANGES")));
        verify(coordinator, org.mockito.Mockito.never()).markServerSyncRetry(
                request, "PERSONAL_WORKTREE_UPDATE_PENDING");
    }

    @Test
    void pendingApplicationWorktreeIsMergedAndDisposedAfterUserMakesItClean() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_personal");
        git.targetContainedInHead = false;
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        AgentConfigRolloutWorktreeClaim claim = new AgentConfigRolloutWorktreeClaim(
                "acr_rollout",
                version.versionId(),
                personal.personalWorkspaceId(),
                "usr_1",
                "127.0.0.1",
                "commit_application_agent",
                "trace_rollout",
                2,
                Instant.now().plusSeconds(60),
                "acl_worktree");
        when(coordinator.claimPendingApplicationWorktree("127.0.0.1"))
                .thenReturn(Optional.of(claim));
        service.setAgentConfigRolloutCoordinator(coordinator);

        service.retryPendingApplicationConfigWorktrees();

        assertThat(git.mergedCommit).isEqualTo("commit_application_agent");
        verify(coordinator).markApplicationWorktreeSynchronized(claim);
        verify(coordinator, org.mockito.Mockito.never()).markApplicationWorktreeRetry(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void featureMergeConflictStaysInDiffUntilUserCompletesMergeCommit() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_personal");
        git.targetContainedInHead = false;
        git.failMergeWithConflict = true;
        git.nextConflictPaths = List.of("F-GCMS/workspace/docs/design.md");
        git.nextStatusPorcelain = "UU F-GCMS/workspace/docs/design.md\n";

        ManagedWorkspaceResponses.WorkspaceSyncResponse sync = service.syncApplicationToPersonal(
                personal.personalWorkspaceId(),
                List.of("docs/design.md"),
                new UserId("usr_1"),
                "trace_sync");
        ManagedWorkspaceResponses.WorkspaceGitDiffResponse diff = service.getWorkspaceGitDiff(
                personal.runtimeWorkspace().workspaceId(),
                new UserId("usr_1"));

        assertThat(sync.status()).isEqualTo(WorkspaceSyncStatus.FAILED.name());
        assertThat(diff.mergeInProgress()).isTrue();
        assertThat(diff.applicationUpdatePending()).isTrue();
        assertThat(diff.files()).singleElement().satisfies(file ->
                assertThat(file.status()).isEqualTo("conflict"));

        // 模拟三方编辑器已把全部冲突写回 index；完成入口提交完整 merge，而不是 reset index 后按文件提交。
        git.nextConflictPaths = List.of();
        git.nextStatusPorcelain = "M  F-GCMS/workspace/docs/design.md\n";
        git.commitStagedUpdatesHead = true;
        ManagedWorkspaceResponses.WorkspaceGitMergeCompletionResponse completion = service.completeWorkspaceGitMerge(
                personal.runtimeWorkspace().workspaceId(),
                new UserId("usr_1"),
                "trace_complete");

        assertThat(completion.status()).isEqualTo("MERGED");
        assertThat(git.mergeInProgress).isFalse();
        assertThat(git.targetContainedInHead).isTrue();
        assertThat(git.committedStagedMessage).startsWith("合并应用 feature 更新");
    }

    private PublicAgentConfigRolloutSyncRequest applicationSyncRequest(String versionId) {
        return new PublicAgentConfigRolloutSyncRequest(
                "acr_application",
                AgentConfigRolloutScope.APPLICATION,
                versionId,
                "feature_testagent_20260707",
                "commit_application_agent",
                "usr_1",
                "trace_application_agent",
                0,
                Instant.now().plusSeconds(180),
                "acl_application");
    }

    @Test
    void syncPersonalToApplicationRejectsDirtyApplicationReplicaBeforeCopyingFiles() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.PersonalWorkspaceResponse personal = service.createPersonalWorkspace(
                version.versionId(),
                "我的空间",
                new UserId("usr_1"),
                "trace_personal");
        Path personalFile = Path.of(personal.runtimeWorkspace().rootPath()).resolve("case.txt");
        Path applicationFile = Path.of(version.runtimeWorkspace().rootPath()).resolve("case.txt");
        Files.writeString(personalFile, "from personal");
        Files.writeString(applicationFile, "from application");
        git.worktreeClean = false;
        git.calls.clear();

        assertThatThrownBy(() -> service.syncPersonalToApplication(
                personal.personalWorkspaceId(),
                List.of("case.txt"),
                false,
                new UserId("usr_1"),
                "trace_sync"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(Files.readString(applicationFile)).isEqualTo("from application");
        assertThat(git.committedFiles).isEmpty();
        assertThat(git.pushedBranch).isNull();
        assertThat(managed.syncRecords).hasSize(1);
        assertThat(managed.syncRecords.get(0).status()).isEqualTo(WorkspaceSyncStatus.FAILED);
        Path applicationRepoRoot = applicationRepoRoot();
        assertThat(git.calls).containsExactly(
                "head:" + applicationRepoRoot,
                "head:" + applicationRepoRoot,
                "clean:" + applicationRepoRoot);
    }

    @Test
    void gitPullVersionUpdatesTargetCommitAndBroadcastsReplicaSync() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git, publisher);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "127.0.0.1",
                "trace_version");
        publisher.events.clear();
        git.nextHeadCommit = "commit_after_pull";

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse response = service.gitPullVersion(
                version.versionId(),
                new UserId("usr_1"),
                "127.0.0.1",
                "trace_pull");

        assertThat(git.pulledBranch).isEqualTo("feature_testagent_20260707");
        assertThat(response.targetCommitHash()).isEqualTo("commit_after_pull");
        assertThat(response.replicaCommitHash()).isEqualTo("commit_after_pull");
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.get(0).payload()).containsEntry("reason", "GIT_PULLED");
    }

    @Test
    void persistsRecentBranchPreferenceAndReadsItBack() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        ManagedWorkspaceResponses.BranchPreferenceResponse recorded = service.markRecentBranch(
                "app_gcms",
                version.runtimeWorkspace().workspaceId(),
                "feature/personalized",
                new UserId("usr_1"));
        assertThat(recorded.branch()).isEqualTo("feature/personalized");
        assertThat(recorded.workspaceId()).isEqualTo(version.runtimeWorkspace().workspaceId());
        assertThat(recorded.appId()).isEqualTo("app_gcms");

        Optional<ManagedWorkspaceResponses.BranchPreferenceResponse> recent = service.recentBranch(
                "app_gcms",
                version.runtimeWorkspace().workspaceId(),
                new UserId("usr_1"));
        assertThat(recent).isPresent();
        assertThat(recent.get().branch()).isEqualTo("feature/personalized");
    }

    @Test
    void rejectsMarkRecentBranchWithBlankBranchName() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        // 空分支名被入口校验直接拒绝
        assertThatThrownBy(() -> service.markRecentBranch(
                "app_gcms",
                version.runtimeWorkspace().workspaceId(),
                "",
                new UserId("usr_1")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void publishPersonalWorkspaceMergesPersonalBranchIntoApplicationBranch() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.nextStatusPorcelain = "M F-GCMS/workspace/README.md\n";
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        // createVersion 后版本 commit 为 commit_base
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_base");

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        // 合并成功后应用版本副本 HEAD 变为 commit_merged
        git.nextHeadCommit = "commit_merged";
        git.targetContainedInHead = false;

        ManagedWorkspaceResponses.PersonalWorkspacePublishResponse result = service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "fix: 修复缺陷",
                List.of("README.md"),
                new UserId("usr_1"),
                "trace_publish");

        Path applicationRepoRoot = applicationRepoRoot();

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.remotePushed()).isTrue();
        assertThat(result.headCommit()).isEqualTo("commit_merged");
        assertThat(result.versionId()).isEqualTo(version.versionId());
        // 发布不合并个人分支，而是把个人 HEAD 的白名单文件投影到 feature worktree。
        assertThat(git.mergeCalls).isEmpty();
        assertThat(git.materializedRepoRoot).isEqualTo(applicationRepoRoot);
        assertThat(git.materializedCommit).isEqualTo("commit_merged");
        assertThat(git.materializedFiles).containsExactly("F-GCMS/workspace/README.md");
        // 不再推送个人 worktree 分支，只推送应用版本特性分支；应用分支推送发生在应用版本副本仓库
        assertThat(git.pushes)
                .extracting(push -> push.branch)
                .containsExactly(version.branch());
        assertThat(git.pushes.get(0).repoRoot).isEqualTo(applicationRepoRoot);
        assertThat(git.pushedBranch).isEqualTo(version.branch());
        assertThat(git.pushedRepoRoot).isEqualTo(applicationRepoRoot);
        // feature 推送后立即把固定 commit 反向合并到本机个人 worktree。
        assertThat(git.mergedCommit).isEqualTo("commit_merged");
        assertThat(git.mergedCommitRepoRoot).isEqualTo(
                personalRepoRoot("feature_testagent_20260707_usr_1_default"));
        // 发布不改个人索引；目标 feature worktree 承担投影后的提交。
        assertThat(git.stagedRepoRoot).isNull();
        assertThat(git.stagedFilesRepoRoot).isNull();
        assertThat(git.resetIndexRepoRoot).isNull();
        assertThat(git.committedStagedRepoRoot).isEqualTo(applicationRepoRoot);
        assertThat(git.committedStagedMessage).isEqualTo("fix: 修复缺陷");
        // 版本 targetCommitHash 和副本 commit 已更新到合并后的 commit
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_merged");
        assertThat(managed.replicas.get(0).currentCommitHash()).isEqualTo("commit_merged");
    }

    @Test
    void publishPersonalWorkspaceRejectsSpecFilesBeforePreparingApplicationBranch() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_default");
        git.calls.clear();

        assertThatThrownBy(() -> service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "test: 不得发布 spec",
                List.of("docs/allowed.md", ".//spec/design.md"),
                new UserId("usr_1"),
                "trace_publish"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.details()).containsEntry("files", List.of(".//spec/design.md"));
                });

        assertThat(git.materializedFiles).isEmpty();
        assertThat(git.pushedBranch).isNull();
        assertThat(git.calls).isEmpty();
    }

    @Test
    void publishPersonalWorkspaceRepairsStaleApplicationReplicaPath() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.nextStatusPorcelain = "M F-GCMS/workspace/README.md\n";
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        ApplicationWorkspaceVersionReplica current = managed.replicas.get(0);
        Instant now = Instant.now();
        managed.replicas.clear();
        managed.replicas.add(new ApplicationWorkspaceVersionReplica(
                current.replicaId(),
                current.versionId(),
                current.linuxServerId(),
                "D:\\data\\.testagent\\agent-opencode\\workspace\\appworkspace\\20260707\\gcms",
                "D:\\data\\.testagent\\agent-opencode\\workspace\\appworkspace\\20260707\\gcms\\F-GCMS\\workspace",
                current.runtimeWorkspaceId(),
                current.currentCommitHash(),
                current.syncStatus(),
                current.lastError(),
                current.lastSyncedAt(),
                "trace_stale",
                current.createdAt(),
                now));
        workspaces.save(new Workspace(
                current.runtimeWorkspaceId(),
                "GCMS Workspace-20260707",
                "D:\\data\\.testagent\\agent-opencode\\workspace\\appworkspace\\20260707\\gcms\\F-GCMS\\workspace",
                WorkspaceStatus.ACTIVE,
                now,
                now,
                "127.0.0.1",
                "trace_stale"));
        ConversationContextStore contextStore = org.mockito.Mockito.mock(ConversationContextStore.class);
        ConversationContextWorkspaceMutation mutation = new ConversationContextWorkspaceMutation(
                current.runtimeWorkspaceId(),
                "mutation-replica-repair");
        org.mockito.Mockito.when(contextStore.beginWorkspaceMutation(current.runtimeWorkspaceId()))
                .thenReturn(mutation);
        service.setConversationContextStore(contextStore);

        git.nextHeadCommit = "commit_merged_repaired";

        ManagedWorkspaceResponses.PersonalWorkspacePublishResponse result = service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "fix: 修复旧路径",
                List.of("README.md"),
                new UserId("usr_1"),
                "trace_publish");

        Path applicationRepoRoot = applicationRepoRoot();
        Workspace runtimeWorkspace = workspaces.findById(current.runtimeWorkspaceId()).orElseThrow();

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(managed.replicas.get(0).repoRootPath()).isEqualTo("appworkspace:20260707/gcms");
        assertThat(applicationRepoRoot).isEqualTo(root.resolve("appworkspace/20260707/gcms").toAbsolutePath().normalize());
        assertThat(runtimeWorkspace.rootPath()).doesNotContain("D:\\data");
        org.mockito.Mockito.verify(contextStore).beginWorkspaceMutation(current.runtimeWorkspaceId());
        org.mockito.Mockito.verify(contextStore).completeWorkspaceMutation(mutation);
        assertThat(git.materializedRepoRoot).isEqualTo(applicationRepoRoot);
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_merged_repaired");
    }

    @Test
    void publishPersonalWorkspaceRetriesAfterCommitWithoutNewChanges() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.nextStatusPorcelain = "";
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        git.nextHeadCommit = "commit_merged_retry";

        ManagedWorkspaceResponses.PersonalWorkspacePublishResponse result = service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "fix: retry publish",
                List.of("README.md"),
                new UserId("usr_1"),
                "trace_publish");

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(git.committedStagedRepoRoot).isNull();
        assertThat(git.pushes)
                .extracting(push -> push.branch)
                .containsExactly(version.branch());
        assertThat(git.mergeCalls).isEmpty();
        assertThat(git.materializedRepoRoot).isEqualTo(applicationRepoRoot());
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_merged_retry");
    }

    @Test
    void publishPersonalWorkspaceContinuesResolvedNativeMergeWithoutPullingRemoteAgain() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.nextStatusPorcelain = "M F-GCMS/workspace/README.md\n";
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");
        git.calls.clear();
        git.mergeInProgress = true;
        git.nextConflictPaths = List.of();
        git.nextHeadCommit = "commit_merged_after_resolve";

        assertThatThrownBy(() -> service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "fix: finish native merge",
                List.of("README.md"),
                "commit_base",
                new UserId("usr_1"),
                "trace_publish"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(git.pushes).isEmpty();
    }

    @Test
    void publishPersonalWorkspaceReturnsConflictWhenMergeFails() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.nextStatusPorcelain = "M F-GCMS/workspace/README.md\n";
        git.failMergeWithConflict = true;
        git.nextConflictPaths = List.of("src/Main.java", "README.md");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");
        git.nextHeadCommit = "commit_remote";

        ManagedWorkspaceResponses.PersonalWorkspacePublishResponse result = service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "fix: 修复缺陷",
                List.of("README.md"),
                new UserId("usr_1"),
                "trace_publish");

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.remotePushed()).isTrue();
        assertThat(result.conflictFiles()).isEmpty();
        assertThat(git.mergeCalls).isEmpty();
        // 旧的 merge 冲突开关不再影响投影发布。
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_remote");
        assertThat(managed.replicas.get(0).currentCommitHash()).isEqualTo("commit_remote");
    }

    @Test
    void previewsIncomingApplicationChangesWithoutTouchingPersonalIndex() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_default");
        git.nextHeadCommit = "commit_remote";
        git.nextCommitCount = 3;
        git.nextNameStatus = "A\t新增.md\nM\t修改.md\nD\t删除.md\nR100\t旧名.md\t新名.md\n";

        ManagedWorkspaceResponses.PersonalWorkspacePublishPreviewResponse preview =
                service.previewPersonalWorkspacePublish(
                        personal.personalWorkspaceId(), new UserId("usr_1"), "trace_preview");

        assertThat(preview.applicationHead()).isEqualTo("commit_remote");
        assertThat(preview.incomingCommitCount()).isEqualTo(3);
        assertThat(preview.changedFileCount()).isEqualTo(4);
        assertThat(preview.addedCount()).isEqualTo(1);
        assertThat(preview.modifiedCount()).isEqualTo(1);
        assertThat(preview.deletedCount()).isEqualTo(1);
        assertThat(preview.renamedCount()).isEqualTo(1);
        assertThat(preview.samplePaths()).containsExactly("新增.md", "修改.md", "删除.md", "新名.md");
        assertThat(git.resetIndexRepoRoot).isNull();
        assertThat(git.committedStagedRepoRoot).isNull();
    }

    @Test
    void rejectsChangedApplicationHeadBeforeCreatingPersonalCommit() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_default");
        git.nextHeadCommit = "commit_changed_after_preview";

        assertThatThrownBy(() -> service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "fix: should not commit",
                List.of("README.md"),
                "commit_seen_in_preview",
                new UserId("usr_1"),
                "trace_publish"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(git.resetIndexRepoRoot).isNull();
        assertThat(git.stagedFilesRepoRoot).isNull();
        assertThat(git.committedStagedRepoRoot).isNull();
        assertThat(git.pushes).isEmpty();
    }

    @Test
    void readsAndResolvesPersonalWorkspaceConflict() throws Exception {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);
        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms", "awp_1", "20260707", null, new UserId("usr_1"), "trace_version");
        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(), new UserId("usr_1"), "trace_default");
        git.nextStatusPorcelain = "UU F-GCMS/workspace/src/Login.java\n";
        git.conflictStageContents.put(1, "base");
        git.conflictStageContents.put(2, "current");
        git.conflictStageContents.put(3, "incoming");
        git.nextConflictPaths = List.of("F-GCMS/workspace/src/Login.java");

        ManagedWorkspaceResponses.WorkspaceGitConflictResponse conflict = service.getWorkspaceGitConflict(
                personal.runtimeWorkspace().workspaceId(),
                "src/Login.java",
                new UserId("usr_1"));

        assertThat(conflict.path()).isEqualTo("src/Login.java");
        assertThat(conflict.baseContent()).isEqualTo("base");
        assertThat(conflict.currentContent()).isEqualTo("current");
        assertThat(conflict.incomingContent()).isEqualTo("incoming");

        service.resolveWorkspaceGitConflict(
                personal.runtimeWorkspace().workspaceId(),
                "src/Login.java",
                "MANUAL",
                "resolved",
                new UserId("usr_1"));

        assertThat(Files.readString(personalRepoRoot(personal.personalWorkspaceBranch())
                .resolve("F-GCMS/workspace/src/Login.java"))).isEqualTo("resolved");
        assertThat(git.stagedFiles).containsExactly("F-GCMS/workspace/src/Login.java");
    }

    @Test
    void publishPersonalWorkspacePropagatesMergeFailureWhenNoConflictFiles() {
        FakeConfigurationRepository configuration = new FakeConfigurationRepository(true);
        FakeManagedWorkspaceRepository managed = new FakeManagedWorkspaceRepository();
        FakeWorkspaceRepository workspaces = new FakeWorkspaceRepository();
        FakeGitWorkspaceService git = new FakeGitWorkspaceService("F-GCMS/workspace");
        git.nextStatusPorcelain = "M F-GCMS/workspace/README.md\n";
        git.failMergeWithConflict = true;
        git.nextConflictPaths = List.of();
        ManagedWorkspaceApplicationService service = service(configuration, managed, workspaces, git);

        ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse version = service.createVersion(
                "app_gcms",
                "awp_1",
                "20260707",
                null,
                new UserId("usr_1"),
                "trace_version");

        ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse personal = service.ensureDefaultPersonalWorkspace(
                version.versionId(),
                new UserId("usr_1"),
                "trace_default");

        ManagedWorkspaceResponses.PersonalWorkspacePublishResponse result = service.publishPersonalWorkspace(
                personal.personalWorkspaceId(),
                "fix: 修复缺陷",
                List.of("README.md"),
                new UserId("usr_1"),
                "trace_publish");
        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(git.mergeCalls).isEmpty();
        assertThat(git.pushes).hasSize(1);
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path current : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }

    private ManagedWorkspaceApplicationService service(
            FakeConfigurationRepository configuration,
            FakeManagedWorkspaceRepository managed,
            FakeWorkspaceRepository workspaces,
            FakeGitWorkspaceService git) {
        return service(configuration, managed, workspaces, git, new RecordingBroadcastPublisher());
    }

    private ManagedWorkspaceApplicationService service(
            FakeConfigurationRepository configuration,
            FakeManagedWorkspaceRepository managed,
            FakeWorkspaceRepository workspaces,
            FakeGitWorkspaceService git,
            ServerBroadcastPublisher publisher) {
        CommonParameterValues commonParameters = commonParameters();
        return new ManagedWorkspaceApplicationService(
                configuration,
                commonParameters,
                managed,
                workspaces,
                new FakeUserRepository(),
                new FakeGitRemoteService(List.of("feature_testagent_20260707")),
                git,
                sshKeyFixtures.encryptionService(),
                new WorkspaceServerIdentity("127.0.0.1"),
                publisher);
    }

    private ManagedWorkspaceApplicationService serviceWithBranches(
            FakeConfigurationRepository configuration,
            FakeManagedWorkspaceRepository managed,
            FakeWorkspaceRepository workspaces,
            FakeGitWorkspaceService git,
            List<String> branches) {
        CommonParameterValues commonParameters = commonParameters();
        return new ManagedWorkspaceApplicationService(
                configuration,
                commonParameters,
                managed,
                workspaces,
                new FakeUserRepository(),
                new FakeGitRemoteService(branches),
                git,
                sshKeyFixtures.encryptionService(),
                new WorkspaceServerIdentity("127.0.0.1"),
                new RecordingBroadcastPublisher());
    }

    private ManagedWorkspaceApplicationService serviceWithGitRemote(
            FakeConfigurationRepository configuration,
            FakeManagedWorkspaceRepository managed,
            FakeWorkspaceRepository workspaces,
            FakeGitWorkspaceService git,
            GitRemoteService gitRemoteService) {
        return new ManagedWorkspaceApplicationService(
                configuration,
                commonParameters(),
                managed,
                workspaces,
                new FakeUserRepository(),
                gitRemoteService,
                git,
                sshKeyFixtures.encryptionService(),
                new WorkspaceServerIdentity("127.0.0.1"),
                new RecordingBroadcastPublisher());
    }

    private Path applicationRepoRoot() {
        return root.resolve("appworkspace/20260707/gcms").toAbsolutePath().normalize();
    }

    private Path personalRepoRoot(String branch) {
        return root.resolve("personalworktree/20260707/usr_1/gcms")
                .resolve(branch)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 内存通用参数仓库：把工作区根目录参数指向 @TempDir 下的子目录，common_parameters 为唯一来源。
     */
    /**
     * 内存通用参数值视图：把工作区根目录参数指向 @TempDir 下的子目录，common_parameters 为唯一来源。
     */
    private CommonParameterValues commonParameters() {
        Map<String, String> parameters = Map.of(
                "OPENCODE_APP_WORKSPACE_ROOT", root.resolve("appworkspace").toString(),
                "OPENCODE_PERSONAL_WORKTREE_ROOT", root.resolve("personalworktree").toString());
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                return Optional.ofNullable(parameters.get(englishName));
            }

            @Override
            public Optional<String> resolvedValue(String englishName, com.enterprise.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.ofNullable(parameters.get(englishName));
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, com.enterprise.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<com.enterprise.testagent.domain.configuration.ResolvedParameter> resolvedAll() {
                return List.of();
            }
        };
    }

    private static final class RecordingBroadcastPublisher implements ServerBroadcastPublisher {
        private final List<ServerBroadcastEvent> events = new ArrayList<>();

        @Override
        public String instanceId() {
            return "instance-test";
        }

        @Override
        public void publish(ServerBroadcastEvent event) {
            events.add(event);
        }
    }

    private static final class FakeGitRemoteService extends GitRemoteService {
        private final List<String> branches;

        private FakeGitRemoteService(List<String> branches) {
            this.branches = branches;
        }

        @Override
        public List<String> listBranches(String gitUrl, String privateKey) {
            return branches;
        }
    }

    private static final class FakeGitWorkspaceService extends GitWorkspaceService {
        private final String directoryPath;
        private String worktreeDirectoryPath;
        private String clonedBranch;
        private String worktreeBranch;
        private List<String> committedFiles = List.of();
        private String pushedBranch;
        private boolean pushedForce;
        private String pulledBranch;
        private String nextHeadCommit = "commit_base";
        private int nextCommitCount;
        private String nextNameStatus = "";
        private boolean worktreeClean = true;
        private Path dirtyRepoRoot;
        private final List<String> calls = new ArrayList<>();
        private boolean failCreateWorktreeWithConflict;
        private String reusedWorktreeBranch;
        private Path createWorktreeBaseRepoRoot;
        private String nextStatusPorcelain = "";
        private final Map<String, String> diffByFile = new java.util.HashMap<>();
        private final Map<String, String> diffByFileAndStage = new java.util.HashMap<>();
        private String lastDiffFile;
        private List<String> restoredFiles = List.of();
        private List<String> unstagedFiles = List.of();
        private List<String> cleanedFiles = List.of();
        private String currentBranchValue;
        private boolean strictGitRepositoryDetection;
        private final Set<Path> gitRoots = new java.util.LinkedHashSet<>();
        // 个人 worktree 推送（合并回应用版本特性分支）链路记录
        private Path stagedRepoRoot;
        private Path stagedFilesRepoRoot;
        private List<String> stagedFiles = List.of();
        private Path statusRepoRoot;
        private String statusPathspec;
        private Path committedStagedRepoRoot;
        private String committedStagedMessage;
        private GitCommitIdentity committedStagedIdentity;
        private boolean commitStagedUpdatesHead;
        private Path materializedRepoRoot;
        private String materializedCommit;
        private List<String> materializedFiles = List.of();
        private Path fetchedRepoRoot;
        private String mergedBranch;
        private Path mergedBranchRepoRoot;
        private final List<MergeCall> mergeCalls = new ArrayList<>();
        private Path mergedCommitRepoRoot;
        private String mergedCommit;
        private boolean targetContainedInHead = true;
        private boolean failMergeWithConflict;
        private List<String> nextConflictPaths = List.of();
        private Path abortedMergeRepoRoot;
        private Path pushedRepoRoot;
        private Path headCommitRepoRoot;
        private Path resetIndexRepoRoot;
        private boolean mergeInProgress;
        private final Map<Integer, String> conflictStageContents = new java.util.HashMap<>();
        private final List<PushCall> pushes = new ArrayList<>();
        private String clonedGitUrl;
        private String originUrlValue = "https://example.com/gcms.git";
        private final List<OriginUpdate> originUpdates = new ArrayList<>();
        private final Set<Path> invalidHeadCommitRoots = new java.util.LinkedHashSet<>();

        private FakeGitWorkspaceService(String directoryPath) {
            this.directoryPath = directoryPath;
            this.worktreeDirectoryPath = directoryPath;
        }

        @Override
        public void cloneBranch(String gitUrl, String branch, Path repoRoot, String privateKey) {
            this.clonedGitUrl = gitUrl;
            this.clonedBranch = branch;
            try {
                Files.createDirectories(repoRoot.resolve(".git"));
                Files.createDirectories(repoRoot.resolve(directoryPath));
                Path normalized = repoRoot.toAbsolutePath().normalize();
                gitRoots.add(normalized);
                invalidHeadCommitRoots.remove(normalized);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public void createWorktree(Path repoRoot, Path worktreeRoot, String branch, String privateKey) {
            this.createWorktreeBaseRepoRoot = repoRoot;
            this.worktreeBranch = branch;
            if (failCreateWorktreeWithConflict) {
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        "Git worktree 创建冲突",
                        Map.of("gitFailureType", "WORKTREE_CONFLICT"));
            }
            createWorktreeDirectory(worktreeRoot);
        }

        @Override
        public void createWorktreeReusingBranch(Path repoRoot, Path worktreeRoot, String branch, String privateKey) {
            this.createWorktreeBaseRepoRoot = repoRoot;
            this.reusedWorktreeBranch = branch;
            createWorktreeDirectory(worktreeRoot);
        }

        @Override
        public boolean isGitRepository(Path repoRoot) {
            if (strictGitRepositoryDetection) {
                return gitRoots.contains(repoRoot.toAbsolutePath().normalize());
            }
            return Files.isDirectory(repoRoot);
        }

        @Override
        public String currentBranch(Path repoRoot) {
            if (strictGitRepositoryDetection && !isGitRepository(repoRoot)) {
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "not a git repository", Map.of("path", repoRoot.toString()));
            }
            if (currentBranchValue != null) {
                return currentBranchValue;
            }
            if (repoRoot.toString().contains("appworkspace")) {
                return clonedBranch;
            }
            return worktreeBranch;
        }

        @Override
        public String originUrl(Path repoRoot) {
            if (strictGitRepositoryDetection && !isGitRepository(repoRoot)) {
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "not a git repository", Map.of("path", repoRoot.toString()));
            }
            return originUrlValue;
        }

        @Override
        public void setOriginUrl(Path repoRoot, String gitUrl, String privateKey) {
            this.originUrlValue = gitUrl;
            this.originUpdates.add(new OriginUpdate(repoRoot, gitUrl));
        }

        @Override
        public void restoreFiles(Path repoRoot, List<String> files, String privateKey) {
            this.restoredFiles = List.copyOf(files);
        }

        @Override
        public void unstageFiles(Path repoRoot, List<String> files, String privateKey) {
            this.unstagedFiles = List.copyOf(files);
        }

        @Override
        public void cleanUntrackedFiles(Path repoRoot, List<String> files, String privateKey) {
            this.cleanedFiles = List.copyOf(files);
        }

        private void createWorktreeDirectory(Path worktreeRoot) {
            try {
                Files.createDirectories(worktreeRoot.resolve(worktreeDirectoryPath));
                gitRoots.add(worktreeRoot.toAbsolutePath().normalize());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public String headCommit(Path repoRoot) {
            calls.add("head:" + repoRoot);
            this.headCommitRepoRoot = repoRoot;
            if (invalidHeadCommitRoots.contains(repoRoot.toAbsolutePath().normalize())) {
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "HEAD 不存在", Map.of("path", repoRoot.toString()));
            }
            return nextHeadCommit;
        }

        @Override
        public int countCommits(Path repoRoot, String from, String to) {
            return nextCommitCount;
        }

        @Override
        public String diffNameStatus(Path repoRoot, String from, String to) {
            return nextNameStatus;
        }

        @Override
        public void stageAll(Path repoRoot, String privateKey) {
            this.stagedRepoRoot = repoRoot;
        }

        @Override
        public void stageFiles(Path repoRoot, List<String> files, String privateKey) {
            this.stagedFilesRepoRoot = repoRoot;
            this.stagedFiles = List.copyOf(files);
        }

        @Override
        public void materializeCommitFiles(Path targetRepoRoot, String sourceCommit, List<String> files, String privateKey) {
            this.materializedRepoRoot = targetRepoRoot;
            this.materializedCommit = sourceCommit;
            this.materializedFiles = List.copyOf(files);
        }

        @Override
        public void resetIndexToHead(Path repoRoot, String privateKey) {
            this.resetIndexRepoRoot = repoRoot;
        }

        @Override
        public boolean isMergeInProgress(Path repoRoot) {
            return mergeInProgress;
        }

        @Override
        public Set<Integer> conflictStages(Path repoRoot, String file) {
            return conflictStageContents.keySet();
        }

        @Override
        public String conflictStageContent(Path repoRoot, int stage, String file) {
            return conflictStageContents.get(stage);
        }

        @Override
        public void commitStaged(Path repoRoot, String message, String privateKey, GitCommitIdentity identity) {
            this.committedStagedIdentity = identity;
            if (nextStatusPorcelain.isBlank() && !commitStagedUpdatesHead) {
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "nothing to commit", Map.of());
            }
            this.committedStagedRepoRoot = repoRoot;
            this.committedStagedMessage = message;
            if (commitStagedUpdatesHead) {
                this.nextHeadCommit = mergeInProgress && mergedCommit != null
                        ? mergedCommit
                        : "commit_after_push";
                if (mergeInProgress) {
                    this.mergeInProgress = false;
                    this.targetContainedInHead = true;
                }
            }
        }

        @Override
        public void fetch(Path repoRoot, String privateKey) {
            calls.add("fetch:" + repoRoot);
            this.fetchedRepoRoot = repoRoot;
        }

        @Override
        public void checkoutTrackingBranch(Path repoRoot, String branch, String privateKey) {
            this.currentBranchValue = branch;
        }

        @Override
        public boolean isAncestor(Path repoRoot, String ancestor, String descendant) {
            return targetContainedInHead;
        }

        @Override
        public void mergeCommit(
                Path repoRoot,
                String targetCommit,
                String privateKey,
                GitCommitIdentity identity) {
            this.mergedCommitRepoRoot = repoRoot;
            this.mergedCommit = targetCommit;
            if (failMergeWithConflict) {
                this.mergeInProgress = true;
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "合并冲突", Map.of());
            }
            this.targetContainedInHead = true;
            this.nextHeadCommit = targetCommit;
        }

        @Override
        public List<String> conflictPaths(Path repoRoot) {
            calls.add("conflicts:" + repoRoot);
            return nextConflictPaths;
        }

        @Override
        public void abortMerge(Path repoRoot, String privateKey) {
            calls.add("abort:" + repoRoot);
            this.abortedMergeRepoRoot = repoRoot;
        }

        @Override
        public void push(Path repoRoot, String branch, boolean force, String privateKey) {
            this.pushes.add(new PushCall(repoRoot, branch, force));
            calls.add("push:" + repoRoot + ":" + branch + ":" + force);
            this.pushedBranch = branch;
            this.pushedForce = force;
            this.pushedRepoRoot = repoRoot;
        }

        @Override
        public boolean isWorktreeClean(Path repoRoot) {
            calls.add("clean:" + repoRoot);
            return worktreeClean && (dirtyRepoRoot == null || !dirtyRepoRoot.equals(repoRoot));
        }

        @Override
        public void pullFastForward(Path repoRoot, String branch, String privateKey) {
            calls.add("pull:" + repoRoot + ":" + branch);
            this.pulledBranch = branch;
        }

        @Override
        public void resetHardToCommit(Path repoRoot, String commitHash) {
        }

        @Override
        public String statusPorcelain(Path repoRoot) {
            this.statusRepoRoot = repoRoot;
            this.statusPathspec = null;
            return nextStatusPorcelain;
        }

        @Override
        public String statusPorcelain(Path repoRoot, String pathspec) {
            this.statusRepoRoot = repoRoot;
            this.statusPathspec = pathspec;
            return nextStatusPorcelain;
        }

        @Override
        public String diff(Path repoRoot, String file, boolean staged) {
            this.lastDiffFile = file;
            return diffByFileAndStage.getOrDefault(file + "|" + staged, diffByFile.getOrDefault(file, ""));
        }
    }

    private record PushCall(Path repoRoot, String branch, boolean force) {
    }

    private record MergeCall(Path repoRoot, String branch) {
    }

    private record OriginUpdate(Path repoRoot, String gitUrl) {
    }

    private static final class FakeConfigurationRepository implements ConfigurationManagementRepository {
        private final boolean member;
        private final ApplicationDefinition app = new ApplicationDefinition(
                new ApplicationId("app_gcms"), "F-GCMS", true, Instant.parse("2026-06-23T00:00:00Z"), Instant.parse("2026-06-23T00:00:00Z"));
        private final CodeRepository repository;
        private final List<UserSshKey> sshKeys;
        private final ApplicationWorkspace workspace;
        private final List<ApplicationWorkspace> savedWorkspaces = new ArrayList<>();

        private FakeConfigurationRepository(boolean member) {
            this(member, new CodeRepository(
                    new CodeRepositoryId("repo_1"), "https://example.com/gcms.git", "gcms/gcms", "gcms", true, Instant.now(), Instant.now()), List.of());
        }

        private FakeConfigurationRepository(boolean member, CodeRepository repository, List<UserSshKey> sshKeys) {
            this.member = member;
            this.repository = repository;
            this.sshKeys = List.copyOf(sshKeys);
            this.workspace = new ApplicationWorkspace(
                    new ApplicationWorkspaceId("awp_1"), app.appId(), repository.repositoryId(), "main", "F-GCMS/workspace", "GCMS Workspace", Instant.now(), Instant.now());
        }

        @Override public List<ApplicationDefinition> findApplications(Boolean enabledOnly) { return List.of(app); }
        @Override public Optional<ApplicationDefinition> findApplication(ApplicationId appId) { return Optional.of(app); }
        @Override public List<ApplicationDefinition> findApplicationsByMember(UserId userId) { return member ? List.of(app) : List.of(); }
        @Override public boolean isActiveMember(ApplicationId appId, UserId userId) { return member; }
        @Override public List<ApplicationMember> findActiveMembers(ApplicationId appId) {
            return member
                    ? List.of(ApplicationMember.active(appId, new UserId("usr_1"), Instant.now()))
                    : List.of();
        }
        @Override public void saveMember(ApplicationMember member) {}
        @Override public void deleteMember(ApplicationId appId, UserId userId) {}
        @Override public PageResponse<CodeRepository> findRepositories(PageRequest pageRequest) { return new PageResponse<>(List.of(repository), 1, 20, 1); }
        @Override public Optional<CodeRepository> findRepository(CodeRepositoryId repositoryId) { return Optional.of(repository); }
        @Override public Optional<CodeRepository> findRepositoryByGitUrl(String gitUrl) { return Optional.of(repository); }
        @Override public Optional<CodeRepository> findRepositoryByEnglishName(String englishName) { return Optional.of(repository); }
        @Override public CodeRepository saveRepository(CodeRepository repository) { return repository; }
        @Override public CodeRepository updateRepositoryMetadata(CodeRepository repository) { return repository; }
        @Override public List<CodeRepository> findRepositoriesByApplication(ApplicationId appId) { return List.of(repository); }
        @Override public List<ApplicationDefinition> findApplicationsByRepository(CodeRepositoryId repositoryId) { return List.of(app); }
        @Override public void linkRepository(ApplicationId appId, CodeRepositoryId repositoryId) {}
        @Override public void unlinkRepository(ApplicationId appId, CodeRepositoryId repositoryId) {}
        @Override public List<ApplicationWorkspace> findWorkspaces(ApplicationId appId) {
            List<ApplicationWorkspace> result = new ArrayList<>();
            result.add(workspace);
            result.addAll(savedWorkspaces);
            return result;
        }
        @Override public Optional<ApplicationWorkspace> findWorkspace(ApplicationWorkspaceId workspaceId) { return Optional.of(workspace); }
        @Override public Optional<ApplicationWorkspace> findWorkspaceByLocation(ApplicationId appId, CodeRepositoryId repositoryId, String branch, String directoryPath) {
            return findWorkspaces(appId).stream()
                    .filter(item -> item.repositoryId().equals(repositoryId)
                            && item.branch().equals(branch)
                            && item.directoryPath().equals(directoryPath))
                    .findFirst();
        }
        @Override public Optional<ApplicationWorkspace> findWorkspaceByName(ApplicationId appId, String workspaceName) {
            return findWorkspaces(appId).stream()
                    .filter(item -> item.workspaceName().equals(workspaceName))
                    .findFirst();
        }
        @Override public ApplicationWorkspace saveWorkspace(ApplicationWorkspace workspace) { savedWorkspaces.add(workspace); return workspace; }
        @Override public ApplicationWorkspace updateWorkspace(ApplicationWorkspace workspace) { return workspace; }
        @Override public void deleteWorkspace(ApplicationWorkspaceId workspaceId) {}
        @Override public List<UserSshKey> findSshKeys(UserId userId) { return sshKeys; }
        @Override public Optional<UserSshKey> findSshKey(UserId userId, SshKeyId sshKeyId) { return Optional.empty(); }
        @Override public UserSshKey saveSshKey(UserSshKey sshKey) { return sshKey; }
        @Override public void deleteSshKey(UserId userId, SshKeyId sshKeyId) {}
    }

    private static final class FakeManagedWorkspaceRepository implements ManagedWorkspaceRepository {
        private final List<ApplicationWorkspaceVersion> versions = new ArrayList<>();
        private final List<ApplicationWorkspaceVersionReplica> replicas = new ArrayList<>();
        private final List<PersonalWorkspace> personals = new ArrayList<>();
        private final List<WorkspaceSyncRecord> syncRecords = new ArrayList<>();
        private UserWorkspacePreference globalPreference;
        private UserWorkspacePreference applicationPreference;
        private UserWorkspaceBranchPreference branchPreference;

        @Override public List<ApplicationWorkspaceVersion> findVersions(ApplicationWorkspaceId applicationWorkspaceId) { return versions; }
        @Override public List<ApplicationWorkspaceVersion> findVersionsByApplication(ApplicationId appId) { return versions; }
        @Override public Optional<ApplicationWorkspaceVersion> findVersion(ApplicationWorkspaceVersionId versionId) { return versions.stream().findFirst(); }
        @Override public Optional<ApplicationWorkspaceVersion> findVersionByTemplateAndVersion(ApplicationWorkspaceId applicationWorkspaceId, String version) { return Optional.empty(); }
        @Override public ApplicationWorkspaceVersion saveVersion(ApplicationWorkspaceVersion version) { versions.add(version); return version; }
        @Override public ApplicationWorkspaceVersion updateVersionTargetCommit(ApplicationWorkspaceVersionId versionId, String targetCommitHash, Instant updatedAt) {
            ApplicationWorkspaceVersion current = findVersion(versionId).orElseThrow();
            ApplicationWorkspaceVersion updated = current.withTargetCommit(targetCommitHash, updatedAt);
            versions.clear();
            versions.add(updated);
            return updated;
        }
        @Override public ApplicationWorkspaceVersionReplica saveVersionReplica(ApplicationWorkspaceVersionReplica replica) {
            replicas.removeIf(item -> item.versionId().equals(replica.versionId()) && item.linuxServerId().equals(replica.linuxServerId()));
            replicas.add(replica);
            return replica;
        }
        @Override public Optional<ApplicationWorkspaceVersionReplica> findVersionReplica(ApplicationWorkspaceVersionId versionId, String linuxServerId) {
            return replicas.stream().filter(item -> item.versionId().equals(versionId) && item.linuxServerId().equals(linuxServerId)).findFirst();
        }
        @Override public Optional<ApplicationWorkspaceVersionReplica> findVersionReplicaByRuntimeWorkspace(WorkspaceId workspaceId) {
            return replicas.stream().filter(item -> item.runtimeWorkspaceId().equals(workspaceId)).findFirst();
        }
        @Override public List<ApplicationWorkspaceVersion> findActiveVersionsMissingReadyReplica(String linuxServerId) { return versions; }
        @Override public List<PersonalWorkspace> findPersonalWorkspaces(ApplicationWorkspaceVersionId versionId, UserId userId) { return personals; }
        @Override public Optional<PersonalWorkspace> findPersonalWorkspace(PersonalWorkspaceId personalWorkspaceId) { return personals.stream().filter(item -> item.personalWorkspaceId().equals(personalWorkspaceId)).findFirst(); }
        @Override public Optional<PersonalWorkspace> findPersonalWorkspaceByRuntimeWorkspace(WorkspaceId workspaceId) { return personals.stream().filter(item -> item.runtimeWorkspaceId().equals(workspaceId)).findFirst(); }
        @Override public PersonalWorkspace savePersonalWorkspace(PersonalWorkspace workspace) { personals.add(workspace); return workspace; }
        @Override public PersonalWorkspace updatePersonalWorkspaceLocation(PersonalWorkspace workspace) {
            personals.removeIf(item -> item.personalWorkspaceId().equals(workspace.personalWorkspaceId()));
            personals.add(workspace);
            return workspace;
        }
        @Override public Optional<ApplicationWorkspaceVersion> findVersionByRuntimeWorkspace(WorkspaceId workspaceId) { return versions.stream().findFirst(); }
        @Override public void savePreference(UserWorkspacePreference preference) {
            if (preference.appId() == null) {
                globalPreference = preference;
            } else {
                applicationPreference = preference;
            }
        }
        @Override public Optional<UserWorkspacePreference> findGlobalPreference(UserId userId) { return Optional.ofNullable(globalPreference); }
        @Override public Optional<UserWorkspacePreference> findApplicationPreference(UserId userId, ApplicationId appId) { return Optional.ofNullable(applicationPreference); }
        @Override public void saveBranchPreference(UserWorkspaceBranchPreference preference) { this.branchPreference = preference; }
        @Override public Optional<UserWorkspaceBranchPreference> findBranchPreference(UserId userId, ApplicationId appId, WorkspaceId workspaceId) {
            UserWorkspaceBranchPreference current = branchPreference;
            if (current == null) {
                return Optional.empty();
            }
            if (!current.userId().equals(userId) || !current.appId().equals(appId) || !current.workspaceId().equals(workspaceId)) {
                return Optional.empty();
            }
            return Optional.of(current);
        }
        @Override public void saveSyncRecord(WorkspaceSyncRecord record) { syncRecords.add(record); }
        @Override public void deleteAllByApplicationWorkspaceId(ApplicationWorkspaceId applicationWorkspaceId) {
            syncRecords.clear();
            replicas.clear();
            personals.clear();
            versions.clear();
        }
    }

    private static final class FakeWorkspaceRepository implements WorkspaceRepository {
        private final List<Workspace> saved = new ArrayList<>();
        @Override public Workspace save(Workspace workspace) {
            saved.removeIf(item -> item.workspaceId().equals(workspace.workspaceId()));
            saved.add(workspace);
            return workspace;
        }
        @Override public Optional<Workspace> findById(WorkspaceId workspaceId) { return saved.stream().filter(item -> item.workspaceId().equals(workspaceId)).findFirst(); }
        @Override public PageResponse<Workspace> findPage(PageRequest pageRequest) { return new PageResponse<>(saved, 1, 20, saved.size()); }
    }

    private static final class FakeUserRepository implements UserRepository {
        private final User user = new User(new UserId("usr_1"), "000857009", "dev", "hash", null, null, null, UserStatus.ACTIVE, Instant.now(), Instant.now());
        @Override public void save(User user) {}
        @Override public Optional<User> findByUserId(UserId userId) { return Optional.of(user); }
        @Override public Optional<User> findByUnifiedAuthId(String unifiedAuthId) { return Optional.of(user); }
        @Override public Optional<User> findByUsername(String username) { return Optional.of(user); }
        @Override public PageResponse<User> findPage(String keyword, PageRequest pageRequest) { return new PageResponse<>(List.of(user), 1, 20, 1); }
        @Override public boolean existsByUsername(String username) { return false; }
        @Override public boolean existsByUnifiedAuthId(String unifiedAuthId) { return false; }
    }
}
