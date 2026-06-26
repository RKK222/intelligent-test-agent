package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.git.GitWorkspaceService;
import com.icbc.testagent.common.git.SshKeyEncryptionService;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastHandler;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.AgentConfigOperation;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStatus;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStep;
import com.icbc.testagent.domain.configuration.AgentConfigRepository;
import com.icbc.testagent.domain.configuration.AgentConfigScope;
import com.icbc.testagent.domain.configuration.AgentConfigWorktree;
import com.icbc.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent 配置应用服务：隔离公共级/工作空间级文件根目录、Git 操作和进度发布。
 */
@Service
public class AgentConfigApplicationService implements ServerBroadcastHandler {

    public static final String PUBLIC_SYNC_EVENT = "agent-config.public-sync-requested";

    private static final String PARAM_PUBLIC_AGENT_GIT_URL = "OPENCODE_PUBLIC_AGENT_GIT_URL";
    private static final String PARAM_PUBLIC_CONFIG_GIT_ROOT = "OPENCODE_PUBLIC_CONFIG_GIT_ROOT";
    private static final String PARAM_PUBLIC_CONFIG_WORKTREE_ROOT = "OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT";
    private static final String PARAM_PERSONAL_WORKTREE_ROOT = "OPENCODE_PERSONAL_WORKTREE_ROOT";
    private static final String UNCONFIGURED = "UNCONFIGURED";
    private static final String PUBLIC_AGENT_RELATIVE_ROOT = "opencode/agents";
    private static final String PUBLIC_AGENT_LEGACY_RELATIVE_ROOT = "opencode/agent";
    private static final String WORKSPACE_AGENT_RELATIVE_ROOT = ".opencode/agents";
    private static final String WORKSPACE_AGENT_LEGACY_RELATIVE_ROOT = ".opencode/agent";
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

    private final CommonParameterRepository commonParameterRepository;
    private final ConfigurationManagementRepository configurationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final GitRemoteService gitRemoteService;
    private final GitWorkspaceService gitWorkspaceService;
    private final SshKeyEncryptionService sshKeyEncryptionService;
    private final WorkspaceFileService fileService;
    private final WorkspaceServerIdentity serverIdentity;
    private final ServerBroadcastPublisher broadcastPublisher;
    private final Clock clock;
    private final AgentConfigProgressSink progressSink;

    /**
     * Spring 构造器：进度 Sink 由 API 模块提供，缺失时降级为 NOOP，便于模块级测试。
     */
    @Autowired
    public AgentConfigApplicationService(
            CommonParameterRepository commonParameterRepository,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceRepository workspaceRepository,
            AgentConfigRepository agentConfigRepository,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            ObjectProvider<AgentConfigProgressSink> progressSinkProvider,
            SshKeyEncryptionService sshKeyEncryptionService) {
        this(
                commonParameterRepository,
                configurationRepository,
                workspaceRepository,
                agentConfigRepository,
                new GitRemoteService(),
                new GitWorkspaceService(),
                sshKeyEncryptionService,
                fileService,
                serverIdentity,
                broadcastPublisher,
                Clock.systemUTC(),
                Optional.ofNullable(progressSinkProvider.getIfAvailable()).orElse(AgentConfigProgressSink.NOOP));
    }

