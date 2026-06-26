package com.icbc.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.git.GitWorkspaceService;
import com.icbc.testagent.common.git.SshKeyCryptoService;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.AgentConfigOperation;
import com.icbc.testagent.domain.configuration.AgentConfigRepository;
import com.icbc.testagent.domain.configuration.AgentConfigScope;
import com.icbc.testagent.domain.configuration.AgentConfigWorktree;
import com.icbc.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
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
                .endsWith("/.config/opencode/agents");
    }

    @Test
    void publicDirectWriteIsRejectedWhenGitUrlIsUnconfigured() {
        AgentConfigApplicationService service = service(Map.of(
                "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()));

        assertThatThrownBy(() -> service.writePublicAgentFile("review.md", "review", null))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("公共 Agent Git 地址未配置");
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
    void publicWorktreeNameAppendsCurrentDateAndCreatesGitWorktree() throws Exception {
        Files.createDirectories(root.resolve(".config/.git"));
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

        assertThat(response.worktreeName()).isEqualTo("change-agent-md-20260626");
        assertThat(response.rootPath().replace('\\', '/'))
                .endsWith("/.configdev/change-agent-md-20260626");
        assertThat(git.worktreeBranch).isEqualTo("change-agent-md-20260626");
        assertThat(git.worktreeRoot.toString().replace('\\', '/'))
                .endsWith("/.configdev/change-agent-md-20260626");
        assertThat(agentConfigs.findWorktree(response.worktreeId()))
                .map(AgentConfigWorktree::status)
                .contains(AgentConfigWorktreeStatus.ACTIVE);
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

        List<FileTreeEntryResponse> entries = service.listWorkspaceAgentFiles("wrk_project", "", null);
        service.writeWorkspaceAgentFile("wrk_project", "new.md", "new agent", null);

        assertThat(entries).extracting(FileTreeEntryResponse::name).contains("review.md");
        assertThat(Files.readString(workspaceRoot.resolve(".opencode/agents/new.md"))).isEqualTo("new agent");
        assertThat(Files.exists(workspaceRoot.resolve(".opencode/agent/new.md"))).isFalse();
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
        CommonParameterRepository commonParameters = commonParameters(parameters);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        when(configuration.findSshKeys(eq(ADMIN))).thenReturn(List.of(encryptedSshKey()));
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        workspace.ifPresent(value -> when(workspaces.findById(value.workspaceId())).thenReturn(Optional.of(value)));
        return new AgentConfigApplicationService(
                commonParameters,
                configuration,
                workspaces,
                agentConfigs,
                new GitRemoteService(),
                git,
                new SshKeyCryptoService(aesKey()),
                new WorkspaceFileService(),
                new WorkspaceServerIdentity("linux-1"),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new RecordingAgentConfigProgressSink());
    }

    private CommonParameterRepository commonParameters(Map<String, String> parameters) {
        return (englishName, platform) -> Optional.ofNullable(parameters.get(englishName))
                .map(value -> new CommonParameter(
                        "param_" + englishName.toLowerCase(),
                        englishName,
                        englishName,
                        value,
                        platform,
                        NOW,
                        NOW));
    }

    private UserSshKey encryptedSshKey() {
        SshKeyCryptoService crypto = new SshKeyCryptoService(aesKey());
        SshKeyCryptoService.EncryptedPrivateKey encrypted = crypto.encrypt(PRIVATE_KEY);
        return new UserSshKey(
                new SshKeyId("ssh_admin"),
                ADMIN,
                "admin",
                crypto.fingerprint(PRIVATE_KEY),
                encrypted.encryptedPrivateKey(),
                encrypted.encryptionNonce(),
                NOW);
    }

    private static String aesKey() {
        return java.util.Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());
    }

    private static final class RecordingGitWorkspaceService extends GitWorkspaceService {
        private String clonedUrl;
        private String clonedBranch;
        private String privateKeyUsed;
        private String worktreeBranch;
        private Path worktreeRoot;

        @Override
        public void cloneBranch(String gitUrl, String branch, Path repoRoot, String privateKey) {
            this.clonedUrl = gitUrl;
            this.clonedBranch = branch;
            this.privateKeyUsed = privateKey;
        }

        @Override
        public String originUrl(Path repoRoot) {
            return "git@gitee.com:test/agent-config.git";
        }

        @Override
        public String currentBranch(Path repoRoot) {
            return "main";
        }

        @Override
        public String headCommit(Path repoRoot) {
            return "commit_base";
        }

        @Override
        public boolean isWorktreeClean(Path repoRoot) {
            return true;
        }

        @Override
        public void fetch(Path repoRoot, String privateKey) {
        }

        @Override
        public void checkoutTrackingBranch(Path repoRoot, String branch, String privateKey) {
        }

        @Override
        public void pullFastForward(Path repoRoot, String branch, String privateKey) {
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
}
