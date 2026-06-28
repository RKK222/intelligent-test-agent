package com.icbc.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.git.GitWorkspaceService;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationMember;
import com.icbc.testagent.domain.configuration.ApplicationWorkspace;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplicaId;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.icbc.testagent.domain.managedworkspace.UserWorkspaceBranchPreference;
import com.icbc.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.icbc.testagent.domain.managedworkspace.WorkspaceReplicaSyncStatus;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncDirection;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecord;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncStatus;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.user.UserStatus;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedWorkspaceApplicationServiceTest {

    private final SshKeyTestFixtures sshKeyFixtures = new SshKeyTestFixtures();

    @TempDir
    Path root;

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
        assertThat(personal.branch()).contains("feature_testagent_20260707_000857009_psw_");
        assertThat(personal.runtimeWorkspace().rootPath()).contains("personalworktree/20260707/000857009/gcms/psw_");
        assertThat(personal.runtimeWorkspace().rootPath()).endsWith("F-GCMS/workspace");
        assertThat(git.worktreeBranch).isEqualTo(personal.branch());
        assertThat(workspaces.saved).hasSize(2);
        assertThat(managed.personals).hasSize(1);
        assertThat(managed.globalPreference.workspaceId()).isEqualTo(managed.personals.get(0).runtimeWorkspaceId());
    }

    @Test
    void syncsPersonalWorkspaceFilesToApplicationAndRecordsPush() throws Exception {
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
        Files.writeString(Path.of(personal.runtimeWorkspace().rootPath()).resolve("case.txt"), "from personal");

        ManagedWorkspaceResponses.WorkspaceSyncResponse result = service.syncPersonalToApplication(
                personal.personalWorkspaceId(),
                List.of("case.txt"),
                true,
                new UserId("usr_1"),
                "trace_sync");

        assertThat(result.status()).isEqualTo(WorkspaceSyncStatus.SUCCEEDED.name());
        assertThat(Files.readString(Path.of(version.runtimeWorkspace().rootPath()).resolve("case.txt"))).isEqualTo("from personal");
        assertThat(git.committedFiles).containsExactly("F-GCMS/workspace/case.txt");
        assertThat(git.pushedBranch).isEqualTo("feature_testagent_20260707");
        assertThat(git.pushedForce).isTrue();
        assertThat(managed.versions.get(0).targetCommitHash()).isEqualTo("commit_after_push");
        assertThat(managed.replicas.get(0).currentCommitHash()).isEqualTo("commit_after_push");
        assertThat(managed.syncRecords).hasSize(1);
        assertThat(managed.syncRecords.get(0).direction()).isEqualTo(WorkspaceSyncDirection.PERSONAL_TO_APPLICATION);
        assertThat(managed.syncRecords.get(0).traceId()).isEqualTo("trace_sync");
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
            public Optional<String> resolvedValue(String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.ofNullable(parameters.get(englishName));
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<com.icbc.testagent.domain.configuration.ResolvedParameter> resolvedAll() {
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
        private String clonedBranch;
        private String worktreeBranch;
        private List<String> committedFiles = List.of();
        private String pushedBranch;
        private boolean pushedForce;
        private String pulledBranch;
        private String nextHeadCommit = "commit_base";

        private FakeGitWorkspaceService(String directoryPath) {
            this.directoryPath = directoryPath;
        }

        @Override
        public void cloneBranch(String gitUrl, String branch, Path repoRoot, String privateKey) {
            this.clonedBranch = branch;
            try {
                Files.createDirectories(repoRoot.resolve(directoryPath));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public void createWorktree(Path repoRoot, Path worktreeRoot, String branch, String privateKey) {
            this.worktreeBranch = branch;
            try {
                Files.createDirectories(worktreeRoot.resolve(directoryPath));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public String headCommit(Path repoRoot) {
            return nextHeadCommit;
        }

        @Override
        public void commitFiles(Path repoRoot, List<String> files, String message, String privateKey) {
            this.committedFiles = List.copyOf(files);
            this.nextHeadCommit = "commit_after_push";
        }

        @Override
        public void push(Path repoRoot, String branch, boolean force, String privateKey) {
            this.pushedBranch = branch;
            this.pushedForce = force;
        }

        @Override
        public boolean isWorktreeClean(Path repoRoot) {
            return true;
        }

        @Override
        public void pullFastForward(Path repoRoot, String branch, String privateKey) {
            this.pulledBranch = branch;
        }
    }

    private static final class FakeConfigurationRepository implements ConfigurationManagementRepository {
        private final boolean member;
        private final ApplicationDefinition app = new ApplicationDefinition(
                new ApplicationId("app_gcms"), "F-GCMS", true, Instant.parse("2026-06-23T00:00:00Z"), Instant.parse("2026-06-23T00:00:00Z"));
        private final CodeRepository repository = new CodeRepository(
                new CodeRepositoryId("repo_1"), "https://example.com/gcms.git", "gcms/gcms", "gcms", true, Instant.now(), Instant.now());
        private final ApplicationWorkspace workspace = new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_1"), app.appId(), repository.repositoryId(), "main", "F-GCMS/workspace", "GCMS Workspace", Instant.now(), Instant.now());

        private FakeConfigurationRepository(boolean member) {
            this.member = member;
        }

        @Override public List<ApplicationDefinition> findApplications(Boolean enabledOnly) { return List.of(app); }
        @Override public Optional<ApplicationDefinition> findApplication(ApplicationId appId) { return Optional.of(app); }
        @Override public List<ApplicationDefinition> findApplicationsByMember(UserId userId) { return member ? List.of(app) : List.of(); }
        @Override public boolean isActiveMember(ApplicationId appId, UserId userId) { return member; }
        @Override public List<ApplicationMember> findActiveMembers(ApplicationId appId) { return List.of(); }
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
        @Override public List<ApplicationWorkspace> findWorkspaces(ApplicationId appId) { return List.of(workspace); }
        @Override public Optional<ApplicationWorkspace> findWorkspace(ApplicationWorkspaceId workspaceId) { return Optional.of(workspace); }
        @Override public Optional<ApplicationWorkspace> findWorkspaceByLocation(ApplicationId appId, CodeRepositoryId repositoryId, String branch, String directoryPath) {
            return workspace.appId().equals(appId)
                    && workspace.repositoryId().equals(repositoryId)
                    && workspace.branch().equals(branch)
                    && workspace.directoryPath().equals(directoryPath)
                    ? Optional.of(workspace)
                    : Optional.empty();
        }
        @Override public ApplicationWorkspace saveWorkspace(ApplicationWorkspace workspace) { return workspace; }
        @Override public ApplicationWorkspace updateWorkspace(ApplicationWorkspace workspace) { return workspace; }
        @Override public void deleteWorkspace(ApplicationWorkspaceId workspaceId) {}
        @Override public List<UserSshKey> findSshKeys(UserId userId) { return List.of(); }
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
    }

    private static final class FakeWorkspaceRepository implements WorkspaceRepository {
        private final List<Workspace> saved = new ArrayList<>();
        @Override public Workspace save(Workspace workspace) { saved.add(workspace); return workspace; }
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
