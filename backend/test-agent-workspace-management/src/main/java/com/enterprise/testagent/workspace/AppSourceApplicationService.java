package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationId;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceRecentSelection;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceRetention;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 应用源码快照入口业务服务。
 *
 * <p>每个公开业务入口都重新核对应用启用、成员关系、仓库关联和代码仓库类型。远端 Git 只读校验
 * 完成后才进入事务登记；事务返回前不调用副本 dispatcher，因此回滚路径不会触碰磁盘或广播。
 */
@Service
public class AppSourceApplicationService {

    public static final String INDEX_FILE_NAME = ".testagent-appsource-index.json";
    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$");
    private static final Pattern ENGLISH_NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,126}[A-Za-z0-9])?$");

    private final ConfigurationManagementRepository configuration;
    private final AppSourceRepository appSources;
    private final UserRepository users;
    private final OpencodeProcessManagementRepository processes;
    private final OpencodeProcessHeartbeatStore heartbeats;
    private final GitRemoteService remote;
    private final GitWorkspaceService git;
    private final SshKeyEncryptionService sshKeyEncryption;
    private final AppSourceMaterializationRegistrar registrar;
    private final AppSourceReplicaRetryRegistrar retryRegistrar;
    private final AppSourceReplicaTaskDispatcher dispatcher;
    private final AppSourceWorkspaceOpener workspaceOpener;
    private final Clock clock;

    /** 生产构造器统一注入权限、Git、事务登记和异步唤醒边界。 */
    @Autowired
    public AppSourceApplicationService(
            ConfigurationManagementRepository configuration,
            AppSourceRepository appSources,
            UserRepository users,
            OpencodeProcessManagementRepository processes,
            OpencodeProcessHeartbeatStore heartbeats,
            SshKeyEncryptionService sshKeyEncryption,
            AppSourceMaterializationRegistrar registrar,
            AppSourceReplicaRetryRegistrar retryRegistrar,
            AppSourceReplicaTaskDispatcher dispatcher,
            AppSourceWorkspaceOpener workspaceOpener) {
        this(configuration, appSources, users, processes, heartbeats, new GitRemoteService(),
                new GitWorkspaceService(), sshKeyEncryption, registrar, retryRegistrar, dispatcher,
                workspaceOpener, Clock.systemUTC());
    }

    /** 测试构造器允许固定远端 Git 和权威时间。 */
    AppSourceApplicationService(
            ConfigurationManagementRepository configuration,
            AppSourceRepository appSources,
            UserRepository users,
            OpencodeProcessManagementRepository processes,
            OpencodeProcessHeartbeatStore heartbeats,
            GitRemoteService remote,
            GitWorkspaceService git,
            SshKeyEncryptionService sshKeyEncryption,
            AppSourceMaterializationRegistrar registrar,
            AppSourceReplicaRetryRegistrar retryRegistrar,
            AppSourceReplicaTaskDispatcher dispatcher,
            AppSourceWorkspaceOpener workspaceOpener,
            Clock clock) {
        this.configuration = Objects.requireNonNull(configuration);
        this.appSources = Objects.requireNonNull(appSources);
        this.users = Objects.requireNonNull(users);
        this.processes = Objects.requireNonNull(processes);
        this.heartbeats = Objects.requireNonNull(heartbeats);
        this.remote = Objects.requireNonNull(remote);
        this.git = Objects.requireNonNull(git);
        this.sshKeyEncryption = Objects.requireNonNull(sshKeyEncryption);
        this.registrar = Objects.requireNonNull(registrar);
        this.retryRegistrar = Objects.requireNonNull(retryRegistrar);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.workspaceOpener = Objects.requireNonNull(workspaceOpener);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 列出当前应用关联的应用代码库及其未过期占用状态。 */
    public List<RepositorySummary> listRepositories(String appId, UserId userId, boolean appAdmin) {
        return listRepositories(appId, userId, appAdmin, null);
    }

    /**
     * 列出当前应用关联的源码仓库，并按当前用户所在服务器计算能否打开。
     *
     * <p>四种 downloadState 只表达稳定生命周期；执行中的细节由 latestOperation 提供，避免把异步
     * operation 状态误当成已经可打开的快照。
     */
    public List<RepositorySummary> listRepositories(
            String appId,
            UserId userId,
            boolean appAdmin,
            String currentLinuxServerId) {
        ApplicationId parsedAppId = applicationId(appId);
        requireMember(parsedAppId, userId);
        Instant now = clock.instant();
        return configuration.findRepositoriesByApplication(parsedAppId).stream()
                .filter(this::isApplicationCodeRepository)
                .map(repository -> {
                    AppSourceSnapshot active = appSources.findActiveSnapshot(repository.repositoryId()).orElse(null);
                    DownloadState downloadState = downloadState(active, userId, now);
                    boolean occupied = active != null && active.expiresAt().isAfter(now);
                    boolean manageable = occupied && (Objects.equals(active.ownerUserId(), userId) || appAdmin);
                    // 列表业务契约直接提供占用人展示身份，API 层不得再越过业务服务查询 UserRepository。
                    User owner = active == null || active.ownerUserId() == null
                            ? null
                            : users.findByUserId(active.ownerUserId()).orElse(null);
                    List<ServerSummary> serverSummaries = active == null
                            ? List.of()
                            : serverSummaries(active, List.of());
                    boolean openable = downloadState == DownloadState.DOWNLOADED_ACTIVE
                            && currentLinuxServerId != null
                            && serverSummaries.stream().anyMatch(server ->
                                    server.linuxServerId().equals(currentLinuxServerId)
                                            && server.replicaStatus() == AppSourceReplicaStatus.READY);
                    String unavailableReason = unavailableReason(
                            downloadState,
                            openable,
                            currentLinuxServerId);
                    OperationSnapshot latestOperation = appSources.findLatestOperation(repository.repositoryId())
                            .map(this::operationSnapshot)
                            .orElse(null);
                    return new RepositorySummary(
                            repository.repositoryId().value(),
                            repository.name(),
                            repository.englishName(),
                            downloadState,
                            active == null ? null : active.generation(),
                            active == null ? null : active.purpose(),
                            active == null ? null : active.ownerUserId(),
                            owner == null ? null : owner.username(),
                            owner == null ? null : owner.unifiedAuthId(),
                            active == null ? null : active.branch(),
                            active == null ? null : active.targetCommit(),
                            active == null ? List.of() : active.selectedPaths(),
                            active == null ? null : active.expiresAt(),
                            occupied,
                            openable,
                            manageable,
                            unavailableReason,
                            latestOperation,
                            serverSummaries);
                })
                .toList();
    }

    private DownloadState downloadState(AppSourceSnapshot active, UserId userId, Instant now) {
        if (active == null) {
            return DownloadState.NOT_DOWNLOADED;
        }
        if (active != null
                && (active.status() != com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus.ACTIVE
                        || !active.expiresAt().isAfter(now))) {
            return DownloadState.DOWNLOADED_EXPIRED;
        }
        if (active != null
                && active.purpose() == AppSourcePurpose.PERSONAL
                && active.expiresAt().isAfter(now)
                && !Objects.equals(active.ownerUserId(), userId)) {
            return DownloadState.PERSONAL_OCCUPIED;
        }
        return DownloadState.DOWNLOADED_ACTIVE;
    }

    private String unavailableReason(
            DownloadState downloadState,
            boolean openable,
            String currentLinuxServerId) {
        return switch (downloadState) {
            case NOT_DOWNLOADED -> "NOT_DOWNLOADED";
            case DOWNLOADED_EXPIRED -> "SNAPSHOT_EXPIRED";
            case PERSONAL_OCCUPIED -> "PERSONAL_OCCUPIED";
            case DOWNLOADED_ACTIVE -> openable
                    ? null
                    : (currentLinuxServerId == null
                            ? "CURRENT_SERVER_UNKNOWN"
                            : "CURRENT_SERVER_REPLICA_NOT_READY");
        };
    }

    private OperationSnapshot operationSnapshot(AppSourceOperation operation) {
        AppSourceSnapshot snapshot = appSources
                .findSnapshot(operation.repositoryId(), operation.targetGeneration())
                .orElse(null);
        List<AppSourceOperationStep> steps = appSources.findSteps(operation.operationId());
        return new OperationSnapshot(
                operation.operationId(),
                operation.appId().value(),
                operation.repositoryId().value(),
                operation.sourceGeneration(),
                operation.targetGeneration(),
                operation.operationType(),
                operation.status(),
                snapshot == null ? null : snapshot.purpose(),
                snapshot == null ? null : snapshot.branch(),
                snapshot == null ? null : snapshot.targetCommit(),
                snapshot == null ? List.of() : snapshot.selectedPaths(),
                snapshot == null ? null : snapshot.expiresAt(),
                operation.traceId(),
                operation.acceptedAt(),
                operation.completedAt(),
                stepSummaries(steps.stream()
                        .filter(step -> step.scope() == com.enterprise.testagent.domain.appsource.AppSourceStepScope.GLOBAL)
                        .toList()),
                snapshot == null ? List.of() : serverSummaries(snapshot, steps));
    }

    private List<ServerSummary> serverSummaries(
            AppSourceSnapshot snapshot,
            List<AppSourceOperationStep> steps) {
        Map<String, AppSourceReplicaStatus> statuses = new LinkedHashMap<>();
        Map<String, com.enterprise.testagent.domain.appsource.AppSourceReplica> replicas = new LinkedHashMap<>();
        appSources.findReplicas(snapshot.repositoryId(), snapshot.generation()).stream()
                .sorted(Comparator.comparing(replica -> replica.linuxServerId().value()))
                .forEach(replica -> {
                    statuses.put(replica.linuxServerId().value(), replica.status());
                    replicas.put(replica.linuxServerId().value(), replica);
                });
        steps.stream()
                .filter(step -> step.scope() == com.enterprise.testagent.domain.appsource.AppSourceStepScope.SERVER)
                .map(step -> step.linuxServerId().value())
                .sorted()
                .forEach(serverId -> statuses.putIfAbsent(serverId, null));
        return statuses.keySet().stream()
                .sorted()
                .map(serverId -> {
                    var replica = replicas.get(serverId);
                    List<AppSourceOperationStep> serverSteps = steps.stream()
                            .filter(step -> step.scope()
                                    == com.enterprise.testagent.domain.appsource.AppSourceStepScope.SERVER)
                            .filter(step -> step.linuxServerId().value().equals(serverId))
                            .toList();
                    return new ServerSummary(
                            serverId,
                            statuses.get(serverId),
                            replica == null ? 0 : replica.attemptCount(),
                            replica == null ? null : replica.safeErrorCode(),
                            replica == null ? null : replica.safeErrorMessage(),
                            snapshot.targetCommit(),
                            stepSummaries(serverSteps));
                })
                .toList();
    }

    private List<StepSummary> stepSummaries(List<AppSourceOperationStep> steps) {
        return steps.stream()
                .sorted(Comparator.comparingInt(AppSourceOperationStep::sequence)
                        .thenComparing(AppSourceOperationStep::stepId))
                .map(step -> new StepSummary(
                        step.stepCode(),
                        step.sequence(),
                        step.status(),
                        step.safeSummary(),
                        step.startedAt(),
                        step.completedAt(),
                        elapsedMillis(step),
                        step.updatedAt()))
                .toList();
    }

    private Long elapsedMillis(AppSourceOperationStep step) {
        if (step.startedAt() == null) {
            return null;
        }
        Instant end = step.completedAt() == null ? step.updatedAt() : step.completedAt();
        return Math.max(0L, java.time.Duration.between(step.startedAt(), end).toMillis());
    }

    /** 普通有效成员可使用自己的凭据列出远端分支。 */
    public List<String> listBranches(String appId, String repositoryId, UserId userId) {
        CodeRepository repository = requireLinkedCodeRepository(applicationId(appId), repositoryId(repositoryId), userId);
        GitAccess access = gitAccess(repository, userId);
        return remote.listBranches(access.url(), access.privateKey());
    }

    /** 普通有效成员可按提交/分支懒加载远端精确树；path 只用于返回指定目录的直接 children。 */
    public List<GitRemoteService.RemoteTreeNode> listTree(
            String appId, String repositoryId, String branch, String path, UserId userId) {
        return getTreeSnapshot(appId, repositoryId, branch, path, userId).nodes();
    }

    /**
     * 返回目录节点及其同一次远端解析得到的固定提交。
     *
     * <p>提交解析与列树必须保持在一次业务调用内，避免分支移动时把旧树节点与新提交组合后交给物化入口。
     */
    public TreeSnapshot getTreeSnapshot(
            String appId, String repositoryId, String branch, String path, UserId userId) {
        CodeRepository repository = requireLinkedCodeRepository(applicationId(appId), repositoryId(repositoryId), userId);
        String normalizedBranch = normalizeBranch(branch);
        String normalizedPath = normalizeSelectedPath(path == null || path.isBlank() ? "." : path);
        GitAccess access = gitAccess(repository, userId);
        String commit = git.resolveRemoteBranchCommit(access.url(), normalizedBranch, access.privateKey());
        List<GitRemoteService.RemoteTreeNode> tree = remote.listTree(access.url(), commit, access.privateKey());
        if (".".equals(normalizedPath)) {
            return new TreeSnapshot(commit, tree);
        }
        List<GitRemoteService.RemoteTreeNode> nodes = flatten(tree).stream()
                .filter(node -> node.path().equals(normalizedPath))
                .filter(node -> GitRemoteService.NODE_TYPE_DIRECTORY.equals(node.type()))
                .findFirst()
                .map(GitRemoteService.RemoteTreeNode::children)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "远端源码目录不存在"));
        return new TreeSnapshot(commit, nodes);
    }

    /** 校验不可变远端选择后登记 generation，并仅在事务提交返回后唤醒副本执行器。 */
    public AppSourceOperation materialize(
            String appId,
            String repositoryId,
            MaterializationCommand command,
            UserId userId,
            boolean appAdmin,
            String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        ApplicationId parsedAppId = applicationId(appId);
        CodeRepository repository = requireLinkedCodeRepository(parsedAppId, repositoryId(repositoryId), userId);
        Instant acceptedAt = clock.instant();
        String branch = normalizeBranch(command.branch());
        List<AppSourceSelectedPath> selectedPaths = normalizeSelections(command.selectedPaths());
        String requestHash = requestHash(command, branch, command.expectedTreeCommit(), selectedPaths);
        AppSourceOperation existingOperation = appSources.findOperation(command.operationId()).orElse(null);
        if (existingOperation != null) {
            boolean sameRequest = existingOperation.appId().equals(parsedAppId)
                    && existingOperation.repositoryId().equals(repository.repositoryId())
                    && existingOperation.actorUserId().equals(userId)
                    && existingOperation.requestHash().equals(requestHash);
            if (!sameRequest) {
                throw new PlatformException(ErrorCode.CONFLICT, "operationId 已被其它应用源码请求使用");
            }
            return existingOperation;
        }
        AppSourceRepositorySlot slot = appSources.findSlot(repository.repositoryId()).orElse(null);
        AppSourceSnapshot active = appSources.findActiveSnapshot(repository.repositoryId()).orElse(null);
        requireLifecyclePermission(active, command.purpose(), userId, appAdmin, acceptedAt, command.confirmReplace());
        Long actualGeneration = slot == null ? null : slot.activeGeneration();
        if (!Objects.equals(actualGeneration, command.expectedGeneration())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "应用源码 generation 已变化",
                    Map.of("actualGeneration", actualGeneration == null ? 0L : actualGeneration));
        }

        GitAccess access = gitAccess(repository, userId);
        String targetCommit = git.resolveRemoteBranchCommit(access.url(), branch, access.privateKey());
        if (!targetCommit.equals(command.expectedTreeCommit())) {
            throw new PlatformException(ErrorCode.CONFLICT, "远端分支已更新，请刷新目录树后重试");
        }
        validateRemoteSelections(
                selectedPaths,
                remote.listTree(access.url(), targetCommit, access.privateKey()));

        Set<LinuxServerId> targets = targetServers(command.purpose(), userId);
        AppSourceRetention retention = new AppSourceRetention(command.retentionHours());
        AppSourceOperationType operationType = operationType(active, command.purpose(), branch, selectedPaths);
        AppSourceMaterializationRegistrar.RegistrationResult registered = registrar.register(
                new AppSourceMaterializationRegistrar.RegistrationRequest(
                        command.operationId(), parsedAppId, repository.repositoryId(),
                        requireEnglishName(repository), userId, operationType, requestHash,
                        command.expectedGeneration(), branch, targetCommit, selectedPaths, command.purpose(),
                        retention.expiresAt(acceptedAt), targets, requireTraceId(traceId), acceptedAt));
        dispatcher.wake(registered.operation(), registered.targetServerIds());
        return registered.operation();
    }

    /** 仅重试当前 active generation 的 FAILED/STALE 服务器，不重新解析 Git 或修改冻结快照。 */
    public AppSourceOperation retry(
            String appId,
            String repositoryId,
            RetryCommand command,
            UserId userId,
            boolean appAdmin,
            String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        ApplicationId parsedAppId = applicationId(appId);
        CodeRepository repository = requireLinkedCodeRepository(parsedAppId, repositoryId(repositoryId), userId);
        AppSourceOperation existing = appSources.findOperation(command.operationId()).orElse(null);
        if (existing != null) {
            if (!matchesRetryIdentity(
                    existing, parsedAppId, repository.repositoryId(), userId, command.expectedGeneration())) {
                throw new PlatformException(ErrorCode.CONFLICT, "operationId 已被其它应用源码请求使用");
            }
            return existing;
        }
        Instant acceptedAt = clock.instant();
        AppSourceSnapshot active = appSources.findActiveSnapshot(repository.repositoryId())
                .filter(snapshot -> snapshot.generation() == command.expectedGeneration())
                .filter(snapshot -> snapshot.status() == com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus.ACTIVE)
                .filter(snapshot -> snapshot.expiresAt().isAfter(acceptedAt))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码 generation 已变化或已到期"));
        if (!Objects.equals(active.ownerUserId(), userId) && !appAdmin) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用源码仅允许下载者或应用管理员重试");
        }
        Set<LinuxServerId> allowedTargets = active.purpose() == AppSourcePurpose.PERSONAL
                ? targetServers(AppSourcePurpose.PERSONAL, userId)
                : null;
        Set<LinuxServerId> failedTargets = appSources.findReplicas(repository.repositoryId(), active.generation()).stream()
                .filter(replica -> replica.status() == AppSourceReplicaStatus.FAILED
                        || replica.status() == AppSourceReplicaStatus.STALE)
                .map(replica -> replica.linuxServerId())
                .filter(serverId -> allowedTargets == null || allowedTargets.contains(serverId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (failedTargets.isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前 generation 没有可重试的失败副本");
        }
        // retry 幂等身份只包含客户端不可变请求；运行中的动态失败服务器集合不能参与重放匹配。
        String hash = sha256(command.operationId() + "\n" + command.expectedGeneration());
        AppSourceOperation operation = retryRegistrar.register(new AppSourceReplicaRetryRegistrar.RetryRequest(
                command.operationId(), parsedAppId, repository.repositoryId(), active.generation(), userId,
                hash, failedTargets, requireTraceId(traceId), acceptedAt));
        dispatcher.wake(operation, failedTargets);
        return operation;
    }

    private boolean matchesRetryIdentity(
            AppSourceOperation operation,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            UserId actorUserId,
            long generation) {
        return operation.appId().equals(appId)
                && operation.repositoryId().equals(repositoryId)
                && operation.actorUserId().equals(actorUserId)
                && operation.operationType() == AppSourceOperationType.RETRY_REPLICAS
                && operation.targetGeneration() == generation;
    }

    /**
     * 查询操作的数据库权威进度快照，并在每次 HTTP/WS 读取时重新执行当前授权。
     *
     * <p>TEAM 允许当前关联应用有效成员读取；PERSONAL 只允许 owner，或仍具应用管理员角色且仍是
     * 当前关联应用有效成员的用户读取。该方法只返回安全摘要，不暴露物理路径或 Git 原始错误。
     */
    public OperationSnapshot getOperation(String operationId, UserId userId, boolean appAdmin) {
        String normalizedOperationId = AppSourceOperationId.normalize(operationId);
        AppSourceOperation operation = appSources.findOperation(normalizedOperationId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码操作不存在"));
        requireOperationRepositoryMember(operation.repositoryId(), userId);
        AppSourceSnapshot snapshot = appSources
                .findSnapshot(operation.repositoryId(), operation.targetGeneration())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码操作缺少快照"));
        if (snapshot.purpose() == AppSourcePurpose.PERSONAL
                && !Objects.equals(snapshot.ownerUserId(), userId)
                && !appAdmin) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "个人应用源码操作只允许拥有者或应用管理员查看");
        }
        return operationSnapshot(operation);
    }

    /** operation 的 TEAM 进度属于 repository 当前关联应用集合，而不是仅属于最初 route app。 */
    private void requireOperationRepositoryMember(CodeRepositoryId repositoryId, UserId userId) {
        CodeRepository repository = configuration.findRepository(repositoryId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码版本库不存在"));
        if (!isApplicationCodeRepository(repository)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本库类型不是应用代码库");
        }
        boolean currentMember = configuration.findApplicationsByRepository(repositoryId).stream()
                .filter(ApplicationDefinition::enabled)
                .anyMatch(application -> configuration.isActiveMember(application.appId(), userId));
        if (!currentMember) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户不是版本库关联应用的有效成员");
        }
    }

    /** 实时授权并打开指定 generation 在当前服务器的 READY Workspace。 */
    public OpenResult open(
            String appId,
            String repositoryId,
            long generation,
            UserId userId,
            String linuxServerId) {
        return openAuthorized(appId, repositoryId, generation, userId, linuxServerId, true);
    }

    private OpenResult openAuthorized(
            String appId,
            String repositoryId,
            long generation,
            UserId userId,
            String linuxServerId,
            boolean recordRecent) {
        ApplicationId parsedAppId = applicationId(appId);
        CodeRepository repository = requireLinkedCodeRepository(parsedAppId, repositoryId(repositoryId), userId);
        Instant now = clock.instant();
        AppSourceRepositorySlot slot = appSources.findSlot(repository.repositoryId())
                .filter(value -> Objects.equals(value.activeGeneration(), generation))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码 generation 不是当前版本"));
        AppSourceSnapshot snapshot = appSources.findSnapshot(repository.repositoryId(), generation)
                .filter(value -> value.status() == com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus.ACTIVE)
                .filter(value -> value.expiresAt().isAfter(now))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码快照不可打开或已到期"));
        if (snapshot.purpose() == AppSourcePurpose.PERSONAL
                && !Objects.equals(snapshot.ownerUserId(), userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "个人应用源码只允许拥有者打开");
        }
        LinuxServerId serverId = new LinuxServerId(linuxServerId);
        Workspace workspace = workspaceOpener.open(snapshot, serverId);
        if (recordRecent) {
            appSources.upsertRecentSelection(new AppSourceRecentSelection(
                    userId, parsedAppId, repository.repositoryId(), generation, now));
        }
        return new OpenResult(
                parsedAppId.value(), repository.repositoryId().value(), snapshot.generation(),
                snapshot.purpose(), workspace.workspaceId().value(), serverId.value(), snapshot.expiresAt());
    }

    /** 最近选择只是便捷指针；每次查询重新执行完整实时授权，失效时立即删除。 */
    public Optional<OpenResult> recent(UserId userId, String linuxServerId) {
        AppSourceRecentSelection recent = appSources.findRecentSelection(userId).orElse(null);
        if (recent == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(openAuthorized(
                    recent.appId().value(), recent.repositoryId().value(), recent.generation(),
                    userId, linuxServerId, true));
        } catch (PlatformException exception) {
            if (isDeterministicallyInvalidRecent(exception.errorCode())) {
                appSources.deleteRecentSelection(userId);
                return Optional.empty();
            }
            throw exception;
        }
    }

    /** clear 也先走 open 的完整实时授权，但不会在删除前重复写入 recent。 */
    public void clearRecent(UserId userId, String linuxServerId) {
        AppSourceRecentSelection recent = appSources.findRecentSelection(userId).orElse(null);
        if (recent == null) {
            return;
        }
        openAuthorized(
                recent.appId().value(), recent.repositoryId().value(), recent.generation(),
                userId, linuxServerId, false);
        appSources.deleteRecentSelection(userId);
    }

    private boolean isDeterministicallyInvalidRecent(ErrorCode errorCode) {
        return errorCode == ErrorCode.FORBIDDEN
                || errorCode == ErrorCode.NOT_FOUND
                || errorCode == ErrorCode.CONFLICT;
    }

    private void requireLifecyclePermission(
            AppSourceSnapshot active,
            AppSourcePurpose requestedPurpose,
            UserId userId,
            boolean appAdmin,
            Instant now,
            boolean confirmReplace) {
        if (active == null || !active.expiresAt().isAfter(now)) {
            return;
        }
        if (active.purpose() == AppSourcePurpose.TEAM && requestedPurpose == AppSourcePurpose.PERSONAL) {
            throw new PlatformException(ErrorCode.CONFLICT, "团队源码快照不能降级为个人快照");
        }
        if (!Objects.equals(active.ownerUserId(), userId) && !appAdmin) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "应用源码仅允许下载者或应用管理员管理");
        }
        if (!confirmReplace) {
            throw new PlatformException(ErrorCode.CONFLICT, "替换现有源码快照需要明确确认");
        }
    }

    private Set<LinuxServerId> targetServers(AppSourcePurpose purpose, UserId userId) {
        if (purpose == AppSourcePurpose.TEAM) {
            Set<LinuxServerId> live = heartbeats.liveBackendServerIds();
            if (live == null || live.isEmpty()) {
                throw new PlatformException(ErrorCode.CONFLICT, "当前没有在线后端服务器可物化团队源码");
            }
            return Set.copyOf(live);
        }
        UserOpencodeProcessBinding binding = processes.findUserBinding(userId, "opencode")
                .filter(value -> value.status() == UserOpencodeProcessBindingStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "当前用户没有 READY OpenCode 进程"));
        OpencodeServerProcess process = processes.findOpencodeServerProcessById(binding.processId())
                .filter(value -> value.userId().equals(userId))
                .filter(value -> value.linuxServerId().equals(binding.linuxServerId()))
                .filter(value -> value.status() == OpencodeServerProcessStatus.RUNNING)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "当前用户没有 READY OpenCode 进程"));
        Set<?> liveProcesses = heartbeats.liveOpencodeProcessIds();
        if (liveProcesses == null || !liveProcesses.contains(process.processId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前用户没有 READY OpenCode 进程");
        }
        return Set.of(process.linuxServerId());
    }

    private void validateRemoteSelections(
            List<AppSourceSelectedPath> selections,
            List<GitRemoteService.RemoteTreeNode> tree) {
        if (selections.size() == 1 && ".".equals(selections.getFirst().path())) {
            if (selections.getFirst().pathType() != AppSourcePathType.DIRECTORY) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "仓库根只能按目录选择");
            }
            return;
        }
        Map<String, String> types = new LinkedHashMap<>();
        flatten(tree).forEach(node -> types.put(node.path(), node.type()));
        for (AppSourceSelectedPath selected : selections) {
            String actualType = types.get(selected.path());
            String expectedType = selected.pathType() == AppSourcePathType.FILE
                    ? GitRemoteService.NODE_TYPE_FILE : GitRemoteService.NODE_TYPE_DIRECTORY;
            if (!expectedType.equals(actualType)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "远端源码选择已变化，请刷新目录树后重试",
                        Map.of("path", selected.path()));
            }
        }
    }

    private List<GitRemoteService.RemoteTreeNode> flatten(List<GitRemoteService.RemoteTreeNode> roots) {
        List<GitRemoteService.RemoteTreeNode> result = new ArrayList<>();
        for (GitRemoteService.RemoteTreeNode node : roots == null ? List.<GitRemoteService.RemoteTreeNode>of() : roots) {
            result.add(node);
            result.addAll(flatten(node.children()));
        }
        return result;
    }

    private List<AppSourceSelectedPath> normalizeSelections(List<SelectedPathCommand> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "至少选择一个源码文件或目录");
        }
        Map<String, AppSourceSelectedPath> normalized = new LinkedHashMap<>();
        for (SelectedPathCommand selected : selections) {
            Objects.requireNonNull(selected, "selected path must not be null");
            String path = normalizeSelectedPath(selected.path());
            AppSourceSelectedPath previous = normalized.putIfAbsent(
                    path, new AppSourceSelectedPath(path, Objects.requireNonNull(selected.pathType())));
            if (previous != null && previous.pathType() != selected.pathType()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "同一路径不能同时作为文件和目录选择");
            }
        }
        if (normalized.containsKey(".") && normalized.size() > 1) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "选择仓库根时不能再选择其它路径");
        }
        return normalized.values().stream()
                .sorted(Comparator.comparing(AppSourceSelectedPath::path))
                .toList();
    }

    private String normalizeSelectedPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "源码选择路径不能为空");
        }
        String text = rawPath.trim().replace('\\', '/');
        if (".".equals(text)) {
            return ".";
        }
        Path normalized = Path.of(text).normalize();
        String value = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || value.isBlank() || value.equals("..") || value.startsWith("../")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "源码选择路径越界");
        }
        for (String segment : value.split("/")) {
            if (".git".equals(segment) || INDEX_FILE_NAME.equals(segment)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "源码选择不能包含平台隐藏路径");
            }
        }
        return value;
    }

    private AppSourceOperationType operationType(
            AppSourceSnapshot active,
            AppSourcePurpose purpose,
            String branch,
            List<AppSourceSelectedPath> selections) {
        if (active == null) {
            return AppSourceOperationType.DOWNLOAD;
        }
        if (active.purpose() == AppSourcePurpose.PERSONAL && purpose == AppSourcePurpose.TEAM) {
            return AppSourceOperationType.PROMOTE_TO_TEAM;
        }
        if (!active.branch().equals(branch)) {
            return AppSourceOperationType.SWITCH_BRANCH;
        }
        if (!active.selectedPaths().equals(selections)) {
            return AppSourceOperationType.CHANGE_SELECTION;
        }
        return AppSourceOperationType.UPDATE;
    }

    private String requestHash(
            MaterializationCommand command,
            String branch,
            String commit,
            List<AppSourceSelectedPath> selectedPaths) {
        String canonical = command.operationId() + "\n"
                + String.valueOf(command.expectedGeneration()) + "\n"
                + branch + "\n" + commit + "\n" + command.purpose().name() + "\n"
                + command.retentionHours() + "\n"
                + selectedPaths.stream()
                        .map(path -> path.pathType().name() + ":" + path.path())
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private CodeRepository requireLinkedCodeRepository(
            ApplicationId appId, CodeRepositoryId repositoryId, UserId userId) {
        requireMember(appId, userId);
        CodeRepository repository = configuration.findRepositoriesByApplication(appId).stream()
                .filter(candidate -> candidate.repositoryId().equals(repositoryId))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码版本库未关联当前应用"));
        if (!isApplicationCodeRepository(repository)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本库类型不是应用代码库");
        }
        requireEnglishName(repository);
        return repository;
    }

    private ApplicationDefinition requireMember(ApplicationId appId, UserId userId) {
        ApplicationDefinition application = configuration.findApplication(appId)
                .filter(ApplicationDefinition::enabled)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用不存在或未启用"));
        if (!configuration.isActiveMember(appId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户不是应用有效成员");
        }
        return application;
    }

    private boolean isApplicationCodeRepository(CodeRepository repository) {
        return CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value().equals(repository.repositoryType());
    }

    private GitAccess gitAccess(CodeRepository repository, UserId userId) {
        String privateKey = null;
        if (repository.internalDeployment() || requiresSshKey(repository.gitUrl())) {
            UserSshKey key = configuration.findSshKeys(userId).stream()
                    .findFirst()
                    .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前用户未配置 SSH key"));
            if (key.encryptedAesKey() == null || key.encryptedAesKey().isBlank()) {
                throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key 使用的旧版加密格式，请重新添加");
            }
            privateKey = sshKeyEncryption.decrypt(
                    key.encryptedPrivateKey(), key.encryptedAesKey(), key.encryptionNonce());
        }
        if (!repository.internalDeployment()) {
            return new GitAccess(repository.gitUrl(), privateKey);
        }
        User user = users.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "凭据用户不存在"));
        return new GitAccess(repository.effectiveGitUrl(user.unifiedAuthId()), privateKey);
    }

    private boolean requiresSshKey(String url) {
        return url != null && (url.startsWith("ssh://") || (url.contains("@") && url.contains(":")));
    }

    private String requireEnglishName(CodeRepository repository) {
        if (repository.englishName() == null || !ENGLISH_NAME_PATTERN.matcher(repository.englishName()).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本库英文名称不符合源码目录规范");
        }
        return repository.englishName();
    }

    private String normalizeBranch(String branch) {
        String value = branch == null ? "" : branch.trim();
        if (!BRANCH_PATTERN.matcher(value).matches()
                || value.contains("..") || value.contains("//") || value.endsWith("/")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Git 分支名称非法");
        }
        return value;
    }

    private ApplicationId applicationId(String value) {
        try {
            return new ApplicationId(value);
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "应用 ID 非法");
        }
    }

    private CodeRepositoryId repositoryId(String value) {
        try {
            return new CodeRepositoryId(value);
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "版本库 ID 非法");
        }
    }

    private String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "traceId 不能为空");
        }
        return traceId.trim();
    }

    /** 物化入口冻结的请求；selectedPaths 表示完整 exact set，而不是增量。 */
    public record MaterializationCommand(
            String operationId,
            Long expectedGeneration,
            String branch,
            String expectedTreeCommit,
            List<SelectedPathCommand> selectedPaths,
            AppSourcePurpose purpose,
            int retentionHours,
            boolean confirmReplace) {
        public MaterializationCommand {
            operationId = AppSourceOperationId.normalize(operationId);
            if (expectedTreeCommit == null || expectedTreeCommit.isBlank()) {
                throw new IllegalArgumentException("expectedTreeCommit must not be blank");
            }
            Objects.requireNonNull(purpose, "purpose must not be null");
            selectedPaths = selectedPaths == null ? List.of() : List.copyOf(selectedPaths);
        }
    }

    /** 单个精确路径及远端节点类型。 */
    public record SelectedPathCommand(String path, AppSourcePathType pathType) {
    }

    /** 失败副本重试只需 operationId 和当前 generation，其他冻结字段从数据库读取。 */
    public record RetryCommand(String operationId, long expectedGeneration) {
        public RetryCommand {
            operationId = AppSourceOperationId.normalize(operationId);
            if (expectedGeneration < 1L) {
                throw new IllegalArgumentException("retry command fields are invalid");
            }
        }
    }

    /** 打开结果只暴露逻辑身份，不把物理根路径返回给 API。 */
    public record OpenResult(
            String appId,
            String repositoryId,
            long generation,
            AppSourcePurpose purpose,
            String workspaceId,
            String linuxServerId,
            Instant expiresAt) {
    }

    /** 远端树与固定提交的低敏快照；空目录仍保留 targetCommit 供后续物化并发校验。 */
    public record TreeSnapshot(
            String targetCommit,
            List<GitRemoteService.RemoteTreeNode> nodes) {
        public TreeSnapshot {
            Objects.requireNonNull(targetCommit, "targetCommit must not be null");
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    /** 供 Task 3 映射列表 DTO 的业务摘要，不包含物理路径或凭据。 */
    public record RepositorySummary(
            String repositoryId,
            String name,
            String englishName,
            DownloadState downloadState,
            Long generation,
            AppSourcePurpose purpose,
            UserId ownerUserId,
            String ownerName,
            String ownerUnifiedAuthId,
            String branch,
            String targetCommit,
            List<AppSourceSelectedPath> selectedPaths,
            Instant expiresAt,
            boolean occupied,
            boolean openable,
            boolean manageable,
            String unavailableReason,
            OperationSnapshot latestOperation,
            List<ServerSummary> serverSummaries) {
    }

    /** 列表稳定四态；异步执行状态通过 latestOperation 独立表达。 */
    public enum DownloadState {
        NOT_DOWNLOADED,
        DOWNLOADED_ACTIVE,
        DOWNLOADED_EXPIRED,
        PERSONAL_OCCUPIED
    }

    /** 进度 HTTP/WS 共用的数据库权威快照，不包含物理路径、凭据或原始 Git 错误。 */
    public record OperationSnapshot(
            String operationId,
            String appId,
            String repositoryId,
            Long sourceGeneration,
            long targetGeneration,
            AppSourceOperationType operationType,
            com.enterprise.testagent.domain.appsource.AppSourceOperationStatus status,
            AppSourcePurpose purpose,
            String branch,
            String targetCommit,
            List<AppSourceSelectedPath> selectedPaths,
            Instant expiresAt,
            String traceId,
            Instant acceptedAt,
            Instant completedAt,
            List<StepSummary> globalSteps,
            List<ServerSummary> serverSummaries) {
    }

    /** 单服务器低敏执行摘要；safeError 字段由 worker 在持久化前统一收敛。 */
    public record ServerSummary(
            String linuxServerId,
            AppSourceReplicaStatus replicaStatus,
            int attemptCount,
            String safeErrorCode,
            String safeErrorMessage,
            String targetCommit,
            List<StepSummary> steps) {
    }

    /** 全局/服务器步骤时间线，elapsedMillis 只由持久化时间戳派生。 */
    public record StepSummary(
            String stepCode,
            int sequence,
            com.enterprise.testagent.domain.appsource.AppSourceStepStatus status,
            String safeSummary,
            Instant startedAt,
            Instant completedAt,
            Long elapsedMillis,
            Instant updatedAt) {
    }

    private record GitAccess(String url, String privateKey) {
    }
}
