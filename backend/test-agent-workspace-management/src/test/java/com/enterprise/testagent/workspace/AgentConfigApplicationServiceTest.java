package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.AgentConfigOperation;
import com.enterprise.testagent.domain.configuration.AgentConfigOperationStatus;
import com.enterprise.testagent.domain.configuration.AgentConfigRepository;
import com.enterprise.testagent.domain.configuration.AgentConfigScope;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktree;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloadResult;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloader;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutPreparation;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import com.enterprise.testagent.domain.configuration.SshKeyId;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentConfigApplicationServiceTest {

    private static final UserId ADMIN = new UserId("usr_admin");
    private static final Instant NOW = Instant.parse("2026-06-26T10:00:00Z");
    private static final String PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n";

    private final SshKeyTestFixtures sshKeyFixtures = new SshKeyTestFixtures();

    @TempDir
    Path root;

    @Test
    void publicStatusIsDisabledWhenGitUrlIsUnconfigured() {
        AgentConfigApplicationService service = service(Map.of(
                "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()));

        AgentConfigResponses.AgentConfigStatusResponse status = service.publicStatus(false);

        assertThat(status.scope()).isEqualTo(AgentConfigScope.PUBLIC.name());
        assertThat(status.enabled()).isFalse();
        assertThat(status.writable()).isFalse();
        assertThat(status.gitUrl()).isEqualTo("UNCONFIGURED");
        assertThat(status.agentDirectory().replace('\\', '/'))
                .endsWith("/.config/opencode");
    }

    @Test
    void publicStatusUsesPublicGitParameterAsInternalFragmentWithCurrentUnifiedAuthId() {
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "scm.example.com:29418/team/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.empty());

        AgentConfigResponses.AgentConfigStatusResponse status = service.publicStatus(true, ADMIN);

        assertThat(status.enabled()).isTrue();
        assertThat(status.gitUrl()).isEqualTo("ssh://AUTH_ADMIN@scm.example.com:29418/team/agent-config.git");
    }

    @Test
    void publicBranchesUseEffectiveInternalGitUrlWithCurrentUnifiedAuthId() {
        RecordingGitRemoteService remote = new RecordingGitRemoteService(List.of("main"));
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "scm.example.com:29418/team/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                remote,
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.empty(),
                "external");

        List<String> branches = service.publicBranches(ADMIN);

        assertThat(branches).containsExactly("main");
        assertThat(remote.gitUrl).isEqualTo("ssh://AUTH_ADMIN@scm.example.com:29418/team/agent-config.git");
        assertThat(remote.privateKey).isEqualTo(PRIVATE_KEY);
    }

    @Test
    void publicStatusUsesCompletePublicGitUrlDirectlyEvenWhenDeploymentModeIsInternal() {
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.empty(),
                "internal");

        AgentConfigResponses.AgentConfigStatusResponse status = service.publicStatus(true, ADMIN);

        assertThat(status.enabled()).isTrue();
        assertThat(status.gitUrl()).isEqualTo("git@gitee.com:test/agent-config.git");
    }

    @Test
    void internalPublicRepositoryStatusIgnoresSshUserInOrigin() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.originUrl = "ssh://OTHER_USER@scm.example.com:29418/team/agent-config.git";
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "scm.example.com:29418/team/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.empty(),
                "internal");

        AgentConfigResponses.PublicRepositoryStatusResponse status = service.localPublicRepositoryStatus(ADMIN);

        assertThat(status.status()).isEqualTo("READY");
        assertThat(status.initialized()).isTrue();
        assertThat(status.initializationAllowed()).isTrue();
    }

    @Test
    void listPublicAgentFilesReturnsEmptyAndDoesNotCreateDirectoryWhenUninitialized() {
        // 公共配置已启用（git url 已配置）但尚未 clone 初始化时，浏览应返回空列表，不得自动创建目录。
        AgentConfigApplicationService service = service(Map.of(
                "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()));

        List<FileTreeEntryResponse> entries = service.listPublicAgentFiles("", null, ADMIN);

        assertThat(entries).isEmpty();
        assertThat(Files.exists(root.resolve(".config"))).isFalse();
        assertThat(Files.exists(root.resolve(".config/opencode/agents"))).isFalse();
    }

    @Test
    void publicDirectWriteIsRejectedBecausePersonalWorktreeIsRequired() {
        AgentConfigApplicationService service = service(Map.of(
                "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()));

        assertThatThrownBy(() -> service.writePublicAgentFile("review.md", "review", null, ADMIN))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("worktreeId 不能为空");
    }

    @Test
    void publicUpdateClonesSelectedBranchAndBroadcastsCommit() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                publisher);
        AgentConfigResponses.AgentConfigOperationResponse response = service.updatePublicConfig(
                "main",
                "aco_update_1234567890",
                ADMIN,
                "trace_update");

        assertThat(git.clonedBranch).isEqualTo("main");
        assertThat(git.clonedUrl).isEqualTo("git@gitee.com:test/agent-config.git");
        assertThat(git.privateKeyUsed).isEqualTo(PRIVATE_KEY);
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.commitHash()).isEqualTo("commit_base");
        assertThat(agentConfigs.findOperation("aco_update_1234567890"))
                .get()
                .extracting(AgentConfigOperation::commitHash)
                .isEqualTo("commit_base");
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.get(0).type()).isEqualTo("agent-config.public-sync-requested");
        assertThat(publisher.events.get(0).payload())
                .containsEntry("branch", "main")
                .containsEntry("commitHash", "commit_base");
    }

    @Test
    void pendingPublicSyncIsRetriedFromDurableRolloutState() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        PublicAgentConfigRolloutSyncRequest request = new PublicAgentConfigRolloutSyncRequest(
                "acr_retry", "main", "commit_remote", ADMIN.value(), "trace_retry",
                0, NOW.plusSeconds(180), "acl_sync");
        when(coordinator.claimPendingSync("linux-1")).thenReturn(Optional.of(request));
        when(coordinator.renewServerSync(request)).thenReturn(true);
        service.setPublicConfigRolloutCoordinator(coordinator);

        service.retryPendingPublicConfigSync();

        assertThat(git.resetCommit).isEqualTo("commit_remote");
        verify(coordinator).markServerSynced(request);
    }

    @Test
    void preparingWithoutRecordedLocalCommitRollsBackAndAbortsAfterDeadline() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        when(coordinator.preparing("linux-1")).thenReturn(Optional.of(new PublicAgentConfigRolloutPreparation(
                "acr_preparing_local",
                "main",
                "PENDING_LOCAL_COMMIT",
                "commit_previous",
                ADMIN.value(),
                "linux-1",
                "trace_preparing_local",
                NOW.minusSeconds(301))));
        service.setPublicConfigRolloutCoordinator(coordinator);

        service.retryPreparingPublicConfigRollout();

        assertThat(git.resetCommit).isEqualTo("commit_previous");
        verify(coordinator).abortPreparation("acr_preparing_local", "LOCAL_COMMIT_NOT_RECORDED");
    }

    @Test
    void preparingWithExpectedRemoteCommitActivatesAndBroadcastsRecovery() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.remoteCommit = "commit_expected";
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                publisher);
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        when(coordinator.preparing("linux-1")).thenReturn(Optional.of(new PublicAgentConfigRolloutPreparation(
                "acr_preparing_remote",
                "main",
                "commit_expected",
                "commit_previous",
                ADMIN.value(),
                "linux-1",
                "trace_preparing_remote",
                NOW.minusSeconds(181))));
        service.setPublicConfigRolloutCoordinator(coordinator);

        service.retryPreparingPublicConfigRollout();

        verify(coordinator).activate("acr_preparing_remote", "commit_expected");
        assertThat(publisher.events).singleElement().satisfies(event ->
                assertThat(event.payload())
                        .containsEntry("rolloutId", "acr_preparing_remote")
                        .containsEntry("reason", "preparing-recovery"));
    }

    @Test
    void dirtyPublicRepositoryRemainsInitializedAndBrowsable() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode/agents"));
        Files.writeString(root.resolve(".config/opencode/agents/review.md"), "review");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeClean = false;
        git.stagedAfterAdd = " M opencode/agents/review.md";
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.PublicRepositoryStatusResponse status = service.localPublicRepositoryStatus();

        assertThat(status.status()).isEqualTo("CONFLICT");
        assertThat(status.initialized()).isTrue();
        assertThat(status.initializationAllowed()).isTrue();
        assertThat(status.message()).isEqualTo("Git 工作树存在未提交变更：opencode/agents/review.md");
        assertThat(service.listPublicAgentFiles("agents", null, ADMIN))
                .extracting(FileTreeEntryResponse::name)
                .containsExactly("review.md");
    }

    @Test
    void publicUpdateRejectsDirtyRepositoryByDefault() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeClean = false;
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        service.setPublicConfigRolloutCoordinator(coordinator);

        assertThatThrownBy(() -> service.updatePublicConfig(
                "main",
                "aco_update_dirty",
                false,
                ADMIN,
                "trace_update"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Git 工作树存在未提交变更");
        assertThat(git.resetCommit).isNull();
        verifyNoInteractions(coordinator);
    }

    @Test
    void publicUpdateCanExplicitlyDiscardTrackedChangesBeforePull() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeClean = false;
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.AgentConfigOperationResponse response = service.updatePublicConfig(
                "main",
                "aco_update_discard",
                true,
                ADMIN,
                "trace_update");

        assertThat(git.resetCommit).isEqualTo("HEAD");
        assertThat(git.pulledBranch).isEqualTo("main");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void publicUpdateMergesRemoteBranchIntoCurrentAdministratorsStableWorktree() throws Exception {
        Path sharedRoot = root.resolve(".config");
        Path personalRoot = root.resolve(".configdev/public-usr_admin");
        Files.createDirectories(sharedRoot.resolve(".git"));
        Files.createDirectories(sharedRoot.resolve("opencode"));
        Files.writeString(sharedRoot.resolve("opencode/config.json"), "{}");
        Files.createDirectories(personalRoot.resolve(".git"));
        Files.createDirectories(personalRoot.resolve("opencode"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_admin",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-usr_admin",
                "public-usr_admin",
                personalRoot.toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeRoot = personalRoot;
        git.worktreeBranch = "public-usr_admin";
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", sharedRoot.toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.AgentConfigOperationResponse response = service.updatePublicConfig(
                "main",
                "aco_pull_personal_worktree",
                false,
                ADMIN,
                "trace_pull_personal_worktree");

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(git.mergedRepoRoot).isEqualTo(personalRoot);
        assertThat(git.mergedBranch).isEqualTo("origin/main");
        assertThat(git.pulledBranch).isEqualTo("main");
        assertThat(git.fetchCallCount).isEqualTo(2);
    }

    @Test
    void publicUpdateRejectsDirtyPersonalWorktreeBeforeChangingSharedRuntimeCopy() throws Exception {
        Path sharedRoot = root.resolve(".config");
        Path personalRoot = root.resolve(".configdev/public-usr_admin");
        Files.createDirectories(sharedRoot.resolve(".git"));
        Files.createDirectories(sharedRoot.resolve("opencode"));
        Files.writeString(sharedRoot.resolve("opencode/config.json"), "{}");
        Files.createDirectories(personalRoot.resolve(".git"));
        Files.createDirectories(personalRoot.resolve("opencode"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_admin_dirty",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-usr_admin",
                "public-usr_admin",
                personalRoot.toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeRoot = personalRoot;
        git.worktreeBranch = "public-usr_admin";
        git.worktreeCleanByRoot.put(personalRoot, false);
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", sharedRoot.toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher());

        assertThatThrownBy(() -> service.updatePublicConfig(
                "main",
                "aco_pull_dirty_personal",
                false,
                ADMIN,
                "trace_pull_dirty_personal"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Git 工作树存在未提交变更");

        assertThat(git.pulledBranch).isNull();
        assertThat(git.fetchCallCount).isZero();
    }

    @Test
    void publicUpdateAndPushStagesCommitsAndPushesWhenLocalChangesExist() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeClean = false;
        git.stagedAfterAdd = "M opencode/agents/review.md";
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                publisher);

        AgentConfigResponses.AgentConfigOperationResponse response = service.updatePublicConfigAndPush(
                "main",
                "chore: sync public agent docs",
                "aco_update_push_1234567890",
                false,
                ADMIN,
                "trace_update_push");

        assertThat(git.stagedAllCallCount).isEqualTo(1);
        assertThat(git.lastCommitMessage).isEqualTo("chore: sync public agent docs");
        assertThat(git.lastCommitIdentity)
                .isEqualTo(GitCommitIdentity.forPlatformUser("admin", "AUTH_ADMIN"));
        assertThat(git.pushedBranch).isEqualTo("main");
        assertThat(git.pushedForce).isFalse();
        assertThat(git.privateKeyUsed).isEqualTo(PRIVATE_KEY);
        assertThat(git.resetCommit).isNull();
        assertThat(git.fetchCallCount).isEqualTo(1);
        assertThat(git.mergedBranch).isEqualTo("origin/main");
        assertThat(git.lastMergeIdentity)
                .isEqualTo(GitCommitIdentity.forPlatformUser("admin", "AUTH_ADMIN"));
        assertThat(git.pulledBranch).isNull();
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.commitHash()).isEqualTo("commit_after_update_and_push");
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.get(0).type()).isEqualTo("agent-config.public-sync-requested");
        assertThat(publisher.events.get(0).payload())
                .containsEntry("branch", "main")
                .containsEntry("commitHash", "commit_after_update_and_push");
    }

    @Test
    void internalPublicUpdateAndPushRefreshesOriginToCurrentAdministratorBeforeFetch() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.originUrl = "ssh://OTHER_USER@scm.example.com:29418/team/agent-config.git";
        git.stagedAfterAdd = "";
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "scm.example.com:29418/team/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.empty(),
                "internal");

        service.updatePublicConfigAndPush(
                "main",
                "chore: sync",
                "aco_internal_origin_refresh",
                false,
                ADMIN,
                "trace_internal_origin_refresh");

        assertThat(git.originUrl).isEqualTo("ssh://AUTH_ADMIN@scm.example.com:29418/team/agent-config.git");
        assertThat(git.originRefreshOrder).isPositive();
        assertThat(git.fetchOrder).isGreaterThan(git.originRefreshOrder);
    }

    @Test
    void publicUpdateAndPushSkipsCommitButStillPushesWhenWorktreeIsClean() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        // stagedAfterAdd 为空表示 stageAll 后没有可提交内容
        git.stagedAfterAdd = "";
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                publisher);

        AgentConfigResponses.AgentConfigOperationResponse response = service.updatePublicConfigAndPush(
                "main",
                "chore: empty",
                "aco_update_push_empty",
                false,
                ADMIN,
                "trace_update_push");

        assertThat(git.stagedAllCallCount).isEqualTo(1);
        assertThat(git.lastCommitMessage).isNull();
        assertThat(git.pushedBranch).isEqualTo("main");
        assertThat(git.pushedForce).isFalse();
        assertThat(git.fetchCallCount).isEqualTo(1);
        assertThat(git.mergedBranch).isEqualTo("origin/main");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.commitHash()).isEqualTo("commit_base");
        assertThat(publisher.events).hasSize(1);
    }

    @Test
    void publicUpdateAndPushResetsTrackedChangesWhenDiscardFlagTrue() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeClean = false;
        git.stagedAfterAdd = "M opencode/agents/review.md";
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.AgentConfigOperationResponse response = service.updatePublicConfigAndPush(
                "main",
                "chore: sync",
                "aco_update_push_discard",
                true,
                ADMIN,
                "trace_update_push");

        assertThat(git.resetCommit).isEqualTo("HEAD");
        assertThat(git.stagedAllCallCount).isEqualTo(1);
        assertThat(git.lastCommitMessage).isEqualTo("chore: sync");
        assertThat(git.pushedBranch).isEqualTo("main");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void publicUpdateAndPushReportsConflictWhenRemoteMergeConflicts() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode/agents"));
        Files.writeString(root.resolve(".config/opencode/agents/review.md"), "local");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeClean = false;
        git.stagedAfterAdd = "M opencode/agents/review.md";
        git.failMergeWithConflict = true;
        git.conflictFiles = List.of("opencode/agents/review.md");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());

        assertThatThrownBy(() -> service.updatePublicConfigAndPush(
                "main",
                "chore: sync",
                "aco_update_push_conflict",
                false,
                ADMIN,
                "trace_update_push"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("conflictFiles", List.of("opencode/agents/review.md"));
                });

        assertThat(git.fetchCallCount).isEqualTo(1);
        assertThat(git.lastCommitMessage).isEqualTo("chore: sync");
        assertThat(git.mergedBranch).isEqualTo("origin/main");
        assertThat(git.pushedBranch).isNull();
    }

    @Test
    void publicUpdateAndPushCommitsResolvedMergeBeforePushing() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode/agents"));
        Files.writeString(root.resolve(".config/opencode/agents/review.md"), "resolved");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.mergeInProgress = true;
        git.conflictFiles = List.of();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.AgentConfigOperationResponse response = service.updatePublicConfigAndPush(
                "main",
                "chore: resolve conflict",
                "aco_update_push_resolved",
                false,
                ADMIN,
                "trace_update_push");

        assertThat(git.fetchCallCount).isZero();
        assertThat(git.stagedAllCallCount).isZero();
        assertThat(git.lastCommitMessage).isEqualTo("chore: resolve conflict");
        assertThat(git.pushedBranch).isEqualTo("main");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void publicUpdateAndPushRejectsEmptyCommitMessage() {
        AgentConfigApplicationService service = service(Map.of(
                "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()));

        assertThatThrownBy(() -> service.updatePublicConfigAndPush(
                "main",
                "   ",
                "aco_update_push_invalid",
                false,
                ADMIN,
                "trace_update_push"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("提交说明不能为空");
    }

    @Test
    void publicWorktreeUsesStablePerUserBranchAndCreatesReusableGitWorktree() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.AgentConfigWorktreeResponse response = service.createPublicWorktree(
                "change-agent-md",
                "main",
                ADMIN,
                "trace_worktree");

        assertThat(response.worktreeName()).isEqualTo("public-usr_admin");
        assertThat(response.rootPath().replace('\\', '/'))
                .endsWith("/.configdev/public-usr_admin");
        assertThat(git.worktreeBranch).isEqualTo("public-usr_admin");
        assertThat(git.worktreeRoot.toString().replace('\\', '/'))
                .endsWith("/.configdev/public-usr_admin");
        assertThat(agentConfigs.findWorktree(response.worktreeId()))
                .map(AgentConfigWorktree::status)
                .contains(AgentConfigWorktreeStatus.ACTIVE);
        assertThat(agentConfigs.findWorktree(response.worktreeId()))
                .map(AgentConfigWorktree::linuxServerId)
                .contains("linux-1");

        AgentConfigResponses.AgentConfigWorktreeResponse reused = service.createPublicWorktree(
                "ignored-on-reuse",
                "main",
                ADMIN,
                "trace_worktree_again");

        assertThat(reused.worktreeId()).isEqualTo(response.worktreeId());
        assertThat(agentConfigs.findWorktrees(AgentConfigScope.PUBLIC, null, ADMIN)).hasSize(1);
    }

    @Test
    void publicWorktreeDoesNotReuseLegacyVersionedBranch() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.writeString(root.resolve(".config/opencode/config.json"), "{}");
        Path legacyRoot = root.resolve(".configdev/public-personal-20260717");
        Files.createDirectories(legacyRoot.resolve(".git"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_legacy_public",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-personal-20260717",
                "public-personal-20260717",
                legacyRoot.toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.AgentConfigWorktreeResponse response = service.createPublicWorktree(
                "ignored",
                "main",
                ADMIN,
                "trace_migrate_legacy_worktree");

        assertThat(response.worktreeId()).isNotEqualTo("agw_legacy_public");
        assertThat(response.worktreeName()).isEqualTo("public-usr_admin");
        assertThat(response.branch()).isEqualTo("public-usr_admin");
        assertThat(agentConfigs.findWorktrees(AgentConfigScope.PUBLIC, null, ADMIN)).hasSize(2);
        assertThat(service.listPublicWorktrees("linux-1", ADMIN))
                .extracting(AgentConfigResponses.AgentConfigWorktreeOptionResponse::worktreeId)
                .containsExactly(response.worktreeId());
    }

    @Test
    void publicWorktreeFailsWhenGitRootMissingAndDoesNotClone() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher());

        assertThatThrownBy(() -> service.createPublicWorktree(
                "change-agent-md",
                "main",
                ADMIN,
                "trace_worktree"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("服务器linux-1上公共配置仓库在")
                .hasMessageContaining("目录中未初始化");
        assertThat(git.clonedUrl).isNull();
        assertThat(git.worktreeBranch).isNull();
    }

    @Test
    void publicWorktreeFailsWhenGitRootIsEmptyDirectoryAndDoesNotClone() throws Exception {
        Files.createDirectories(root.resolve(".config"));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());

        assertThatThrownBy(() -> service.createPublicWorktree(
                "change-agent-md",
                "main",
                ADMIN,
                "trace_worktree"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("服务器linux-1上公共配置仓库在")
                .hasMessageContaining("目录中未初始化");

        assertThat(git.clonedUrl).isNull();
        assertThat(git.worktreeRoot).isNull();
    }

    @Test
    void initializePublicRepositoryClonesMissingGitRootAndValidatesConfigDirectory() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_DIR", root.resolve(".config/opencode").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher());

        AgentConfigResponses.PublicRepositoryStatusResponse response = service.initializeLocalPublicRepository(
                "main",
                "aco_init_1234567890",
                ADMIN,
                "trace_init");

        assertThat(git.clonedUrl).isEqualTo("git@gitee.com:test/agent-config.git");
        assertThat(git.clonedBranch).isEqualTo("main");
        assertThat(response.initialized()).isTrue();
        assertThat(response.linuxServerId()).isEqualTo("linux-1");
    }

    @Test
    void workspaceAgentFilesUsePluralAgentsDirectory() throws Exception {
        Path workspaceRoot = root.resolve("project");
        Files.createDirectories(workspaceRoot.resolve(".opencode/agents"));
        Files.writeString(workspaceRoot.resolve(".opencode/agents/review.md"), "review");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));

        List<FileTreeEntryResponse> entries = service.listWorkspaceAgentFiles("wrk_project", "agents", null);
        service.writeWorkspaceAgentFile("wrk_project", "agents/new.md", "new agent", null);
        service.uploadWorkspaceAgentFile("wrk_project", "agents/icon.bin", "AAEC/w==", null);
        service.renameWorkspaceAgentFile("wrk_project", "agents/review.md", "payment-review.md", null);

        assertThat(entries).extracting(FileTreeEntryResponse::name).contains("review.md");
        assertThat(Files.readString(workspaceRoot.resolve(".opencode/agents/new.md"))).isEqualTo("new agent");
        assertThat(Files.readString(workspaceRoot.resolve(".opencode/agents/payment-review.md"))).isEqualTo("review");
        assertThat(Files.readAllBytes(workspaceRoot.resolve(".opencode/agents/icon.bin")))
                .containsExactly((byte) 0, (byte) 1, (byte) 2, (byte) 255);
        assertThat(Files.exists(workspaceRoot.resolve(".opencode/agents/review.md"))).isFalse();
        assertThat(Files.exists(workspaceRoot.resolve(".opencode/agent/new.md"))).isFalse();
        assertThatThrownBy(() -> service.uploadWorkspaceAgentFile("wrk_project", "package.json", "e30=", null))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("只允许上传");
        service.deleteWorkspaceAgentFile("wrk_project", "agents/new.md", null);
        assertThat(Files.exists(workspaceRoot.resolve(".opencode/agents/new.md"))).isFalse();
    }

    @Test
    void workspaceAgentFilesExposeOpencodeRootForAgentAndSkillPackages() throws Exception {
        Path workspaceRoot = root.resolve("project");
        Files.createDirectories(workspaceRoot.resolve(".opencode/agents"));
        Files.createDirectories(workspaceRoot.resolve(".opencode/skills/app-skill"));
        Files.writeString(workspaceRoot.resolve(".opencode/agents/review.md"), "review");
        Files.writeString(workspaceRoot.resolve(".opencode/skills/app-skill/SKILL.md"), "skill");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));

        List<FileTreeEntryResponse> rootEntries = service.listWorkspaceAgentFiles("wrk_project", "", null);
        List<FileTreeEntryResponse> skillEntries = service.listWorkspaceAgentFiles("wrk_project", "skills/app-skill", null);
        service.writeWorkspaceAgentFile("wrk_project", "skills/new-skill/SKILL.md", "new skill", null);

        assertThat(rootEntries).extracting(FileTreeEntryResponse::name).contains("agents", "skills");
        assertThat(skillEntries).extracting(FileTreeEntryResponse::name).containsExactly("SKILL.md");
        assertThat(Files.readString(workspaceRoot.resolve(".opencode/skills/new-skill/SKILL.md"))).isEqualTo("new skill");
        assertThat(Files.exists(workspaceRoot.resolve(".opencode/agents/skills/new-skill/SKILL.md"))).isFalse();
        service.deleteWorkspaceAgentFile("wrk_project", "skills/new-skill", null);
        assertThat(Files.exists(workspaceRoot.resolve(".opencode/skills/new-skill"))).isFalse();
    }

    @Test
    void workspaceDiffIncludesApplicationRuntimeConfigAgentAndSkillFiles() {
        Path workspaceRoot = root.resolve("project/F-COSS/workspace");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.statusByPathspec.put(
                ".opencode",
                """
                 M F-COSS/workspace/02-设计/Test Material.md
                ?? F-COSS/workspace/.opencode/opencode.jsonc
                 M "F-COSS/workspace/.opencode/agents/review rule.md"
                 M F-COSS/workspace/.opencode/skills/payment/SKILL.md
                ?? F-COSS/workspace/.opencode/package.json
                """);
        git.diffByFile.put(".opencode/opencode.jsonc", "diff --git a/.opencode/opencode.jsonc b/.opencode/opencode.jsonc\n");
        git.diffByFile.put(".opencode/agents/review rule.md", "diff --git a/.opencode/agents/review rule.md b/.opencode/agents/review rule.md\n");
        git.diffByFile.put(".opencode/skills/payment/SKILL.md", "diff --git a/.opencode/skills/payment/SKILL.md b/.opencode/skills/payment/SKILL.md\n");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));

        AgentConfigResponses.AgentConfigDiffResponse diff = service.workspaceDiff("wrk_project", null);

        assertThat(diff.files()).extracting(AgentConfigResponses.AgentConfigDiffFileResponse::path)
                .containsExactly("opencode.jsonc", "agents/review rule.md", "skills/payment/SKILL.md");
        assertThat(git.lastStatusPathspec).isEqualTo(".opencode");
        // 未跟踪文件由 collectDiffFiles 直接合成响应，不会调用 git diff；已跟踪文件仍逐个读取 patch。
        assertThat(git.diffFiles).containsExactly(
                ".opencode/agents/review rule.md",
                ".opencode/skills/payment/SKILL.md");
    }

    @Test
    void publicDiffKeepsRawPorcelainStatusAndMergesStagedAndUnstagedPatch() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.stagedAfterAdd = "MM opencode/agents/public-review.md\n";
        git.diffByFileAndStage.put(
                "opencode/agents/public-review.md|true",
                "diff --git a/opencode/agents/public-review.md b/opencode/agents/public-review.md\n@@ -1 +1 @@\n-old\n+staged\n");
        git.diffByFileAndStage.put(
                "opencode/agents/public-review.md|false",
                "diff --git a/opencode/agents/public-review.md b/opencode/agents/public-review.md\n@@ -2 +2 @@\n-old2\n+unstaged\n");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.empty());

        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        AgentConfigWorktree worktree = new AgentConfigWorktree(
                "agw_public_diff",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-usr_admin",
                "public-usr_admin",
                root.resolve(".config").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW);
        agentConfigs.saveWorktree(worktree);
        service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher(),
                Optional.empty());

        AgentConfigResponses.AgentConfigDiffResponse diff = service.publicDiff("agw_public_diff", ADMIN);

        assertThat(diff.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("opencode/agents/public-review.md");
            assertThat(file.status()).isEqualTo("MM");
            assertThat(file.staged()).isTrue();
            assertThat(file.patch()).contains("+staged").contains("+unstaged");
        });
        assertThat(git.diffFiles).containsExactly("opencode/agents/public-review.md", "opencode/agents/public-review.md");
    }

    @Test
    void publicDiscardUsesOwnedPublicWorktreePaths() {
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_discard",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-usr_admin",
                "public-usr_admin",
                root.resolve(".configdev/public-usr_admin").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher(),
                Optional.empty());

        service.publicDiscard(
                List.of("opencode/agents/review.md", "opencode/skills/case/SKILL.md"),
                "agw_public_discard",
                ADMIN);

        assertThat(git.discardedFiles).containsExactly("opencode/agents/review.md", "opencode/skills/case/SKILL.md");
    }

    @Test
    void publicAgentFileRenameUsesOwnedPersonalWorktree() throws Exception {
        Path worktreeRoot = root.resolve(".configdev/public-usr_admin");
        Files.createDirectories(worktreeRoot.resolve("opencode/agents"));
        Files.writeString(worktreeRoot.resolve("opencode/agents/review.md"), "review");
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_rename",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-usr_admin",
                "public-usr_admin",
                worktreeRoot.toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.empty());

        service.renamePublicAgentFile("agents/review.md", "shared-review.md", "agw_public_rename", ADMIN);
        service.uploadPublicAgentFile("agents/icon.bin", "AAEC/w==", "agw_public_rename", ADMIN);

        assertThat(Files.readString(worktreeRoot.resolve("opencode/agents/shared-review.md"))).isEqualTo("review");
        assertThat(Files.readAllBytes(worktreeRoot.resolve("opencode/agents/icon.bin")))
                .containsExactly((byte) 0, (byte) 1, (byte) 2, (byte) 255);
        assertThat(Files.exists(worktreeRoot.resolve("opencode/agents/review.md"))).isFalse();
    }

    @Test
    void publicWorktreePublishReturnsConflictFilesAndDoesNotMarkPublishedWhenMergeConflicts() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.createDirectories(root.resolve(".configdev/review-agent/.git"));
        Files.createDirectories(root.resolve(".configdev/review-agent/opencode"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        AgentConfigWorktree worktree = new AgentConfigWorktree(
                "agw_public",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "review-agent",
                "review-agent",
                root.resolve(".configdev/review-agent").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW);
        agentConfigs.saveWorktree(worktree);
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.failMergeWithConflict = true;
        git.conflictFiles = List.of("opencode/agents/review.md", "opencode/skills/pay/SKILL.md");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher());

        assertThatThrownBy(() -> service.publicPublish("agw_public", "aco_publish_conflict", ADMIN, "trace_publish"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("conflictFiles", List.of("opencode/agents/review.md", "opencode/skills/pay/SKILL.md"));
                });

        assertThat(git.abortedMergeRepoRoot).isNull();
        assertThat(git.mergedRepoRoot).isEqualTo(root.resolve(".configdev/review-agent"));
        assertThat(git.pushedBranch).isNull();
        assertThat(agentConfigs.findWorktree("agw_public")).get().extracting(AgentConfigWorktree::status)
                .isEqualTo(AgentConfigWorktreeStatus.ACTIVE);
        assertThat(agentConfigs.findOperation("aco_publish_conflict")).get().satisfies(operation -> {
            assertThat(operation.status()).isEqualTo(AgentConfigOperationStatus.FAILED);
            assertThat(operation.errorCode()).isEqualTo(ErrorCode.CONFLICT.name());
            assertThat(operation.errorMessage()).contains("冲突");
        });
    }

    @Test
    void publicWorktreePublishMergesRemoteInPersonalWorktreeAndPushesToPublicBranch() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.createDirectories(root.resolve(".configdev/review-agent/.git"));
        Files.createDirectories(root.resolve(".configdev/review-agent/opencode"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        AgentConfigWorktree worktree = new AgentConfigWorktree(
                "agw_public",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "review-agent",
                "review-agent",
                root.resolve(".configdev/review-agent").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW);
        agentConfigs.saveWorktree(worktree);
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                publisher);
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        when(coordinator.prepare("main", "commit_base", "commit_base", "linux-1", ADMIN.value(), "trace_publish"))
                .thenReturn("acr_publish");
        PublicAgentConfigRolloutSyncRequest syncRequest = new PublicAgentConfigRolloutSyncRequest(
                "acr_publish", "main", "commit_base", ADMIN.value(), "trace_publish",
                0, NOW.plusSeconds(180), "acl_publish");
        when(coordinator.claimPendingSync("linux-1")).thenReturn(Optional.of(syncRequest));
        when(coordinator.renewServerSync(syncRequest)).thenReturn(true);
        service.setPublicConfigRolloutCoordinator(coordinator);

        AgentConfigResponses.AgentConfigOperationResponse response = service.publicPublish(
                "agw_public",
                "aco_publish_success",
                ADMIN,
                "trace_publish");

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(git.mergedRepoRoot).isEqualTo(root.resolve(".configdev/review-agent"));
        assertThat(git.mergedBranch).isEqualTo("origin/main");
        assertThat(git.pushedBranch).isEqualTo("review-agent:main");
        assertThat(git.resetCommit).isEqualTo("commit_base");
        assertThat(agentConfigs.findWorktree("agw_public")).get().extracting(AgentConfigWorktree::status)
                .isEqualTo(AgentConfigWorktreeStatus.ACTIVE);
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.get(0).payload()).containsEntry("rolloutId", "acr_publish");
        verify(coordinator).prepare("main", "commit_base", "commit_base", "linux-1", ADMIN.value(), "trace_publish");
        verify(coordinator).activate("acr_publish", "commit_base");
        verify(coordinator).markServerSynced(syncRequest);
    }

    @Test
    void publicWorktreePublishStaysSuccessfulWhenImmediateLocalSyncKickFails() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
        Files.createDirectories(root.resolve(".config/opencode"));
        Files.createDirectories(root.resolve(".configdev/review-agent/.git"));
        Files.createDirectories(root.resolve(".configdev/review-agent/opencode"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "review-agent",
                "review-agent",
                root.resolve(".configdev/review-agent").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        RecordingBroadcastPublisher publisher = new RecordingBroadcastPublisher();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                publisher);
        PublicAgentConfigRolloutCoordinator coordinator = mock(PublicAgentConfigRolloutCoordinator.class);
        when(coordinator.prepare("main", "commit_base", "commit_base", "linux-1", ADMIN.value(), "trace_publish"))
                .thenReturn("acr_publish");
        when(coordinator.claimPendingSync("linux-1"))
                .thenThrow(new IllegalStateException("temporary rollout storage failure"));
        service.setPublicConfigRolloutCoordinator(coordinator);

        AgentConfigResponses.AgentConfigOperationResponse response = service.publicPublish(
                "agw_public",
                "aco_publish_deferred_sync",
                ADMIN,
                "trace_publish");

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(git.pushedBranch).isEqualTo("review-agent:main");
        assertThat(git.resetCommit).isEqualTo("commit_base");
        assertThat(publisher.events).hasSize(1);
        verify(coordinator).activate("acr_publish", "commit_base");
        verify(coordinator).claimPendingSync("linux-1");
    }

    @Test
    void publicWorktreeOperationsRejectAnotherUsersWorktree() {
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_other",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-other",
                "public-other",
                root.resolve(".configdev/public-other").toString(),
                new UserId("usr_alice"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher());

        assertThatThrownBy(() -> service.publicDiff("agw_other", ADMIN))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void workspaceWorktreePublishReturnsConflictFilesAndDoesNotPushWhenMergeConflicts() throws Exception {
        Path workspaceRoot = root.resolve("project");
        Files.createDirectories(workspaceRoot.resolve(".git"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        AgentConfigWorktree worktree = new AgentConfigWorktree(
                "agw_workspace",
                AgentConfigScope.WORKSPACE,
                new WorkspaceId("wrk_project"),
                "linux-1",
                "workspace-agent",
                "workspace-agent",
                root.resolve("worktrees/workspace-agent").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW);
        agentConfigs.saveWorktree(worktree);
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.failMergeWithConflict = true;
        git.conflictFiles = List.of(".opencode/agents/review.md");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));

        assertThatThrownBy(() -> service.workspacePublish("wrk_project", "agw_workspace", "aco_workspace_publish", ADMIN, "trace_publish"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("conflictFiles", List.of(".opencode/agents/review.md"));
                });

        assertThat(git.abortedMergeRepoRoot).isEqualTo(workspaceRoot);
        assertThat(git.pushedBranch).isNull();
        assertThat(agentConfigs.findWorktree("agw_workspace")).get().extracting(AgentConfigWorktree::status)
                .isEqualTo(AgentConfigWorktreeStatus.ACTIVE);
        assertThat(agentConfigs.findOperation("aco_workspace_publish")).get().satisfies(operation -> {
            assertThat(operation.status()).isEqualTo(AgentConfigOperationStatus.FAILED);
            assertThat(operation.errorCode()).isEqualTo(ErrorCode.CONFLICT.name());
        });
    }

    @Test
    void workspacePublishUpdatesManagedFeatureHeadAndBroadcastsThroughSharedService() throws Exception {
        Path workspaceRoot = root.resolve("project-feature");
        Files.createDirectories(workspaceRoot.resolve(".git"));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_feature"),
                        "feature",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));
        ManagedWorkspaceApplicationService managedWorkspaceService = mock(ManagedWorkspaceApplicationService.class);
        service.setManagedWorkspaceApplicationService(managedWorkspaceService);

        service.workspacePublish("wrk_feature", null, "aco_workspace_publish", ADMIN, "trace_publish");

        verify(managedWorkspaceService).recordFeatureWorkspacePublished(
                "wrk_feature", git.currentHead, ADMIN, "trace_publish");
    }

    @Test
    void workspaceDiffMergesStagedAndUnstagedPatchAfterFilteringAgentFiles() {
        Path workspaceRoot = root.resolve("project/F-COSS/workspace");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.statusByPathspec.put(
                ".opencode",
                """
                MM F-COSS/workspace/.opencode/agents/review rule.md
                 M F-COSS/workspace/02-设计/Test Material.md
                """);
        git.diffByFileAndStage.put(
                ".opencode/agents/review rule.md|true",
                "diff --git a/.opencode/agents/review rule.md b/.opencode/agents/review rule.md\n@@ -1 +1 @@\n-old\n+staged\n");
        git.diffByFileAndStage.put(
                ".opencode/agents/review rule.md|false",
                "diff --git a/.opencode/agents/review rule.md b/.opencode/agents/review rule.md\n@@ -2 +2 @@\n-old2\n+unstaged\n");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));

        AgentConfigResponses.AgentConfigDiffResponse diff = service.workspaceDiff("wrk_project", null);

        assertThat(diff.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("agents/review rule.md");
            assertThat(file.status()).isEqualTo("MM");
            assertThat(file.staged()).isTrue();
            assertThat(file.patch()).contains("+staged").contains("+unstaged");
        });
        assertThat(git.diffFiles).containsExactly(".opencode/agents/review rule.md", ".opencode/agents/review rule.md");
    }

    @Test
    void workspaceStageMapsDisplayedAgentPathsBackToOpencodeRoot() {
        Path workspaceRoot = root.resolve("project");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));

        service.workspaceStage(
                "wrk_project",
                List.of("opencode.jsonc", "agents/review.md", "skills/payment/SKILL.md", "package.json"),
                null,
                ADMIN);

        assertThat(git.stagedFiles).containsExactly(
                ".opencode/opencode.jsonc",
                ".opencode/agents/review.md",
                ".opencode/skills/payment/SKILL.md");
    }

    @Test
    void workspaceDiscardMapsDisplayedAgentPathsBackToOpencodeRoot() {
        Path workspaceRoot = root.resolve("project");
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                git,
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-1",
                        "trace_workspace")));

        service.workspaceDiscard("wrk_project", List.of("agents/review.md", "skills/payment/SKILL.md"), null, ADMIN);

        assertThat(git.discardedFiles).containsExactly(".opencode/agents/review.md", ".opencode/skills/payment/SKILL.md");
    }

    @Test
    void workspaceAgentFileLinuxServerUsesWorkspaceServerWhenNoWorktree() {
        Path workspaceRoot = root.resolve("project");
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                new InMemoryAgentConfigRepository(),
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-2",
                        "trace_workspace")));

        assertThat(service.workspaceAgentFilesLinuxServerId("wrk_project", null)).isEqualTo("linux-2");
    }

    @Test
    void workspaceAgentFileLinuxServerUsesWorktreeServerWhenPresent() {
        Path workspaceRoot = root.resolve("project");
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_workspace",
                AgentConfigScope.WORKSPACE,
                new WorkspaceId("wrk_project"),
                "linux-3",
                "change-agent-md",
                "change-agent-md",
                root.resolve("worktrees/change-agent-md").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher(),
                Optional.of(new Workspace(
                        new WorkspaceId("wrk_project"),
                        "project",
                        workspaceRoot.toString(),
                        WorkspaceStatus.ACTIVE,
                        NOW,
                        NOW,
                        "linux-2",
                        "trace_workspace")));

        assertThat(service.workspaceAgentFilesLinuxServerId("wrk_project", "agw_workspace")).isEqualTo("linux-3");
    }

    @Test
    void listPublicWorktreesOnlyReturnsCurrentUsersActiveWorktreeOnServer() throws Exception {
        Files.createDirectories(root.resolve("worktrees/public-usr_admin/.git"));
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_new",
                AgentConfigScope.PUBLIC,
                null,
                "linux-2",
                "public-usr_admin",
                "public-usr_admin",
                root.resolve("worktrees/public-usr_admin").toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(30)));
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_old",
                AgentConfigScope.PUBLIC,
                null,
                "linux-2",
                "old-change",
                "old-change",
                root.resolve("worktrees/old-change").toString(),
                new UserId("usr_missing"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(10)));
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_published",
                AgentConfigScope.PUBLIC,
                null,
                "linux-2",
                "published-change",
                "published-change",
                root.resolve("worktrees/published-change").toString(),
                new UserId("usr_alice"),
                AgentConfigWorktreeStatus.PUBLISHED,
                NOW,
                NOW.plusSeconds(40)));
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_other_server",
                AgentConfigScope.PUBLIC,
                null,
                "linux-3",
                "other-server",
                "other-server",
                root.resolve("worktrees/other-server").toString(),
                new UserId("usr_alice"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(50)));
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_workspace",
                AgentConfigScope.WORKSPACE,
                new WorkspaceId("wrk_project"),
                "linux-2",
                "workspace-change",
                "workspace-change",
                root.resolve("worktrees/workspace-change").toString(),
                new UserId("usr_alice"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(60)));
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.worktreeRoot = root.resolve("worktrees/public-usr_admin");
        git.worktreeBranch = "public-usr_admin";
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                git,
                new RecordingBroadcastPublisher());

        List<AgentConfigResponses.AgentConfigWorktreeOptionResponse> options = service.listPublicWorktrees("linux-2", ADMIN);

        assertThat(options).extracting(AgentConfigResponses.AgentConfigWorktreeOptionResponse::worktreeId)
                .containsExactly("agw_public_new");
        assertThat(options.get(0).createdByUserId()).isEqualTo("usr_admin");
        assertThat(options.get(0).createdByUsername()).isEqualTo("admin");
    }

    @Test
    void reloadPublicPersonalRuntimeUsesOwnedWorktreeConfigRootAndCurrentServer() throws Exception {
        Path worktreeRoot = root.resolve("worktrees/public-usr_admin");
        Files.createDirectories(worktreeRoot.resolve("opencode"));
        Files.writeString(worktreeRoot.resolve("opencode/opencode.jsonc"), "{}");
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public",
                AgentConfigScope.PUBLIC,
                null,
                "linux-1",
                "public-usr_admin",
                "public-usr_admin",
                worktreeRoot.toString(),
                ADMIN,
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher());
        PersonalAgentConfigRuntimeReloader reloader = mock(PersonalAgentConfigRuntimeReloader.class);
        when(reloader.reloadPublicPreview(
                        ADMIN,
                        "linux-1",
                        worktreeRoot.resolve("opencode").toString(),
                        "trace-reload"))
                .thenReturn(new PersonalAgentConfigRuntimeReloadResult(true, "reloaded"));
        service.setPersonalRuntimeReloader(reloader);

        PersonalAgentConfigRuntimeReloadResult result = service.reloadPublicPersonalRuntime(
                "agw_public", ADMIN, "trace-reload");

        assertThat(result.reloaded()).isTrue();
        verify(reloader).reloadPublicPreview(
                ADMIN,
                "linux-1",
                worktreeRoot.resolve("opencode").toString(),
                "trace-reload");
    }

    private AgentConfigApplicationService service(Map<String, String> parameters) {
        return service(parameters, new InMemoryAgentConfigRepository(), new RecordingGitWorkspaceService(), new RecordingBroadcastPublisher());
    }

    private AgentConfigApplicationService service(
            Map<String, String> parameters,
            AgentConfigRepository agentConfigs,
            RecordingGitWorkspaceService git,
            ServerBroadcastPublisher publisher) {
        return service(parameters, agentConfigs, git, publisher, Optional.empty());
    }

    private AgentConfigApplicationService service(
            Map<String, String> parameters,
            AgentConfigRepository agentConfigs,
            RecordingGitWorkspaceService git,
            ServerBroadcastPublisher publisher,
            Optional<Workspace> workspace) {
        return service(parameters, agentConfigs, git, publisher, workspace, "external");
    }

    private AgentConfigApplicationService service(
            Map<String, String> parameters,
            AgentConfigRepository agentConfigs,
            RecordingGitWorkspaceService git,
            ServerBroadcastPublisher publisher,
            Optional<Workspace> workspace,
            String deploymentMode) {
        return service(
                parameters,
                agentConfigs,
                new GitRemoteService(),
                git,
                publisher,
                workspace,
                deploymentMode);
    }

    private AgentConfigApplicationService service(
            Map<String, String> parameters,
            AgentConfigRepository agentConfigs,
            GitRemoteService remote,
            RecordingGitWorkspaceService git,
            ServerBroadcastPublisher publisher,
            Optional<Workspace> workspace,
            String deploymentMode) {
        CommonParameterValues commonParameters = commonParameters(parameters);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        when(configuration.findSshKeys(eq(ADMIN))).thenReturn(List.of(encryptedSshKey()));
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        workspace.ifPresent(value -> when(workspaces.findById(value.workspaceId())).thenReturn(Optional.of(value)));
        return new AgentConfigApplicationService(
                commonParameters,
                configuration,
                workspaces,
                agentConfigs,
                new InMemoryUserRepository(),
                remote,
                git,
                sshKeyFixtures.encryptionService(),
                new WorkspaceFileService(),
                ManagedWorkspacePathResolver.legacyOnly(),
                new WorkspaceServerIdentity("linux-1"),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new RecordingAgentConfigProgressSink(),
                deploymentMode);
    }

    private CommonParameterValues commonParameters(Map<String, String> parameters) {
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

    private UserSshKey encryptedSshKey() {
        return sshKeyFixtures.encryptedSshKey(new SshKeyId("ssh_admin"), ADMIN, "admin", PRIVATE_KEY, NOW);
    }

    private static final class RecordingGitRemoteService extends GitRemoteService {
        private final List<String> branches;
        private String gitUrl;
        private String privateKey;

        private RecordingGitRemoteService(List<String> branches) {
            this.branches = branches;
        }

        @Override
        public List<String> listBranches(String gitUrl, String privateKey) {
            this.gitUrl = gitUrl;
            this.privateKey = privateKey;
            return branches;
        }
    }

    private static final class RecordingGitWorkspaceService extends GitWorkspaceService {
        private String clonedUrl;
        private String clonedBranch;
        private String privateKeyUsed;
        private String worktreeBranch;
        private Path worktreeRoot;
        private boolean worktreeClean = true;
        private final Map<Path, Boolean> worktreeCleanByRoot = new LinkedHashMap<>();
        private String originUrl = "git@gitee.com:test/agent-config.git";
        private int commandOrder;
        private int originRefreshOrder;
        private int fetchOrder;
        private String resetCommit;
        private String pulledBranch;
        private int fetchCallCount;
        // 用于验证 update-and-push：模拟 stageAll 之后 porcelain 状态
        private String stagedAfterAdd = "M opencode/agents/review.md";
        // 模拟当前 HEAD；commit 后会推进到下一个 commit id
        private String currentHead = "commit_base";
        private String remoteCommit = "commit_base";
        private final List<String> headHistory = new ArrayList<>();
        private int stagedAllCallCount;
        private String lastCommitMessage;
        private GitCommitIdentity lastCommitIdentity;
        private String pushedBranch;
        private Boolean pushedForce;
        private String mergedBranch;
        private Path mergedRepoRoot;
        private boolean mergeInProgress;
        private boolean failMergeWithConflict;
        private GitCommitIdentity lastMergeIdentity;
        private List<String> conflictFiles = List.of();
        private Path abortedMergeRepoRoot;
        private final Map<String, String> statusByPathspec = new LinkedHashMap<>();
        private String lastStatusPathspec;
        private final Map<String, String> diffByFile = new LinkedHashMap<>();
        private final Map<String, String> diffByFileAndStage = new LinkedHashMap<>();
        private final List<String> diffFiles = new ArrayList<>();
        private List<String> stagedFiles = List.of();
        private List<String> discardedFiles = List.of();

        @Override
        public void cloneBranch(String gitUrl, String branch, Path repoRoot, String privateKey) {
            this.clonedUrl = gitUrl;
            this.clonedBranch = branch;
            this.privateKeyUsed = privateKey;
            try {
                Files.createDirectories(repoRoot.resolve(".git"));
                Files.createDirectories(repoRoot.resolve("opencode"));
                Files.writeString(repoRoot.resolve("opencode/config.json"), "{}");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public String originUrl(Path repoRoot) {
            return originUrl;
        }

        @Override
        public void setOriginUrl(Path repoRoot, String gitUrl, String privateKey) {
            this.originUrl = gitUrl;
            this.originRefreshOrder = ++commandOrder;
        }

        @Override
        public String currentBranch(Path repoRoot) {
            return repoRoot.equals(worktreeRoot) && worktreeBranch != null ? worktreeBranch : "main";
        }

        @Override
        public String headCommit(Path repoRoot) {
            return currentHead;
        }

        @Override
        public String resolveCommit(Path repoRoot, String ref) {
            return remoteCommit;
        }

        @Override
        public boolean isAncestor(Path repoRoot, String ancestor, String descendant) {
            return false;
        }

        @Override
        public boolean isWorktreeClean(Path repoRoot) {
            return worktreeCleanByRoot.getOrDefault(repoRoot, worktreeClean);
        }

        @Override
        public void fetch(Path repoRoot, String privateKey) {
            this.fetchCallCount += 1;
            this.fetchOrder = ++commandOrder;
        }

        @Override
        public void checkoutTrackingBranch(Path repoRoot, String branch, String privateKey) {
        }

        @Override
        public void pullFastForward(Path repoRoot, String branch, String privateKey) {
            this.pulledBranch = branch;
        }

        @Override
        public void mergeBranch(Path repoRoot, String branch, String privateKey, GitCommitIdentity identity) {
            this.lastMergeIdentity = identity;
            this.mergedRepoRoot = repoRoot;
            this.mergedBranch = branch;
            if (failMergeWithConflict) {
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "合并冲突", Map.of());
            }
        }

        @Override
        public boolean isMergeInProgress(Path repoRoot) {
            return mergeInProgress;
        }

        @Override
        public List<String> conflictPaths(Path repoRoot) {
            return conflictFiles;
        }

        @Override
        public void abortMerge(Path repoRoot) {
            this.abortedMergeRepoRoot = repoRoot;
        }

        @Override
        public void resetHardToCommit(Path repoRoot, String commitHash) {
            this.resetCommit = commitHash;
        }

        @Override
        public boolean isGitRepository(Path repoRoot) {
            return Files.isDirectory(repoRoot.resolve(".git"));
        }

        @Override
        public void createWorktree(Path repoRoot, Path worktreeRoot, String branch, String privateKey) {
            this.worktreeRoot = worktreeRoot;
            this.worktreeBranch = branch;
        }

        @Override
        public void createWorktreeReusingBranch(Path repoRoot, Path worktreeRoot, String branch, String privateKey) {
            createWorktree(repoRoot, worktreeRoot, branch, privateKey);
            try {
                Files.createDirectories(worktreeRoot.resolve(".git"));
                Files.createDirectories(worktreeRoot.resolve("opencode"));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void stageAll(Path repoRoot, String privateKey) {
            this.stagedAllCallCount += 1;
            this.privateKeyUsed = privateKey;
        }

        @Override
        public String statusPorcelain(Path repoRoot) {
            return stagedAfterAdd;
        }

        @Override
        public String statusPorcelain(Path repoRoot, String pathspec) {
            this.lastStatusPathspec = pathspec;
            return statusByPathspec.getOrDefault(pathspec, stagedAfterAdd);
        }

        @Override
        public String diff(Path repoRoot, String file, boolean staged) {
            this.diffFiles.add(file);
            return diffByFileAndStage.getOrDefault(file + "|" + staged, diffByFile.getOrDefault(file, ""));
        }

        @Override
        public void stageFiles(Path repoRoot, List<String> files, String privateKey) {
            this.stagedFiles = List.copyOf(files);
            this.privateKeyUsed = privateKey;
        }

        @Override
        public void discardFiles(Path repoRoot, List<String> files, String privateKey) {
            this.discardedFiles = List.copyOf(files);
            this.privateKeyUsed = privateKey;
        }

        @Override
        public void commitStaged(Path repoRoot, String message, String privateKey, GitCommitIdentity identity) {
            this.lastCommitIdentity = identity;
            this.lastCommitMessage = message;
            this.privateKeyUsed = privateKey;
            // 模拟 commit 后 commit 前进一格
            headHistory.add(currentHead);
            currentHead = "commit_after_update_and_push";
        }

        @Override
        public void push(Path repoRoot, String branch, boolean force, String privateKey) {
            this.pushedBranch = branch;
            this.pushedForce = force;
            this.privateKeyUsed = privateKey;
        }


        @Override
        public void pushRef(Path repoRoot, String sourceBranch, String targetBranch, String privateKey) {
            this.pushedBranch = sourceBranch + ":" + targetBranch;
            this.pushedForce = false;
            this.privateKeyUsed = privateKey;
        }
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

    private static final class RecordingAgentConfigProgressSink implements AgentConfigProgressSink {
        private final List<AgentConfigProgressEvent> events = new ArrayList<>();

        @Override
        public void publish(AgentConfigProgressEvent event) {
            events.add(event);
        }
    }

    private static final class InMemoryAgentConfigRepository implements AgentConfigRepository {
        private final Map<String, AgentConfigOperation> operations = new LinkedHashMap<>();
        private final Map<String, AgentConfigWorktree> worktrees = new LinkedHashMap<>();

        @Override
        public AgentConfigOperation saveOperation(AgentConfigOperation operation) {
            operations.put(operation.operationId(), operation);
            return operation;
        }

        @Override
        public Optional<AgentConfigOperation> findOperation(String operationId) {
            return Optional.ofNullable(operations.get(operationId));
        }

        @Override
        public AgentConfigWorktree saveWorktree(AgentConfigWorktree worktree) {
            worktrees.put(worktree.worktreeId(), worktree);
            return worktree;
        }

        @Override
        public Optional<AgentConfigWorktree> findWorktree(String worktreeId) {
            return Optional.ofNullable(worktrees.get(worktreeId));
        }

        @Override
        public List<AgentConfigWorktree> findWorktrees(AgentConfigScope scope, WorkspaceId workspaceId, UserId createdBy) {
            return worktrees.values().stream()
                    .filter(worktree -> worktree.scope() == scope)
                    .filter(worktree -> workspaceId == null || workspaceId.equals(worktree.workspaceId()))
                    .filter(worktree -> createdBy == null || createdBy.equals(worktree.createdBy()))
                    .toList();
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {

        @Override
        public void save(User user) {
        }

        @Override
        public Optional<User> findByUserId(UserId userId) {
            if (ADMIN.equals(userId)) {
                return Optional.of(new User(
                        userId,
                        "AUTH_ADMIN",
                        "admin",
                        "hash",
                        "org",
                        "rd",
                        "dept",
                        UserStatus.ACTIVE,
                        NOW,
                        NOW));
            }
            if (new UserId("usr_alice").equals(userId)) {
                return Optional.of(new User(
                        userId,
                        "AUTH_ALICE",
                        "alice",
                        "hash",
                        "org",
                        "rd",
                        "dept",
                        UserStatus.ACTIVE,
                        NOW,
                        NOW));
            }
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUnifiedAuthId(String unifiedAuthId) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public com.enterprise.testagent.common.pagination.PageResponse<User> findPage(
                String keyword,
                com.enterprise.testagent.common.pagination.PageRequest pageRequest) {
            return new com.enterprise.testagent.common.pagination.PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }

        @Override
        public boolean existsByUsername(String username) {
            return false;
        }

        @Override
        public boolean existsByUnifiedAuthId(String unifiedAuthId) {
            return false;
        }
    }
}
