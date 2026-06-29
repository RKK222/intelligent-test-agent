package com.icbc.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.git.GitWorkspaceService;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.AgentConfigOperation;
import com.icbc.testagent.domain.configuration.AgentConfigRepository;
import com.icbc.testagent.domain.configuration.AgentConfigScope;
import com.icbc.testagent.domain.configuration.AgentConfigWorktree;
import com.icbc.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.user.UserStatus;
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
    void listPublicAgentFilesReturnsEmptyAndDoesNotCreateDirectoryWhenUninitialized() {
        // 公共配置已启用（git url 已配置）但尚未 clone 初始化时，浏览应返回空列表，不得自动创建目录。
        AgentConfigApplicationService service = service(Map.of(
                "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()));

        List<FileTreeEntryResponse> entries = service.listPublicAgentFiles("", null);

        assertThat(entries).isEmpty();
        assertThat(Files.exists(root.resolve(".config"))).isFalse();
        assertThat(Files.exists(root.resolve(".config/opencode/agents"))).isFalse();
    }

    @Test
    void publicDirectWriteIsRejectedWhenGitUrlIsUnconfigured() {
        AgentConfigApplicationService service = service(Map.of(
                "OPENCODE_PUBLIC_AGENT_GIT_URL", "UNCONFIGURED",
                "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()));

        assertThatThrownBy(() -> service.writePublicAgentFile("review.md", "review", null))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("请先在“系统管理 → 通用参数管理”中配置 OPENCODE_PUBLIC_AGENT_GIT_URL");
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

        assertThat(response.worktreeName()).isEqualTo("change-agent-md-20260626");
        assertThat(response.rootPath().replace('\\', '/'))
                .endsWith("/.configdev/change-agent-md-20260626");
        assertThat(git.worktreeBranch).isEqualTo("change-agent-md-20260626");
        assertThat(git.worktreeRoot.toString().replace('\\', '/'))
                .endsWith("/.configdev/change-agent-md-20260626");
        assertThat(agentConfigs.findWorktree(response.worktreeId()))
                .map(AgentConfigWorktree::status)
                .contains(AgentConfigWorktreeStatus.ACTIVE);
        assertThat(agentConfigs.findWorktree(response.worktreeId()))
                .map(AgentConfigWorktree::linuxServerId)
                .contains("linux-1");
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

        assertThat(entries).extracting(FileTreeEntryResponse::name).contains("review.md");
        assertThat(Files.readString(workspaceRoot.resolve(".opencode/agents/new.md"))).isEqualTo("new agent");
        assertThat(Files.exists(workspaceRoot.resolve(".opencode/agent/new.md"))).isFalse();
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
    void listPublicWorktreesFiltersActiveWorktreesByServerAndIncludesCreatorName() {
        InMemoryAgentConfigRepository agentConfigs = new InMemoryAgentConfigRepository();
        agentConfigs.saveWorktree(new AgentConfigWorktree(
                "agw_public_new",
                AgentConfigScope.PUBLIC,
                null,
                "linux-2",
                "new-change",
                "new-change",
                root.resolve("worktrees/new-change").toString(),
                new UserId("usr_alice"),
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
        AgentConfigApplicationService service = service(
                Map.of(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL", "git@gitee.com:test/agent-config.git",
                        "OPENCODE_PUBLIC_CONFIG_GIT_ROOT", root.resolve(".config").toString(),
                        "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", root.resolve(".configdev").toString()),
                agentConfigs,
                new RecordingGitWorkspaceService(),
                new RecordingBroadcastPublisher());

        List<AgentConfigResponses.AgentConfigWorktreeOptionResponse> options = service.listPublicWorktrees("linux-2");

        assertThat(options).extracting(AgentConfigResponses.AgentConfigWorktreeOptionResponse::worktreeId)
                .containsExactly("agw_public_new", "agw_public_old");
        assertThat(options.get(0).createdByUserId()).isEqualTo("usr_alice");
        assertThat(options.get(0).createdByUsername()).isEqualTo("alice");
        assertThat(options.get(1).createdByUserId()).isEqualTo("usr_missing");
        assertThat(options.get(1).createdByUsername()).isNull();
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
                new GitRemoteService(),
                git,
                sshKeyFixtures.encryptionService(),
                new WorkspaceFileService(),
                new WorkspaceServerIdentity("linux-1"),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new RecordingAgentConfigProgressSink());
    }

    private CommonParameterValues commonParameters(Map<String, String> parameters) {
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

    private UserSshKey encryptedSshKey() {
        return sshKeyFixtures.encryptedSshKey(new SshKeyId("ssh_admin"), ADMIN, "admin", PRIVATE_KEY, NOW);
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

    private static final class InMemoryUserRepository implements UserRepository {

        @Override
        public void save(User user) {
        }

        @Override
        public Optional<User> findByUserId(UserId userId) {
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
        public com.icbc.testagent.common.pagination.PageResponse<User> findPage(
                String keyword,
                com.icbc.testagent.common.pagination.PageRequest pageRequest) {
            return new com.icbc.testagent.common.pagination.PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
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
