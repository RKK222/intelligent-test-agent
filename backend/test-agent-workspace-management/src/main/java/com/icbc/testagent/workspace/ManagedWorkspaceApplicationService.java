package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.git.GitWorkspaceService;
import com.icbc.testagent.common.git.GitWorkspaceService.GitStatusEntry;
import com.icbc.testagent.common.git.SshKeyEncryptionService;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastHandler;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationWorkspace;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperation;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationRepository;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationStatus;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationStep;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplicaId;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.icbc.testagent.domain.managedworkspace.UserWorkspaceBranchPreference;
import com.icbc.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncDirection;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecord;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecordId;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncStatus;
import com.icbc.testagent.domain.managedworkspace.WorkspaceReplicaSyncStatus;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 托管工作区应用服务，负责把应用工作空间配置落到物理 Git 目录和运行态 Workspace。
 */
@Service
public class ManagedWorkspaceApplicationService implements ServerBroadcastHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedWorkspaceApplicationService.class);

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d{8}$");
    // 兼容前端"yyyy年M月"格式（如 2024年1月 / 2024年12月），用于「+新增版本」场景。
    // 版本字符串允许原样落库，但分支名/路径需要走 sanitizeVersionForBranchAndPath 转为安全片段。
    private static final Pattern VERSION_PATTERN_YEAR_MONTH = Pattern.compile("^(\\d{4})年(\\d{1,2})月$");
    private static final Pattern STANDARD_BRANCH_PATTERN = Pattern.compile("^feature_testagent_(\\d{8})$");
    private static final Pattern OPERATION_ID_PATTERN = Pattern.compile("^wco_[A-Za-z0-9_-]{8,128}$");
    private static final Pattern SCP_LIKE_SSH_URL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9._-]+:.+");
    private static final String VERSION_SYNC_EVENT = "workspace.version.sync-requested";
    private static final String PARAM_OPENCODE_APP_WORKSPACE_ROOT = "OPENCODE_APP_WORKSPACE_ROOT";
    private static final String PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT = "OPENCODE_PERSONAL_WORKTREE_ROOT";
    private static final ServerBroadcastPublisher NOOP_BROADCAST_PUBLISHER = event -> { };
    private static final CommonParameterValues EMPTY_PARAMETER_VALUES = new CommonParameterValues() {
        @Override
        public Optional<String> resolvedValue(String englishName) {
            return Optional.empty();
        }

        @Override
        public Optional<String> resolvedValue(String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
            return Optional.empty();
        }

        @Override
        public Optional<com.icbc.testagent.domain.configuration.CommonParameter> raw(
                String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
            return Optional.empty();
        }

        @Override
        public java.util.List<com.icbc.testagent.domain.configuration.CommonParameter> findAll() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.icbc.testagent.domain.configuration.ResolvedParameter> resolvedAll() {
            return java.util.List.of();
        }
    };
    private static final WorkspaceCreateOperationRepository NOOP_OPERATION_REPOSITORY = new NoopWorkspaceCreateOperationRepository();

    private final ConfigurationManagementRepository configurationRepository;
    private final CommonParameterValues commonParameterValues;
    private final ManagedWorkspacePathResolver pathResolver;
    private final WorkspaceCreateOperationRepository workspaceCreateOperationRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final GitRemoteService gitRemoteService;
    private final GitWorkspaceService gitWorkspaceService;
    private final GitPublishWorkflow gitPublishWorkflow;
    private final SshKeyEncryptionService sshKeyEncryptionService;
    private final WorkspaceServerIdentity serverIdentity;
    private final ServerBroadcastPublisher broadcastPublisher;
    private final String broadcastInstanceId;
    private final Object defaultPersonalWorkspaceLock = new Object();

    /**
     * Spring 构造器：注入通用参数仓库和 SSH key 加密密钥。
     * 工作区根目录统一从 common_parameters 读取，不再在 yaml 预留 fallback。
     */
    @Autowired
    public ManagedWorkspaceApplicationService(
            ConfigurationManagementRepository configurationRepository,
            CommonParameterValues commonParameterValues,
            WorkspaceCreateOperationRepository workspaceCreateOperationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            SshKeyEncryptionService sshKeyEncryptionService) {
        this(
                configurationRepository,
                commonParameterValues,
                workspaceCreateOperationRepository,
                managedWorkspaceRepository,
                workspaceRepository,
                userRepository,
                new GitRemoteService(),
                new GitWorkspaceService(),
                sshKeyEncryptionService,
                serverIdentity,
                broadcastPublisher);
    }

    /**
     * 测试构造器：允许注入 fake Git 服务。
     */
    ManagedWorkspaceApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService) {
        this(
                configurationRepository,
                EMPTY_PARAMETER_VALUES,
                NOOP_OPERATION_REPOSITORY,
                managedWorkspaceRepository,
                workspaceRepository,
                userRepository,
                gitRemoteService,
                gitWorkspaceService,
                sshKeyEncryptionService,
                new WorkspaceServerIdentity("127.0.0.1"),
                NOOP_BROADCAST_PUBLISHER);
    }

    /**
     * 测试构造器：允许注入 fake Git 服务、服务器身份和广播发布器。
     */
    ManagedWorkspaceApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher) {
        this(
                configurationRepository,
                EMPTY_PARAMETER_VALUES,
                NOOP_OPERATION_REPOSITORY,
                managedWorkspaceRepository,
                workspaceRepository,
                userRepository,
                gitRemoteService,
                gitWorkspaceService,
                sshKeyEncryptionService,
                serverIdentity,
                broadcastPublisher);
    }

    /**
     * 测试构造器：允许注入 fake Git 服务、通用参数仓库、服务器身份和广播发布器。
     * operation 进度仓库使用 noop 实现，工作区根目录从 common_parameters 读取。
     */
    ManagedWorkspaceApplicationService(
            ConfigurationManagementRepository configurationRepository,
            CommonParameterValues commonParameterValues,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher) {
        this(
                configurationRepository,
                commonParameterValues,
                NOOP_OPERATION_REPOSITORY,
                managedWorkspaceRepository,
                workspaceRepository,
                userRepository,
                gitRemoteService,
                gitWorkspaceService,
                sshKeyEncryptionService,
                serverIdentity,
                broadcastPublisher);
    }

    ManagedWorkspaceApplicationService(
            ConfigurationManagementRepository configurationRepository,
            CommonParameterValues commonParameterValues,
            WorkspaceCreateOperationRepository workspaceCreateOperationRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            GitRemoteService gitRemoteService,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository, "configurationRepository must not be null");
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
        this.pathResolver = new ManagedWorkspacePathResolver(this.commonParameterValues);
        this.workspaceCreateOperationRepository = Objects.requireNonNull(workspaceCreateOperationRepository, "workspaceCreateOperationRepository must not be null");
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository, "managedWorkspaceRepository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.gitRemoteService = Objects.requireNonNull(gitRemoteService, "gitRemoteService must not be null");
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService, "gitWorkspaceService must not be null");
        this.gitPublishWorkflow = new GitPublishWorkflow(this.gitWorkspaceService);
        this.sshKeyEncryptionService = Objects.requireNonNull(sshKeyEncryptionService, "sshKeyEncryptionService must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
        this.broadcastInstanceId = this.broadcastPublisher.instanceId();
    }

    public List<ManagedWorkspaceResponses.ManagedApplicationResponse> listApplications(UserId userId) {
        return configurationRepository.findApplicationsByMember(userId).stream()
                .map(ManagedWorkspaceResponses.ManagedApplicationResponse::from)
                .toList();
    }

    public List<ManagedWorkspaceResponses.WorkspaceTemplateResponse> listTemplates(String appId, UserId userId) {
        ApplicationId applicationId = existingMemberApp(appId, userId, "workspace-templates").appId();
        Map<String, Boolean> standardByRepoId = configurationRepository.findRepositoriesByApplication(applicationId).stream()
                .collect(Collectors.toMap(
                        repo -> repo.repositoryId().value(),
                        CodeRepository::standard));
        return configurationRepository.findWorkspaces(applicationId).stream()
                .map(workspace -> ManagedWorkspaceResponses.WorkspaceTemplateResponse.from(
                        workspace,
                        standardByRepoId.getOrDefault(workspace.repositoryId().value(), false)))
                .toList();
    }

    public List<ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse> listVersions(String templateId, UserId userId) {
        ApplicationWorkspace template = existingTemplate(new ApplicationWorkspaceId(templateId));
        ensureMember(template.appId(), userId, loadingContext("workspace-versions")
                .applicationWorkspaceId(template.workspaceId().value())
                .workspaceKind("应用工作空间模板")
                .workspaceName(template.workspaceName()));
        return managedWorkspaceRepository.findVersions(template.workspaceId()).stream()
                .map(this::versionResponse)
                .toList();
    }

    public ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse createVersion(
            String appId,
            String templateId,
            String version,
            String branch,
            UserId userId,
            String traceId) {
        return createVersion(appId, templateId, version, branch, userId, serverIdentity.linuxServerId(), traceId);
    }

    public ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse createVersion(
            String appId,
            String templateId,
            String version,
            String branch,
            UserId userId,
            String targetLinuxServerId,
            String traceId) {
        try {
            return doCreateVersion(appId, templateId, version, branch, userId, targetLinuxServerId, traceId);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建应用版本工作区失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    private ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse doCreateVersion(
            String appId,
            String templateId,
            String version,
            String branch,
            UserId userId,
            String targetLinuxServerId,
            String traceId) {
        ApplicationDefinition application = existingMemberApp(appId, userId, "create-workspace-version");
        ApplicationWorkspace template = existingTemplate(new ApplicationWorkspaceId(templateId));
        if (!template.appId().equals(application.appId())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作空间不属于当前应用", Map.of("workspaceId", templateId));
        }
        String normalizedVersion = normalizeVersion(version);
        CodeRepository repository = existingRepository(template.repositoryId());
        String resolvedBranch = resolveBranch(repository, normalizedVersion, branch, userId);
        return createVersionFromTemplate(
                application,
                template,
                repository,
                normalizedVersion,
                resolvedBranch,
                userId,
                targetLinuxServerId,
                traceId,
                true,
                WorkspaceCreateProgress.noop());
    }

    public ManagedWorkspaceResponses.ApplicationWorkspaceCreateResponse createApplicationWorkspaceWithInitialVersion(
            String appId,
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName,
            String version,
            String operationId,
            UserId userId,
            String targetLinuxServerId,
            String traceId) {
        String normalizedOperationId = normalizeOperationId(operationId).orElse(null);
        WorkspaceCreateProgress progress = WorkspaceCreateProgress.noop();
        try {
            ApplicationDefinition application = existingEnabledApp(appId);
            progress = createProgress(normalizedOperationId, application.appId(), userId, traceId);
            progress.step(WorkspaceCreateOperationStep.VALIDATING_INPUT);
            CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
            requireRepositoryEnglishName(repository);
            ensureRepositoryLinked(application.appId(), repository.repositoryId());
            String normalizedBranch = requireText(branch, "分支不能为空", "branch");
            String normalizedPath = normalizeDirectoryPath(directoryPath);
            String normalizedVersion = repository.standard()
                    ? versionFromStandardBranch(normalizedBranch)
                    : normalizeNonStandardWorkspaceCreateVersion(version);

            progress.step(WorkspaceCreateOperationStep.SAVING_TEMPLATE);
            ApplicationWorkspace template = saveOrReuseWorkspaceTemplate(
                    application.appId(),
                    repository.repositoryId(),
                    normalizedBranch,
                    normalizedPath,
                    workspaceName);

            progress.step(WorkspaceCreateOperationStep.RESOLVING_VERSION);
            ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse initialVersion = createVersionFromTemplate(
                    application,
                    template,
                    repository,
                    normalizedVersion,
                    normalizedBranch,
                    userId,
                    targetLinuxServerId,
                    traceId,
                    configurationRepository.isActiveMember(application.appId(), userId),
                    progress);
            progress.succeeded(template.workspaceId(), new ApplicationWorkspaceVersionId(initialVersion.versionId()));
            return ManagedWorkspaceResponses.ApplicationWorkspaceCreateResponse.from(template, initialVersion);
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "创建应用工作空间失败");
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建应用工作空间失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    /**
     * 异步创建工作空间的入口：验证参数、初始化 operation、启动异步任务、立即返回。
     * 前端通过返回的 operationId 轮询进度。
     */
    public ManagedWorkspaceResponses.CreateWorkspaceAcceptedResponse createWorkspaceAccepted(
            String appId,
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName,
            String version,
            String operationId,
            UserId userId,
            String targetLinuxServerId,
            String traceId) {
        String normalizedOperationId = normalizeOperationId(operationId)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "operationId 不能为空", Map.of("field", "operationId")));

        // 快速校验必要参数（确保 application 和 repository 存在）
        ApplicationDefinition application = existingEnabledApp(appId);
        CodeRepository repository = existingRepository(new CodeRepositoryId(repositoryId));
        requireRepositoryEnglishName(repository);
        ensureRepositoryLinked(application.appId(), repository.repositoryId());

        // 初始化 operation 记录（状态 RUNNING，步骤 VALIDATING_INPUT）
        WorkspaceCreateProgress progress = createProgress(normalizedOperationId, application.appId(), userId, traceId);
        LOGGER.info("Workspace create accepted, operationId={}, appId={}", normalizedOperationId, appId);

        // 启动异步任务执行实际创建逻辑
        Mono.fromRunnable(() -> executeWorkspaceCreateAsync(
                application,
                repository,
                branch,
                directoryPath,
                workspaceName,
                version,
                normalizedOperationId,
                userId,
                targetLinuxServerId,
                traceId,
                progress))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> {
                    LOGGER.error("Failed to schedule workspace create async, operationId={}, error={}",
                            normalizedOperationId, error.getMessage(), error);
                    progress.failed("SCHEDULE_FAILED", "无法启动异步任务: " + error.getMessage());
                })
                .subscribe();

        LOGGER.info("Workspace create async scheduled, operationId={}", normalizedOperationId);

        // 立即返回 accepted 响应
        return new ManagedWorkspaceResponses.CreateWorkspaceAcceptedResponse(
                normalizedOperationId,
                "ACCEPTED",
                Instant.now());
    }

    /**
     * 异步执行工作空间创建的详细逻辑，由 boundedElastic 线程池执行。
     * 异常会被捕获并记录到 operation 中。
     */
    private void executeWorkspaceCreateAsync(
            ApplicationDefinition application,
            CodeRepository repository,
            String branch,
            String directoryPath,
            String workspaceName,
            String version,
            String normalizedOperationId,
            UserId userId,
            String targetLinuxServerId,
            String traceId,
            WorkspaceCreateProgress progress) {
        LOGGER.info("Starting async workspace create, operationId={}", normalizedOperationId);
        try {
            // 校验并规范化参数
            String normalizedBranch = requireText(branch, "分支不能为空", "branch");
            String normalizedPath = normalizeDirectoryPath(directoryPath);
            String normalizedVersion = repository.standard()
                    ? versionFromStandardBranch(normalizedBranch)
                    : normalizeNonStandardWorkspaceCreateVersion(version);

            progress.step(WorkspaceCreateOperationStep.SAVING_TEMPLATE);
            ApplicationWorkspace template = saveOrReuseWorkspaceTemplate(
                    application.appId(),
                    repository.repositoryId(),
                    normalizedBranch,
                    normalizedPath,
                    workspaceName);

            progress.step(WorkspaceCreateOperationStep.RESOLVING_VERSION);
            ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse initialVersion = createVersionFromTemplate(
                    application,
                    template,
                    repository,
                    normalizedVersion,
                    normalizedBranch,
                    userId,
                    targetLinuxServerId,
                    traceId,
                    configurationRepository.isActiveMember(application.appId(), userId),
                    progress);

            LOGGER.info("Workspace create completed, operationId={}, workspaceId={}, versionId={}",
                    normalizedOperationId, template.workspaceId().value(), initialVersion.versionId());
            progress.succeeded(template.workspaceId(), new ApplicationWorkspaceVersionId(initialVersion.versionId()));
            LOGGER.info("Workspace create succeeded marked, operationId={}", normalizedOperationId);
        } catch (PlatformException exception) {
            progress.failed(exception.errorCode().name(), exception.getMessage());
            LOGGER.warn("Workspace create async failed, operationId={}, error={}", normalizedOperationId, exception.getMessage());
        } catch (Exception exception) {
            progress.failed(ErrorCode.INTERNAL_ERROR.name(), "创建应用工作空间失败");
            LOGGER.error("Workspace create async failed unexpectedly, operationId={}", normalizedOperationId, exception);
        }
    }

    public ManagedWorkspaceResponses.WorkspaceCreateOperationResponse getWorkspaceCreateOperation(String operationId, UserId userId) {
        String normalizedOperationId = normalizeOperationId(operationId)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "operationId 不能为空", Map.of("field", "operationId")));
        WorkspaceCreateOperation operation = workspaceCreateOperationRepository.findById(normalizedOperationId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "工作空间创建进度不存在", Map.of("operationId", normalizedOperationId)));
        if (!operation.requestedBy().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无工作空间创建进度权限", Map.of("operationId", normalizedOperationId));
        }
        return ManagedWorkspaceResponses.WorkspaceCreateOperationResponse.from(operation);
    }

    private ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse createVersionFromTemplate(
            ApplicationDefinition application,
            ApplicationWorkspace template,
            CodeRepository repository,
            String normalizedVersion,
            String resolvedBranch,
            UserId userId,
            String targetLinuxServerId,
            String traceId,
            boolean markRecent,
            WorkspaceCreateProgress progress) {
        Optional<ApplicationWorkspaceVersion> existing = managedWorkspaceRepository.findVersionByTemplateAndVersion(template.workspaceId(), normalizedVersion);
        if (existing.isPresent()) {
            LOGGER.info("Version already exists, templateId={}, version={}", template.workspaceId().value(), normalizedVersion);
            ApplicationWorkspaceVersion current = existing.get();
            ApplicationWorkspaceVersionReplica replica = ensureReplicaForTarget(current, template, userId, targetLinuxServerId, "EXISTING_VERSION", traceId);
            if (markRecent) {
                markRecent(userId, application.appId(), replica.runtimeWorkspaceId());
            }
            return versionResponse(current, replica);
        }
        String target = requireText(targetLinuxServerId, "目标服务器不能为空", "targetLinuxServerId");
        Path repoRoot = appRepoRoot(normalizedVersion, repository);
        Path workspaceRoot = repoRoot.resolve(template.directoryPath()).normalize();
        String repoRootValue = appRepoValue(normalizedVersion, repository);
        String workspaceRootValue = appWorkspaceValue(normalizedVersion, repository, template);
        String privateKey = privateKeyFor(repository, userId);
        progress.step(WorkspaceCreateOperationStep.PREPARING_REPOSITORY);
        prepareApplicationRepo(repository, resolvedBranch, repoRoot, workspaceRoot, privateKey, effectiveGitUrl(repository, userId));
        progress.step(WorkspaceCreateOperationStep.CREATING_RUNTIME_WORKSPACE);
        Workspace runtimeWorkspace = createRuntimeWorkspace(
                template.workspaceName() + "-" + normalizedVersion,
                workspaceRoot,
                workspaceRootValue,
                traceId);
        Instant now = Instant.now();
        ApplicationWorkspaceVersion saved = managedWorkspaceRepository.saveVersion(new ApplicationWorkspaceVersion(
                new ApplicationWorkspaceVersionId(RuntimeIdGenerator.applicationWorkspaceVersionId()),
                template.workspaceId(),
                template.appId(),
                repository.repositoryId(),
                normalizedVersion,
                resolvedBranch,
                repoRootValue,
                workspaceRootValue,
                runtimeWorkspace.workspaceId(),
                userId,
                ManagedWorkspaceStatus.ACTIVE,
                gitWorkspaceService.headCommit(repoRoot),
                Instant.now(),
                now,
                now));
        ApplicationWorkspaceVersionReplica replica = saveReadyReplica(
                saved,
                realPath(repoRoot),
                realPath(workspaceRoot),
                repoRootValue,
                workspaceRootValue,
                runtimeWorkspace.workspaceId(),
                saved.targetCommitHash(),
                traceId,
                now);
        if (!serverIdentity.linuxServerId().equals(target)) {
            publishVersionSync(saved, userId, "CREATED", traceId, Map.of("targetLinuxServerId", target));
            ApplicationWorkspaceVersionReplica targetReplica = waitForReadyReplica(saved.versionId(), target)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.CONFLICT,
                    "目标服务器应用版本工作区副本未就绪",
                            Map.of("targetLinuxServerId", target, "versionId", saved.versionId().value())));
            if (markRecent) {
                markRecent(userId, application.appId(), targetReplica.runtimeWorkspaceId());
            }
            return versionResponse(existingVersion(saved.versionId()), targetReplica);
        }
        if (markRecent) {
            markRecent(userId, application.appId(), replica.runtimeWorkspaceId());
        }
        publishVersionSync(saved, userId, "CREATED", traceId, Map.of());
        return ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse.from(
                versionForResponse(saved),
                replicaForResponse(replica),
                workspaceForResponse(runtimeWorkspace));
    }

    public List<ManagedWorkspaceResponses.PersonalWorkspaceResponse> listPersonalWorkspaces(String versionId, UserId userId) {
        ApplicationWorkspaceVersion version = existingVersion(new ApplicationWorkspaceVersionId(versionId));
        ensureMember(version.appId(), userId, loadingContextForVersion(
                "personal-workspaces",
                version,
                "个人工作区列表",
                null));
        return managedWorkspaceRepository.findPersonalWorkspaces(version.versionId(), userId).stream()
                .map(this::personalResponse)
                .toList();
    }

    public ManagedWorkspaceResponses.PersonalWorkspaceResponse createPersonalWorkspace(
            String versionId,
            String workspaceName,
            UserId userId,
            String traceId) {
        try {
            return doCreatePersonalWorkspace(versionId, workspaceName, userId, traceId);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建个人工作区失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    private ManagedWorkspaceResponses.PersonalWorkspaceResponse doCreatePersonalWorkspace(
            String versionId,
            String workspaceName,
            UserId userId,
            String traceId) {
        ApplicationWorkspaceVersion version = existingVersion(new ApplicationWorkspaceVersionId(versionId));
        ensureMember(version.appId(), userId, loadingContextForVersion(
                "create-personal-workspace",
                version,
                "个人工作区",
                workspaceName));
        return createPersonalWorkspaceForVersion(
                version,
                workspaceName,
                userId,
                traceId);
    }

    /**
     * 自定义命名个人工作区和 default 个人工作区共用同一套落库流程。
     * 调用方负责成员校验；创建前必须先确保当前服务器应用版本副本，避免旧绝对路径被当作 Git 根目录。
     */
    private ManagedWorkspaceResponses.PersonalWorkspaceResponse createPersonalWorkspaceForVersion(
            ApplicationWorkspaceVersion version,
            String workspaceName,
            UserId userId,
            String traceId) {
        ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
        String normalizedName = requireText(workspaceName, "个人工作区名称不能为空", "workspaceName");
        PersonalWorkspaceId personalId = new PersonalWorkspaceId(RuntimeIdGenerator.personalWorkspaceId());
        String branch = personalWorkspaceBranch(version, userId, normalizedName);
        Path repoRoot = personalRepoRootWithName(version, userId, branch);
        Path workspaceRoot = repoRoot.resolve(template.directoryPath()).normalize();
        String repoRootValue = personalRepoValue(version, userId, branch);
        String workspaceRootValue = personalWorkspaceValue(version, template, userId, branch);
        CodeRepository repository = existingRepository(version.repositoryId());
        ApplicationWorkspaceVersionReplica applicationReplica = ensureLocalReplica(version, template, userId, traceId);
        ensurePersonalWorktreeRoot(
                pathResolver.resolve(applicationReplica.repoRootPath()),
                repoRoot,
                branch,
                privateKeyFor(repository, userId));
        workspaceRoot = effectiveWorkspaceRoot(repoRoot, workspaceRoot);
        if (workspaceRoot.equals(repoRoot.toAbsolutePath().normalize())) {
            workspaceRootValue = repoRootValue;
        }
        Workspace runtimeWorkspace = createRuntimeWorkspace(normalizedName, workspaceRoot, workspaceRootValue, traceId);
        Instant now = Instant.now();
        PersonalWorkspace saved = managedWorkspaceRepository.savePersonalWorkspace(new PersonalWorkspace(
                personalId,
                version.versionId(),
                version.appId(),
                version.applicationWorkspaceId(),
                userId,
                normalizedName,
                branch,
                repoRootValue,
                workspaceRootValue,
                runtimeWorkspace.workspaceId(),
                gitWorkspaceService.headCommit(repoRoot),
                ManagedWorkspaceStatus.ACTIVE,
                now,
                now));
        markRecent(userId, version.appId(), runtimeWorkspace.workspaceId());
        return ManagedWorkspaceResponses.PersonalWorkspaceResponse.from(
                personalForResponse(saved),
                workspaceForResponse(runtimeWorkspace));
    }

    private String personalWorkspaceBranch(ApplicationWorkspaceVersion version, UserId userId, String workspaceName) {
        return version.branch() + "_" + sanitizeBranchPart(userId.value()) + "_" + sanitizeBranchPart(workspaceName);
    }

    public ManagedWorkspaceResponses.WorkspaceRuntimeResponse markRecentWorkspace(String workspaceId, UserId userId) {
        Workspace workspace = existingWorkspace(new WorkspaceId(workspaceId));
        ApplicationId appId = appIdForRuntimeWorkspace(workspace.workspaceId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "托管工作区不存在", Map.of("workspaceId", workspaceId)));
        ApplicationDefinition application = existingEnabledApp(appId.value());
        ensureMember(appId, userId, loadingContextForWorkspace("mark-recent-workspace", application, workspace));
        markRecent(userId, appId, workspace.workspaceId());
        // 复用 recent-workspace 的回填逻辑，把 appId/versionId/applicationWorkspaceId 一起回写，
        // 让前端在切到运行态 Workspace 时立即拿到当前版本与模板信息，无需等模板 versions 异步加载。
        return resolveRecentWorkspaceResponse(workspace);
    }

    public Optional<ManagedWorkspaceResponses.WorkspaceRuntimeResponse> recentWorkspace(UserId userId) {
        // 全局最近工作区会随用户切换应用而变化；为了让前端在重新登录或换电脑时能直接还原
        // 上一次所在的应用 + 模板 + 版本组合（让左下角"切换工作空间"按钮立刻显示当前工作区），
        // 这里在返回时把工作区所属的托管应用 appId、应用版本 versionId、模板 applicationWorkspaceId 一并写出。
        // 工作区不属于任何应用版本时（例如历史手动注册或超级管理员服务器工作空间），三者留空；前端只选择应用，
        // 不再兜底加载首模板首版本。
        return managedWorkspaceRepository.findGlobalPreference(userId)
                .flatMap(preference -> workspaceRepository.findById(preference.workspaceId()))
                .map(workspace -> resolveRecentWorkspaceResponse(workspace));
    }

    public Optional<ManagedWorkspaceResponses.WorkspaceRuntimeResponse> recentWorkspace(String appId, UserId userId) {
        // 应用级最近工作区按应用维度持久化；同样在响应里回写应用 / 版本 / 模板 ID，
        // 让前端在「切换应用 → 自动进入 per-app recent」链路里也能立即定位到当前版本并高亮工作区菜单。
        // 工作区对应的运行态 Workspace 已被应用版本回收、或工作区仅作为个人工作区存在时，versionId / applicationWorkspaceId 留空。
        ApplicationDefinition application = existingEnabledApp(appId);
        LoadingContext context = loadingContextForApplicationPreference(
                "application-recent-workspace",
                application,
                userId);
        ensureMember(application.appId(), userId, context);
        ApplicationId applicationId = application.appId();
        return managedWorkspaceRepository.findApplicationPreference(userId, applicationId)
                .flatMap(preference -> workspaceRepository.findById(preference.workspaceId()))
                .map(workspace -> resolveRecentWorkspaceResponse(workspace));
    }

    /**
     * 回填 appId / versionId / applicationWorkspaceId 到 WorkspaceRuntimeResponse。
     * 优先通过应用版本工作区反查，查不到时回退到个人工作区——后者同样需要填充 versionId 和
     * applicationWorkspaceId，确保前端 syncCurrentVersionFromWorkspace 能正确匹配菜单高亮。
     */
    private ManagedWorkspaceResponses.WorkspaceRuntimeResponse resolveRecentWorkspaceResponse(Workspace workspace) {
        Optional<ApplicationWorkspaceVersion> version =
                managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspace.workspaceId());
        String versionId = version.map(v -> v.versionId().value()).orElse(null);
        String applicationWorkspaceId = version.map(v -> v.applicationWorkspaceId().value()).orElse(null);
        String appId = version.map(v -> v.appId().value()).orElse(null);
        // 应用版本未关联该运行时 workspace 时，回退查个人工作区
        if (appId == null) {
            Optional<PersonalWorkspace> personal = managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspace.workspaceId());
            if (personal.isPresent()) {
                PersonalWorkspace pw = personal.get();
                appId = pw.appId().value();
                if (versionId == null) versionId = pw.versionId().value();
                if (applicationWorkspaceId == null) applicationWorkspaceId = pw.applicationWorkspaceId().value();
            }
        }
        return ManagedWorkspaceResponses.WorkspaceRuntimeResponse.from(
                workspaceForResponse(workspace),
                appId,
                versionId,
                applicationWorkspaceId);
    }

    /**
     * 确保指定用户在某应用版本下存在默认私人空间（workspaceName=default）。
     * 查询条件: (versionId, userId, workspaceName=default)，存在则直接返回，不存在则后台创建。
     * 新创建的分支命名规则: {应用版本分支}_{userId}_default。
     * 保留已有旧 personal workspace 记录，不做迁移；新规则只影响新建的 default/custom 私有空间。
     */
    public ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse ensureDefaultPersonalWorkspace(
            String versionId,
            UserId userId,
            String traceId) {
        synchronized (defaultPersonalWorkspaceLock) {
            return doEnsureDefaultPersonalWorkspace(versionId, userId, traceId);
        }
    }

    private ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse doEnsureDefaultPersonalWorkspace(
            String versionId,
            UserId userId,
            String traceId) {
        ApplicationWorkspaceVersion version = existingVersion(new ApplicationWorkspaceVersionId(versionId));
        ensureMember(version.appId(), userId, loadingContextForVersion(
                "ensure-default-personal-workspace",
                version,
                "default 私人工作区",
                "default"));
        String defaultName = "default";
        // 先查是否已有 (versionId, userId, workspaceName=default) 的私人空间
        Optional<PersonalWorkspace> existing = managedWorkspaceRepository.findPersonalWorkspaces(version.versionId(), userId).stream()
                .filter(pw -> defaultName.equals(pw.workspaceName()))
                .findFirst();
        if (existing.isPresent()) {
            PersonalWorkspace pw = existing.get();
            Workspace runtimeWorkspace = existingWorkspace(pw.runtimeWorkspaceId());
            PersonalWorkspace repaired = repairDefaultPersonalWorkspaceIfNeeded(version, pw, runtimeWorkspace, userId, traceId);
            if (!repaired.equals(pw)) {
                runtimeWorkspace = existingWorkspace(repaired.runtimeWorkspaceId());
                pw = repaired;
            }
            markRecent(userId, version.appId(), runtimeWorkspace.workspaceId());
            return new ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse(
                    pw.personalWorkspaceId().value(),
                    pw.workspaceName(),
                    pw.branch(),
                    ManagedWorkspaceResponses.WorkspaceRuntimeResponse.from(workspaceForResponse(runtimeWorkspace),
                            version.appId().value(), version.versionId().value(), version.applicationWorkspaceId().value()));
        }
        // 不存在则新建：按新规则命名分支 {branch}_{userId}_default
        try {
            ManagedWorkspaceResponses.PersonalWorkspaceResponse created = createPersonalWorkspaceForVersion(
                    version,
                    defaultName,
                    userId,
                    traceId);
            return new ManagedWorkspaceResponses.DefaultPersonalWorkspaceResponse(
                    created.personalWorkspaceId(),
                    created.workspaceName(),
                    created.branch(),
                    created.runtimeWorkspace());
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "创建默认私人工作区失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    private PersonalWorkspace repairDefaultPersonalWorkspaceIfNeeded(
            ApplicationWorkspaceVersion version,
            PersonalWorkspace personal,
            Workspace runtimeWorkspace,
            UserId userId,
            String traceId) {
        String expectedBranch = personalWorkspaceBranch(version, userId, "default");
        Path expectedRepoRoot = personalRepoRootWithName(version, userId, expectedBranch);
        ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
        Path expectedWorkspaceRoot = effectiveWorkspaceRoot(expectedRepoRoot, expectedRepoRoot.resolve(template.directoryPath()).normalize());
        Path expectedRepoPath = expectedRepoRoot.toAbsolutePath().normalize();
        Path expectedWorkspacePath = expectedWorkspaceRoot.toAbsolutePath().normalize();
        boolean matches = expectedBranch.equals(personal.branch())
                && sameNormalizedPath(expectedRepoPath, personal.repoRootPath())
                && sameNormalizedPath(expectedWorkspacePath, personal.workspaceRootPath())
                && sameNormalizedPath(expectedWorkspacePath, runtimeWorkspace.rootPath());
        if (matches || canReuseExistingPersonalWorkspace(personal, runtimeWorkspace, expectedBranch)) {
            return personal;
        }
        CodeRepository repository = existingRepository(version.repositoryId());
        ApplicationWorkspaceVersionReplica applicationReplica = ensureLocalReplica(version, template, userId, traceId);
        ensurePersonalWorktreeRoot(
                pathResolver.resolve(applicationReplica.repoRootPath()),
                expectedRepoRoot,
                expectedBranch,
                privateKeyFor(repository, userId));
        expectedWorkspaceRoot = effectiveWorkspaceRoot(expectedRepoRoot, expectedRepoRoot.resolve(template.directoryPath()).normalize());
        expectedRepoPath = realPath(expectedRepoRoot);
        expectedWorkspacePath = realPath(expectedWorkspaceRoot);
        String expectedRepoValue = personalRepoValue(version, userId, expectedBranch);
        String expectedWorkspaceValue = personalWorkspaceValue(version, template, userId, expectedBranch);
        if (expectedWorkspacePath.equals(expectedRepoPath)) {
            expectedWorkspaceValue = expectedRepoValue;
        }
        Instant now = Instant.now();
        Workspace repairedRuntime = workspaceRepository.save(new Workspace(
                runtimeWorkspace.workspaceId(),
                runtimeWorkspace.name(),
                expectedWorkspaceValue,
                runtimeWorkspace.status(),
                runtimeWorkspace.createdAt(),
                now,
                runtimeWorkspace.linuxServerId(),
                traceId));
        PersonalWorkspace repaired = new PersonalWorkspace(
                personal.personalWorkspaceId(),
                personal.versionId(),
                personal.appId(),
                personal.applicationWorkspaceId(),
                personal.userId(),
                personal.workspaceName(),
                expectedBranch,
                expectedRepoValue,
                expectedWorkspaceValue,
                repairedRuntime.workspaceId(),
                gitWorkspaceService.headCommit(expectedRepoRoot),
                personal.status(),
                personal.createdAt(),
                now);
        return managedWorkspaceRepository.updatePersonalWorkspaceLocation(repaired);
    }

    private boolean canReuseExistingPersonalWorkspace(
            PersonalWorkspace personal,
            Workspace runtimeWorkspace,
            String expectedBranch) {
        if (!expectedBranch.equals(personal.branch())) {
            return false;
        }
        try {
            Path personalRepoRoot = pathResolver.resolve(personal.repoRootPath()).toAbsolutePath().normalize();
            Path runtimeRoot = pathResolver.resolve(runtimeWorkspace.rootPath()).toAbsolutePath().normalize();
            Path personalWorkspaceRoot = pathResolver.resolve(personal.workspaceRootPath()).toAbsolutePath().normalize();
            return Files.isDirectory(personalRepoRoot)
                    && Files.isDirectory(runtimeRoot)
                    && Files.isDirectory(personalWorkspaceRoot)
                    && gitWorkspaceService.isGitRepository(personalRepoRoot)
                    && expectedBranch.equals(gitWorkspaceService.currentBranch(personalRepoRoot));
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean sameNormalizedPath(Path left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.toAbsolutePath().normalize().equals(pathResolver.resolve(right).toAbsolutePath().normalize());
    }

    private Path effectiveWorkspaceRoot(Path repoRoot, Path configuredWorkspaceRoot) {
        Path normalizedConfigured = configuredWorkspaceRoot.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedConfigured)) {
            return normalizedConfigured;
        }
        Path normalizedRepo = repoRoot.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedRepo)) {
            return normalizedRepo;
        }
        return normalizedConfigured;
    }

    private void ensurePersonalWorktreeRoot(Path applicationRepoRoot, Path repoRoot, String branch, String privateKey) {
        if (Files.exists(repoRoot)) {
            if (gitWorkspaceService.isGitRepository(repoRoot) && branch.equals(gitWorkspaceService.currentBranch(repoRoot))) {
                // 已有同路径同分支的有效 worktree，直接复用。
                return;
            }
            if (isEmptyDirectory(repoRoot)) {
                deleteEmptyDirectory(repoRoot);
                gitWorkspaceService.createWorktreeReusingBranch(applicationRepoRoot, repoRoot, branch, privateKey);
                return;
            }
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "私人工作区目录已存在且不属于当前用户分支",
                    Map.of("path", repoRoot.toString(), "branch", branch));
        }
        gitWorkspaceService.createWorktreeReusingBranch(applicationRepoRoot, repoRoot, branch, privateKey);
    }

    private boolean isEmptyDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var stream = Files.list(directory)) {
            return stream.findAny().isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    private void deleteEmptyDirectory(Path directory) {
        try {
            Files.delete(directory);
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "默认私人工作区空目录残留清理失败",
                    Map.of("path", directory.toString()),
                    exception);
        }
    }

    /**
     * 个人工作区物理根路径（使用名字而非 personalId 作为末段，使路径可读）。
     */
    private Path personalRepoRootWithName(ApplicationWorkspaceVersion version, UserId userId, String branch) {
        CodeRepository repository = existingRepository(version.repositoryId());
        return configuredPath(PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT)
                .resolve(sanitizeVersionForBranchAndPath(version.version()))
                .resolve(sanitizePathPart(userId.value()))
                .resolve(requireRepositoryEnglishName(repository))
                .resolve(sanitizePathPart(branch))
                .normalize();
    }

    private String appRepoValue(String version, CodeRepository repository) {
        return pathResolver.appValue(
                sanitizeVersionForBranchAndPath(version),
                requireRepositoryEnglishName(repository));
    }

    private String appWorkspaceValue(String version, CodeRepository repository, ApplicationWorkspace template) {
        return pathResolver.appValue(
                sanitizeVersionForBranchAndPath(version),
                requireRepositoryEnglishName(repository),
                template.directoryPath());
    }

    private String personalRepoValue(ApplicationWorkspaceVersion version, UserId userId, String branch) {
        CodeRepository repository = existingRepository(version.repositoryId());
        return pathResolver.personalValue(
                sanitizeVersionForBranchAndPath(version.version()),
                sanitizePathPart(userId.value()),
                requireRepositoryEnglishName(repository),
                sanitizePathPart(branch));
    }

    private String personalWorkspaceValue(
            ApplicationWorkspaceVersion version,
            ApplicationWorkspace template,
            UserId userId,
            String branch) {
        CodeRepository repository = existingRepository(version.repositoryId());
        return pathResolver.personalValue(
                sanitizeVersionForBranchAndPath(version.version()),
                sanitizePathPart(userId.value()),
                requireRepositoryEnglishName(repository),
                sanitizePathPart(branch),
                template.directoryPath());
    }

    /**
     * 基于本地 Git 获取工作区变更文件列表（不依赖 opencode runtime /vcs/diff）。
     * 通过 workspace 反查 personal workspace，若找到则基于其 repoRoot 进行 git status/diff；
     * 若找不到则尝试基于 workspace rootPath 自身。
     */
    public ManagedWorkspaceResponses.WorkspaceGitDiffResponse getWorkspaceGitDiff(String workspaceId, UserId userId) {
        Workspace workspace = existingWorkspace(new WorkspaceId(workspaceId));
        // 尝试通过运行时 workspace 反查个人工作区
        Optional<PersonalWorkspace> personal = managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspace.workspaceId());
        Path gitRoot;
        String displayPathPrefix = "";
        if (personal.isPresent()) {
            ensurePersonalOwner(personal.get(), userId);
            gitRoot = pathResolver.resolve(personal.get().repoRootPath());
            displayPathPrefix = repoRelativePrefix(gitRoot, pathResolver.resolve(personal.get().workspaceRootPath()));
        } else {
            // 回退：直接基于 workspace rootPath（可能是应用版本工作区副本）
            gitRoot = pathResolver.resolve(workspace.rootPath());
        }
        if (!Files.exists(gitRoot)) {
            return new ManagedWorkspaceResponses.WorkspaceGitDiffResponse(List.of());
        }
        try {
            String porcelain = gitWorkspaceService.statusPorcelain(gitRoot);
            String finalDisplayPathPrefix = displayPathPrefix;
            List<ManagedWorkspaceResponses.WorkspaceGitDiffFileResponse> files = gitWorkspaceService.collectDiffFiles(gitRoot, porcelain).stream()
                    .map(file -> new ManagedWorkspaceResponses.WorkspaceGitDiffFileResponse(
                            stripDisplayPathPrefix(file.path(), finalDisplayPathPrefix),
                            file.rawStatus(),
                            file.status(),
                            file.staged(),
                            file.patch(),
                            file.additions(),
                            file.deletions()))
                    .toList();
            return new ManagedWorkspaceResponses.WorkspaceGitDiffResponse(files);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "获取 Git 变更列表失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    public void discardWorkspaceGitFiles(String workspaceId, List<String> files, UserId userId) {
        Workspace workspace = existingWorkspace(new WorkspaceId(workspaceId));
        Optional<PersonalWorkspace> personal = managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspace.workspaceId());
        if (personal.isEmpty()) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "个人工作区不存在", Map.of("workspaceId", workspaceId));
        }
        PersonalWorkspace current = personal.get();
        ensurePersonalOwner(current, userId);
        ApplicationWorkspaceVersion version = existingVersion(current.versionId());
        CodeRepository repository = existingRepository(version.repositoryId());
        Path repoRoot = pathResolver.resolve(current.repoRootPath());
        Path workspaceRoot = pathResolver.resolve(current.workspaceRootPath());
        List<String> gitFiles = repoRelativeFiles(repoRoot, workspaceRoot, normalizeFiles(files));
        try {
            Map<String, GitStatusEntry> statuses = new LinkedHashMap<>();
            for (GitStatusEntry status : gitWorkspaceService.parseStatusPorcelain(gitWorkspaceService.statusPorcelain(repoRoot))) {
                statuses.put(status.path(), status);
            }
            List<String> trackedFiles = new ArrayList<>();
            List<String> stagedNewFiles = new ArrayList<>();
            List<String> untrackedFiles = new ArrayList<>();
            for (String gitFile : gitFiles) {
                GitStatusEntry status = statuses.get(gitFile);
                if (status != null && status.stagedNewFile()) {
                    stagedNewFiles.add(gitFile);
                } else if (status != null && status.untrackedFile()) {
                    untrackedFiles.add(gitFile);
                } else {
                    trackedFiles.add(gitFile);
                }
            }
            String privateKey = privateKeyFor(repository, userId);
            gitWorkspaceService.restoreFiles(repoRoot, trackedFiles, privateKey);
            gitWorkspaceService.unstageFiles(repoRoot, stagedNewFiles, privateKey);
            List<String> filesToClean = new ArrayList<>(stagedNewFiles);
            filesToClean.addAll(untrackedFiles);
            gitWorkspaceService.cleanUntrackedFiles(repoRoot, filesToClean, privateKey);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "回退工作区文件失败: " + exception.getMessage(), Map.of(), exception);
        }
    }

    private String repoRelativePrefix(Path repoRoot, Path workspaceRoot) {
        try {
            Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
            Path normalizedWorkspaceRoot = workspaceRoot.toAbsolutePath().normalize();
            if (!normalizedWorkspaceRoot.startsWith(normalizedRepoRoot)) {
                return "";
            }
            String prefix = normalizedRepoRoot.relativize(normalizedWorkspaceRoot).toString().replace('\\', '/');
            return prefix.isBlank() ? "" : prefix + "/";
        } catch (Exception exception) {
            return "";
        }
    }

    private String stripDisplayPathPrefix(String path, String displayPathPrefix) {
        if (displayPathPrefix == null || displayPathPrefix.isBlank()) {
            return path;
        }
        return path.startsWith(displayPathPrefix) ? path.substring(displayPathPrefix.length()) : path;
    }

    /**
     * 个人工作区"提交并推送"：将个人 worktree 上的变更合并回应用版本特性分支并推送。
     * 流程: 在个人 worktree 上 stage+commit → 切到应用版本副本（特性分支）→ fetch/pull 远端特性分支 →
     * 将最新特性分支 merge 进个人分支 → 将个人分支本地 merge 回特性分支 → 只推送特性分支。
     * 合并冲突: 冲突保留在当前个人 worktree，返回 CONFLICT 及冲突文件列表，便于用户直接解决。
     */
    public ManagedWorkspaceResponses.PersonalWorkspacePublishResponse publishPersonalWorkspace(
            String personalWorkspaceId,
            String commitMessage,
            List<String> files,
            UserId userId,
            String traceId) {
        PersonalWorkspace personal = existingPersonal(new PersonalWorkspaceId(personalWorkspaceId));
        ensurePersonalOwner(personal, userId);
        ApplicationWorkspaceVersion version = existingVersion(personal.versionId());
        CodeRepository repository = existingRepository(version.repositoryId());
        String privateKey = privateKeyFor(repository, userId);
        Path personalRepoRoot = pathResolver.resolve(personal.repoRootPath());
        Path personalWorkspaceRoot = pathResolver.resolve(personal.workspaceRootPath());
        List<String> gitFiles = repoRelativeFiles(personalRepoRoot, personalWorkspaceRoot, normalizeFiles(files));

        // 1. 只暂存前端显式选择的文件，避免一次发布把未选择的 diff 全部提交并从列表消失。
        gitWorkspaceService.stageFiles(personalRepoRoot, gitFiles, privateKey);
        try {
            gitWorkspaceService.commitStaged(personalRepoRoot, requireText(commitMessage, "提交说明不能为空", "commitMessage"), privateKey);
        } catch (PlatformException e) {
            if (!e.getMessage().contains("nothing to commit") && !e.getMessage().contains("nothing added")) {
                throw e;
            }
        }

        // 2. 先拉取远端特性分支，再把最新特性分支合入个人 worktree。
        //    如果这里冲突，冲突文件会留在用户当前可编辑的个人工作区，由用户解决后再次发布。
        ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
        ApplicationWorkspaceVersionReplica applicationReplica = ensureLocalReplica(version, template, userId, traceId);
        Path applicationRepoRoot = pathResolver.resolve(applicationReplica.repoRootPath());
        String applicationBranch = version.branch();
        if (!gitWorkspaceService.isWorktreeClean(applicationRepoRoot)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "应用版本工作区存在未提交变更，无法合并个人工作区",
                    Map.of("versionId", version.versionId().value()));
        }
        gitWorkspaceService.fetch(applicationRepoRoot, privateKey);
        gitWorkspaceService.pullFastForward(applicationRepoRoot, applicationBranch, privateKey);
        try {
            gitWorkspaceService.mergeBranch(personalRepoRoot, applicationBranch, privateKey);
        } catch (PlatformException mergeException) {
            List<String> conflictFiles = List.of();
            try {
                conflictFiles = gitWorkspaceService.conflictPaths(personalRepoRoot);
            } catch (Exception ignored) {
            }
            if (conflictFiles.isEmpty()) {
                throw mergeException;
            }
            return new ManagedWorkspaceResponses.PersonalWorkspacePublishResponse(
                    "CONFLICT", personalWorkspaceId, version.versionId().value(), conflictFiles,
                    "合并冲突，请在个人工作区中解决冲突后重新提交并推送");
        }

        // 3. 个人分支已包含最新特性分支后，再在应用版本副本上本地合并个人分支。
        try {
            gitWorkspaceService.mergeBranch(applicationRepoRoot, personal.branch(), privateKey);
        } catch (PlatformException mergeException) {
            List<String> conflictFiles = List.of();
            try {
                conflictFiles = gitWorkspaceService.conflictPaths(applicationRepoRoot);
            } catch (Exception ignored) {
            }
            if (conflictFiles.isEmpty()) {
                throw mergeException;
            }
            abortMergeQuietly(applicationRepoRoot, privateKey);
            throw mergeException;
        }

        // 4. 合并成功：只推送应用版本特性分支，更新版本 targetCommitHash 和副本 commit。
        gitWorkspaceService.push(applicationRepoRoot, applicationBranch, false, privateKey);
        Instant now = Instant.now();
        String headCommit = gitWorkspaceService.headCommit(applicationRepoRoot);
        ApplicationWorkspaceVersion updatedVersion = managedWorkspaceRepository.updateVersionTargetCommit(version.versionId(), headCommit, now);
        Optional<ApplicationWorkspaceVersionReplica> currentReplica = managedWorkspaceRepository.findVersionReplica(
                version.versionId(), serverIdentity.linuxServerId());
        if (currentReplica.isPresent()) {
            managedWorkspaceRepository.saveVersionReplica(currentReplica.get().ready(headCommit, now, traceId));
        }
        publishVersionSync(updatedVersion, userId, "PERSONAL_PUBLISHED", traceId, Map.of());
        return new ManagedWorkspaceResponses.PersonalWorkspacePublishResponse(
                "MERGED", personalWorkspaceId, version.versionId().value(), List.of(),
                "合并成功: " + headCommit);
    }

    private void abortMergeQuietly(Path applicationRepoRoot, String privateKey) {
        try {
            gitWorkspaceService.abortMerge(applicationRepoRoot, privateKey);
        } catch (Exception exception) {
            LOGGER.warn("abort personal workspace merge failed repoRoot={}", applicationRepoRoot, exception);
        }
    }

    public ManagedWorkspaceResponses.WorkspaceDiffResponse diffPersonalWorkspace(String personalWorkspaceId, UserId userId, String traceId) {
        PersonalWorkspace personal = existingPersonal(new PersonalWorkspaceId(personalWorkspaceId));
        ensurePersonalOwner(personal, userId);
        ApplicationWorkspaceVersion version = existingVersion(personal.versionId());
        ApplicationWorkspaceVersionReplica applicationReplica = replicaForPersonalWorkspace(version, personal, traceId);
        return new ManagedWorkspaceResponses.WorkspaceDiffResponse(compareDirectories(
                pathResolver.resolve(personal.workspaceRootPath()),
                pathResolver.resolve(applicationReplica.workspaceRootPath())));
    }

    public ManagedWorkspaceResponses.WorkspaceSyncResponse syncPersonalToApplication(
            String personalWorkspaceId,
            List<String> files,
            boolean force,
            UserId userId,
            String traceId) {
        PersonalWorkspace personal = existingPersonal(new PersonalWorkspaceId(personalWorkspaceId));
        ensurePersonalOwner(personal, userId);
        ApplicationWorkspaceVersion version = existingVersion(personal.versionId());
        ApplicationWorkspaceVersionReplica applicationReplica = replicaForPersonalWorkspace(version, personal, traceId);
        List<String> normalizedFiles = normalizeFiles(files);
        WorkspaceSyncRecordId syncId = new WorkspaceSyncRecordId(RuntimeIdGenerator.workspaceSyncRecordId());
        try {
            Path applicationRepoRoot = pathResolver.resolve(applicationReplica.repoRootPath());
            Path applicationWorkspaceRoot = pathResolver.resolve(applicationReplica.workspaceRootPath());
            CodeRepository repository = existingRepository(version.repositoryId());
            String privateKey = privateKeyFor(repository, userId);
            GitPublishWorkflow.PublishResult publishResult = gitPublishWorkflow.syncFilesThenPush(
                    applicationRepoRoot,
                    version.branch(),
                    repoRelativeFiles(applicationRepoRoot, applicationWorkspaceRoot, normalizedFiles),
                    "test-agent sync " + syncId.value(),
                    force,
                    privateKey,
                    () -> copyFiles(pathResolver.resolve(personal.workspaceRootPath()), applicationWorkspaceRoot, normalizedFiles));
            Instant now = Instant.now();
            String headCommit = publishResult.headCommit();
            ApplicationWorkspaceVersion updatedVersion = managedWorkspaceRepository.updateVersionTargetCommit(version.versionId(), headCommit, now);
            ApplicationWorkspaceVersionReplica updatedReplica = managedWorkspaceRepository.saveVersionReplica(applicationReplica.ready(headCommit, now, traceId));
            publishVersionSync(updatedVersion, userId, "SYNC_TO_APPLICATION", traceId, Map.of());
            saveSync(syncId, userId, personal.runtimeWorkspaceId(), updatedReplica.runtimeWorkspaceId(), WorkspaceSyncDirection.PERSONAL_TO_APPLICATION, normalizedFiles, force, WorkspaceSyncStatus.SUCCEEDED, traceId);
            return new ManagedWorkspaceResponses.WorkspaceSyncResponse(syncId.value(), WorkspaceSyncStatus.SUCCEEDED.name(), normalizedFiles, force);
        } catch (RuntimeException exception) {
            saveSync(syncId, userId, personal.runtimeWorkspaceId(), applicationReplica.runtimeWorkspaceId(), WorkspaceSyncDirection.PERSONAL_TO_APPLICATION, normalizedFiles, force, WorkspaceSyncStatus.FAILED, traceId);
            throw exception;
        }
    }

    public ManagedWorkspaceResponses.WorkspaceSyncResponse syncApplicationToPersonal(
            String personalWorkspaceId,
            List<String> files,
            UserId userId,
            String traceId) {
        PersonalWorkspace personal = existingPersonal(new PersonalWorkspaceId(personalWorkspaceId));
        ensurePersonalOwner(personal, userId);
        ApplicationWorkspaceVersion version = existingVersion(personal.versionId());
        ApplicationWorkspaceVersionReplica applicationReplica = replicaForPersonalWorkspace(version, personal, traceId);
        List<String> normalizedFiles = normalizeFiles(files);
        WorkspaceSyncRecordId syncId = new WorkspaceSyncRecordId(RuntimeIdGenerator.workspaceSyncRecordId());
        copyFiles(pathResolver.resolve(applicationReplica.workspaceRootPath()), pathResolver.resolve(personal.workspaceRootPath()), normalizedFiles);
        saveSync(syncId, userId, applicationReplica.runtimeWorkspaceId(), personal.runtimeWorkspaceId(), WorkspaceSyncDirection.APPLICATION_TO_PERSONAL, normalizedFiles, false, WorkspaceSyncStatus.SUCCEEDED, traceId);
        return new ManagedWorkspaceResponses.WorkspaceSyncResponse(syncId.value(), WorkspaceSyncStatus.SUCCEEDED.name(), normalizedFiles, false);
    }

    /**
     * 启动和周期补偿入口：扫描当前服务器缺失或落后的应用版本副本并尝试追平目标 commit。
     */
    public void reconcileLocalReplicas(String traceId) {
        List<ApplicationWorkspaceVersion> versions = managedWorkspaceRepository.findActiveVersionsMissingReadyReplica(serverIdentity.linuxServerId());
        for (ApplicationWorkspaceVersion version : versions) {
            try {
                ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
                ensureLocalReplica(version, template, version.createdBy(), traceId);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Failed to reconcile managed workspace replica, versionId={}, linuxServerId={}",
                        version.versionId().value(),
                        serverIdentity.linuxServerId(),
                        exception);
            }
        }
    }

    public ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse gitPullVersion(
            String versionId,
            UserId userId,
            String targetLinuxServerId,
            String traceId) {
        ApplicationWorkspaceVersion version = existingVersion(new ApplicationWorkspaceVersionId(versionId));
        ensureMember(version.appId(), userId, loadingContextForVersion(
                "git-pull-version",
                version,
                "应用版本工作区",
                version.version()));
        ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
        if (!serverIdentity.linuxServerId().equals(targetLinuxServerId)) {
            publishVersionSync(version, userId, "GIT_PULL_REQUESTED", traceId, Map.of("targetLinuxServerId", targetLinuxServerId));
            ApplicationWorkspaceVersionReplica remoteReplica = waitForReadyReplica(version.versionId(), targetLinuxServerId)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.CONFLICT,
                            "目标服务器应用版本工作区副本未就绪",
                            Map.of("targetLinuxServerId", targetLinuxServerId, "versionId", versionId)));
            ApplicationWorkspaceVersion updated = existingVersion(version.versionId());
            return versionResponse(updated, remoteReplica);
        }
        ApplicationWorkspaceVersionReplica replica = managedWorkspaceRepository.findVersionReplica(version.versionId(), targetLinuxServerId)
                .orElseGet(() -> ensureReplicaForTarget(version, template, userId, targetLinuxServerId, "GIT_PULL", traceId));
        Path replicaRepoRoot = pathResolver.resolve(replica.repoRootPath());
        if (!gitWorkspaceService.isWorktreeClean(replicaRepoRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用版本工作区存在未提交变更，无法 git pull", Map.of("versionId", versionId));
        }
        CodeRepository repository = existingRepository(version.repositoryId());
        String privateKey = privateKeyFor(repository, userId);
        ensureInternalOrigin(repository, userId, replicaRepoRoot, privateKey);
        gitWorkspaceService.pullFastForward(replicaRepoRoot, version.branch(), privateKey);
        Instant now = Instant.now();
        String headCommit = gitWorkspaceService.headCommit(replicaRepoRoot);
        ApplicationWorkspaceVersion updatedVersion = managedWorkspaceRepository.updateVersionTargetCommit(version.versionId(), headCommit, now);
        ApplicationWorkspaceVersionReplica updatedReplica = managedWorkspaceRepository.saveVersionReplica(replica.ready(headCommit, now, traceId));
        publishVersionSync(updatedVersion, userId, "GIT_PULLED", traceId, Map.of());
        return versionResponse(updatedVersion, updatedReplica);
    }

    @Override
    public boolean supports(String type) {
        return VERSION_SYNC_EVENT.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!supports(event.type()) || serverIdentity.linuxServerId().equals(event.originLinuxServerId())) {
            return;
        }
        try {
            handleVersionSyncEvent(event);
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "处理应用版本工作区广播失败", Map.of("eventId", event.eventId()), exception);
        }
    }

    /**
     * 记录当前用户在某 (appId, workspaceId) 维度下最近一次手动选择的 VCS 分支，
     * 用于下次进入同一工作区时把分支显示在 IDE 上；非托管工作区会抛 NOT_FOUND，
     * 由 Controller 决定是否向上抛错。
     */
    public ManagedWorkspaceResponses.BranchPreferenceResponse markRecentBranch(
            String appId,
            String workspaceId,
            String branch,
            UserId userId) {
        ApplicationDefinition application = existingEnabledApp(appId);
        Workspace workspace = existingWorkspace(new WorkspaceId(workspaceId));
        ensureMember(application.appId(), userId, loadingContextForWorkspace("mark-recent-branch", application, workspace));
        ApplicationId applicationId = application.appId();
        ApplicationId workspaceAppId = appIdForRuntimeWorkspace(workspace.workspaceId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "工作区不属于任何应用", Map.of("workspaceId", workspaceId)));
        if (!applicationId.equals(workspaceAppId)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作区不属于当前应用", Map.of("appId", appId, "workspaceId", workspaceId));
        }
        String normalizedBranch = requireText(branch, "分支名不能为空", "branch");
        UserWorkspaceBranchPreference preference = new UserWorkspaceBranchPreference(
                userId,
                applicationId,
                workspace.workspaceId(),
                normalizedBranch,
                Instant.now());
        managedWorkspaceRepository.saveBranchPreference(preference);
        return ManagedWorkspaceResponses.BranchPreferenceResponse.from(preference);
    }

    /**
     * 查询当前用户在某 (appId, workspaceId) 维度下的最近 VCS 分支偏好；未设置时返回 Optional.empty。
     * 工作区不属于任何应用时返回 Optional.empty，由前端走"无偏好"分支。
     */
    public Optional<ManagedWorkspaceResponses.BranchPreferenceResponse> recentBranch(String appId, String workspaceId, UserId userId) {
        ApplicationId applicationId;
        try {
            applicationId = existingMemberApp(appId, userId).appId();
        } catch (PlatformException exception) {
            return Optional.empty();
        }
        Workspace workspace;
        try {
            workspace = existingWorkspace(new WorkspaceId(workspaceId));
        } catch (PlatformException exception) {
            return Optional.empty();
        }
        return managedWorkspaceRepository.findBranchPreference(userId, applicationId, workspace.workspaceId())
                .map(ManagedWorkspaceResponses.BranchPreferenceResponse::from);
    }

    private void prepareApplicationRepo(CodeRepository repository, String branch, Path repoRoot, Path workspaceRoot, String privateKey, String effectiveGitUrl) {
        try {
            if (Files.exists(repoRoot)) {
                if (!Files.isDirectory(repoRoot)) {
                    throw new PlatformException(ErrorCode.CONFLICT, "工作区仓库路径已存在且不是目录", Map.of("path", repoRoot.toString()));
                }
                String origin = gitWorkspaceService.originUrl(repoRoot);
                String currentBranch = gitWorkspaceService.currentBranch(repoRoot);
                if (!repository.matchesStoredOrigin(origin) || !branch.equals(currentBranch)) {
                    throw new PlatformException(ErrorCode.CONFLICT, "已有目录不是目标代码库分支", Map.of("path", repoRoot.toString()));
                }
                ensureInternalOrigin(repository, effectiveGitUrl, repoRoot, privateKey);
            } else {
                Files.createDirectories(repoRoot.getParent());
                gitWorkspaceService.cloneBranch(effectiveGitUrl, branch, repoRoot, privateKey);
            }
            if (!Files.isDirectory(workspaceRoot)) {
                throw new PlatformException(ErrorCode.CONFLICT, "应用工作区目录不存在", Map.of("path", workspaceRoot.toString()));
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "创建应用版本工作区失败", Map.of("path", repoRoot.toString()), exception);
        }
    }

    private ApplicationWorkspaceVersionReplica ensureReplicaForTarget(
            ApplicationWorkspaceVersion version,
            ApplicationWorkspace template,
            UserId userId,
            String targetLinuxServerId,
            String reason,
            String traceId) {
        String target = requireText(targetLinuxServerId, "目标服务器不能为空", "targetLinuxServerId");
        if (!serverIdentity.linuxServerId().equals(target)) {
            publishVersionSync(version, userId, reason, traceId, Map.of("targetLinuxServerId", target));
            return waitForReadyReplica(version.versionId(), target)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.CONFLICT,
                            "目标服务器应用版本工作区副本未就绪",
                            Map.of("targetLinuxServerId", target, "versionId", version.versionId().value())));
        }
        return ensureLocalReplica(version, template, userId, traceId);
    }

    private ApplicationWorkspaceVersionReplica ensureLocalReplica(
            ApplicationWorkspaceVersion version,
            ApplicationWorkspace template,
            UserId userId,
            String traceId) {
        CodeRepository repository = existingRepository(version.repositoryId());
        Path repoRoot = appRepoRoot(version.version(), repository);
        Path workspaceRoot = repoRoot.resolve(template.directoryPath()).normalize();
        String repoRootValue = appRepoValue(version.version(), repository);
        String workspaceRootValue = appWorkspaceValue(version.version(), repository, template);
        String privateKey = privateKeyFor(repository, userId);
        prepareApplicationRepo(repository, version.branch(), repoRoot, workspaceRoot, privateKey, effectiveGitUrl(repository, userId));
        Optional<ApplicationWorkspaceVersionReplica> existing = managedWorkspaceRepository.findVersionReplica(
                version.versionId(),
                serverIdentity.linuxServerId());
        Instant now = Instant.now();
        Workspace runtimeWorkspace = existing
                .flatMap(replica -> workspaceRepository.findById(replica.runtimeWorkspaceId()))
                .map(current -> new Workspace(
                        current.workspaceId(),
                        current.name(),
                        workspaceRootValue,
                        current.status(),
                        current.createdAt(),
                        now,
                        serverIdentity.linuxServerId(),
                        traceId))
                .map(workspaceRepository::save)
                .orElseGet(() -> createRuntimeWorkspace(template.workspaceName() + "-" + version.version(), workspaceRoot, workspaceRootValue, traceId));
        String currentCommit = syncReplicaToTargetCommit(version, repoRoot, privateKey, existing.orElse(null), traceId);
        ApplicationWorkspaceVersion updatedVersion = version.targetCommitHash() == null
                ? managedWorkspaceRepository.updateVersionTargetCommit(version.versionId(), currentCommit, now)
                : version;
        ApplicationWorkspaceVersionReplica replica = existing
                .map(current -> new ApplicationWorkspaceVersionReplica(
                        current.replicaId(),
                        updatedVersion.versionId(),
                        serverIdentity.linuxServerId(),
                        repoRootValue,
                        workspaceRootValue,
                        runtimeWorkspace.workspaceId(),
                        currentCommit,
                        WorkspaceReplicaSyncStatus.READY,
                        null,
                        now,
                        traceId,
                        current.createdAt(),
                        now))
                .orElseGet(() -> new ApplicationWorkspaceVersionReplica(
                        new ApplicationWorkspaceVersionReplicaId(RuntimeIdGenerator.applicationWorkspaceVersionReplicaId()),
                        updatedVersion.versionId(),
                        serverIdentity.linuxServerId(),
                        repoRootValue,
                        workspaceRootValue,
                        runtimeWorkspace.workspaceId(),
                        currentCommit,
                        WorkspaceReplicaSyncStatus.READY,
                        null,
                        now,
                        traceId,
                        now,
                        now));
        return managedWorkspaceRepository.saveVersionReplica(replica);
    }

    private String syncReplicaToTargetCommit(
            ApplicationWorkspaceVersion version,
            Path repoRoot,
            String privateKey,
            ApplicationWorkspaceVersionReplica existingReplica,
            String traceId) {
        String targetCommit = version.targetCommitHash();
        if (targetCommit == null || targetCommit.isBlank()) {
            return gitWorkspaceService.headCommit(repoRoot);
        }
        String current = gitWorkspaceService.headCommit(repoRoot);
        if (targetCommit.equals(current)) {
            return current;
        }
        if (!gitWorkspaceService.isWorktreeClean(repoRoot)) {
            if (existingReplica != null) {
                managedWorkspaceRepository.saveVersionReplica(existingReplica.failed("工作树存在未提交变更", Instant.now(), traceId));
            }
            throw new PlatformException(ErrorCode.CONFLICT, "应用版本工作区存在未提交变更，无法同步副本", Map.of("versionId", version.versionId().value()));
        }
        gitWorkspaceService.fetch(repoRoot, privateKey);
        gitWorkspaceService.resetHardToCommit(repoRoot, targetCommit);
        return gitWorkspaceService.headCommit(repoRoot);
    }

    private ApplicationWorkspaceVersionReplica saveReadyReplica(
            ApplicationWorkspaceVersion version,
            Path repoRoot,
            Path workspaceRoot,
            String repoRootValue,
            String workspaceRootValue,
            WorkspaceId runtimeWorkspaceId,
            String currentCommit,
            String traceId,
            Instant now) {
        return managedWorkspaceRepository.saveVersionReplica(new ApplicationWorkspaceVersionReplica(
                new ApplicationWorkspaceVersionReplicaId(RuntimeIdGenerator.applicationWorkspaceVersionReplicaId()),
                version.versionId(),
                serverIdentity.linuxServerId(),
                repoRootValue,
                workspaceRootValue,
                runtimeWorkspaceId,
                currentCommit,
                WorkspaceReplicaSyncStatus.READY,
                null,
                now,
                traceId,
                now,
                now));
    }

    private ApplicationWorkspaceVersionReplica replicaForPersonalWorkspace(
            ApplicationWorkspaceVersion version,
            PersonalWorkspace personal,
            String traceId) {
        ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
        return ensureLocalReplica(version, template, personal.userId(), traceId);
    }

    private Optional<ApplicationWorkspaceVersionReplica> waitForReadyReplica(
            ApplicationWorkspaceVersionId versionId,
            String linuxServerId) {
        for (int i = 0; i < 20; i++) {
            Optional<ApplicationWorkspaceVersionReplica> replica = managedWorkspaceRepository.findVersionReplica(versionId, linuxServerId)
                    .filter(item -> item.syncStatus() == WorkspaceReplicaSyncStatus.READY);
            if (replica.isPresent()) {
                return replica;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void publishVersionSync(
            ApplicationWorkspaceVersion version,
            UserId userId,
            String reason,
            String traceId,
            Map<String, Object> extraPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        payload.put("versionId", version.versionId().value());
        payload.put("applicationWorkspaceId", version.applicationWorkspaceId().value());
        payload.put("appId", version.appId().value());
        payload.put("repositoryId", version.repositoryId().value());
        payload.put("version", version.version());
        payload.put("branch", version.branch());
        payload.put("userId", userId.value());
        if (version.targetCommitHash() != null) {
            payload.put("targetCommitHash", version.targetCommitHash());
        }
        if (extraPayload != null) {
            extraPayload.forEach((key, value) -> {
                if (value != null) {
                    payload.put(key, value);
                }
            });
        }
        broadcastPublisher.publish(new ServerBroadcastEvent(
                RuntimeIdGenerator.serverBroadcastEventId(),
                VERSION_SYNC_EVENT,
                broadcastInstanceId,
                serverIdentity.linuxServerId(),
                traceId,
                Instant.now(),
                payload));
    }

    private void handleVersionSyncEvent(ServerBroadcastEvent event) {
        Map<String, Object> payload = event.payload();
        String targetLinuxServerId = payloadString(payload, "targetLinuxServerId").orElse(null);
        if (targetLinuxServerId != null && !serverIdentity.linuxServerId().equals(targetLinuxServerId)) {
            return;
        }
        ApplicationWorkspaceVersionId versionId = new ApplicationWorkspaceVersionId(
                payloadString(payload, "versionId").orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "广播事件缺少 versionId")));
        UserId userId = new UserId(payloadString(payload, "userId")
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "广播事件缺少 userId")));
        ApplicationWorkspaceVersion version = existingVersion(versionId);
        ApplicationWorkspace template = existingTemplate(version.applicationWorkspaceId());
        String reason = payloadString(payload, "reason").orElse("SYNC");
        ApplicationWorkspaceVersionReplica replica = ensureLocalReplica(version, template, userId, event.traceId());
        if ("GIT_PULL_REQUESTED".equals(reason)) {
            Path replicaRepoRoot = pathResolver.resolve(replica.repoRootPath());
            if (!gitWorkspaceService.isWorktreeClean(replicaRepoRoot)) {
                managedWorkspaceRepository.saveVersionReplica(replica.failed("工作树存在未提交变更", Instant.now(), event.traceId()));
                return;
            }
            CodeRepository repository = existingRepository(version.repositoryId());
            String privateKey = privateKeyFor(repository, userId);
            ensureInternalOrigin(repository, userId, replicaRepoRoot, privateKey);
            gitWorkspaceService.pullFastForward(replicaRepoRoot, version.branch(), privateKey);
            Instant now = Instant.now();
            String headCommit = gitWorkspaceService.headCommit(replicaRepoRoot);
            ApplicationWorkspaceVersion updatedVersion = managedWorkspaceRepository.updateVersionTargetCommit(version.versionId(), headCommit, now);
            ApplicationWorkspaceVersionReplica updatedReplica = managedWorkspaceRepository.saveVersionReplica(replica.ready(headCommit, now, event.traceId()));
            publishVersionSync(updatedVersion, userId, "GIT_PULLED", event.traceId(), Map.of("targetLinuxServerId", updatedReplica.linuxServerId()));
        }
    }

    private Optional<String> payloadString(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString().trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private String resolveBranch(CodeRepository repository, String version, String branch, UserId userId) {
        if (!repository.standard()) {
            return requireText(branch, "非标准代码库必须指定分支", "branch");
        }
        // yyyy年M月 格式的版本在分支名里需转为 yyyy-MM，避免 git ref 中出现中文 / 年月字面量。
        String branchFragment = sanitizeVersionForBranchAndPath(version);
        String expected = "feature_testagent_" + branchFragment;
        List<String> branches = gitRemoteService.listBranches(effectiveGitUrl(repository, userId), privateKeyFor(repository, userId));
        if (!branches.contains(expected)) {
            throw new PlatformException(ErrorCode.CONFLICT, repository.name() + "代码库无" + expected + "分支，请到开发者门户创建分支", Map.of("branch", expected));
        }
        return expected;
    }

    private Workspace createRuntimeWorkspace(String name, Path workspaceRoot, String storedRootPath, String traceId) {
        Instant now = Instant.now();
        realPath(workspaceRoot);
        Workspace workspace = new Workspace(
                new WorkspaceId(RuntimeIdGenerator.workspaceId()),
                name,
                requireText(storedRootPath, "工作区根路径不能为空", "rootPath"),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                serverIdentity.linuxServerId(),
                traceId);
        return workspaceRepository.save(workspace);
    }

    private ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse versionResponse(ApplicationWorkspaceVersion version) {
        ApplicationWorkspaceVersionReplica replica = managedWorkspaceRepository.findVersionReplica(version.versionId(), serverIdentity.linuxServerId())
                .orElse(null);
        if (replica == null) {
            return ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse.from(
                    versionForResponse(version),
                    workspaceForResponse(existingWorkspace(version.runtimeWorkspaceId())));
        }
        return versionResponse(version, replica);
    }

    private ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse versionResponse(
            ApplicationWorkspaceVersion version,
            ApplicationWorkspaceVersionReplica replica) {
        return ManagedWorkspaceResponses.ApplicationWorkspaceVersionResponse.from(
                versionForResponse(version),
                replicaForResponse(replica),
                workspaceForResponse(existingWorkspace(replica.runtimeWorkspaceId())));
    }

    private ManagedWorkspaceResponses.PersonalWorkspaceResponse personalResponse(PersonalWorkspace personal) {
        return ManagedWorkspaceResponses.PersonalWorkspaceResponse.from(
                personalForResponse(personal),
                workspaceForResponse(existingWorkspace(personal.runtimeWorkspaceId())));
    }

    private Workspace workspaceForResponse(Workspace workspace) {
        return pathResolver.withResolvedRootPath(workspace);
    }

    private ApplicationWorkspaceVersion versionForResponse(ApplicationWorkspaceVersion version) {
        return new ApplicationWorkspaceVersion(
                version.versionId(),
                version.applicationWorkspaceId(),
                version.appId(),
                version.repositoryId(),
                version.version(),
                version.branch(),
                pathResolver.resolve(version.repoRootPath()).toString(),
                pathResolver.resolve(version.workspaceRootPath()).toString(),
                version.runtimeWorkspaceId(),
                version.createdBy(),
                version.status(),
                version.targetCommitHash(),
                version.targetCommitUpdatedAt(),
                version.createdAt(),
                version.updatedAt());
    }

    private ApplicationWorkspaceVersionReplica replicaForResponse(ApplicationWorkspaceVersionReplica replica) {
        return new ApplicationWorkspaceVersionReplica(
                replica.replicaId(),
                replica.versionId(),
                replica.linuxServerId(),
                pathResolver.resolve(replica.repoRootPath()).toString(),
                pathResolver.resolve(replica.workspaceRootPath()).toString(),
                replica.runtimeWorkspaceId(),
                replica.currentCommitHash(),
                replica.syncStatus(),
                replica.lastError(),
                replica.lastSyncedAt(),
                replica.traceId(),
                replica.createdAt(),
                replica.updatedAt());
    }

    private PersonalWorkspace personalForResponse(PersonalWorkspace personal) {
        return new PersonalWorkspace(
                personal.personalWorkspaceId(),
                personal.versionId(),
                personal.appId(),
                personal.applicationWorkspaceId(),
                personal.userId(),
                personal.workspaceName(),
                personal.branch(),
                pathResolver.resolve(personal.repoRootPath()).toString(),
                pathResolver.resolve(personal.workspaceRootPath()).toString(),
                personal.runtimeWorkspaceId(),
                personal.baseCommit(),
                personal.status(),
                personal.createdAt(),
                personal.updatedAt());
    }

    private void markRecent(UserId userId, ApplicationId appId, WorkspaceId workspaceId) {
        Instant now = Instant.now();
        managedWorkspaceRepository.savePreference(new UserWorkspacePreference(userId, null, workspaceId, now));
        managedWorkspaceRepository.savePreference(new UserWorkspacePreference(userId, appId, workspaceId, now));
    }

    private Optional<ApplicationId> appIdForRuntimeWorkspace(WorkspaceId workspaceId) {
        Optional<ApplicationWorkspaceVersion> version = managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspaceId);
        if (version.isPresent()) {
            return Optional.of(version.get().appId());
        }
        return managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspaceId).map(PersonalWorkspace::appId);
    }

    private ApplicationDefinition existingEnabledApp(String appId) {
        ApplicationDefinition application = configurationRepository.findApplication(new ApplicationId(appId))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用不存在", Map.of("appId", appId)));
        if (!application.enabled()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用未启用", Map.of("appId", appId));
        }
        return application;
    }

    private ApplicationDefinition existingMemberApp(String appId, UserId userId) {
        return existingMemberApp(appId, userId, "managed-workspace");
    }

    private ApplicationDefinition existingMemberApp(String appId, UserId userId, String loadingStage) {
        ApplicationDefinition application = existingEnabledApp(appId);
        ensureMember(application.appId(), userId, loadingContext(loadingStage)
                .application(application)
                .workspaceKind("应用工作区"));
        return application;
    }

    private LoadingContext loadingContext(String loadingStage) {
        return new LoadingContext(loadingStage);
    }

    private LoadingContext loadingContextForVersion(
            String loadingStage,
            ApplicationWorkspaceVersion version,
            String workspaceKind,
            String workspaceName) {
        return loadingContext(loadingStage)
                .applicationId(version.appId().value())
                .version(version)
                .applicationWorkspaceId(version.applicationWorkspaceId().value())
                .workspaceKind(workspaceKind)
                .workspaceName(workspaceName);
    }

    private LoadingContext loadingContextForApplicationPreference(
            String loadingStage,
            ApplicationDefinition application,
            UserId userId) {
        Optional<UserWorkspacePreference> preference = managedWorkspaceRepository.findApplicationPreference(userId, application.appId());
        if (preference.isPresent()) {
            Optional<Workspace> workspace = workspaceRepository.findById(preference.get().workspaceId());
            if (workspace.isPresent()) {
                return loadingContextForWorkspace(loadingStage, application, workspace.get());
            }
        }
        return loadingContext(loadingStage)
                .application(application)
                .workspaceKind("应用最近工作区/default 私人工作区候选");
    }

    private LoadingContext loadingContextForWorkspace(
            String loadingStage,
            ApplicationDefinition application,
            Workspace workspace) {
        LoadingContext context = loadingContext(loadingStage)
                .application(application)
                .workspaceKind("应用最近工作区")
                .workspaceName(workspace.name())
                .workspaceId(workspace.workspaceId().value());
        // 优先按个人工作区反查；recent 常指向 default 私人 worktree，必须在无权限错误里标明。
        Optional<PersonalWorkspace> personal = managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(workspace.workspaceId());
        if (personal.isPresent()) {
            PersonalWorkspace current = personal.get();
            context.applicationId(current.appId().value())
                    .applicationWorkspaceId(current.applicationWorkspaceId().value())
                    .versionId(current.versionId().value())
                    .workspaceKind("default".equals(current.workspaceName()) ? "default 私人工作区" : "私人工作区")
                    .workspaceName(current.workspaceName())
                    .personalWorkspaceId(current.personalWorkspaceId().value());
            managedWorkspaceRepository.findVersion(current.versionId()).ifPresent(context::version);
            return context;
        }
        managedWorkspaceRepository.findVersionByRuntimeWorkspace(workspace.workspaceId()).ifPresent(version -> context
                .applicationId(version.appId().value())
                .version(version)
                .applicationWorkspaceId(version.applicationWorkspaceId().value())
                .workspaceKind("应用版本工作区"));
        return context;
    }

    private void ensureRepositoryLinked(ApplicationId appId, CodeRepositoryId repositoryId) {
        boolean linked = configurationRepository.findRepositoriesByApplication(appId).stream()
                .anyMatch(repository -> repository.repositoryId().equals(repositoryId));
        if (!linked) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "代码库未关联当前应用",
                    Map.of("appId", appId.value(), "repositoryId", repositoryId.value()));
        }
    }

    private ApplicationWorkspace saveOrReuseWorkspaceTemplate(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            String branch,
            String directoryPath,
            String workspaceName) {
        Optional<ApplicationWorkspace> existing = configurationRepository.findWorkspaceByLocation(appId, repositoryId, branch, directoryPath);
        if (existing.isPresent()) {
            return existing.get();
        }
        String resolvedName = workspaceName == null || workspaceName.isBlank()
                ? defaultWorkspaceName(directoryPath)
                : workspaceName.trim();
        Instant now = Instant.now();
        return configurationRepository.saveWorkspace(new ApplicationWorkspace(
                new ApplicationWorkspaceId(RuntimeIdGenerator.applicationWorkspaceId()),
                appId,
                repositoryId,
                branch,
                directoryPath,
                resolvedName,
                now,
                now));
    }

    private String defaultWorkspaceName(String directoryPath) {
        String value = directoryPath == null || directoryPath.isBlank() ? "workspace" : directoryPath;
        int index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private void ensureMember(ApplicationId appId, UserId userId) {
        ensureMember(appId, userId, loadingContext("managed-workspace").applicationId(appId.value()));
    }

    private void ensureMember(ApplicationId appId, UserId userId, LoadingContext context) {
        if (!configurationRepository.isActiveMember(appId, userId)) {
            LoadingContext resolvedContext = (context == null ? loadingContext("managed-workspace") : context)
                    .applicationId(appId.value());
            if (resolvedContext.appName == null) {
                configurationRepository.findApplication(appId).ifPresent(resolvedContext::application);
            }
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    resolvedContext.permissionMessage(),
                    resolvedContext.details());
        }
    }

    /**
     * 构造托管工作区权限错误上下文。只暴露业务 ID 和展示名，不写入物理路径、Git URL 或密钥信息。
     */
    private static final class LoadingContext {
        private final String loadingStage;
        private String appId;
        private String appName;
        private String versionId;
        private String version;
        private String applicationWorkspaceId;
        private String workspaceKind;
        private String workspaceName;
        private String workspaceId;
        private String personalWorkspaceId;

        private LoadingContext(String loadingStage) {
            this.loadingStage = loadingStage;
        }

        private LoadingContext application(ApplicationDefinition application) {
            if (application != null) {
                this.appId = application.appId().value();
                this.appName = application.appName();
            }
            return this;
        }

        private LoadingContext applicationId(String appId) {
            this.appId = firstNonBlank(this.appId, appId);
            return this;
        }

        private LoadingContext version(ApplicationWorkspaceVersion version) {
            if (version != null) {
                this.versionId = version.versionId().value();
                this.version = version.version();
                this.applicationWorkspaceId = version.applicationWorkspaceId().value();
                this.appId = firstNonBlank(this.appId, version.appId().value());
            }
            return this;
        }

        private LoadingContext versionId(String versionId) {
            if (versionId != null && !versionId.isBlank()) {
                this.versionId = versionId;
            }
            return this;
        }

        private LoadingContext applicationWorkspaceId(String applicationWorkspaceId) {
            if (applicationWorkspaceId != null && !applicationWorkspaceId.isBlank()) {
                this.applicationWorkspaceId = applicationWorkspaceId;
            }
            return this;
        }

        private LoadingContext workspaceKind(String workspaceKind) {
            if (workspaceKind != null && !workspaceKind.isBlank()) {
                this.workspaceKind = workspaceKind;
            }
            return this;
        }

        private LoadingContext workspaceName(String workspaceName) {
            if (workspaceName != null && !workspaceName.isBlank()) {
                this.workspaceName = workspaceName;
            }
            return this;
        }

        private LoadingContext workspaceId(String workspaceId) {
            this.workspaceId = firstNonBlank(this.workspaceId, workspaceId);
            return this;
        }

        private LoadingContext personalWorkspaceId(String personalWorkspaceId) {
            this.personalWorkspaceId = firstNonBlank(this.personalWorkspaceId, personalWorkspaceId);
            return this;
        }

        private String permissionMessage() {
            return "无该应用工作区权限：当前正在加载应用 "
                    + displayApp()
                    + "，版本 "
                    + displayVersion()
                    + "，工作区 "
                    + displayWorkspace();
        }

        private Map<String, Object> details() {
            Map<String, Object> details = new LinkedHashMap<>();
            putIfPresent(details, "loadingStage", loadingStage);
            putIfPresent(details, "appId", appId);
            putIfPresent(details, "appName", appName);
            putIfPresent(details, "versionId", versionId);
            putIfPresent(details, "version", version);
            putIfPresent(details, "applicationWorkspaceId", applicationWorkspaceId);
            putIfPresent(details, "workspaceKind", workspaceKind);
            putIfPresent(details, "workspaceName", workspaceName);
            putIfPresent(details, "workspaceId", workspaceId);
            putIfPresent(details, "personalWorkspaceId", personalWorkspaceId);
            return Map.copyOf(details);
        }

        private String displayApp() {
            String resolvedAppId = display(appId);
            String resolvedAppName = display(appName);
            if ("未确定".equals(resolvedAppName) && "未确定".equals(resolvedAppId)) {
                return "未确定";
            }
            return resolvedAppName + "(" + resolvedAppId + ")";
        }

        private String displayVersion() {
            return display(firstNonBlank(version, versionId));
        }

        private String displayWorkspace() {
            return display(workspaceKind) + ":" + display(firstNonBlank(workspaceName, workspaceId));
        }

        private static void putIfPresent(Map<String, Object> target, String key, String value) {
            if (value != null && !value.isBlank()) {
                target.put(key, value);
            }
        }

        private static String display(String value) {
            return value == null || value.isBlank() ? "未确定" : value;
        }

        private static String firstNonBlank(String first, String second) {
            if (first != null && !first.isBlank()) {
                return first;
            }
            if (second != null && !second.isBlank()) {
                return second;
            }
            return null;
        }
    }

    private void ensurePersonalOwner(PersonalWorkspace personal, UserId userId) {
        if (!personal.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无个人工作区权限", Map.of("personalWorkspaceId", personal.personalWorkspaceId().value()));
        }
    }

    private ApplicationWorkspace existingTemplate(ApplicationWorkspaceId workspaceId) {
        return configurationRepository.findWorkspace(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用工作空间不存在", Map.of("workspaceId", workspaceId.value())));
    }

    private ApplicationWorkspaceVersion existingVersion(ApplicationWorkspaceVersionId versionId) {
        return managedWorkspaceRepository.findVersion(versionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用版本工作区不存在", Map.of("versionId", versionId.value())));
    }

    private PersonalWorkspace existingPersonal(PersonalWorkspaceId personalWorkspaceId) {
        return managedWorkspaceRepository.findPersonalWorkspace(personalWorkspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "个人工作区不存在", Map.of("personalWorkspaceId", personalWorkspaceId.value())));
    }

    private CodeRepository existingRepository(CodeRepositoryId repositoryId) {
        return configurationRepository.findRepository(repositoryId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "代码库不存在", Map.of("repositoryId", repositoryId.value())));
    }

    private String requireRepositoryEnglishName(CodeRepository repository) {
        String englishName = repository.englishName();
        if (englishName == null || englishName.isBlank()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "版本库英文名称不能为空，请先在版本库管理中补充",
                    Map.of("repositoryId", repository.repositoryId().value(), "field", "englishName"));
        }
        return sanitizePathPart(englishName);
    }

    private Workspace existingWorkspace(WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
    }

    private User existingUser(UserId userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "用户不存在", Map.of("userId", userId.value())));
    }

    private String privateKeyFor(CodeRepository repository, UserId userId) {
        if (!repository.internalDeployment() && !requiresSshKey(repository.gitUrl())) {
            return null;
        }
        UserSshKey sshKey = configurationRepository.findSshKeys(userId).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前用户未配置 SSH key"));

        if (sshKey.encryptedAesKey() == null || sshKey.encryptedAesKey().isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR,
                    "SSH key 使用的旧版加密格式，请重新添加",
                    Map.of("sshKeyId", sshKey.sshKeyId().value()));
        }

        return sshKeyEncryptionService.decrypt(
                sshKey.encryptedPrivateKey(),
                sshKey.encryptedAesKey(),
                sshKey.encryptionNonce());
    }

    private String effectiveGitUrl(CodeRepository repository, UserId userId) {
        if (!repository.internalDeployment()) {
            return repository.gitUrl();
        }
        return repository.effectiveGitUrl(existingUser(userId).unifiedAuthId());
    }

    private void ensureInternalOrigin(CodeRepository repository, UserId userId, Path repoRoot, String privateKey) {
        if (!repository.internalDeployment()) {
            return;
        }
        ensureInternalOrigin(repository, effectiveGitUrl(repository, userId), repoRoot, privateKey);
    }

    private void ensureInternalOrigin(CodeRepository repository, String effectiveGitUrl, Path repoRoot, String privateKey) {
        if (repository.internalDeployment()) {
            gitWorkspaceService.setOriginUrl(repoRoot, effectiveGitUrl, privateKey);
        }
    }

    private Path appRepoRoot(String version, CodeRepository repository) {
        // 路径片段统一走 sanitizeVersionForBranchAndPath：yyyy年M月 → yyyy-MM，避免路径里出现中文。
        String pathFragment = sanitizeVersionForBranchAndPath(version);
        return configuredPath(PARAM_OPENCODE_APP_WORKSPACE_ROOT)
                .resolve(pathFragment)
                .resolve(requireRepositoryEnglishName(repository))
                .normalize();
    }

    /**
     * 从通用参数数据库读取必填路径参数（已展开变量引用），缺失或空白时抛异常。
     * common_parameters 为唯一事实源，不在 yaml 或代码常量预留 fallback。
     */
    private Path configuredPath(String parameterEnglishName) {
        return commonParameterValues.resolvedValue(parameterEnglishName)
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "通用参数未配置：" + parameterEnglishName,
                        Map.of("parameter", parameterEnglishName)))
                .toAbsolutePath()
                .normalize();
    }

    private List<ManagedWorkspaceResponses.WorkspaceDiffFileResponse> compareDirectories(Path source, Path target) {
        Map<String, Path> sourceFiles = fileMap(source);
        Map<String, Path> targetFiles = fileMap(target);
        Set<String> all = new java.util.TreeSet<>();
        all.addAll(sourceFiles.keySet());
        all.addAll(targetFiles.keySet());
        List<ManagedWorkspaceResponses.WorkspaceDiffFileResponse> files = new ArrayList<>();
        for (String path : all) {
            Path sourceFile = sourceFiles.get(path);
            Path targetFile = targetFiles.get(path);
            if (sourceFile == null) {
                files.add(new ManagedWorkspaceResponses.WorkspaceDiffFileResponse(path, "deleted", false));
            } else if (targetFile == null) {
                files.add(new ManagedWorkspaceResponses.WorkspaceDiffFileResponse(path, "added", false));
            } else if (!sameContent(sourceFile, targetFile)) {
                files.add(new ManagedWorkspaceResponses.WorkspaceDiffFileResponse(path, "modified", true));
            }
        }
        return files;
    }

    private Map<String, Path> fileMap(Path root) {
        try (var stream = Files.walk(root)) {
            Map<String, Path> files = new LinkedHashMap<>();
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> files.put(root.relativize(path).toString().replace('\\', '/'), path));
            return files;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区目录不可用", Map.of("path", root.toString()), exception);
        }
    }

    private boolean sameContent(Path left, Path right) {
        try {
            return Files.mismatch(left, right) < 0;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "读取工作区文件失败", Map.of("path", left.toString()), exception);
        }
    }

    private void copyFiles(Path sourceRoot, Path targetRoot, List<String> files) {
        for (String file : files) {
            Path source = sourceRoot.resolve(file).normalize();
            Path target = targetRoot.resolve(file).normalize();
            if (!source.startsWith(sourceRoot) || !target.startsWith(targetRoot)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "同步文件路径越权", Map.of("path", file));
            }
            try {
                if (!Files.exists(source)) {
                    Files.deleteIfExists(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception exception) {
                throw new PlatformException(ErrorCode.CONFLICT, "同步文件失败", Map.of("path", file), exception);
            }
        }
    }

    private List<String> repoRelativeFiles(Path repoRoot, Path workspaceRoot, List<String> files) {
        return files.stream()
                .map(file -> repoRoot.relativize(workspaceRoot.resolve(file).normalize()).toString().replace('\\', '/'))
                .toList();
    }

    private void saveSync(
            WorkspaceSyncRecordId syncId,
            UserId userId,
            WorkspaceId source,
            WorkspaceId target,
            WorkspaceSyncDirection direction,
            List<String> files,
            boolean force,
            WorkspaceSyncStatus status,
            String traceId) {
        managedWorkspaceRepository.saveSyncRecord(new WorkspaceSyncRecord(
                syncId,
                userId,
                source,
                target,
                direction,
                files,
                force,
                status,
                traceId,
                Instant.now()));
    }

    private List<String> normalizeFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "同步文件不能为空");
        }
        return files.stream().map(file -> normalizeRelativePath(file, "path")).distinct().toList();
    }

    private String normalizeDirectoryPath(String directoryPath) {
        String value = requireText(directoryPath, "目录路径不能为空", "directoryPath")
                .replace('\\', '/')
                .trim();
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.equals(".") || value.startsWith("/") || value.startsWith("../") || value.contains("/../")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录路径无效", Map.of("directoryPath", directoryPath));
        }
        return value;
    }

    private String normalizeRelativePath(String path, String field) {
        String value = requireText(path, "路径不能为空", field).replace('\\', '/');
        if (value.equals(".") || value.startsWith("/") || value.startsWith("../") || value.contains("/../")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "路径无效", Map.of("path", path));
        }
        return value;
    }

    private Optional<String> normalizeOperationId(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return Optional.empty();
        }
        String value = operationId.trim();
        if (!OPERATION_ID_PATTERN.matcher(value).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "operationId 格式无效", Map.of("field", "operationId"));
        }
        return Optional.of(value);
    }

    private String normalizeVersion(String version) {
        String value = requireText(version, "版本不能为空", "version");
        // 兼容两种格式：
        // - 历史数据：yyyyMMdd（8 位数字）
        // - 新增版本：yyyy年M月（如 2024年1月），用户在前端「+新增版本」选择后原样传入。
        if (VERSION_PATTERN.matcher(value).matches()) {
            return value;
        }
        if (VERSION_PATTERN_YEAR_MONTH.matcher(value).matches()) {
            return value;
        }
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本必须为 yyyyMMdd 或 yyyy年M月", Map.of("version", value));
    }

    private String normalizeNonStandardWorkspaceCreateVersion(String version) {
        String value = requireText(version, "非标准代码库必须输入 yyyyMMdd 版本", "version");
        if (!VERSION_PATTERN.matcher(value).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "非标准代码库版本必须为 yyyyMMdd", Map.of("version", value));
        }
        return value;
    }

    private String versionFromStandardBranch(String branch) {
        java.util.regex.Matcher matcher = STANDARD_BRANCH_PATTERN.matcher(branch);
        if (!matcher.matches()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "标准代码库分支必须为 feature_testagent_yyyyMMdd",
                    Map.of("branch", branch));
        }
        return matcher.group(1);
    }

    /**
     * 把版本字符串转为分支名/路径安全片段。
     * - yyyyMMdd：原样使用。
     * - yyyy年M月：转为 yyyy-MM（如 2024年1月 → 2024-01，2024年12月 → 2024-12）。
     * 数据库中 version 字段保留用户原值（"2024年1月"），仅在拼分支 / 路径时调本方法做转换。
     */
    private String sanitizeVersionForBranchAndPath(String version) {
        java.util.regex.Matcher matcher = VERSION_PATTERN_YEAR_MONTH.matcher(version);
        if (matcher.matches()) {
            String year = matcher.group(1);
            String month = String.format("%02d", Integer.parseInt(matcher.group(2)));
            return year + "-" + month;
        }
        return version;
    }

    private String requireText(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of("field", field));
        }
        return value.trim();
    }

    private Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "工作区目录不可用", Map.of("path", path.toString()), exception);
        }
    }

    private WorkspaceCreateProgress createProgress(
            String operationId,
            ApplicationId appId,
            UserId userId,
            String traceId) {
        if (operationId == null) {
            return WorkspaceCreateProgress.noop();
        }
        workspaceCreateOperationRepository.start(operationId, appId, userId, traceId, Instant.now());
        return new WorkspaceCreateProgress(workspaceCreateOperationRepository, operationId);
    }

    private static boolean requiresSshKey(String gitUrl) {
        return gitUrl.startsWith("ssh://") || SCP_LIKE_SSH_URL.matcher(gitUrl).matches();
    }

    private static String sanitizePathPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sanitizeBranchPart(String value) {
        return sanitizePathPart(value);
    }

    private static final class WorkspaceCreateProgress {
        private static final WorkspaceCreateProgress NOOP = new WorkspaceCreateProgress(null, null);

        private final WorkspaceCreateOperationRepository repository;
        private final String operationId;

        private WorkspaceCreateProgress(WorkspaceCreateOperationRepository repository, String operationId) {
            this.repository = repository;
            this.operationId = operationId;
        }

        private static WorkspaceCreateProgress noop() {
            return NOOP;
        }

        private void step(WorkspaceCreateOperationStep step) {
            if (repository != null) {
                LOGGER.debug("Marking step, operationId={}, step={}", operationId, step.name());
                repository.markStep(operationId, step, Instant.now());
                LOGGER.debug("Step marked, operationId={}, step={}", operationId, step.name());
            }
        }

        private void succeeded(ApplicationWorkspaceId workspaceId, ApplicationWorkspaceVersionId versionId) {
            if (repository != null) {
                LOGGER.info("Marking succeeded, operationId={}, workspaceId={}", operationId, workspaceId.value());
                repository.markSucceeded(operationId, workspaceId, versionId, Instant.now());
                LOGGER.info("Succeeded marked, operationId={}", operationId);
            }
        }

        private void failed(String errorCode, String errorMessage) {
            if (repository != null) {
                repository.markFailed(operationId, errorCode, errorMessage, Instant.now());
            }
        }
    }

    private static final class NoopWorkspaceCreateOperationRepository implements WorkspaceCreateOperationRepository {
        @Override
        public WorkspaceCreateOperation start(
                String operationId,
                ApplicationId appId,
                UserId requestedBy,
                String traceId,
                Instant now) {
            return operation(operationId, appId, requestedBy, WorkspaceCreateOperationStatus.RUNNING, WorkspaceCreateOperationStep.VALIDATING_INPUT, null, null, null, null, traceId, now);
        }

        @Override
        public WorkspaceCreateOperation markStep(String operationId, WorkspaceCreateOperationStep step, Instant now) {
            return operation(operationId, new ApplicationId("app_noop"), new UserId("usr_noop"), WorkspaceCreateOperationStatus.RUNNING, step, null, null, null, null, "trace_noop", now);
        }

        @Override
        public WorkspaceCreateOperation markSucceeded(
                String operationId,
                ApplicationWorkspaceId workspaceId,
                ApplicationWorkspaceVersionId versionId,
                Instant now) {
            return operation(operationId, new ApplicationId("app_noop"), new UserId("usr_noop"), WorkspaceCreateOperationStatus.SUCCEEDED, WorkspaceCreateOperationStep.COMPLETED, null, null, workspaceId, versionId, "trace_noop", now);
        }

        @Override
        public WorkspaceCreateOperation markFailed(String operationId, String errorCode, String errorMessage, Instant now) {
            return operation(operationId, new ApplicationId("app_noop"), new UserId("usr_noop"), WorkspaceCreateOperationStatus.FAILED, WorkspaceCreateOperationStep.VALIDATING_INPUT, errorCode, errorMessage, null, null, "trace_noop", now);
        }

        @Override
        public Optional<WorkspaceCreateOperation> findById(String operationId) {
            return Optional.empty();
        }

        private WorkspaceCreateOperation operation(
                String operationId,
                ApplicationId appId,
                UserId requestedBy,
                WorkspaceCreateOperationStatus status,
                WorkspaceCreateOperationStep currentStep,
                String errorCode,
                String errorMessage,
                ApplicationWorkspaceId workspaceId,
                ApplicationWorkspaceVersionId versionId,
                String traceId,
                Instant now) {
            return new WorkspaceCreateOperation(
                    operationId,
                    appId,
                    requestedBy,
                    status,
                    currentStep,
                    errorCode,
                    errorMessage,
                    workspaceId,
                    versionId,
                    traceId,
                    now,
                    now);
        }
    }
}
