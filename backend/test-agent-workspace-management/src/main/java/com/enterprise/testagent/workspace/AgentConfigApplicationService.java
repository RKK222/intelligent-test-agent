package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitCommandExecutor;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.GitWorkspaceService.GitDiffFile;
import com.enterprise.testagent.common.git.GitWorkspaceService.GitStatusEntry;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.AgentConfigOperation;
import com.enterprise.testagent.domain.configuration.AgentConfigOperationStatus;
import com.enterprise.testagent.domain.configuration.AgentConfigOperationStep;
import com.enterprise.testagent.domain.configuration.AgentConfigRepository;
import com.enterprise.testagent.domain.configuration.AgentConfigScope;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktree;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.enterprise.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutPreparation;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloadResult;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloader;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Agent 配置应用服务：隔离公共级/工作空间级文件根目录、Git 操作和进度发布。
 */
@Service
public class AgentConfigApplicationService implements ServerBroadcastHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentConfigApplicationService.class);

    public static final String PUBLIC_SYNC_EVENT = "agent-config.public-sync-requested";

    private static final String PARAM_PUBLIC_AGENT_GIT_URL = "OPENCODE_PUBLIC_AGENT_GIT_URL";
    private static final String PARAM_PUBLIC_CONFIG_GIT_ROOT = "OPENCODE_PUBLIC_CONFIG_GIT_ROOT";
    private static final String PARAM_PUBLIC_CONFIG_DIR = "OPENCODE_PUBLIC_CONFIG_DIR";
    private static final String PARAM_PUBLIC_CONFIG_WORKTREE_ROOT = "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT";
    private static final String PARAM_PERSONAL_WORKTREE_ROOT = "OPENCODE_PERSONAL_WORKTREE_ROOT";
    private static final String UNCONFIGURED = "UNCONFIGURED";
    private static final String PUBLIC_AGENT_RELATIVE_ROOT = "opencode";
    private static final String PUBLIC_AGENT_LEGACY_RELATIVE_ROOT = "opencode/agents";
    private static final String WORKSPACE_AGENT_RELATIVE_ROOT = ".opencode";
    private static final String WORKSPACE_AGENT_LEGACY_RELATIVE_ROOT = ".opencode/agents";
    private static final long MAX_CONFLICT_FILE_BYTES = 1024L * 1024L;
    private static final Duration PREPARATION_RECOVERY_DELAY = Duration.ofMinutes(3);
    private static final Duration PREPARATION_ABORT_DELAY = Duration.ofMinutes(5);
    /** 本地最终 commit 尚未产生；崩溃恢复不能把当前远端 HEAD 误当作本次发布结果。 */
    private static final String PENDING_EXPECTED_COMMIT = "PENDING_LOCAL_COMMIT";
    private static final Pattern OPERATION_ID_PATTERN = Pattern.compile("^aco_[A-Za-z0-9_-]{8,128}$");
    private static final Pattern WORKTREE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final DateTimeFormatter WORKTREE_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final ServerBroadcastPublisher NOOP_BROADCAST = new ServerBroadcastPublisher() {
        @Override
        public String instanceId() {
            return "noop";
        }

        @Override
        public void publish(ServerBroadcastEvent event) {
        }
    };

    private final CommonParameterValues commonParameterValues;
    private final ConfigurationManagementRepository configurationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final UserRepository userRepository;
    private final GitRemoteService gitRemoteService;
    private final GitWorkspaceService gitWorkspaceService;
    private final GitPublishWorkflow gitPublishWorkflow;
    private final SshKeyEncryptionService sshKeyEncryptionService;
    private final WorkspaceFileService fileService;
    private final ManagedWorkspacePathResolver pathResolver;
    private final WorkspaceServerIdentity serverIdentity;
    private final ServerBroadcastPublisher broadcastPublisher;
    private final Clock clock;
    private final AgentConfigProgressSink progressSink;
    private final CodeRepositoryDeploymentMode publicGitDeploymentMode;
    private final Map<String, Object> publicWorktreeLocks = new ConcurrentHashMap<>();
    private ManagedWorkspaceApplicationService managedWorkspaceApplicationService;
    private PublicAgentConfigRolloutCoordinator publicConfigRolloutCoordinator;
    private PersonalAgentConfigRuntimeReloader personalRuntimeReloader;

    /** 应用配置发布复用托管 feature 版本的 HEAD 更新与广播链路。 */
    @Autowired
    void setManagedWorkspaceApplicationService(ManagedWorkspaceApplicationService service) {
        this.managedWorkspaceApplicationService = Objects.requireNonNull(service, "service must not be null");
    }

    /** 发布排空由 opencode-runtime 模块实现，方法注入避免扩张已有测试兼容构造器。 */
    @Autowired
    void setPublicConfigRolloutCoordinator(PublicAgentConfigRolloutCoordinator coordinator) {
        this.publicConfigRolloutCoordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
    }

    /** 个人保存热加载由 opencode-runtime 实现，工作区模块只传递已校验的公共 worktree 配置根。 */
    @Autowired
    void setPersonalRuntimeReloader(PersonalAgentConfigRuntimeReloader reloader) {
        this.personalRuntimeReloader = Objects.requireNonNull(reloader, "reloader must not be null");
    }

    /**
     * Spring 构造器：进度 Sink 由 API 模块提供，缺失时降级为 NOOP，便于模块级测试。
     */
    @Autowired
    public AgentConfigApplicationService(
            CommonParameterValues commonParameterValues,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceRepository workspaceRepository,
            AgentConfigRepository agentConfigRepository,
            UserRepository userRepository,
            WorkspaceFileService fileService,
            ManagedWorkspacePathResolver pathResolver,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            ObjectProvider<AgentConfigProgressSink> progressSinkProvider,
            SshKeyEncryptionService sshKeyEncryptionService,
            @Value("${test-agent.deployment.mode:external}") String deploymentMode) {
        this(
                commonParameterValues,
                configurationRepository,
                workspaceRepository,
                agentConfigRepository,
                userRepository,
                new GitRemoteService(),
                new GitWorkspaceService(),
                sshKeyEncryptionService,
                fileService,
                pathResolver,
                serverIdentity,
                broadcastPublisher,
                Clock.systemUTC(),
                Optional.ofNullable(progressSinkProvider.getIfAvailable()).orElse(AgentConfigProgressSink.NOOP),
                deploymentMode);
    }

    AgentConfigApplicationService(
            CommonParameterValues commonParameterValues,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceRepository workspaceRepository,
            AgentConfigRepository agentConfigRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceFileService fileService,
            ManagedWorkspacePathResolver pathResolver,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            Clock clock,
            AgentConfigProgressSink progressSink) {
        this(
                commonParameterValues,
                configurationRepository,
                workspaceRepository,
                agentConfigRepository,
                userRepository,
                gitRemoteService,
                gitWorkspaceService,
                sshKeyEncryptionService,
                fileService,
                pathResolver,
                serverIdentity,
                broadcastPublisher,
                clock,
                progressSink,
                CodeRepositoryDeploymentMode.EXTERNAL.value());
    }

    AgentConfigApplicationService(
            CommonParameterValues commonParameterValues,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceRepository workspaceRepository,
            AgentConfigRepository agentConfigRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceFileService fileService,
            ManagedWorkspacePathResolver pathResolver,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            Clock clock,
            AgentConfigProgressSink progressSink,
            String deploymentMode) {
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
        this.configurationRepository = Objects.requireNonNull(configurationRepository, "configurationRepository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.agentConfigRepository = Objects.requireNonNull(agentConfigRepository, "agentConfigRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.gitRemoteService = Objects.requireNonNull(gitRemoteService, "gitRemoteService must not be null");
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService, "gitWorkspaceService must not be null");
        this.gitPublishWorkflow = new GitPublishWorkflow(this.gitWorkspaceService);
        this.sshKeyEncryptionService = Objects.requireNonNull(sshKeyEncryptionService, "sshKeyEncryptionService must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.broadcastPublisher = broadcastPublisher == null ? NOOP_BROADCAST : broadcastPublisher;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.progressSink = progressSink == null ? AgentConfigProgressSink.NOOP : progressSink;
        this.publicGitDeploymentMode = CodeRepositoryDeploymentMode.fromDeploymentProperty(deploymentMode);
    }

    AgentConfigApplicationService(
            CommonParameterValues commonParameterValues,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceRepository workspaceRepository,
            AgentConfigRepository agentConfigRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            Clock clock,
            AgentConfigProgressSink progressSink) {
        this(
                commonParameterValues,
                configurationRepository,
                workspaceRepository,
                agentConfigRepository,
                userRepository,
                gitRemoteService,
                gitWorkspaceService,
                sshKeyEncryptionService,
                fileService,
                ManagedWorkspacePathResolver.legacyOnly(),
                serverIdentity,
                broadcastPublisher,
                clock,
                progressSink,
                CodeRepositoryDeploymentMode.EXTERNAL.value());
    }

    public AgentConfigResponses.AgentConfigStatusResponse publicStatus(boolean superAdmin) {
        return publicStatus(superAdmin, null);
    }

    public AgentConfigResponses.AgentConfigStatusResponse publicStatus(boolean superAdmin, UserId userId) {
        PublicConfig config = publicConfig(userId);
        boolean enabled = config.enabled();
        String currentBranch = null;
        String commitHash = null;
        if (enabled && gitWorkspaceService.isGitRepository(config.gitRoot())) {
            currentBranch = gitWorkspaceService.currentBranch(config.gitRoot());
            commitHash = gitWorkspaceService.headCommit(config.gitRoot());
        }
        return new AgentConfigResponses.AgentConfigStatusResponse(
                AgentConfigScope.PUBLIC.name(),
                enabled,
                superAdmin && enabled,
                config.gitUrl(),
                config.gitRoot().toString(),
                publicStandardAgentRoot(config.gitRoot()).toString(),
                currentBranch,
                commitHash);
    }

    public AgentConfigResponses.AgentConfigStatusResponse workspaceStatus(String workspaceId, boolean superAdmin) {
        Workspace workspace = existingWorkspace(workspaceId);
        Path root = workspaceRoot(workspace);
        boolean gitRepository = gitWorkspaceService.isGitRepository(root);
        return new AgentConfigResponses.AgentConfigStatusResponse(
                AgentConfigScope.WORKSPACE.name(),
                true,
                superAdmin,
                null,
                root.toString(),
                workspaceStandardAgentRoot(root).toString(),
                gitRepository ? gitWorkspaceService.currentBranch(root) : null,
                gitRepository ? gitWorkspaceService.headCommit(root) : null);
    }

    public List<String> publicBranches(UserId userId) {
        PublicConfig config = requireEnabledPublicConfig(userId);
        String gitCommandUrl = publicGitCommandUrl(config, userId);
        long startedAt = System.nanoTime();
        LOGGER.info(
                "event=agent_config_public_branches_start linuxServerId={} userId={} gitUrl={} gitRoot={}",
                serverIdentity.linuxServerId(),
                userId.value(),
                safeGitUrlForLog(gitCommandUrl),
                config.gitRoot());
        try {
            List<String> branches = gitRemoteService.listBranches(gitCommandUrl, decryptSingleSshKey(userId));
            LOGGER.info(
                    "event=agent_config_public_branches_success linuxServerId={} userId={} branchCount={} durationMs={}",
                    serverIdentity.linuxServerId(),
                    userId.value(),
                    branches.size(),
                    elapsedMillis(startedAt));
            return branches;
        } catch (PlatformException exception) {
            LOGGER.warn(
                    "event=agent_config_public_branches_failed linuxServerId={} userId={} errorCode={} durationMs={} gitUrl={} message={}",
                    serverIdentity.linuxServerId(),
                    userId.value(),
                    exception.errorCode(),
                    elapsedMillis(startedAt),
                    safeGitUrlForLog(gitCommandUrl),
                    exception.getMessage());
            throw exception;
        }
    }

    public AgentConfigResponses.PublicRepositoryStatusResponse localPublicRepositoryStatus() {
        return localPublicRepositoryStatus(null);
    }

    public AgentConfigResponses.PublicRepositoryStatusResponse localPublicRepositoryStatus(UserId userId) {
        PublicConfig config = publicConfig(userId);
        return publicRepositoryStatus(config);
    }

    public AgentConfigResponses.PublicRepositoryStatusResponse initializeLocalPublicRepository(
            String branch,
            String operationId,
            UserId userId,
            String traceId) {
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "initialize-repository", normalizedBranch, traceId);
        try {
            PublicConfig config = requireEnabledPublicConfig(userId);
            String privateKey = decryptSingleSshKey(userId);
            LOGGER.info(
                    "event=agent_config_public_repository_initialize_start linuxServerId={} userId={} branch={} gitUrl={} gitRoot={} configDir={}",
                    serverIdentity.linuxServerId(),
                    userId.value(),
                    normalizedBranch,
                    safeGitUrlForLog(config.gitUrl()),
                    config.gitRoot(),
                    config.configDir());
            progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
            ensurePublicRepositoryReady(config, normalizedBranch, privateKey);
            requireInitializedConfigDirectory(config);
            String commitHash = gitWorkspaceService.headCommit(config.gitRoot());
            progress.succeeded(commitHash);
            LOGGER.info(
                    "event=agent_config_public_repository_initialize_success linuxServerId={} userId={} branch={} gitRoot={} commitHash={}",
                    serverIdentity.linuxServerId(),
                    userId.value(),
                    normalizedBranch,
                    config.gitRoot(),
                    commitHash);
            return publicRepositoryStatus(config);
        } catch (PlatformException exception) {
            LOGGER.warn(
                    "event=agent_config_public_repository_initialize_failed linuxServerId={} userId={} branch={} errorCode={} message={}",
                    serverIdentity.linuxServerId(),
                    userId.value(),
                    normalizedBranch,
                    exception.errorCode(),
                    exception.getMessage());
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn(
                    "event=agent_config_public_repository_initialize_failed linuxServerId={} userId={} branch={} errorCode={} message={}",
                    serverIdentity.linuxServerId(),
                    userId.value(),
                    normalizedBranch,
                    ErrorCode.INTERNAL_ERROR,
                    exception.toString());
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "初始化公共 Agent 配置仓库失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "初始化公共 Agent 配置仓库失败", Map.of(), exception);
        }
    }

    public AgentConfigResponses.AgentConfigOperationResponse updatePublicConfig(
            String branch,
            String operationId,
            UserId userId,
            String traceId) {
        return updatePublicConfig(branch, operationId, false, userId, traceId);
    }

    /**
     * 更新公共配置；只有调用方明确确认时才放弃受控仓库中的已跟踪文件修改。
     */
    public AgentConfigResponses.AgentConfigOperationResponse updatePublicConfig(
            String branch,
            String operationId,
            boolean discardLocalChanges,
            UserId userId,
            String traceId) {
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "update", normalizedBranch, traceId);
        String rolloutId = null;
        try {
            PublicConfig config = requireEnabledPublicConfig(userId);
            String privateKey = decryptSingleSshKey(userId);
            progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
            String previousCommitHash = existingRepositoryHead(config.gitRoot());
            // “拉取”不仅更新服务器共享运行副本，还必须先把远端公共分支合并到当前超管的稳定个人
            // worktree；否则前端继续从个人 worktree 读取时会看到旧内容，却收到“已拉取”的误导结果。
            progress.step(AgentConfigOperationStep.MERGING);
            syncOwnedPublicWorktreeFromRemote(
                    config,
                    normalizedBranch,
                    discardLocalChanges,
                    userId,
                    privateKey);
            validatePublicRuntimeRepositoryBeforeRollout(config, discardLocalChanges);
            // 个人 worktree 更新不影响运行实例；只有它成功后，才在共享运行副本 clone/pull 前建立门禁。
            rolloutId = preparePublicConfigRollout(
                    normalizedBranch, null, previousCommitHash, userId, traceId);
            ensurePublicRepositoryReady(config, normalizedBranch, privateKey, discardLocalChanges);
            String commitHash = gitWorkspaceService.headCommit(config.gitRoot());
            activatePublicConfigRollout(rolloutId, commitHash);
            progress.step(AgentConfigOperationStep.BROADCASTING);
            broadcastPublicSync(normalizedBranch, commitHash, "update", rolloutId, traceId);
            return progress.succeeded(commitHash);
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "公共 Agent 配置更新失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "公共 Agent 配置更新失败", Map.of(), exception);
        }
    }

    /**
     * 将远端公共分支合并到当前用户在本服务器上的稳定个人 worktree。
     *
     * <p>公共文件读写已经以个人 worktree 为事实源，因此服务器“拉取”必须同步这棵 worktree。
     * 有未提交改动时默认拒绝覆盖；只有调用方明确确认放弃本地已跟踪改动后才 reset。
     * 合并冲突保留在个人 worktree 中，供既有三方冲突界面继续处理。
     */
    private void syncOwnedPublicWorktreeFromRemote(
            PublicConfig config,
            String branch,
            boolean discardLocalChanges,
            UserId userId,
            String privateKey) {
        Optional<AgentConfigWorktree> activeWorktree = agentConfigRepository.findWorktrees(
                AgentConfigScope.PUBLIC,
                null,
                userId,
                serverIdentity.linuxServerId(),
                AgentConfigWorktreeStatus.ACTIVE).stream()
                .filter(worktree -> isReusablePublicWorktree(worktree, userId))
                .findFirst();
        if (activeWorktree.isEmpty()) {
            return;
        }

        Path repoRoot = Path.of(activeWorktree.get().rootPath());
        ensureExistingRepositoryReadyForSync(
                repoRoot,
                config,
                discardLocalChanges,
                PublicRepositorySyncTarget.PERSONAL_WORKTREE);
        if (gitWorkspaceService.isMergeInProgress(repoRoot)) {
            List<String> conflictFiles = gitWorkspaceService.conflictPaths(repoRoot);
            throw publicMergeConflict(
                    conflictFiles,
                    conflictFiles.isEmpty()
                            ? "公共 Agent 个人 worktree 存在未完成的 Git 合并，请先完成或取消合并"
                            : "公共 Agent 个人 worktree 仍有未解决的 Git 合并冲突");
        }
        gitWorkspaceService.fetch(repoRoot, privateKey);
        try {
            gitWorkspaceService.mergeBranch(
                    repoRoot,
                    "origin/" + branch,
                    privateKey,
                    gitCommitIdentity(userId));
        } catch (PlatformException mergeException) {
            List<String> conflictFiles = gitWorkspaceService.conflictPaths(repoRoot);
            if (!conflictFiles.isEmpty()) {
                throw publicMergeConflict(
                        conflictFiles,
                        "公共 Agent 个人 worktree 与远端分支存在冲突，请解决后重新拉取");
            }
            throw mergeException;
        }
    }

    /**
     * "更新公共配置 + 提交并推送"复合操作：fetch 远端最新提交后提交本地变更，合并远端分支，再 push 并广播同步。
     * <p>
     * 工作区有未提交修改时按以下语义处理：
     * <ul>
     *   <li>discardLocalChanges=true：先 {@code git reset --hard HEAD} 放弃受控仓库中的已跟踪修改，再 fetch+commit+merge+push；</li>
     *   <li>discardLocalChanges=false：保留所有本地修改并提交，再合并远端分支；</li>
     *   <li>工作区无变更时不产生新 commit，但仍 fetch+merge+push，确保已有未推送本地提交和远端新增提交正确汇合。</li>
     * </ul>
     * <p>
     * 失败时统一抛 {@link PlatformException}，前端按统一错误格式处理。
     */
    public AgentConfigResponses.AgentConfigOperationResponse updatePublicConfigAndPush(
            String branch,
            String commitMessage,
            String operationId,
            boolean discardLocalChanges,
            UserId userId,
            String traceId) {
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        String normalizedMessage = requireText(commitMessage, "提交说明不能为空", "commitMessage");
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "update-and-push", normalizedBranch, traceId);
        GitCommandExecutor.startRecording(command -> progress.command(command, traceId));
        String rolloutId = null;
        try {
            PublicConfig config = requireEnabledPublicConfig(userId);
            String privateKey = decryptSingleSshKey(userId);
            GitCommitIdentity commitIdentity = gitCommitIdentity(userId);
            Path repoRoot = config.gitRoot();
            if (!gitWorkspaceService.isGitRepository(repoRoot)) {
                throw publicRepositoryUninitialized(repoRoot);
            }
            String previousCommitHash = gitWorkspaceService.headCommit(repoRoot);
            // 内部部署的 origin 含当前管理员统一认证号；共享仓库可能由其他管理员初始化，
            // 每次联网操作前都必须刷新为本次操作人，避免“私钥正确但登录用户名仍是上一位管理员”。
            ensurePublicRepositoryOriginReady(repoRoot, config);
            // 共享仓库本身是运行副本；reset、commit、merge 之前必须先建立 PREPARING 全员闸门。
            rolloutId = preparePublicConfigRollout(
                    normalizedBranch, PENDING_EXPECTED_COMMIT, previousCommitHash, userId, traceId);
            // 可选：放弃受控仓库中的已跟踪修改（不删除未跟踪文件）。
            if (discardLocalChanges && !gitWorkspaceService.isWorktreeClean(repoRoot)) {
                gitWorkspaceService.resetHardToCommit(repoRoot, "HEAD");
            }
            boolean mergeInProgress = gitWorkspaceService.isMergeInProgress(repoRoot);
            if (mergeInProgress) {
                List<String> unresolved = gitWorkspaceService.conflictPaths(repoRoot);
                if (!unresolved.isEmpty()) {
                    throw publicMergeConflict(unresolved, "仍有未解决的公共 Agent 合并冲突");
                }
                progress.step(AgentConfigOperationStep.COMMITTING);
                gitWorkspaceService.commitStaged(repoRoot, normalizedMessage, privateKey, commitIdentity);
            } else {
                progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
                gitWorkspaceService.fetch(repoRoot, privateKey);
                progress.step(AgentConfigOperationStep.COMMITTING);
                gitWorkspaceService.stageAll(repoRoot, privateKey);
                if (!gitWorkspaceService.isWorktreeClean(repoRoot) || hasStagedChanges(repoRoot)) {
                    gitWorkspaceService.commitStaged(repoRoot, normalizedMessage, privateKey, commitIdentity);
                }
                progress.step(AgentConfigOperationStep.MERGING);
                try {
                    gitWorkspaceService.mergeBranch(repoRoot, "origin/" + normalizedBranch, privateKey, commitIdentity);
                } catch (PlatformException mergeException) {
                    List<String> conflictFiles = gitWorkspaceService.conflictPaths(repoRoot);
                    if (!conflictFiles.isEmpty()) {
                        throw publicMergeConflict(conflictFiles, "公共 Agent 合并远端分支产生冲突，请解决后重新提交并推送");
                    }
                    throw mergeException;
                }
            }
            progress.step(AgentConfigOperationStep.PUSHING);
            String commitHash = gitWorkspaceService.headCommit(repoRoot);
            recordExpectedPublicConfigCommit(rolloutId, commitHash);
            try {
                gitWorkspaceService.push(repoRoot, normalizedBranch, false, privateKey);
            } catch (PlatformException pushException) {
                handleUncertainPushFailure(
                        rolloutId,
                        repoRoot,
                        normalizedBranch,
                        commitHash,
                        privateKey,
                        repoRoot,
                        previousCommitHash,
                        pushException);
            }
            activatePublicConfigRollout(rolloutId, commitHash);
            progress.step(AgentConfigOperationStep.BROADCASTING);
            broadcastPublicSync(normalizedBranch, commitHash, "update-and-push", rolloutId, traceId);
            return progress.succeeded(commitHash);
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "公共 Agent 配置更新并推送失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "公共 Agent 配置更新并推送失败", Map.of(), exception);
        } finally {
            GitCommandExecutor.stopRecording();
        }
    }

    /**
     * 检查仓库是否还有未推送的本地提交：比较 porcelain 状态是否非空；空表示没有 staged/unstaged/untracked 变更。
     */
    private boolean hasStagedChanges(Path repoRoot) {
        String porcelain = gitWorkspaceService.statusPorcelain(repoRoot);
        return !porcelain.trim().isEmpty();
    }

    private PlatformException publicMergeConflict(List<String> conflictFiles, String message) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                message,
                Map.of("conflictFiles", List.copyOf(conflictFiles)));
    }

    public ManagedWorkspaceResponses.WorkspaceGitConflictResponse getPublicGitConflict(
            String path,
            String worktreeId,
            UserId userId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId, userId);
        String gitFile = publicGitFile(path);
        Set<Integer> stages = gitWorkspaceService.conflictStages(repoRoot, gitFile);
        if (stages.isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "文件当前不是未解决的 Git 冲突", Map.of("path", gitFile));
        }
        Path resultPath = repoRoot.resolve(gitFile).normalize();
        return new ManagedWorkspaceResponses.WorkspaceGitConflictResponse(
                gitFile,
                conflictRawStatus(repoRoot, gitFile),
                conflictStageContent(repoRoot, stages, 1, gitFile),
                conflictStageContent(repoRoot, stages, 2, gitFile),
                conflictStageContent(repoRoot, stages, 3, gitFile),
                readConflictWorkingContent(resultPath));
    }

    public List<String> publicGitConflictFiles(String worktreeId, UserId userId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId, userId);
        if (!gitWorkspaceService.isGitRepository(repoRoot)) {
            return List.of();
        }
        return gitWorkspaceService.conflictPaths(repoRoot);
    }

    public void resolvePublicGitConflict(String path, String resolution, String content, String worktreeId, UserId userId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId, userId);
        String gitFile = publicGitFile(path);
        Set<Integer> stages = gitWorkspaceService.conflictStages(repoRoot, gitFile);
        if (stages.isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "文件当前不是未解决的 Git 冲突", Map.of("path", gitFile));
        }
        String mode = requireText(resolution, "冲突解决方式不能为空", "resolution").toUpperCase(Locale.ROOT);
        String resolved = switch (mode) {
            case "CURRENT" -> conflictStageContent(repoRoot, stages, 2, gitFile);
            case "INCOMING" -> conflictStageContent(repoRoot, stages, 3, gitFile);
            case "BOTH" -> joinConflictSides(
                    conflictStageContent(repoRoot, stages, 2, gitFile),
                    conflictStageContent(repoRoot, stages, 3, gitFile));
            case "MANUAL" -> content;
            case "DELETE" -> null;
            default -> throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "不支持的冲突解决方式",
                    Map.of("resolution", mode));
        };
        if ("MANUAL".equals(mode) && content == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "手工解决内容不能为空", Map.of("field", "content"));
        }
        Path target = repoRoot.resolve(gitFile).normalize();
        try {
            if (resolved == null) {
                Files.deleteIfExists(target);
            } else {
                byte[] bytes = resolved.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_CONFLICT_FILE_BYTES) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR, "冲突文件超过 1MB 限制", Map.of("path", gitFile));
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, resolved, StandardCharsets.UTF_8);
            }
            gitWorkspaceService.stageFiles(repoRoot, List.of(gitFile), decryptSingleSshKey(userId));
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "解决公共 Agent Git 冲突失败", Map.of("path", gitFile), exception);
        }
    }

    public void abortPublicGitConflict(String worktreeId, UserId userId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId, userId);
        if (!gitWorkspaceService.isMergeInProgress(repoRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前没有可取消的 Git 合并", Map.of());
        }
        gitWorkspaceService.abortMerge(repoRoot, decryptSingleSshKey(userId));
    }

    public void resolveAllPublicGitConflicts(String resolution, String worktreeId, UserId userId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId, userId);
        if (!gitWorkspaceService.isMergeInProgress(repoRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前没有可解决的 Git 合并", Map.of());
        }
        if (gitWorkspaceService.conflictPaths(repoRoot).isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前没有未解决的 Git 冲突", Map.of());
        }
        String mode = requireText(resolution, "冲突解决方式不能为空", "resolution").toUpperCase(Locale.ROOT);
        GitWorkspaceService.ConflictResolutionSide side = switch (mode) {
            case "CURRENT" -> GitWorkspaceService.ConflictResolutionSide.CURRENT;
            case "INCOMING" -> GitWorkspaceService.ConflictResolutionSide.INCOMING;
            default -> throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "批量解决只支持 CURRENT 或 INCOMING",
                    Map.of("resolution", mode));
        };
        gitWorkspaceService.resolveAllConflicts(repoRoot, side, decryptSingleSshKey(userId));
    }

    public List<FileTreeEntryResponse> listPublicAgentFiles(String relativePath, String worktreeId, UserId userId) {
        Path agentRoot = publicAgentRootForRead(worktreeId, userId);
        // 公共配置目录须由管理员初始化（git clone）后才会存在；未初始化时返回空列表，不自动创建，
        // 避免浏览即静默建出 OPENCODE_PUBLIC_CONFIG_DIR 空壳。
        if (!Files.isDirectory(agentRoot)) {
            return List.of();
        }
        return fileService.listDirectory(agentRoot.toString(), relativePath);
    }

    public FileContentResponse readPublicAgentFile(String relativePath, String worktreeId, UserId userId) {
        return fileService.readContent(publicAgentRootForRead(worktreeId, userId).toString(), relativePath);
    }

    /** 公共 Agent 大文件按 UTF-8 字节偏移渐进只读预览。 */
    public FilePreviewChunkResponse readPublicAgentFilePreviewChunk(
            String relativePath,
            long offset,
            Long expectedSize,
            Long expectedLastModifiedMillis,
            String worktreeId,
            UserId userId) {
        return fileService.readContentChunk(
                publicAgentRootForRead(worktreeId, userId).toString(),
                relativePath,
                offset,
                expectedSize,
                expectedLastModifiedMillis);
    }

    public void writePublicAgentFile(String relativePath, String content, String worktreeId, UserId userId) {
        Path agentRoot = publicAgentRootForWrite(worktreeId, userId);
        ensureDirectory(agentRoot);
        fileService.writeContent(agentRoot.toString(), relativePath, content);
    }

    /** 公共 Agent 上传复用工作空间文件服务的 Base64、大小、重名和路径安全校验。 */
    public void uploadPublicAgentFile(String relativePath, String contentBase64, String worktreeId, UserId userId) {
        Path agentRoot = publicAgentRootForWrite(worktreeId, userId);
        ensureDirectory(agentRoot);
        fileService.uploadFile(agentRoot.toString(), relativePath, contentBase64);
    }

    /** 公共 Agent 分片上传绑定当前管理员个人 worktree，文件总大小不设应用级上限。 */
    public WorkspaceFileUpload beginPublicAgentFileUpload(
            String relativePath,
            long expectedBytes,
            String worktreeId,
            UserId userId) {
        Path agentRoot = publicAgentRootForWrite(worktreeId, userId);
        ensureDirectory(agentRoot);
        return fileService.beginUpload(agentRoot.toString(), relativePath, expectedBytes);
    }

    /** 公共 Agent 文件改名复用工作空间文件服务的同目录重命名与路径安全校验。 */
    public void renamePublicAgentFile(String relativePath, String name, String worktreeId, UserId userId) {
        Path agentRoot = publicAgentRootForWrite(worktreeId, userId);
        fileService.renameFile(agentRoot.toString(), relativePath, name);
    }

    /** 公共 Agent 文件或目录跨目录移动继续绑定当前管理员个人 worktree，并复用统一文件安全校验。 */
    public void movePublicAgentFile(String sourcePath, String targetPath, String worktreeId, UserId userId) {
        Path agentRoot = publicAgentRootForWrite(worktreeId, userId);
        fileService.moveFile(agentRoot.toString(), sourcePath, targetPath);
    }

    /** 公共 Agent 普通文件复制复用工作空间文件服务，不覆盖目标同名条目。 */
    public void copyPublicAgentFile(String sourcePath, String targetPath, String worktreeId, UserId userId) {
        Path agentRoot = publicAgentRootForWrite(worktreeId, userId);
        fileService.copyFile(agentRoot.toString(), sourcePath, targetPath);
    }

    /** 公共 Agent 文件和目录删除复用工作空间文件服务的路径归一化与递归删除保护。 */
    public void deletePublicAgentFile(String relativePath, String worktreeId, UserId userId) {
        Path agentRoot = publicAgentRootForWrite(worktreeId, userId);
        fileService.deleteFile(agentRoot.toString(), relativePath);
    }

    /**
     * 把当前管理员公共个人 worktree 的完整 opencode 配置热加载到本人进程。
     *
     * <p>worktree 所有权和服务器归属必须先在本模块确认；运行时模块只接收可信绝对路径，
     * 不参与 Git 分支、文件路由或权限判断。
     */
    public PersonalAgentConfigRuntimeReloadResult reloadPublicPersonalRuntime(
            String worktreeId,
            UserId userId,
            String traceId) {
        AgentConfigWorktree worktree = ownedPublicWorktree(
                requireText(worktreeId, "公共 Agent 个人 worktree 不能为空", "worktreeId"),
                userId);
        String worktreeServer = worktree.linuxServerId() == null
                ? serverIdentity.linuxServerId()
                : worktree.linuxServerId();
        if (!serverIdentity.linuxServerId().equals(worktreeServer)) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共 Agent 个人 worktree 不属于当前服务器");
        }
        Path configRoot = publicStandardAgentRoot(Path.of(worktree.rootPath()));
        if (!Files.isDirectory(configRoot)) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "公共 Agent 个人配置目录不存在");
        }
        if (personalRuntimeReloader == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "个人 Agent 配置运行态重载服务不可用");
        }
        return personalRuntimeReloader.reloadPublicPreview(
                userId,
                worktreeServer,
                configRoot.toString(),
                traceId);
    }

    public List<FileTreeEntryResponse> listWorkspaceAgentFiles(String workspaceId, String relativePath, String worktreeId) {
        Path agentRoot = workspaceAgentRootForRead(workspaceId, worktreeId);
        // 工作区 agent 目录不存在时返回空列表，不自动创建。
        if (!Files.isDirectory(agentRoot)) {
            return List.of();
        }
        return fileService.listDirectory(agentRoot.toString(), relativePath);
    }

    public FileContentResponse readWorkspaceAgentFile(String workspaceId, String relativePath, String worktreeId) {
        return fileService.readContent(workspaceAgentRootForRead(workspaceId, worktreeId).toString(), relativePath);
    }

    /** 应用 Agent 大文件按 UTF-8 字节偏移渐进只读预览。 */
    public FilePreviewChunkResponse readWorkspaceAgentFilePreviewChunk(
            String workspaceId,
            String relativePath,
            long offset,
            Long expectedSize,
            Long expectedLastModifiedMillis,
            String worktreeId) {
        return fileService.readContentChunk(
                workspaceAgentRootForRead(workspaceId, worktreeId).toString(),
                relativePath,
                offset,
                expectedSize,
                expectedLastModifiedMillis);
    }

    public void writeWorkspaceAgentFile(String workspaceId, String relativePath, String content, String worktreeId) {
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        ensureDirectory(agentRoot);
        fileService.writeContent(agentRoot.toString(), relativePath, content);
    }

    /** 应用 Agent 上传固定在可发布的 opencode.jsonc、agents、skills 和 tools 白名单内。 */
    public void uploadWorkspaceAgentFile(String workspaceId, String relativePath, String contentBase64, String worktreeId) {
        requireWorkspaceAgentUploadPath(relativePath);
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        ensureDirectory(agentRoot);
        fileService.uploadFile(agentRoot.toString(), relativePath, contentBase64);
    }

    /** 应用 Agent 分片上传沿用发布白名单，并绑定当前应用个人 worktree。 */
    public WorkspaceFileUpload beginWorkspaceAgentFileUpload(
            String workspaceId,
            String relativePath,
            long expectedBytes,
            String worktreeId) {
        requireWorkspaceAgentUploadPath(relativePath);
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        ensureDirectory(agentRoot);
        return fileService.beginUpload(agentRoot.toString(), relativePath, expectedBytes);
    }

    private void requireWorkspaceAgentUploadPath(String relativePath) {
        if (workspaceAgentDisplayPath(workspaceAgentGitPath(relativePath)) == null) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "应用 Agent 配置只允许上传 opencode.jsonc、agents、skills 或 tools 下的文件");
        }
    }

    /** 应用 Agent 文件改名复用工作空间文件服务的同目录重命名与路径安全校验。 */
    public void renameWorkspaceAgentFile(String workspaceId, String relativePath, String name, String worktreeId) {
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        fileService.renameFile(agentRoot.toString(), relativePath, name);
    }

    /** 应用 Agent 移动前同时校验源和目标白名单，避免借跨目录移动写入其它 `.opencode` 内容。 */
    public void moveWorkspaceAgentFile(String workspaceId, String sourcePath, String targetPath, String worktreeId) {
        requireWorkspaceAgentUploadPath(sourcePath);
        requireWorkspaceAgentUploadPath(targetPath);
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        fileService.moveFile(agentRoot.toString(), sourcePath, targetPath);
    }

    /** 应用 Agent 普通文件复制同时校验源和目标发布白名单。 */
    public void copyWorkspaceAgentFile(String workspaceId, String sourcePath, String targetPath, String worktreeId) {
        requireWorkspaceAgentUploadPath(sourcePath);
        requireWorkspaceAgentUploadPath(targetPath);
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        fileService.copyFile(agentRoot.toString(), sourcePath, targetPath);
    }

    /** 应用 Agent 文件和目录删除复用工作空间文件服务，并固定在当前个人 worktree 的 `.opencode` 根内。 */
    public void deleteWorkspaceAgentFile(String workspaceId, String relativePath, String worktreeId) {
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        fileService.deleteFile(agentRoot.toString(), relativePath);
    }

    public AgentConfigResponses.AgentConfigWorktreeResponse createPublicWorktree(
            String baseName,
            String branch,
            UserId userId,
            String traceId) {
        return createPublicWorktree(baseName, branch, null, userId, traceId);
    }

    public AgentConfigResponses.AgentConfigWorktreeResponse createPublicWorktree(
            String baseName,
            String branch,
            String operationId,
            UserId userId,
            String traceId) {
        return createPublicWorktree(baseName, branch, operationId, null, userId, traceId);
    }

    public AgentConfigResponses.AgentConfigWorktreeResponse createPublicWorktree(
            String baseName,
            String branch,
            String operationId,
            String linuxServerId,
            UserId userId,
            String traceId) {
        if (linuxServerId != null && !linuxServerId.isBlank() && !serverIdentity.linuxServerId().equals(linuxServerId.trim())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "公共 Agent worktree 必须在目标服务器创建",
                    Map.of("targetLinuxServerId", linuxServerId.trim(), "currentLinuxServerId", serverIdentity.linuxServerId()));
        }
        String lockKey = serverIdentity.linuxServerId() + ":" + userId.value();
        synchronized (publicWorktreeLocks.computeIfAbsent(lockKey, ignored -> new Object())) {
            PublicConfig config = requireEnabledPublicConfig(userId);
            String normalizedBranch = requireText(branch, "分支不能为空", "branch");
            Optional<AgentConfigWorktree> existing = agentConfigRepository.findWorktrees(
                    AgentConfigScope.PUBLIC,
                    null,
                    userId,
                    serverIdentity.linuxServerId(),
                    AgentConfigWorktreeStatus.ACTIVE).stream()
                    .filter(worktree -> isReusablePublicWorktree(worktree, userId))
                    .findFirst();
            if (existing.isPresent()) {
                AgentConfigWorktree worktree = existing.get();
                return AgentConfigResponses.AgentConfigWorktreeResponse.from(
                        worktree,
                        publicStandardAgentRoot(Path.of(worktree.rootPath())).toString());
            }
            String worktreeName = publicWorktreeName(userId);
            AgentConfigProgress progress = startProgress(
                    operationId,
                    AgentConfigScope.PUBLIC,
                    null,
                    "create-worktree",
                    normalizedBranch,
                    traceId);
            try {
                String privateKey = decryptSingleSshKey(userId);
                progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
                ensureExistingPublicRepositoryReady(config, normalizedBranch, privateKey);
                progress.step(AgentConfigOperationStep.CREATING_WORKTREE);
                Path worktreeRoot = config.worktreeRoot().resolve(worktreeName).normalize();
                ensureChild(config.worktreeRoot(), worktreeRoot, "worktreeName");
                gitWorkspaceService.createWorktreeReusingBranch(
                        config.gitRoot(),
                        worktreeRoot,
                        worktreeName,
                        privateKey);
                AgentConfigWorktree worktree = new AgentConfigWorktree(
                        RuntimeIdGenerator.agentConfigWorktreeId(),
                        AgentConfigScope.PUBLIC,
                        null,
                        serverIdentity.linuxServerId(),
                        worktreeName,
                        worktreeName,
                        worktreeRoot.toString(),
                        userId,
                        AgentConfigWorktreeStatus.ACTIVE,
                        now(),
                        now());
                agentConfigRepository.saveWorktree(worktree);
                progress.succeeded(gitWorkspaceService.headCommit(worktreeRoot));
                return AgentConfigResponses.AgentConfigWorktreeResponse.from(
                        worktree,
                        publicStandardAgentRoot(worktreeRoot).toString());
            } catch (PlatformException exception) {
                progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
                throw exception;
            } catch (Exception exception) {
                progress.failed(ErrorCode.INTERNAL_ERROR.name(), "创建公共 Agent worktree 失败");
                throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建公共 Agent worktree 失败", Map.of(), exception);
            }
        }
    }

    public Optional<String> publicWorktreeLinuxServerId(String worktreeId) {
        if (worktreeId == null || worktreeId.isBlank()) {
            return Optional.empty();
        }
        AgentConfigWorktree worktree = agentConfigRepository.findWorktree(worktreeId.trim())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Agent worktree 不存在", Map.of("worktreeId", worktreeId)));
        if (worktree.scope() != AgentConfigScope.PUBLIC || worktree.status() != AgentConfigWorktreeStatus.ACTIVE) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Agent worktree 不存在", Map.of("worktreeId", worktreeId));
        }
        return Optional.ofNullable(worktree.linuxServerId()).or(() -> Optional.of(serverIdentity.linuxServerId()));
    }

    /**
     * 查询当前用户在某台服务器上可复用的公共 ACTIVE worktree。
     */
    public List<AgentConfigResponses.AgentConfigWorktreeOptionResponse> listPublicWorktrees(
            String linuxServerId,
            UserId userId) {
        String targetServerId = requireText(linuxServerId, "服务器不能为空", "linuxServerId");
        return agentConfigRepository.findWorktrees(
                AgentConfigScope.PUBLIC,
                null,
                userId,
                targetServerId,
                AgentConfigWorktreeStatus.ACTIVE).stream()
                .filter(worktree -> isReusablePublicWorktree(worktree, userId))
                .map(worktree -> AgentConfigResponses.AgentConfigWorktreeOptionResponse.from(
                        worktree,
                        publicStandardAgentRoot(Path.of(worktree.rootPath())).toString(),
                        usernameFor(worktree.createdBy())))
                .toList();
    }

    /**
     * 查询工作空间级 Agent 配置文件应路由到的服务器；仅做归属解析，不访问文件系统。
     */
    public String workspaceAgentFilesLinuxServerId(String workspaceId, String worktreeId) {
        Workspace workspace = existingWorkspaceForRouting(workspaceId);
        String workspaceLinuxServerId = workspace.linuxServerId() == null
                ? serverIdentity.linuxServerId()
                : workspace.linuxServerId();
        if (worktreeId == null || worktreeId.isBlank()) {
            return workspaceLinuxServerId;
        }
        AgentConfigWorktree worktree = existingWorktreeForRouting(worktreeId, AgentConfigScope.WORKSPACE, workspace.workspaceId());
        return worktree.linuxServerId() == null ? workspaceLinuxServerId : worktree.linuxServerId();
    }

    public AgentConfigResponses.AgentConfigWorktreeResponse createWorkspaceWorktree(
            String workspaceId,
            String baseName,
            String branch,
            UserId userId,
            String traceId) {
        return createWorkspaceWorktree(workspaceId, baseName, branch, null, userId, traceId);
    }

    public AgentConfigResponses.AgentConfigWorktreeResponse createWorkspaceWorktree(
            String workspaceId,
            String baseName,
            String branch,
            String operationId,
            UserId userId,
            String traceId) {
        Workspace workspace = existingWorkspace(workspaceId);
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        String worktreeName = worktreeName(baseName);
        String privateKey = decryptSingleSshKey(userId);
        Path repoRoot = workspaceRoot(workspace);
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.WORKSPACE, workspace.workspaceId(), "create-worktree", normalizedBranch, traceId);
        try {
            ensureExistingCleanRepository(repoRoot, (String) null);
            progress.step(AgentConfigOperationStep.CREATING_WORKTREE);
            Path worktreeBase = Path.of(requiredParameter(PARAM_PERSONAL_WORKTREE_ROOT))
                    .resolve("agentconfig")
                    .resolve(workspace.workspaceId().value())
                    .normalize();
            Path worktreeRoot = worktreeBase.resolve(worktreeName).normalize();
            ensureChild(worktreeBase, worktreeRoot, "worktreeName");
            gitWorkspaceService.createWorktree(repoRoot, worktreeRoot, worktreeName, privateKey);
            AgentConfigWorktree worktree = new AgentConfigWorktree(
                    RuntimeIdGenerator.agentConfigWorktreeId(),
                    AgentConfigScope.WORKSPACE,
                    workspace.workspaceId(),
                    serverIdentity.linuxServerId(),
                    worktreeName,
                    worktreeName,
                    worktreeRoot.toString(),
                    userId,
                    AgentConfigWorktreeStatus.ACTIVE,
                    now(),
                    now());
            agentConfigRepository.saveWorktree(worktree);
            progress.succeeded(gitWorkspaceService.headCommit(repoRoot));
            return AgentConfigResponses.AgentConfigWorktreeResponse.from(worktree, workspaceStandardAgentRoot(worktreeRoot).toString());
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "创建工作空间 Agent worktree 失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建工作空间 Agent worktree 失败", Map.of(), exception);
        }
    }

    public AgentConfigResponses.AgentConfigDiffResponse publicDiff(String worktreeId, UserId userId) {
        Path personalRepoRoot = repoRootForPublicOperation(worktreeId, userId);
        AgentConfigResponses.AgentConfigDiffResponse diff = diff(personalRepoRoot);
        return new AgentConfigResponses.AgentConfigDiffResponse(
                diff.files(),
                diff.files().isEmpty()
                        && hasPendingPublicPublish(personalRepoRoot, publicConfig(userId).gitRoot()));
    }

    public AgentConfigResponses.AgentConfigDiffResponse workspaceDiff(String workspaceId, String worktreeId) {
        return workspaceDiff(repoRootForWorkspaceOperation(workspaceId, worktreeId));
    }

    public void publicStage(List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.stageFiles(repoRootForPublicOperation(worktreeId, userId), normalizeFiles(files), decryptSingleSshKey(userId));
    }

    public void publicUnstage(List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.unstageFiles(repoRootForPublicOperation(worktreeId, userId), normalizeFiles(files), decryptSingleSshKey(userId));
    }

    /** 丢弃当前用户公共个人 worktree 中指定 Agent/Skill 文件的本地改动。 */
    public void publicDiscard(List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.discardFiles(
                repoRootForPublicOperation(worktreeId, userId),
                normalizeFiles(files),
                decryptSingleSshKey(userId));
    }

    public void workspaceStage(String workspaceId, List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.stageFiles(
                repoRootForWorkspaceOperation(workspaceId, worktreeId),
                normalizeWorkspaceAgentFiles(files),
                decryptSingleSshKey(userId));
    }

    public void workspaceUnstage(String workspaceId, List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.unstageFiles(
                repoRootForWorkspaceOperation(workspaceId, worktreeId),
                normalizeWorkspaceAgentFiles(files),
                decryptSingleSshKey(userId));
    }

    /** 丢弃应用版本个人 worktree 中指定 Agent/Skill 文件的本地改动。 */
    public void workspaceDiscard(String workspaceId, List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.discardFiles(
                repoRootForWorkspaceOperation(workspaceId, worktreeId),
                normalizeWorkspaceAgentFiles(files),
                decryptSingleSshKey(userId));
    }

    public AgentConfigResponses.AgentConfigOperationResponse publicCommit(
            String message,
            String worktreeId,
            String operationId,
            UserId userId,
            String traceId) {
        return commit(repoRootForPublicOperation(worktreeId, userId), AgentConfigScope.PUBLIC, null, message, operationId, userId, traceId);
    }

    public AgentConfigResponses.AgentConfigOperationResponse workspaceCommit(
            String workspaceId,
            String message,
            String worktreeId,
            String operationId,
            UserId userId,
            String traceId) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        return commit(repoRootForWorkspaceOperation(workspaceId, worktreeId), AgentConfigScope.WORKSPACE, id, message, operationId, userId, traceId);
    }

    public AgentConfigResponses.AgentConfigOperationResponse publicPublish(
            String worktreeId,
            String operationId,
            UserId userId,
            String traceId) {
        PublicConfig config = requireEnabledPublicConfig(userId);
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "publish", null, traceId);
        String rolloutId = null;
        try {
            String privateKey = decryptSingleSshKey(userId);
            GitCommitIdentity commitIdentity = gitCommitIdentity(userId);
            AgentConfigWorktree worktree = ownedPublicWorktree(worktreeId, userId);
            Path sharedRepoRoot = config.gitRoot();
            Path personalRepoRoot = Path.of(worktree.rootPath());
            ensureExistingCleanRepository(sharedRepoRoot, config);
            ensureExistingCleanRepository(
                    personalRepoRoot,
                    config,
                    PublicRepositorySyncTarget.PERSONAL_WORKTREE);
            String branch = gitWorkspaceService.currentBranch(sharedRepoRoot);
            String previousCommitHash = gitWorkspaceService.headCommit(sharedRepoRoot);
            progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
            gitWorkspaceService.fetch(personalRepoRoot, privateKey);
            progress.step(AgentConfigOperationStep.MERGING);
            try {
                gitWorkspaceService.mergeBranch(
                        personalRepoRoot,
                        "origin/" + branch,
                        privateKey,
                        commitIdentity);
            } catch (PlatformException mergeException) {
                List<String> conflictFiles = gitWorkspaceService.conflictPaths(personalRepoRoot);
                if (!conflictFiles.isEmpty()) {
                    throw publicMergeConflict(
                            conflictFiles,
                            "公共 Agent 配置与远端分支存在冲突，请解决后重新提交并推送");
                }
                throw mergeException;
            }
            progress.step(AgentConfigOperationStep.PUSHING);
            String personalCommitHash = gitWorkspaceService.headCommit(personalRepoRoot);
            String remoteCommitHash = gitWorkspaceService.resolveCommit(personalRepoRoot, "origin/" + branch);
            // 个人公共 worktree 会长期复用，历史中可能存在企业 SCM 不认可的旧提交身份。
            // 发布只投影最终文件树，并以当前远端提交为唯一父节点，避免把污染历史推入公共分支。
            String commitHash = gitWorkspaceService.createLinearCommitFromTree(
                    personalRepoRoot,
                    personalCommitHash,
                    remoteCommitHash,
                    "发布公共 Agent 配置",
                    commitIdentity);
            // refspec 的 source 必须是本地 ref；先让个人分支指向干净提交，再按分支名非强推。
            // 即使后续远端拒绝，个人 worktree 仍保留同一最终文件树，可直接重试发布。
            gitWorkspaceService.resetHardToCommit(personalRepoRoot, commitHash);
            // PREPARING 必须先于 push，保证远端已变化但数据库写入失败时仍有可恢复的持久化闸门。
            rolloutId = preparePublicConfigRollout(
                    branch, commitHash, previousCommitHash, userId, traceId);
            try {
                gitWorkspaceService.pushRef(personalRepoRoot, worktree.branch(), branch, privateKey);
            } catch (PlatformException pushException) {
                handleUncertainPushFailure(
                        rolloutId,
                        personalRepoRoot,
                        branch,
                        commitHash,
                        privateKey,
                        null,
                        null,
                        pushException);
            }
            // 发起服务器先在 PREPARING 闸门内切换自己的共享运行副本；激活后再由数据库租约确认进程清单。
            gitWorkspaceService.checkoutTrackingBranch(sharedRepoRoot, branch, privateKey);
            gitWorkspaceService.resetHardToCommit(sharedRepoRoot, commitHash);
            activatePublicConfigRollout(rolloutId, commitHash);
            progress.step(AgentConfigOperationStep.BROADCASTING);
            broadcastPublicSync(branch, commitHash, "publish", rolloutId, traceId);
            return progress.succeeded(commitHash);
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "发布公共 Agent 配置失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "发布公共 Agent 配置失败", Map.of(), exception);
        }
    }

    public AgentConfigResponses.AgentConfigOperationResponse workspacePublish(
            String workspaceId,
            String worktreeId,
            String operationId,
            UserId userId,
            String traceId) {
        Workspace workspace = existingWorkspace(workspaceId);
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.WORKSPACE, workspace.workspaceId(), "publish", null, traceId);
        String applicationRolloutId = null;
        try {
            String privateKey = decryptSingleSshKey(userId);
            GitCommitIdentity commitIdentity = gitCommitIdentity(userId);
            Path repoRoot = workspaceRoot(workspace);
            ensureExistingCleanRepository(repoRoot, (String) null);
            String branch = gitWorkspaceService.currentBranch(repoRoot);
            if (managedWorkspaceApplicationService != null) {
                applicationRolloutId = managedWorkspaceApplicationService.prepareFeatureWorkspaceAgentConfigPublish(
                        workspaceId, userId, traceId);
            }
            String commitHash;
            if (worktreeId != null && !worktreeId.isBlank()) {
                AgentConfigWorktree worktree = existingWorktree(worktreeId, AgentConfigScope.WORKSPACE, workspace.workspaceId());
                progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
                progress.step(AgentConfigOperationStep.MERGING);
                GitPublishWorkflow.PublishResult result = gitPublishWorkflow.publishMergedBranch(
                        repoRoot,
                        branch,
                        worktree.branch(),
                        false,
                        privateKey,
                        commitIdentity);
                throwIfConflicted(result, "工作空间 Agent 配置合并冲突");
                commitHash = result.headCommit();
                agentConfigRepository.saveWorktree(worktree.markPublished(now()));
            } else {
                progress.step(AgentConfigOperationStep.PUSHING);
                commitHash = gitPublishWorkflow.publishDirectBranch(repoRoot, branch, false, privateKey).headCommit();
            }
            progress.step(AgentConfigOperationStep.BROADCASTING);
            if (managedWorkspaceApplicationService != null) {
                if (applicationRolloutId == null) {
                    managedWorkspaceApplicationService.recordFeatureWorkspacePublished(
                            workspaceId, commitHash, userId, traceId);
                } else {
                    managedWorkspaceApplicationService.recordFeatureWorkspacePublished(
                            workspaceId, commitHash, applicationRolloutId, userId, traceId);
                }
            }
            return progress.succeeded(commitHash);
        } catch (PlatformException exception) {
            if (managedWorkspaceApplicationService != null) {
                managedWorkspaceApplicationService.abortFeatureWorkspaceAgentConfigPublish(
                        applicationRolloutId, "APPLICATION_AGENT_CONFIG_PUBLISH_FAILED");
            }
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            if (managedWorkspaceApplicationService != null) {
                managedWorkspaceApplicationService.abortFeatureWorkspaceAgentConfigPublish(
                        applicationRolloutId, "APPLICATION_AGENT_CONFIG_PUBLISH_FAILED");
            }
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "发布工作空间 Agent 配置失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "发布工作空间 Agent 配置失败", Map.of(), exception);
        }
    }

    public Optional<AgentConfigResponses.AgentConfigOperationResponse> findOperation(String operationId) {
        return agentConfigRepository.findOperation(operationId).map(AgentConfigResponses.AgentConfigOperationResponse::from);
    }

    @Override
    public boolean supports(String type) {
        return PUBLIC_SYNC_EVENT.equals(type);
    }

    /**
     * 服务器间公共 Agent 同步只传 branch/commitHash，不包含文件内容或私钥。
     */
    @Override
    public void handle(ServerBroadcastEvent event) {
        if (broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            return;
        }
        Object branchValue = event.payload().get("branch");
        Object commitValue = event.payload().get("commitHash");
        if (!(branchValue instanceof String branch) || !(commitValue instanceof String commitHash)) {
            return;
        }
        Object rolloutValue = event.payload().get("rolloutId");
        String rolloutId = rolloutValue instanceof String value && !value.isBlank() ? value : null;
        if (rolloutId != null && publicConfigRolloutCoordinator != null) {
            publicConfigRolloutCoordinator.claimPendingSync(serverIdentity.linuxServerId())
                    .filter(request -> rolloutId.equals(request.rolloutId()))
                    .ifPresent(this::synchronizePublicRuntimeRepository);
            return;
        }
        synchronizePublicRuntimeRepository(branch, commitHash, null, null, event.traceId());
    }

    /**
     * Redis 广播不是持久队列；定时读取数据库 PENDING 服务器记录，保证广播丢失或 Java 重启后仍会继续同步。
     */
    @Scheduled(
            fixedDelayString = "${test-agent.public-agent-config.rollout.sync-retry-delay-ms:5000}",
            initialDelayString = "${test-agent.public-agent-config.rollout.initial-delay-ms:5000}")
    void retryPendingPublicConfigSync() {
        if (publicConfigRolloutCoordinator == null) {
            return;
        }
        publicConfigRolloutCoordinator.claimPendingSync(serverIdentity.linuxServerId())
                .ifPresent(this::synchronizePublicRuntimeRepository);
    }

    /**
     * PREPARING 是 push/共享副本更新的恢复日志；发起进程退出后，同服务器其它 Java 会按远端事实继续激活。
     */
    @Scheduled(
            fixedDelayString = "${test-agent.public-agent-config.rollout.preparing-retry-delay-ms:5000}",
            initialDelayString = "${test-agent.public-agent-config.rollout.initial-delay-ms:5000}")
    void retryPreparingPublicConfigRollout() {
        if (publicConfigRolloutCoordinator == null) {
            return;
        }
        publicConfigRolloutCoordinator.preparing(serverIdentity.linuxServerId())
                .ifPresent(this::reconcilePreparingPublicConfigRollout);
    }

    private void synchronizePublicRuntimeRepository(PublicAgentConfigRolloutSyncRequest request) {
        synchronized (publicWorktreeLocks.computeIfAbsent("public-runtime-sync", ignored -> new Object())) {
            try {
                if (!publicConfigRolloutCoordinator.renewServerSync(request)) {
                    return;
                }
                UserId initiator = request.initiatedByUserId() == null || request.initiatedByUserId().isBlank()
                        ? null
                        : new UserId(request.initiatedByUserId());
                PublicConfig config = initiator == null ? publicConfig() : requireEnabledPublicConfig(initiator);
                if (!config.enabled() || !gitWorkspaceService.isGitRepository(config.gitRoot())) {
                    throw new PlatformException(
                            ErrorCode.CONFLICT,
                            "公共 Agent 运行副本尚未初始化",
                            Map.of("path", config.gitRoot().toString()));
                }
                if (!gitWorkspaceService.isWorktreeClean(config.gitRoot())) {
                    throw new PlatformException(
                            ErrorCode.CONFLICT,
                            "公共 Agent 运行副本存在未提交变更",
                            Map.of("path", config.gitRoot().toString()));
                }
                String privateKey = initiator == null ? null : decryptSingleSshKey(initiator);
                if (initiator != null) {
                    ensurePublicRepositoryOriginReady(config.gitRoot(), config);
                }
                gitWorkspaceService.fetch(config.gitRoot(), privateKey);
                if (!publicConfigRolloutCoordinator.renewServerSync(request)) {
                    return;
                }
                gitWorkspaceService.checkoutTrackingBranch(config.gitRoot(), request.branch(), privateKey);
                if (!publicConfigRolloutCoordinator.renewServerSync(request)) {
                    return;
                }
                gitWorkspaceService.resetHardToCommit(config.gitRoot(), request.commitHash());
                if (!publicConfigRolloutCoordinator.renewServerSync(request)) {
                    return;
                }
                publicConfigRolloutCoordinator.markServerSynced(request);
            } catch (Exception exception) {
                publicConfigRolloutCoordinator.markServerSyncRetry(request, safeErrorMessage(exception.getMessage()));
                LOGGER.warn(
                        "event=agent_config_public_replica_sync_retry rolloutId={} linuxServerId={} retryCount={} message={}",
                        request.rolloutId(),
                        serverIdentity.linuxServerId(),
                        request.retryCount() + 1,
                        safeErrorMessage(exception.getMessage()));
            }
        }
    }

    private void synchronizePublicRuntimeRepository(
            String branch,
            String commitHash,
            String rolloutId,
            String initiatedByUserId,
            String traceId) {
        synchronized (publicWorktreeLocks.computeIfAbsent("public-runtime-sync", ignored -> new Object())) {
            UserId initiator = initiatedByUserId == null || initiatedByUserId.isBlank()
                    ? null
                    : new UserId(initiatedByUserId);
            PublicConfig config = initiator == null ? publicConfig() : requireEnabledPublicConfig(initiator);
            if (!config.enabled() || !gitWorkspaceService.isGitRepository(config.gitRoot())) {
                return;
            }
            if (!gitWorkspaceService.isWorktreeClean(config.gitRoot())) {
                return;
            }
            String privateKey = initiator == null ? null : decryptSingleSshKey(initiator);
            if (initiator != null) {
                ensurePublicRepositoryOriginReady(config.gitRoot(), config);
            }
            gitWorkspaceService.fetch(config.gitRoot(), privateKey);
            gitWorkspaceService.checkoutTrackingBranch(config.gitRoot(), branch, privateKey);
            gitWorkspaceService.resetHardToCommit(config.gitRoot(), commitHash);
            if (rolloutId != null && publicConfigRolloutCoordinator != null) {
                // 新 rollout 只允许走带数据库租约的重载；这里仅保留旧广播兼容路径。
                return;
            }
        }
    }

    private AgentConfigResponses.AgentConfigOperationResponse commit(
            Path repoRoot,
            AgentConfigScope scope,
            WorkspaceId workspaceId,
            String message,
            String operationId,
            UserId userId,
            String traceId) {
        String normalizedMessage = requireText(message, "提交说明不能为空", "message");
        AgentConfigProgress progress = startProgress(operationId, scope, workspaceId, "commit", gitWorkspaceService.currentBranch(repoRoot), traceId);
        try {
            progress.step(AgentConfigOperationStep.COMMITTING);
            gitWorkspaceService.commitStaged(
                    repoRoot,
                    normalizedMessage,
                    decryptSingleSshKey(userId),
                    gitCommitIdentity(userId));
            return progress.succeeded(gitWorkspaceService.headCommit(repoRoot));
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "提交 Agent 配置失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "提交 Agent 配置失败", Map.of(), exception);
        }
    }

    private AgentConfigResponses.AgentConfigDiffResponse diff(Path repoRoot) {
        String statusOutput = gitWorkspaceService.statusPorcelain(repoRoot);
        Map<String, AgentConfigResponses.AgentConfigDiffFileResponse> files = new LinkedHashMap<>();
        for (GitDiffFile file : gitWorkspaceService.collectDiffFiles(repoRoot, statusOutput)) {
            files.put(file.path(), new AgentConfigResponses.AgentConfigDiffFileResponse(
                    file.path(),
                    file.rawStatus().trim(),
                    file.staged(),
                    file.patch()));
        }
        return new AgentConfigResponses.AgentConfigDiffResponse(List.copyOf(files.values()));
    }

    /**
     * Git status 只能看到尚未提交的文件；发布失败后个人 worktree 已经 clean，页面刷新会因此丢失重试入口。
     * 个人 HEAD 不是共享 HEAD 的祖先且两者文件树不同，表示个人分支仍有需要合并或重新发布的内容。
     */
    private boolean hasPendingPublicPublish(Path personalRepoRoot, Path sharedRepoRoot) {
        if (!gitWorkspaceService.isGitRepository(personalRepoRoot)
                || !gitWorkspaceService.isGitRepository(sharedRepoRoot)) {
            return false;
        }
        String personalHead = gitWorkspaceService.headCommit(personalRepoRoot);
        String sharedHead = gitWorkspaceService.headCommit(sharedRepoRoot);
        if (personalHead.equals(sharedHead)
                || gitWorkspaceService.isAncestor(personalRepoRoot, personalHead, sharedHead)) {
            return false;
        }
        String personalTree = gitWorkspaceService.resolveCommit(personalRepoRoot, personalHead + "^{tree}");
        String sharedTree = gitWorkspaceService.resolveCommit(personalRepoRoot, sharedHead + "^{tree}");
        return !personalTree.equals(sharedTree);
    }

    private void throwIfConflicted(GitPublishWorkflow.PublishResult result, String message) {
        if (!result.hasConflicts()) {
            return;
        }
        throw new PlatformException(
                ErrorCode.CONFLICT,
                message,
                Map.of("conflictFiles", result.conflictFiles()));
    }

    /**
     * 工作空间级 Agent 配置只允许展示 .opencode 下的 opencode.jsonc、agents 与 skills。
     * Git porcelain 在子目录执行时仍可能返回仓库根相对路径，因此这里保留 Git 命令路径并单独生成 UI 展示路径。
     */
    private AgentConfigResponses.AgentConfigDiffResponse workspaceDiff(Path repoRoot) {
        String statusOutput = gitWorkspaceService.statusPorcelain(repoRoot, WORKSPACE_AGENT_RELATIVE_ROOT);
        Map<String, AgentConfigResponses.AgentConfigDiffFileResponse> files = new LinkedHashMap<>();
        List<GitStatusEntry> entries = gitWorkspaceService.parseStatusPorcelain(statusOutput).stream()
                .map(entry -> entry.withPath(workspaceAgentGitPath(entry.path())))
                .filter(entry -> workspaceAgentDisplayPath(entry.path()) != null)
                .toList();
        for (GitDiffFile file : gitWorkspaceService.collectDiffFiles(repoRoot, entries)) {
            String displayPath = workspaceAgentDisplayPath(file.path());
            if (displayPath == null) {
                continue;
            }
            files.put(displayPath, new AgentConfigResponses.AgentConfigDiffFileResponse(
                    displayPath,
                    file.rawStatus().trim(),
                    file.staged(),
                    file.patch()));
        }
        return new AgentConfigResponses.AgentConfigDiffResponse(List.copyOf(files.values()));
    }

    private void ensurePublicRepositoryReady(PublicConfig config, String branch, String privateKey) {
        ensurePublicRepositoryReady(config, branch, privateKey, false);
    }

    private void ensurePublicRepositoryReady(
            PublicConfig config,
            String branch,
            String privateKey,
            boolean discardLocalChanges) {
        Path gitRoot = config.gitRoot();
        if (gitWorkspaceService.isGitRepository(gitRoot)) {
            ensureExistingRepositoryReadyForSync(gitRoot, config, discardLocalChanges);
            gitWorkspaceService.fetch(gitRoot, privateKey);
            gitWorkspaceService.checkoutTrackingBranch(gitRoot, branch, privateKey);
            gitWorkspaceService.pullFastForward(gitRoot, branch, privateKey);
            requireInitializedConfigDirectory(config);
            return;
        }
        // 公共配置首次使用时允许根目录不存在或为空目录，由后端按通用参数自动 clone。
        if (Files.exists(gitRoot) && !isEmptyDirectory(gitRoot)) {
            // 目录已存在且非空，但又不是 Git 仓库，两种情况都阻碍自动 clone
            throw new PlatformException(ErrorCode.CONFLICT, "目录已存在且非空，但不是 Git 仓库：" + gitRoot, Map.of("path", gitRoot.toString()));
        }
        ensureDirectory(gitRoot.getParent());
        gitWorkspaceService.cloneBranch(config.gitUrl(), branch, gitRoot, privateKey);
        requireInitializedConfigDirectory(config);
    }

    private void ensureExistingPublicRepositoryReady(PublicConfig config, String branch, String privateKey) {
        if (!gitWorkspaceService.isGitRepository(config.gitRoot())) {
            throw publicRepositoryUninitialized(config.gitRoot());
        }
        ensureExistingCleanRepository(config.gitRoot(), config);
        gitWorkspaceService.fetch(config.gitRoot(), privateKey);
        gitWorkspaceService.checkoutTrackingBranch(config.gitRoot(), branch, privateKey);
        gitWorkspaceService.pullFastForward(config.gitRoot(), branch, privateKey);
        requireInitializedConfigDirectory(config);
    }

    private AgentConfigResponses.PublicRepositoryStatusResponse publicRepositoryStatus(PublicConfig config) {
        String message = null;
        String status = "UNINITIALIZED";
        boolean initialized = false;
        boolean initializationAllowed = config.enabled();
        String currentBranch = null;
        String commitHash = null;
        if (!config.enabled()) {
            status = "DISABLED";
            initializationAllowed = false;
            message = publicGitUrlUnconfiguredMessage();
        } else if (gitWorkspaceService.isGitRepository(config.gitRoot())) {
            currentBranch = gitWorkspaceService.currentBranch(config.gitRoot());
            commitHash = gitWorkspaceService.headCommit(config.gitRoot());
            boolean originMatched = config.matchesOrigin(gitWorkspaceService.originUrl(config.gitRoot()));
            boolean clean = gitWorkspaceService.isWorktreeClean(config.gitRoot());
            boolean configReady = isInitializedConfigDirectory(config);
            initialized = originMatched && configReady;
            status = initialized && clean ? "READY" : "CONFLICT";
            initializationAllowed = originMatched;
            if (!originMatched) {
                message = "Git origin 与配置不一致";
            } else if (!clean) {
                message = dirtyPublicRepositoryMessage(config.gitRoot());
            } else if (!configReady) {
                status = "UNINITIALIZED";
                message = "公共配置目录未初始化";
            }
        } else if (Files.exists(config.gitRoot()) && !isEmptyDirectory(config.gitRoot())) {
            status = "CONFLICT";
            initializationAllowed = false;
            message = "目录已存在且非空，但不是 Git 仓库";
        } else {
            message = "公共配置仓库未初始化";
        }
        return new AgentConfigResponses.PublicRepositoryStatusResponse(
                serverIdentity.linuxServerId(),
                serverIdentity.linuxServerId(),
                config.gitRoot().toString(),
                config.configDir().toString(),
                config.worktreeRoot().toString(),
                status,
                initialized,
                initializationAllowed,
                currentBranch,
                commitHash,
                message);
    }

    private boolean isInitializedConfigDirectory(PublicConfig config) {
        return Files.isDirectory(config.configDir()) && !isEmptyDirectory(config.configDir());
    }

    private void requireInitializedConfigDirectory(PublicConfig config) {
        if (!isInitializedConfigDirectory(config)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "服务器" + serverIdentity.linuxServerId() + "上公共配置目录" + config.configDir()
                            + "未初始化；请先用公共 Agent Git 仓库初始化该服务器，确保仓库中包含 opencode 配置目录和配置文件。",
                    Map.of("linuxServerId", serverIdentity.linuxServerId(), "configDirPath", config.configDir().toString()));
        }
    }

    private PlatformException publicRepositoryUninitialized(Path gitRoot) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "服务器" + serverIdentity.linuxServerId() + "上公共配置仓库在" + gitRoot + "目录中未初始化。",
                Map.of("linuxServerId", serverIdentity.linuxServerId(), "gitRootPath", gitRoot.toString()));
    }

    private boolean isEmptyDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (java.util.stream.Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取目录失败", Map.of("path", directory.toString()), exception);
        }
    }

    private void ensureExistingCleanRepository(Path repoRoot, String expectedOrigin) {
        ensureExistingRepositoryReadyForSync(repoRoot, expectedOrigin, false);
    }

    private void ensureExistingCleanRepository(Path repoRoot, PublicConfig config) {
        ensureExistingRepositoryReadyForSync(repoRoot, config, false);
    }

    private void ensureExistingCleanRepository(
            Path repoRoot,
            PublicConfig config,
            PublicRepositorySyncTarget syncTarget) {
        ensureExistingRepositoryReadyForSync(repoRoot, config, false, syncTarget);
    }

    private void ensureExistingRepositoryReadyForSync(
            Path repoRoot,
            String expectedOrigin,
            boolean discardLocalChanges) {
        ensureExistingRepositoryReadyForSync(repoRoot, expectedOrigin, null, discardLocalChanges);
    }

    private void ensureExistingRepositoryReadyForSync(
            Path repoRoot,
            PublicConfig config,
            boolean discardLocalChanges) {
        ensureExistingRepositoryReadyForSync(
                repoRoot,
                config,
                discardLocalChanges,
                PublicRepositorySyncTarget.SHARED_RUNTIME);
    }

    private void ensureExistingRepositoryReadyForSync(
            Path repoRoot,
            PublicConfig config,
            boolean discardLocalChanges,
            PublicRepositorySyncTarget syncTarget) {
        ensureExistingRepositoryReadyForSync(repoRoot, null, config, discardLocalChanges, syncTarget);
    }

    private void ensureExistingRepositoryReadyForSync(
            Path repoRoot,
            String expectedOrigin,
            PublicConfig config,
            boolean discardLocalChanges) {
        ensureExistingRepositoryReadyForSync(
                repoRoot,
                expectedOrigin,
                config,
                discardLocalChanges,
                PublicRepositorySyncTarget.GENERIC);
    }

    private void ensureExistingRepositoryReadyForSync(
            Path repoRoot,
            String expectedOrigin,
            PublicConfig config,
            boolean discardLocalChanges,
            PublicRepositorySyncTarget syncTarget) {
        if (!gitWorkspaceService.isGitRepository(repoRoot)) {
            // 消息带上具体目录，便于前端直接定位问题路径
            throw new PlatformException(ErrorCode.CONFLICT, "目录不是 Git 仓库：" + repoRoot, Map.of("path", repoRoot.toString()));
        }
        if (config != null) {
            ensurePublicRepositoryOriginReady(repoRoot, config);
        } else {
            String origin = gitWorkspaceService.originUrl(repoRoot);
            if (expectedOrigin != null && !expectedOrigin.isBlank() && !Objects.equals(origin, expectedOrigin)) {
                throw new PlatformException(ErrorCode.CONFLICT, "Git origin 与配置不一致", Map.of("path", repoRoot.toString()));
            }
        }
        if (!gitWorkspaceService.isWorktreeClean(repoRoot)) {
            if (!discardLocalChanges) {
                throw dirtyPublicRepositoryConflict(repoRoot, syncTarget);
            }
            // 只恢复 Git 已跟踪内容；未跟踪文件不删除，避免“更新”扩大为不可逆清理。
            gitWorkspaceService.resetHardToCommit(repoRoot, "HEAD");
        }
    }

    /**
     * 校验公共仓库来源，并在内部部署中把 origin 的 SSH 用户刷新为当前操作人。
     */
    private void ensurePublicRepositoryOriginReady(Path repoRoot, PublicConfig config) {
        String origin = gitWorkspaceService.originUrl(repoRoot);
        if (!config.matchesOrigin(origin)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Git origin 与配置不一致", Map.of("path", repoRoot.toString()));
        }
        if (config.internalDeployment()) {
            gitWorkspaceService.setOriginUrl(repoRoot, config.gitUrl(), null);
        }
    }

    /**
     * 公共仓库脏状态最多展示五个真实 Git 路径，帮助管理员区分“目录未初始化”和“文件待提交”。
     */
    private String dirtyPublicRepositoryMessage(Path repoRoot) {
        List<String> paths = dirtyPublicRepositoryPaths(repoRoot);
        if (paths.isEmpty()) {
            return "Git 工作树存在未提交变更（Git 未返回可展示的文件路径，请在该服务器仓库执行 git status --short）";
        }
        boolean truncated = paths.size() > 5;
        List<String> visiblePaths = truncated ? paths.subList(0, 5) : paths;
        return "Git 工作树存在未提交变更：" + String.join("、", visiblePaths) + (truncated ? " 等" : "");
    }

    private List<String> dirtyPublicRepositoryPaths(Path repoRoot) {
        return gitWorkspaceService.parseStatusPorcelain(gitWorkspaceService.statusPorcelain(repoRoot)).stream()
                .map(GitStatusEntry::path)
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .limit(6)
                .toList();
    }

    /**
     * 脏仓库冲突必须标明真实仓库角色和绝对路径，避免把个人编辑 worktree 误诊为共享运行副本。
     */
    private PlatformException dirtyPublicRepositoryConflict(
            Path repoRoot,
            PublicRepositorySyncTarget syncTarget) {
        List<String> paths = dirtyPublicRepositoryPaths(repoRoot);
        boolean truncated = paths.size() > 5;
        List<String> visiblePaths = truncated ? paths.subList(0, 5) : paths;
        String files = visiblePaths.isEmpty()
                ? "Git 未返回可展示的文件路径，请在该目录执行 git status --short"
                : String.join("、", visiblePaths) + (truncated ? " 等" : "");
        return new PlatformException(
                ErrorCode.CONFLICT,
                syncTarget.displayName() + " 存在未提交变更：" + files
                        + "；仓库路径：" + repoRoot
                        + "。确认无需保留后，可选择放弃相关本地变更并拉取。",
                Map.of(
                        "path", repoRoot.toString(),
                        "repositoryKind", syncTarget.name(),
                        "dirtyFiles", visiblePaths,
                        "discardLocalChangesAllowed", true));
    }

    private String decryptSingleSshKey(UserId userId) {
        List<UserSshKey> keys = configurationRepository.findSshKeys(userId);
        if (keys.size() != 1) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "当前用户必须配置唯一 SSH key",
                    Map.of("keyCount", keys.size()));
        }
        UserSshKey key = keys.get(0);

        if (key.encryptedAesKey() == null || key.encryptedAesKey().isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR,
                    "SSH key 使用的旧版加密格式，请重新添加",
                    Map.of("sshKeyId", key.sshKeyId().value()));
        }

        return sshKeyEncryptionService.decrypt(
                key.encryptedPrivateKey(),
                key.encryptedAesKey(),
                key.encryptionNonce());
    }

    private Path publicAgentRootForRead(String worktreeId, UserId userId) {
        Path repoRoot = worktreeId == null || worktreeId.isBlank()
                ? requireEnabledPublicConfig().gitRoot()
                : repoRootForPublicOperation(worktreeId, userId);
        Path standard = publicStandardAgentRoot(repoRoot);
        if (Files.isDirectory(standard)) {
            return standard;
        }
        Path legacy = repoRoot.resolve(PUBLIC_AGENT_LEGACY_RELATIVE_ROOT).normalize();
        return Files.isDirectory(legacy) ? legacy : standard;
    }

    private Path publicAgentRootForWrite(String worktreeId, UserId userId) {
        return publicStandardAgentRoot(repoRootForPublicOperation(worktreeId, userId));
    }

    private String usernameFor(UserId userId) {
        return userRepository.findByUserId(userId)
                .map(User::username)
                .orElse(null);
    }

    /**
     * 将当前平台用户转换为 Git 单次提交身份；平台用户表没有邮箱字段，因此按统一认证号生成企业 SCM 登记邮箱。
     */
    private GitCommitIdentity gitCommitIdentity(UserId userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "用户不存在", Map.of("userId", userId.value())));
        return GitCommitIdentity.forPlatformUser(user.username(), user.unifiedAuthId());
    }

    private Path workspaceAgentRootForRead(String workspaceId, String worktreeId) {
        Path repoRoot = repoRootForWorkspaceOperation(workspaceId, worktreeId);
        Path standard = workspaceStandardAgentRoot(repoRoot);
        if (Files.isDirectory(standard)) {
            return standard;
        }
        Path legacy = repoRoot.resolve(WORKSPACE_AGENT_LEGACY_RELATIVE_ROOT).normalize();
        return Files.isDirectory(legacy) ? legacy : standard;
    }

    private Path workspaceAgentRootForWrite(String workspaceId, String worktreeId) {
        return workspaceStandardAgentRoot(repoRootForWorkspaceOperation(workspaceId, worktreeId));
    }

    private Path repoRootForPublicOperation(String worktreeId, UserId userId) {
        return Path.of(ownedPublicWorktree(worktreeId, userId).rootPath());
    }

    private Path repoRootForWorkspaceOperation(String workspaceId, String worktreeId) {
        Workspace workspace = existingWorkspace(workspaceId);
        if (worktreeId == null || worktreeId.isBlank()) {
            return workspaceRoot(workspace);
        }
        return Path.of(existingWorktree(worktreeId, AgentConfigScope.WORKSPACE, workspace.workspaceId()).rootPath());
    }

    private Path workspaceRoot(Workspace workspace) {
        return pathResolver.resolve(workspace.rootPath()).toAbsolutePath().normalize();
    }

    private AgentConfigWorktree existingWorktree(String worktreeId, AgentConfigScope scope, WorkspaceId workspaceId) {
        AgentConfigWorktree worktree = existingWorktreeForRouting(worktreeId, scope, workspaceId);
        if (worktree.linuxServerId() != null && !worktree.linuxServerId().equals(serverIdentity.linuxServerId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Agent worktree 不属于当前服务器",
                    Map.of(
                            "worktreeId", worktreeId,
                            "targetLinuxServerId", worktree.linuxServerId(),
                            "currentLinuxServerId", serverIdentity.linuxServerId()));
        }
        return worktree;
    }

    private AgentConfigWorktree ownedPublicWorktree(String worktreeId, UserId userId) {
        AgentConfigWorktree worktree = existingWorktree(worktreeId, AgentConfigScope.PUBLIC, null);
        if (!Objects.equals(userId, worktree.createdBy())) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Agent worktree 不存在", Map.of("worktreeId", worktreeId));
        }
        return worktree;
    }

    private boolean isReusablePublicWorktree(AgentConfigWorktree worktree, UserId userId) {
        try {
            String stableName = publicWorktreeName(userId);
            Path root = Path.of(worktree.rootPath());
            // 历史公共 worktree 可能带日期或手工名称；继续复用会让公共分支再次表现为版本化分支，
            // 因此只认当前用户的稳定名称，旧记录保留但不再挂载。
            return stableName.equals(worktree.worktreeName())
                    && stableName.equals(worktree.branch())
                    && Files.isDirectory(root)
                    && gitWorkspaceService.isGitRepository(root)
                    && worktree.branch().equals(gitWorkspaceService.currentBranch(root));
        } catch (Exception exception) {
            return false;
        }
    }

    private AgentConfigWorktree existingWorktreeForRouting(String worktreeId, AgentConfigScope scope, WorkspaceId workspaceId) {
        AgentConfigWorktree worktree = agentConfigRepository.findWorktree(requireText(worktreeId, "worktreeId 不能为空", "worktreeId"))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Agent worktree 不存在", Map.of("worktreeId", worktreeId)));
        if (worktree.scope() != scope || (workspaceId != null && !workspaceId.equals(worktree.workspaceId()))) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Agent worktree 不存在", Map.of("worktreeId", worktreeId));
        }
        if (worktree.status() != AgentConfigWorktreeStatus.ACTIVE) {
            throw new PlatformException(ErrorCode.CONFLICT, "Agent worktree 不可编辑", Map.of("worktreeId", worktreeId));
        }
        return worktree;
    }

    private Workspace existingWorkspace(String workspaceId) {
        Workspace workspace = existingWorkspaceForRouting(workspaceId);
        if (workspace.linuxServerId() != null && !workspace.linuxServerId().equals(serverIdentity.linuxServerId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区不属于当前服务器", Map.of("workspaceId", workspaceId));
        }
        return workspace;
    }

    private Workspace existingWorkspaceForRouting(String workspaceId) {
        WorkspaceId id = new WorkspaceId(requireText(workspaceId, "工作区 ID 不能为空", "workspaceId"));
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "工作区不存在", Map.of("workspaceId", workspaceId)));
        if (workspace.status() != WorkspaceStatus.ACTIVE) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区不可用", Map.of("workspaceId", workspaceId));
        }
        return workspace;
    }

    private PublicConfig requireEnabledPublicConfig() {
        return requireEnabledPublicConfig(null);
    }

    private PublicConfig requireEnabledPublicConfig(UserId userId) {
        PublicConfig config = publicConfig(userId);
        if (!config.enabled()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    publicGitUrlUnconfiguredMessage(),
                    Map.of("parameter", config.parameterName()));
        }
        return config;
    }

    /**
     * 返回公共配置 Git 地址缺失时的可执行提示，避免用户只看到“未配置”却不知道处理入口。
     */
    private String publicGitUrlUnconfiguredMessage() {
        return "公共 Agent Git 地址未配置，请先在“系统管理 → 通用参数管理”中配置 "
                + PARAM_PUBLIC_AGENT_GIT_URL;
    }

    private PublicConfig publicConfig() {
        return publicConfig(null);
    }

    private PublicConfig publicConfig(UserId userId) {
        // gitUrl 缺失或为 UNCONFIGURED 均视为公共级功能未启用（合法语义，不抛异常）。
        String parameterName = PARAM_PUBLIC_AGENT_GIT_URL;
        String storedGitUrl = optionalParameter(parameterName, UNCONFIGURED);
        String gitUrl = effectivePublicGitUrl(storedGitUrl, userId, parameterName);
        Path gitRoot = Path.of(requiredParameter(PARAM_PUBLIC_CONFIG_GIT_ROOT)).normalize();
        Path configDir = Path.of(optionalParameter(PARAM_PUBLIC_CONFIG_DIR, gitRoot.resolve("opencode").toString())).normalize();
        Path worktreeRoot = Path.of(requiredParameter(PARAM_PUBLIC_CONFIG_WORKTREE_ROOT)).normalize();
        return new PublicConfig(
                publicGitDeploymentMode,
                parameterName,
                storedGitUrl,
                gitUrl,
                gitRoot,
                configDir,
                worktreeRoot);
    }

    private String effectivePublicGitUrl(String storedGitUrl, UserId userId, String parameterName) {
        if (storedGitUrl == null || storedGitUrl.isBlank() || UNCONFIGURED.equalsIgnoreCase(storedGitUrl.trim())) {
            return storedGitUrl;
        }
        String fragment = storedGitUrl.trim();
        if (!PublicConfig.isInternalStoredGitUrl(fragment)) {
            return fragment;
        }
        validateInternalPublicGitUrl(fragment, parameterName);
        if (userId == null) return fragment;
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "用户不存在",
                        Map.of("userId", userId.value())));
        return "ssh://" + user.unifiedAuthId().trim() + "@" + fragment;
    }

    /**
     * 公共配置 Git 命令必须用当前操作人的有效 URL，避免内部部署保存片段被直接传给 ls-remote/clone。
     */
    private String publicGitCommandUrl(PublicConfig config, UserId userId) {
        return effectivePublicGitUrl(config.storedGitUrl(), userId, config.parameterName());
    }

    private void validateInternalPublicGitUrl(String gitUrl, String parameterName) {
        if (gitUrl.contains("://") || gitUrl.contains("@") || gitUrl.chars().anyMatch(Character::isWhitespace)) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "内部公共 Agent Git 地址必须为 host[:port]/path，不包含协议、用户和空白字符",
                    Map.of("parameter", parameterName));
        }
        int slash = gitUrl.indexOf('/');
        if (slash <= 0 || slash == gitUrl.length() - 1) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "内部公共 Agent Git 地址必须包含仓库路径",
                    Map.of("parameter", parameterName));
        }
        String path = gitUrl.substring(slash + 1);
        for (String segment : path.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "内部公共 Agent Git 地址路径无效",
                        Map.of("parameter", parameterName));
            }
        }
    }

    /**
     * 读取可选参数（已展开变量引用）：缺失或空白时回退到 defaultValue，用于语义性的"未配置"开关值。
     */
    private String optionalParameter(String englishName, String defaultValue) {
        return commonParameterValues.resolvedValue(englishName)
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    /**
     * 读取必填参数（已展开变量引用）：common_parameters 为唯一事实源，缺失或空白时抛异常，不在 yaml/代码预留 fallback。
     */
    private String requiredParameter(String englishName) {
        return commonParameterValues.resolvedValue(englishName)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "通用参数未配置：" + englishName,
                        Map.of("parameter", englishName)));
    }

    private AgentConfigProgress startProgress(
            String operationId,
            AgentConfigScope scope,
            WorkspaceId workspaceId,
            String action,
            String branch,
            String traceId) {
        String normalizedOperationId = normalizeOperationId(operationId);
        String normalizedTraceId = requireText(traceId, "traceId 不能为空", "traceId");
        AgentConfigOperation operation = AgentConfigOperation.started(
                normalizedOperationId,
                scope,
                workspaceId,
                action,
                normalizedTraceId,
                branch,
                now());
        agentConfigRepository.saveOperation(operation);
        AgentConfigProgress progress = new AgentConfigProgress(operation);
        progress.publish("snapshot", operation);
        return progress;
    }

    private String normalizeOperationId(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return "aco_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }
        String normalized = operationId.trim();
        if (!OPERATION_ID_PATTERN.matcher(normalized).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "operationId 格式无效", Map.of("operationId", normalized));
        }
        return normalized;
    }

    private String worktreeName(String baseName) {
        String normalized = requireText(baseName, "worktree 名称不能为空", "worktreeName");
        if (!WORKTREE_NAME_PATTERN.matcher(normalized).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "worktree 名称格式无效", Map.of("worktreeName", normalized));
        }
        return normalized + "-" + WORKTREE_SUFFIX_FORMATTER.format(now());
    }

    private String publicWorktreeName(UserId userId) {
        String userPart = userId.value().replaceAll("[^A-Za-z0-9._-]", "-");
        if (userPart.length() > 48) {
            userPart = userPart.substring(0, 48);
        }
        return "public-" + userPart;
    }

    private List<String> normalizeFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String file : files) {
            String value = requireText(file, "文件路径不能为空", "path").replace('\\', '/');
            if (value.startsWith("../") || value.contains("/../") || value.startsWith("/")) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "文件路径无效", Map.of("path", value));
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private String publicGitFile(String path) {
        return normalizeFiles(List.of(path)).get(0);
    }

    private String conflictRawStatus(Path repoRoot, String gitFile) {
        return gitWorkspaceService.parseStatusPorcelain(gitWorkspaceService.statusPorcelain(repoRoot)).stream()
                .filter(entry -> entry.path().equals(gitFile))
                .map(GitStatusEntry::rawStatus)
                .findFirst()
                .orElse("UU");
    }

    private String conflictStageContent(Path repoRoot, Set<Integer> stages, int stage, String gitFile) {
        return stages.contains(stage) ? gitWorkspaceService.conflictStageContent(repoRoot, stage, gitFile) : null;
    }

    private String readConflictWorkingContent(Path target) {
        try {
            if (!Files.exists(target)) {
                return null;
            }
            if (Files.size(target) > MAX_CONFLICT_FILE_BYTES) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "冲突文件超过 1MB 限制", Map.of());
            }
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "读取公共 Agent Git 冲突文件失败", Map.of(), exception);
        }
    }

    private String joinConflictSides(String current, String incoming) {
        if (current == null || incoming == null) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "文件删除冲突不能使用保留两者",
                    Map.of());
        }
        return current + (current.endsWith("\n") ? "" : "\n") + incoming;
    }

    private static long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    /**
     * 日志只保留远端定位信息，隐藏内部部署动态拼接的统一认证号或 HTTPS token。
     */
    private static String safeGitUrlForLog(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            return "";
        }
        String value = gitUrl.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("ssh://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getUserInfo() == null || uri.getHost() == null) {
                    return value;
                }
                String authority = "***@" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
                return new URI(uri.getScheme(), authority, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            } catch (Exception ignored) {
                return value.replaceFirst("^(https?://|ssh://)[^/@]+@", "$1***@");
            }
        }
        int at = value.indexOf('@');
        int colon = value.indexOf(':', at + 1);
        if (at > 0 && colon > at + 1 && !value.substring(0, at).contains("/")) {
            return "***@" + value.substring(at + 1);
        }
        return value;
    }

    private List<String> normalizeWorkspaceAgentFiles(List<String> files) {
        return normalizeFiles(files).stream()
                .map(this::workspaceAgentGitPath)
                .filter(path -> workspaceAgentDisplayPath(path) != null)
                .toList();
    }

    private String workspaceAgentGitPath(String path) {
        String value = requireText(path, "文件路径不能为空", "path").replace('\\', '/');
        int marker = value.indexOf(WORKSPACE_AGENT_RELATIVE_ROOT + "/");
        if (marker >= 0) {
            return value.substring(marker);
        }
        return value.startsWith(WORKSPACE_AGENT_RELATIVE_ROOT + "/")
                ? value
                : WORKSPACE_AGENT_RELATIVE_ROOT + "/" + value;
    }

    private String workspaceAgentDisplayPath(String gitPath) {
        String normalized = gitPath.replace('\\', '/');
        String prefix = WORKSPACE_AGENT_RELATIVE_ROOT + "/";
        if (!normalized.startsWith(prefix)) {
            return null;
        }
        String display = normalized.substring(prefix.length());
        // opencode.jsonc 是应用级 Agent/Skill 的运行态入口配置，必须与目录定义进入同一 Diff、提交和发布链路。
        return "opencode.jsonc".equals(display)
                || display.startsWith("agents/")
                || display.startsWith("skills/")
                || display.startsWith("tools/")
                ? display
                : null;
    }

    /**
     * 广播只负责低延迟唤醒其它节点；rollout 已持久化，调用方广播后必须直接返回，
     * 禁止在 HTTP 请求线程继续认领本机同步，后续统一由消费者或定时补偿任务执行。
     */
    private void broadcastPublicSync(String branch, String commitHash, String reason, String rolloutId, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("branch", branch);
        payload.put("commitHash", commitHash);
        payload.put("reason", reason);
        if (rolloutId != null && !rolloutId.isBlank()) {
            payload.put("rolloutId", rolloutId);
        }
        broadcastPublisher.publish(new ServerBroadcastEvent(
                RuntimeIdGenerator.serverBroadcastEventId(),
                PUBLIC_SYNC_EVENT,
                broadcastPublisher.instanceId(),
                serverIdentity.linuxServerId(),
                traceId,
                now(),
                Map.copyOf(payload)));
    }

    private String preparePublicConfigRollout(
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            UserId userId,
            String traceId) {
        if (publicConfigRolloutCoordinator == null) {
            return null;
        }
        return publicConfigRolloutCoordinator.prepare(
                branch,
                expectedCommitHash,
                previousCommitHash,
                serverIdentity.linuxServerId(),
                userId.value(),
                traceId);
    }

    private void activatePublicConfigRollout(String rolloutId, String commitHash) {
        if (rolloutId != null && publicConfigRolloutCoordinator != null) {
            publicConfigRolloutCoordinator.activate(rolloutId, commitHash);
        }
    }

    private void recordExpectedPublicConfigCommit(String rolloutId, String commitHash) {
        if (rolloutId != null && publicConfigRolloutCoordinator != null) {
            publicConfigRolloutCoordinator.recordExpectedCommit(rolloutId, commitHash);
        }
    }

    private void synchronizeLocalPublicRuntimeRepository(String rolloutId) {
        if (rolloutId == null || publicConfigRolloutCoordinator == null) {
            return;
        }
        try {
            publicConfigRolloutCoordinator.claimPendingSync(serverIdentity.linuxServerId())
                    .filter(request -> rolloutId.equals(request.rolloutId()))
                    .ifPresent(this::synchronizePublicRuntimeRepository);
        } catch (RuntimeException exception) {
            // 远端 push 与 rollout 激活已经完成；这里仅是本机同步的低延迟触发器。失败后保留数据库任务，
            // 由既有定时排空程序继续认领，不能把已经成功且不可逆的远端发布误报为失败。
            LOGGER.warn(
                    "event=agent_config_public_local_sync_kick_deferred rolloutId={} linuxServerId={} message={}",
                    rolloutId,
                    serverIdentity.linuxServerId(),
                    exception.getMessage(),
                    exception);
        }
    }

    /**
     * push 失败回包可能发生在远端已经接收提交之后；只有成功 fetch 且确认目标提交不在远端时才中止 PREPARING。
     */
    private void handleUncertainPushFailure(
            String rolloutId,
            Path repoRoot,
            String branch,
            String expectedCommitHash,
            String privateKey,
            Path rollbackRepoRoot,
            String rollbackCommitHash,
            PlatformException pushException) {
        Boolean contained = remoteContainsCommit(repoRoot, branch, expectedCommitHash, privateKey);
        if (Boolean.TRUE.equals(contained)) {
            LOGGER.warn(
                    "event=agent_config_public_push_response_uncertain_remote_confirmed rolloutId={} branch={} commitHash={}",
                    rolloutId,
                    branch,
                    expectedCommitHash);
            return;
        }
        if (Boolean.FALSE.equals(contained) && rolloutId != null && publicConfigRolloutCoordinator != null) {
            try {
                if (rollbackRepoRoot != null && rollbackCommitHash != null) {
                    gitWorkspaceService.resetHardToCommit(rollbackRepoRoot, rollbackCommitHash);
                }
                publicConfigRolloutCoordinator.abortPreparation(
                        rolloutId,
                        safeErrorMessage(pushException.getMessage()));
            } catch (Exception rollbackException) {
                LOGGER.warn(
                        "event=agent_config_public_push_rollback_failed rolloutId={} commitHash={} message={}",
                        rolloutId,
                        rollbackCommitHash,
                        safeErrorMessage(rollbackException.getMessage()));
            }
        }
        throw pushException;
    }

    /** null 表示远端验证本身失败，此时保留 PREPARING 交给定时恢复，不能误开门禁。 */
    private Boolean remoteContainsCommit(
            Path repoRoot,
            String branch,
            String expectedCommitHash,
            String privateKey) {
        try {
            gitWorkspaceService.fetch(repoRoot, privateKey);
            String remoteRef = "origin/" + branch;
            String remoteCommit = gitWorkspaceService.resolveCommit(repoRoot, remoteRef);
            return expectedCommitHash.equals(remoteCommit)
                    || gitWorkspaceService.isAncestor(repoRoot, expectedCommitHash, remoteRef);
        } catch (Exception verificationException) {
            LOGGER.warn(
                    "event=agent_config_public_push_remote_verify_failed branch={} commitHash={} message={}",
                    branch,
                    expectedCommitHash,
                    safeErrorMessage(verificationException.getMessage()));
            return null;
        }
    }

    private void reconcilePreparingPublicConfigRollout(PublicAgentConfigRolloutPreparation preparation) {
        if (preparation.createdAt().plus(PREPARATION_RECOVERY_DELAY).isAfter(now())) {
            return;
        }
        try {
            UserId initiator = new UserId(preparation.initiatedByUserId());
            PublicConfig config = requireEnabledPublicConfig(initiator);
            String privateKey = decryptSingleSshKey(initiator);
            if (PENDING_EXPECTED_COMMIT.equals(preparation.expectedCommitHash())) {
                // 该占位值只存在于最终 commit 回写之前，因此远端 push 必然尚未发起；超过恢复窗口后可安全回滚。
                if (preparation.createdAt().plus(PREPARATION_ABORT_DELAY).isBefore(now())
                        && preparation.previousCommitHash() != null
                        && !preparation.previousCommitHash().isBlank()) {
                    gitWorkspaceService.resetHardToCommit(
                            config.gitRoot(), preparation.previousCommitHash());
                    publicConfigRolloutCoordinator.abortPreparation(
                            preparation.rolloutId(), "LOCAL_COMMIT_NOT_RECORDED");
                }
                return;
            }
            if (!gitWorkspaceService.isGitRepository(config.gitRoot())) {
                ensurePublicRepositoryReady(config, preparation.branch(), privateKey);
            } else {
                ensurePublicRepositoryOriginReady(config.gitRoot(), config);
                gitWorkspaceService.fetch(config.gitRoot(), privateKey);
            }
            String remoteRef = "origin/" + preparation.branch();
            String remoteCommit = gitWorkspaceService.resolveCommit(config.gitRoot(), remoteRef);
            boolean expectedReached = preparation.expectedCommitHash() == null
                    || preparation.expectedCommitHash().isBlank()
                    || preparation.expectedCommitHash().equals(remoteCommit)
                    || gitWorkspaceService.isAncestor(
                            config.gitRoot(), preparation.expectedCommitHash(), remoteRef);
            if (!expectedReached) {
                if (preparation.createdAt().plus(PREPARATION_ABORT_DELAY).isBefore(now())) {
                    if (preparation.previousCommitHash() == null || preparation.previousCommitHash().isBlank()) {
                        return;
                    }
                    // 只有先把共享运行副本恢复到发布前提交，才允许终止 PREPARING 并重新开放消息。
                    gitWorkspaceService.resetHardToCommit(
                            config.gitRoot(), preparation.previousCommitHash());
                    publicConfigRolloutCoordinator.abortPreparation(preparation.rolloutId(), "REMOTE_COMMIT_NOT_REACHED");
                }
                return;
            }
            publicConfigRolloutCoordinator.activate(preparation.rolloutId(), remoteCommit);
            broadcastPublicSync(
                    preparation.branch(),
                    remoteCommit,
                    "preparing-recovery",
                    preparation.rolloutId(),
                    preparation.traceId());
            synchronizeLocalPublicRuntimeRepository(preparation.rolloutId());
        } catch (Exception exception) {
            LOGGER.warn(
                    "event=agent_config_public_preparing_recovery_retry rolloutId={} linuxServerId={} message={}",
                    preparation.rolloutId(),
                    serverIdentity.linuxServerId(),
                    safeErrorMessage(exception.getMessage()));
        }
    }

    private String existingRepositoryHead(Path repoRoot) {
        return gitWorkspaceService.isGitRepository(repoRoot)
                ? gitWorkspaceService.headCommit(repoRoot)
                : null;
    }

    /**
     * 在建立全员门禁前排除可预知的本地输入错误；真正的 reset/clone/fetch 仍只能在 PREPARING 内执行。
     */
    private void validatePublicRuntimeRepositoryBeforeRollout(
            PublicConfig config,
            boolean discardLocalChanges) {
        Path repoRoot = config.gitRoot();
        if (gitWorkspaceService.isGitRepository(repoRoot)) {
            ensurePublicRepositoryOriginReady(repoRoot, config);
            if (!discardLocalChanges && !gitWorkspaceService.isWorktreeClean(repoRoot)) {
                throw dirtyPublicRepositoryConflict(
                        repoRoot,
                        PublicRepositorySyncTarget.SHARED_RUNTIME);
            }
            return;
        }
        if (Files.exists(repoRoot) && !isEmptyDirectory(repoRoot)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "目录已存在且非空，但不是 Git 仓库：" + repoRoot,
                    Map.of("path", repoRoot.toString()));
        }
    }

    private Path publicStandardAgentRoot(Path repoRoot) {
        return repoRoot.resolve(PUBLIC_AGENT_RELATIVE_ROOT).normalize();
    }

    private Path workspaceStandardAgentRoot(Path repoRoot) {
        return repoRoot.resolve(WORKSPACE_AGENT_RELATIVE_ROOT).normalize();
    }

    private void ensureDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建目录失败", Map.of("path", directory.toString()), exception);
        }
    }

    private void ensureChild(Path root, Path child, String fieldName) {
        Path normalizedRoot = root.normalize();
        Path normalizedChild = child.normalize();
        if (!normalizedChild.startsWith(normalizedRoot)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "路径超出允许目录", Map.of(fieldName, child.toString()));
        }
    }

    private String requireText(String value, String message, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of("field", fieldName));
        }
        return value.trim();
    }

    private String safeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Agent 配置操作失败";
        }
        return message.replace('\r', ' ').replace('\n', ' ');
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private enum PublicRepositorySyncTarget {
        GENERIC("Git 工作树"),
        SHARED_RUNTIME("公共 Agent 服务器共享运行副本"),
        PERSONAL_WORKTREE("当前管理员公共 Agent 个人 worktree");

        private final String displayName;

        PublicRepositorySyncTarget(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }

    private record PublicConfig(
            CodeRepositoryDeploymentMode deploymentMode,
            String parameterName,
            String storedGitUrl,
            String gitUrl,
            Path gitRoot,
            Path configDir,
            Path worktreeRoot) {
        private boolean enabled() {
            return storedGitUrl != null && !storedGitUrl.isBlank() && !UNCONFIGURED.equalsIgnoreCase(storedGitUrl.trim());
        }

        private boolean internalDeployment() {
            return isInternalStoredGitUrl(storedGitUrl);
        }

        private boolean matchesOrigin(String originUrl) {
            if (originUrl == null) {
                return false;
            }
            String value = originUrl.trim();
            if (internalDeployment()) {
                return storedGitUrl.equals(stripInternalSshUser(value));
            }
            return storedGitUrl.equals(value);
        }

        private static String stripInternalSshUser(String value) {
            String prefix = "ssh://";
            if (!value.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return value;
            }
            String rest = value.substring(prefix.length());
            int at = rest.indexOf('@');
            return at > 0 ? rest.substring(at + 1) : value;
        }

        private static boolean isInternalStoredGitUrl(String value) {
            if (value == null || value.isBlank() || UNCONFIGURED.equalsIgnoreCase(value.trim())) {
                return false;
            }
            String normalized = value.trim();
            return !normalized.contains("://")
                    && !normalized.contains("@")
                    && normalized.contains("/")
                    && normalized.chars().noneMatch(Character::isWhitespace);
        }
    }

    private final class AgentConfigProgress {
        private AgentConfigOperation operation;

        private AgentConfigProgress(AgentConfigOperation operation) {
            this.operation = operation;
        }

        private void step(AgentConfigOperationStep step) {
            operation = agentConfigRepository.saveOperation(operation.step(step, now()));
            publish("step", operation);
        }

        private AgentConfigResponses.AgentConfigOperationResponse succeeded(String commitHash) {
            operation = agentConfigRepository.saveOperation(operation.succeeded(commitHash, now()));
            publish("completed", operation);
            return AgentConfigResponses.AgentConfigOperationResponse.from(operation);
        }

        private void failed(String errorCode, String errorMessage) {
            operation = agentConfigRepository.saveOperation(operation.failed(errorCode, errorMessage, now()));
            publish("failed", operation);
        }

        private void command(String command, String traceId) {
            if (command == null || command.isBlank()) {
                return;
            }
            try {
                progressSink.publish(new AgentConfigProgressEvent(
                        operation.operationId(),
                        "step",
                        operation.status(),
                        operation.currentStep().name(),
                        command,
                        null,
                        null,
                        null,
                        traceId,
                        now()));
            } catch (RuntimeException exception) {
                LOGGER.warn("发布公共 Agent Git 命令进度事件失败 operationId={} traceId={}", operation.operationId(), traceId, exception);
            }
        }

        private void publish(String type, AgentConfigOperation value) {
            progressSink.publish(new AgentConfigProgressEvent(
                    value.operationId(),
                    type,
                    value.status(),
                    value.currentStep().name(),
                    null,
                    value.errorCode(),
                    value.errorMessage(),
                    value.commitHash(),
                    value.traceId(),
                    value.updatedAt()));
        }
    }
}
