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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private ManagedWorkspaceApplicationService managedWorkspaceApplicationService;

    /** 应用配置发布复用托管 feature 版本的 HEAD 更新与广播链路。 */
    @Autowired
    void setManagedWorkspaceApplicationService(ManagedWorkspaceApplicationService service) {
        this.managedWorkspaceApplicationService = Objects.requireNonNull(service, "service must not be null");
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
        try {
            PublicConfig config = requireEnabledPublicConfig(userId);
            String privateKey = decryptSingleSshKey(userId);
            progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
            ensurePublicRepositoryReady(config, normalizedBranch, privateKey, discardLocalChanges);
            String commitHash = gitWorkspaceService.headCommit(config.gitRoot());
            progress.step(AgentConfigOperationStep.BROADCASTING);
            broadcastPublicSync(normalizedBranch, commitHash, "update", traceId);
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
        try {
            PublicConfig config = requireEnabledPublicConfig(userId);
            String privateKey = decryptSingleSshKey(userId);
            GitCommitIdentity commitIdentity = gitCommitIdentity(userId);
            Path repoRoot = config.gitRoot();
            if (!gitWorkspaceService.isGitRepository(repoRoot)) {
                throw publicRepositoryUninitialized(repoRoot);
            }
            // 内部部署的 origin 含当前管理员统一认证号；共享仓库可能由其他管理员初始化，
            // 每次联网操作前都必须刷新为本次操作人，避免“私钥正确但登录用户名仍是上一位管理员”。
            ensurePublicRepositoryOriginReady(repoRoot, config);
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
            gitWorkspaceService.push(repoRoot, normalizedBranch, false, privateKey);
            String commitHash = gitWorkspaceService.headCommit(repoRoot);
            progress.step(AgentConfigOperationStep.BROADCASTING);
            broadcastPublicSync(normalizedBranch, commitHash, "update-and-push", traceId);
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

    public ManagedWorkspaceResponses.WorkspaceGitConflictResponse getPublicGitConflict(String path, String worktreeId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId);
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

    public List<String> publicGitConflictFiles(String worktreeId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId);
        if (!gitWorkspaceService.isGitRepository(repoRoot)) {
            return List.of();
        }
        return gitWorkspaceService.conflictPaths(repoRoot);
    }

    public void resolvePublicGitConflict(String path, String resolution, String content, String worktreeId, UserId userId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId);
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
        Path repoRoot = repoRootForPublicOperation(worktreeId);
        if (!gitWorkspaceService.isMergeInProgress(repoRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前没有可取消的 Git 合并", Map.of());
        }
        gitWorkspaceService.abortMerge(repoRoot, decryptSingleSshKey(userId));
    }

    public void resolveAllPublicGitConflicts(String resolution, String worktreeId, UserId userId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId);
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

    public List<FileTreeEntryResponse> listPublicAgentFiles(String relativePath, String worktreeId) {
        Path agentRoot = publicAgentRootForRead(worktreeId);
        // 公共配置目录须由管理员初始化（git clone）后才会存在；未初始化时返回空列表，不自动创建，
        // 避免浏览即静默建出 OPENCODE_PUBLIC_CONFIG_DIR 空壳。
        if (!Files.isDirectory(agentRoot)) {
            return List.of();
        }
        return fileService.listDirectory(agentRoot.toString(), relativePath);
    }

    public FileContentResponse readPublicAgentFile(String relativePath, String worktreeId) {
        return fileService.readContent(publicAgentRootForRead(worktreeId).toString(), relativePath);
    }

    public void writePublicAgentFile(String relativePath, String content, String worktreeId) {
        if (worktreeId == null || worktreeId.isBlank()) {
            requireEnabledPublicConfig();
        }
        Path agentRoot = publicAgentRootForWrite(worktreeId);
        ensureDirectory(agentRoot);
        fileService.writeContent(agentRoot.toString(), relativePath, content);
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

    public void writeWorkspaceAgentFile(String workspaceId, String relativePath, String content, String worktreeId) {
        Path agentRoot = workspaceAgentRootForWrite(workspaceId, worktreeId);
        ensureDirectory(agentRoot);
        fileService.writeContent(agentRoot.toString(), relativePath, content);
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
        PublicConfig config = requireEnabledPublicConfig(userId);
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        String worktreeName = worktreeName(baseName);
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "create-worktree", normalizedBranch, traceId);
        try {
            String privateKey = decryptSingleSshKey(userId);
            progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
            ensureExistingPublicRepositoryReady(config, normalizedBranch, privateKey);
            progress.step(AgentConfigOperationStep.CREATING_WORKTREE);
            Path worktreeRoot = config.worktreeRoot().resolve(worktreeName).normalize();
            ensureChild(config.worktreeRoot(), worktreeRoot, "worktreeName");
            gitWorkspaceService.createWorktree(config.gitRoot(), worktreeRoot, worktreeName, privateKey);
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
            progress.succeeded(gitWorkspaceService.headCommit(config.gitRoot()));
            return AgentConfigResponses.AgentConfigWorktreeResponse.from(worktree, publicStandardAgentRoot(worktreeRoot).toString());
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "创建公共 Agent worktree 失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建公共 Agent worktree 失败", Map.of(), exception);
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
     * 查询某台服务器上的公共 ACTIVE worktree，并补齐创建人展示名供管理员切换时识别。
     */
    public List<AgentConfigResponses.AgentConfigWorktreeOptionResponse> listPublicWorktrees(String linuxServerId) {
        String targetServerId = requireText(linuxServerId, "服务器不能为空", "linuxServerId");
        return agentConfigRepository.findWorktrees(
                        AgentConfigScope.PUBLIC,
                        null,
                        null,
                        targetServerId,
                        AgentConfigWorktreeStatus.ACTIVE).stream()
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

    public AgentConfigResponses.AgentConfigDiffResponse publicDiff(String worktreeId) {
        return diff(repoRootForPublicOperation(worktreeId));
    }

    public AgentConfigResponses.AgentConfigDiffResponse workspaceDiff(String workspaceId, String worktreeId) {
        return workspaceDiff(repoRootForWorkspaceOperation(workspaceId, worktreeId));
    }

    public void publicStage(List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.stageFiles(repoRootForPublicOperation(worktreeId), normalizeFiles(files), decryptSingleSshKey(userId));
    }

    public void publicUnstage(List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.unstageFiles(repoRootForPublicOperation(worktreeId), normalizeFiles(files), decryptSingleSshKey(userId));
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

    public AgentConfigResponses.AgentConfigOperationResponse publicCommit(
            String message,
            String worktreeId,
            String operationId,
            UserId userId,
            String traceId) {
        return commit(repoRootForPublicOperation(worktreeId), AgentConfigScope.PUBLIC, null, message, operationId, userId, traceId);
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
        try {
            String privateKey = decryptSingleSshKey(userId);
            GitCommitIdentity commitIdentity = gitCommitIdentity(userId);
            String commitHash;
            String branch;
            if (worktreeId == null || worktreeId.isBlank()) {
                Path repoRoot = config.gitRoot();
                ensureExistingCleanRepository(repoRoot, config);
                branch = gitWorkspaceService.currentBranch(repoRoot);
                progress.step(AgentConfigOperationStep.PUSHING);
                commitHash = gitPublishWorkflow.publishDirectBranch(repoRoot, branch, false, privateKey).headCommit();
            } else {
                AgentConfigWorktree worktree = existingWorktree(worktreeId, AgentConfigScope.PUBLIC, null);
                Path repoRoot = config.gitRoot();
                ensureExistingCleanRepository(repoRoot, config);
                branch = gitWorkspaceService.currentBranch(repoRoot);
                progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
                progress.step(AgentConfigOperationStep.MERGING);
                GitPublishWorkflow.PublishResult result = gitPublishWorkflow.publishMergedBranch(
                        repoRoot,
                        branch,
                        worktree.branch(),
                        false,
                        privateKey,
                        commitIdentity);
                throwIfConflicted(result, "公共 Agent 配置合并冲突");
                progress.step(AgentConfigOperationStep.PUSHING);
                commitHash = result.headCommit();
                agentConfigRepository.saveWorktree(worktree.markPublished(now()));
            }
            progress.step(AgentConfigOperationStep.BROADCASTING);
            broadcastPublicSync(branch, commitHash, "publish", traceId);
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
        try {
            String privateKey = decryptSingleSshKey(userId);
            GitCommitIdentity commitIdentity = gitCommitIdentity(userId);
            Path repoRoot = workspaceRoot(workspace);
            ensureExistingCleanRepository(repoRoot, (String) null);
            String branch = gitWorkspaceService.currentBranch(repoRoot);
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
                managedWorkspaceApplicationService.recordFeatureWorkspacePublished(
                        workspaceId,
                        commitHash,
                        userId,
                        traceId);
            }
            return progress.succeeded(commitHash);
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), safeErrorMessage(exception.getMessage()));
            throw exception;
        } catch (Exception exception) {
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
        PublicConfig config = publicConfig();
        if (!config.enabled() || !gitWorkspaceService.isGitRepository(config.gitRoot())) {
            return;
        }
        if (!gitWorkspaceService.isWorktreeClean(config.gitRoot())) {
            return;
        }
        gitWorkspaceService.fetch(config.gitRoot(), null);
        gitWorkspaceService.checkoutTrackingBranch(config.gitRoot(), branch, null);
        gitWorkspaceService.resetHardToCommit(config.gitRoot(), commitHash);
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
     * 工作空间级 Agent 配置只允许展示 .opencode 下的 agents 与 skills。
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
        ensureExistingRepositoryReadyForSync(repoRoot, null, config, discardLocalChanges);
    }

    private void ensureExistingRepositoryReadyForSync(
            Path repoRoot,
            String expectedOrigin,
            PublicConfig config,
            boolean discardLocalChanges) {
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
                throw new PlatformException(ErrorCode.CONFLICT, "Git 工作树存在未提交变更", Map.of("path", repoRoot.toString()));
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
        List<String> paths = gitWorkspaceService.parseStatusPorcelain(gitWorkspaceService.statusPorcelain(repoRoot)).stream()
                .map(GitStatusEntry::path)
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .limit(6)
                .toList();
        if (paths.isEmpty()) {
            return "Git 工作树存在未提交变更";
        }
        boolean truncated = paths.size() > 5;
        List<String> visiblePaths = truncated ? paths.subList(0, 5) : paths;
        return "Git 工作树存在未提交变更：" + String.join("、", visiblePaths) + (truncated ? " 等" : "");
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

    private Path publicAgentRootForRead(String worktreeId) {
        Path repoRoot = repoRootForPublicOperation(worktreeId);
        Path standard = publicStandardAgentRoot(repoRoot);
        if (Files.isDirectory(standard)) {
            return standard;
        }
        Path legacy = repoRoot.resolve(PUBLIC_AGENT_LEGACY_RELATIVE_ROOT).normalize();
        return Files.isDirectory(legacy) ? legacy : standard;
    }

    private Path publicAgentRootForWrite(String worktreeId) {
        return publicStandardAgentRoot(repoRootForPublicOperation(worktreeId));
    }

    private String usernameFor(UserId userId) {
        return userRepository.findByUserId(userId)
                .map(User::username)
                .orElse(null);
    }

    /**
     * 将当前平台用户转换为 Git 单次提交身份；平台用户表没有邮箱字段，因此使用保留域名生成稳定地址。
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

    private Path repoRootForPublicOperation(String worktreeId) {
        if (worktreeId == null || worktreeId.isBlank()) {
            return requireEnabledPublicConfig().gitRoot();
        }
        return Path.of(existingWorktree(worktreeId, AgentConfigScope.PUBLIC, null).rootPath());
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
        return display.startsWith("agents/") || display.startsWith("skills/") ? display : null;
    }

    private void broadcastPublicSync(String branch, String commitHash, String reason, String traceId) {
        broadcastPublisher.publish(new ServerBroadcastEvent(
                RuntimeIdGenerator.serverBroadcastEventId(),
                PUBLIC_SYNC_EVENT,
                broadcastPublisher.instanceId(),
                serverIdentity.linuxServerId(),
                traceId,
                now(),
                Map.of(
                        "branch", branch,
                        "commitHash", commitHash,
                        "reason", reason)));
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