    AgentConfigApplicationService(
            CommonParameterRepository commonParameterRepository,
            ConfigurationManagementRepository configurationRepository,
            WorkspaceRepository workspaceRepository,
            AgentConfigRepository agentConfigRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceFileService fileService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            Clock clock,
            AgentConfigProgressSink progressSink) {
        this.commonParameterRepository = Objects.requireNonNull(commonParameterRepository, "commonParameterRepository must not be null");
        this.configurationRepository = Objects.requireNonNull(configurationRepository, "configurationRepository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.agentConfigRepository = Objects.requireNonNull(agentConfigRepository, "agentConfigRepository must not be null");
        this.gitRemoteService = Objects.requireNonNull(gitRemoteService, "gitRemoteService must not be null");
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService, "gitWorkspaceService must not be null");
        this.sshKeyEncryptionService = Objects.requireNonNull(sshKeyEncryptionService, "sshKeyEncryptionService must not be null");
        this.fileService = Objects.requireNonNull(fileService, "fileService must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.broadcastPublisher = broadcastPublisher == null ? NOOP_BROADCAST : broadcastPublisher;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.progressSink = progressSink == null ? AgentConfigProgressSink.NOOP : progressSink;
    }

    public AgentConfigResponses.AgentConfigStatusResponse publicStatus(boolean superAdmin) {
        PublicConfig config = publicConfig();
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
        return new AgentConfigResponses.AgentConfigStatusResponse(
                AgentConfigScope.WORKSPACE.name(),
                true,
                superAdmin,
                null,
                workspace.rootPath(),
                workspaceStandardAgentRoot(Path.of(workspace.rootPath())).toString(),
                gitWorkspaceService.isGitRepository(Path.of(workspace.rootPath())) ? gitWorkspaceService.currentBranch(Path.of(workspace.rootPath())) : null,
                gitWorkspaceService.isGitRepository(Path.of(workspace.rootPath())) ? gitWorkspaceService.headCommit(Path.of(workspace.rootPath())) : null);
    }

    public List<String> publicBranches(UserId userId) {
        PublicConfig config = requireEnabledPublicConfig();
        return gitRemoteService.listBranches(config.gitUrl(), decryptSingleSshKey(userId));
    }

    public AgentConfigResponses.AgentConfigOperationResponse updatePublicConfig(
            String branch,
            String operationId,
            UserId userId,
            String traceId) {
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "update", normalizedBranch, traceId);
        try {
            PublicConfig config = requireEnabledPublicConfig();
            String privateKey = decryptSingleSshKey(userId);
            progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
            ensurePublicRepositoryReady(config, normalizedBranch, privateKey);
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

    public List<FileTreeEntryResponse> listPublicAgentFiles(String relativePath, String worktreeId) {
        Path agentRoot = publicAgentRootForRead(worktreeId);
        ensureDirectory(agentRoot);
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
        ensureDirectory(agentRoot);
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
        PublicConfig config = requireEnabledPublicConfig();
        String normalizedBranch = requireText(branch, "分支不能为空", "branch");
        String worktreeName = worktreeName(baseName);
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "create-worktree", normalizedBranch, traceId);
        try {
            String privateKey = decryptSingleSshKey(userId);
            progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
            ensureExistingCleanRepository(config.gitRoot(), config.gitUrl());
            gitWorkspaceService.fetch(config.gitRoot(), privateKey);
            gitWorkspaceService.checkoutTrackingBranch(config.gitRoot(), normalizedBranch, privateKey);
            gitWorkspaceService.pullFastForward(config.gitRoot(), normalizedBranch, privateKey);
            progress.step(AgentConfigOperationStep.CREATING_WORKTREE);
            Path worktreeRoot = config.worktreeRoot().resolve(worktreeName).normalize();
            ensureChild(config.worktreeRoot(), worktreeRoot, "worktreeName");
            gitWorkspaceService.createWorktree(config.gitRoot(), worktreeRoot, worktreeName, privateKey);
            AgentConfigWorktree worktree = new AgentConfigWorktree(
                    RuntimeIdGenerator.agentConfigWorktreeId(),
                    AgentConfigScope.PUBLIC,
                    null,
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
        Path repoRoot = Path.of(workspace.rootPath());
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.WORKSPACE, workspace.workspaceId(), "create-worktree", normalizedBranch, traceId);
        try {
            ensureExistingCleanRepository(repoRoot, null);
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
        return diff(repoRootForWorkspaceOperation(workspaceId, worktreeId));
    }

    public void publicStage(List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.stageFiles(repoRootForPublicOperation(worktreeId), normalizeFiles(files), decryptSingleSshKey(userId));
    }

    public void publicUnstage(List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.unstageFiles(repoRootForPublicOperation(worktreeId), normalizeFiles(files), decryptSingleSshKey(userId));
    }

    public void workspaceStage(String workspaceId, List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.stageFiles(repoRootForWorkspaceOperation(workspaceId, worktreeId), normalizeFiles(files), decryptSingleSshKey(userId));
    }

    public void workspaceUnstage(String workspaceId, List<String> files, String worktreeId, UserId userId) {
        gitWorkspaceService.unstageFiles(repoRootForWorkspaceOperation(workspaceId, worktreeId), normalizeFiles(files), decryptSingleSshKey(userId));
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
        PublicConfig config = requireEnabledPublicConfig();
        AgentConfigProgress progress = startProgress(operationId, AgentConfigScope.PUBLIC, null, "publish", null, traceId);
        try {
            String privateKey = decryptSingleSshKey(userId);
            String commitHash;
            String branch;
            if (worktreeId == null || worktreeId.isBlank()) {
                Path repoRoot = config.gitRoot();
                ensureExistingCleanRepository(repoRoot, config.gitUrl());
                branch = gitWorkspaceService.currentBranch(repoRoot);
                progress.step(AgentConfigOperationStep.PUSHING);
                gitWorkspaceService.push(repoRoot, branch, false, privateKey);
                commitHash = gitWorkspaceService.headCommit(repoRoot);
            } else {
                AgentConfigWorktree worktree = existingWorktree(worktreeId, AgentConfigScope.PUBLIC, null);
                Path repoRoot = config.gitRoot();
                ensureExistingCleanRepository(repoRoot, config.gitUrl());
                branch = gitWorkspaceService.currentBranch(repoRoot);
                progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
                gitWorkspaceService.pullFastForward(repoRoot, branch, privateKey);
                progress.step(AgentConfigOperationStep.MERGING);
                gitWorkspaceService.mergeBranch(repoRoot, worktree.branch(), privateKey);
                progress.step(AgentConfigOperationStep.PUSHING);
                gitWorkspaceService.push(repoRoot, branch, false, privateKey);
                commitHash = gitWorkspaceService.headCommit(repoRoot);
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
            Path repoRoot = Path.of(workspace.rootPath());
            ensureExistingCleanRepository(repoRoot, null);
            String branch = gitWorkspaceService.currentBranch(repoRoot);
            if (worktreeId != null && !worktreeId.isBlank()) {
                AgentConfigWorktree worktree = existingWorktree(worktreeId, AgentConfigScope.WORKSPACE, workspace.workspaceId());
                progress.step(AgentConfigOperationStep.PREPARING_REPOSITORY);
                gitWorkspaceService.pullFastForward(repoRoot, branch, privateKey);
                progress.step(AgentConfigOperationStep.MERGING);
                gitWorkspaceService.mergeBranch(repoRoot, worktree.branch(), privateKey);
                agentConfigRepository.saveWorktree(worktree.markPublished(now()));
            }
            progress.step(AgentConfigOperationStep.PUSHING);
            gitWorkspaceService.push(repoRoot, branch, false, privateKey);
            return progress.succeeded(gitWorkspaceService.headCommit(repoRoot));
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
            gitWorkspaceService.commitStaged(repoRoot, normalizedMessage, decryptSingleSshKey(userId));
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
        for (String line : statusOutput.lines().toList()) {
            if (line.length() < 4) {
                continue;
            }
            String status = line.substring(0, 2);
            String path = line.substring(3).trim();
            int rename = path.indexOf(" -> ");
            if (rename >= 0) {
                path = path.substring(rename + 4).trim();
            }
            boolean staged = status.charAt(0) != ' ' && status.charAt(0) != '?';
            String patch = gitWorkspaceService.diff(repoRoot, path, staged);
            files.put(path, new AgentConfigResponses.AgentConfigDiffFileResponse(path, status.trim(), staged, patch));
        }
        return new AgentConfigResponses.AgentConfigDiffResponse(List.copyOf(files.values()));
    }

    private void ensurePublicRepositoryReady(PublicConfig config, String branch, String privateKey) {
        Path gitRoot = config.gitRoot();
        if (Files.exists(gitRoot)) {
            ensureExistingCleanRepository(gitRoot, config.gitUrl());
            gitWorkspaceService.fetch(gitRoot, privateKey);
            gitWorkspaceService.checkoutTrackingBranch(gitRoot, branch, privateKey);
            gitWorkspaceService.pullFastForward(gitRoot, branch, privateKey);
            return;
        }
        ensureDirectory(gitRoot.getParent());
        gitWorkspaceService.cloneBranch(config.gitUrl(), branch, gitRoot, privateKey);
    }

    private void ensureExistingCleanRepository(Path repoRoot, String expectedOrigin) {
        if (!gitWorkspaceService.isGitRepository(repoRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "目录不是 Git 仓库", Map.of("path", repoRoot.toString()));
        }
        String origin = gitWorkspaceService.originUrl(repoRoot);
        if (expectedOrigin != null && !expectedOrigin.isBlank() && !Objects.equals(origin, expectedOrigin)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Git origin 与配置不一致", Map.of("path", repoRoot.toString()));
        }
        if (!gitWorkspaceService.isWorktreeClean(repoRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "Git 工作树存在未提交变更", Map.of("path", repoRoot.toString()));
        }
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
            return Path.of(workspace.rootPath());
        }
        return Path.of(existingWorktree(worktreeId, AgentConfigScope.WORKSPACE, workspace.workspaceId()).rootPath());
    }

    private AgentConfigWorktree existingWorktree(String worktreeId, AgentConfigScope scope, WorkspaceId workspaceId) {
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
        WorkspaceId id = new WorkspaceId(requireText(workspaceId, "工作区 ID 不能为空", "workspaceId"));
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "工作区不存在", Map.of("workspaceId", workspaceId)));
        if (workspace.status() != WorkspaceStatus.ACTIVE) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区不可用", Map.of("workspaceId", workspaceId));
        }
        if (workspace.linuxServerId() != null && !workspace.linuxServerId().equals(serverIdentity.linuxServerId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区不属于当前服务器", Map.of("workspaceId", workspaceId));
        }
        return workspace;
    }

    private PublicConfig requireEnabledPublicConfig() {
        PublicConfig config = publicConfig();
        if (!config.enabled()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "公共 Agent Git 地址未配置", Map.of("parameter", PARAM_PUBLIC_AGENT_GIT_URL));
        }
        return config;
    }

    private PublicConfig publicConfig() {
        // gitUrl 缺失或为 UNCONFIGURED 均视为公共级功能未启用（合法语义，不抛异常）。
        String gitUrl = optionalParameter(PARAM_PUBLIC_AGENT_GIT_URL, UNCONFIGURED);
        Path gitRoot = Path.of(requiredParameter(PARAM_PUBLIC_CONFIG_GIT_ROOT)).normalize();
        Path worktreeRoot = Path.of(requiredParameter(PARAM_PUBLIC_CONFIG_WORKTREE_ROOT)).normalize();
        return new PublicConfig(gitUrl, gitRoot, worktreeRoot);
    }

    /**
     * 读取可选参数：缺失或空白时回退到 defaultValue，用于语义性的"未配置"开关值。
     */
    private String optionalParameter(String englishName, String defaultValue) {
        ParameterPlatform current = ParameterPlatform.current();
        return commonParameterRepository.findByEnglishNameAndPlatform(englishName, current)
                .or(() -> commonParameterRepository.findByEnglishNameAndPlatform(englishName, ParameterPlatform.ALL))
                .map(parameter -> parameter.parameterValue())
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    /**
     * 读取必填参数：common_parameters 为唯一事实源，缺失或空白时抛异常，不在 yaml/代码预留 fallback。
     */
    private String requiredParameter(String englishName) {
        ParameterPlatform current = ParameterPlatform.current();
        return commonParameterRepository.findByEnglishNameAndPlatform(englishName, current)
                .or(() -> commonParameterRepository.findByEnglishNameAndPlatform(englishName, ParameterPlatform.ALL))
                .map(parameter -> parameter.parameterValue())
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

    private record PublicConfig(String gitUrl, Path gitRoot, Path worktreeRoot) {
        private boolean enabled() {
            return gitUrl != null && !gitUrl.isBlank() && !UNCONFIGURED.equalsIgnoreCase(gitUrl.trim());
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

        private void publish(String type, AgentConfigOperation value) {
            progressSink.publish(new AgentConfigProgressEvent(
                    value.operationId(),
                    type,
                    value.status(),
                    value.currentStep(),
                    value.errorCode(),
                    value.errorMessage(),
                    value.commitHash(),
                    value.traceId(),
                    value.updatedAt()));
        }
    }
}
