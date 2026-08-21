package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceReplica;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceOperationType;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.RepositoryRemoteTreeReader;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 应用级自动化引用编排。每个应用、每个版本库只有一个当前配置，副本按不可变 generation
 * 落到服务器共享目录；任何用户都不会因此获得个人目录或 Git worktree。
 */
@Service
public class ApplicationAutomationReferenceService implements ServerBroadcastHandler {

    public static final String SYNC_REQUESTED_EVENT = "automation-reference.sync-requested";
    public static final String CANCEL_REQUESTED_EVENT = "automation-reference.cancel-requested";
    private static final String REFERENCES_DIR_PARAMETER = "OPENCODE_REFERENCES_DIR";
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final int RECOVERY_LIMIT = 500;
    private static final String TERMINATED_MESSAGE = "自动化引用操作已由管理员终止";
    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$");
    private static final Pattern ENGLISH_NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,126}[A-Za-z0-9])?$");
    private static final Pattern OPERATION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAutomationReferenceService.class);

    private final ConfigurationManagementRepository configurationRepository;
    private final ApplicationAutomationReferenceRepository automationRepository;
    private final RepositoryRemoteTreeReader remoteTreeReader;
    private final UserRepository userRepository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final CommonParameterValues commonParameterValues;
    private final GitWorkspaceService gitWorkspaceService;
    private final SshKeyEncryptionService sshKeyEncryptionService;
    private final WorkspaceServerIdentity serverIdentity;
    private final ServerBroadcastPublisher broadcastPublisher;
    private final ReferenceRepositoryReplicaTaskDispatcher taskDispatcher;
    private final Clock clock;
    private final ReferenceRepositoryDirectoryMover directoryMover;

    @Autowired
    public ApplicationAutomationReferenceService(
            ConfigurationManagementRepository configurationRepository,
            ApplicationAutomationReferenceRepository automationRepository,
            RepositoryRemoteTreeReader remoteTreeReader,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            CommonParameterValues commonParameterValues,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            ReferenceRepositoryReplicaTaskDispatcher taskDispatcher) {
        this(
                configurationRepository,
                automationRepository,
                remoteTreeReader,
                userRepository,
                heartbeatStore,
                commonParameterValues,
                new GitWorkspaceService(),
                sshKeyEncryptionService,
                serverIdentity,
                broadcastPublisher,
                taskDispatcher,
                Clock.systemUTC(),
                ReferenceRepositoryDirectoryMover.filesystem());
    }

    ApplicationAutomationReferenceService(
            ConfigurationManagementRepository configurationRepository,
            ApplicationAutomationReferenceRepository automationRepository,
            RepositoryRemoteTreeReader remoteTreeReader,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            CommonParameterValues commonParameterValues,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            ReferenceRepositoryReplicaTaskDispatcher taskDispatcher,
            Clock clock,
            ReferenceRepositoryDirectoryMover directoryMover) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.automationRepository = Objects.requireNonNull(automationRepository);
        this.remoteTreeReader = Objects.requireNonNull(remoteTreeReader);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore);
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues);
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService);
        this.sshKeyEncryptionService = Objects.requireNonNull(sshKeyEncryptionService);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher);
        this.taskDispatcher = Objects.requireNonNull(taskDispatcher);
        this.clock = Objects.requireNonNull(clock);
        this.directoryMover = Objects.requireNonNull(directoryMover);
    }

    public List<AutomationReferenceRepositoryResponses.Status> list(
            String appId, UserId userId, boolean privileged) {
        ApplicationId applicationId = requireApplicationAccess(appId, userId, privileged);
        return configurationRepository.findRepositoriesByApplication(applicationId).stream()
                .filter(this::isAutomationRepository)
                .map(repository -> status(applicationId, repository))
                .toList();
    }

    public AutomationReferenceRepositoryResponses.Status status(
            String appId, String repositoryId, UserId userId, boolean privileged) {
        ApplicationId applicationId = requireApplicationAccess(appId, userId, privileged);
        CodeRepository repository = requireLinkedAutomationRepository(applicationId, repositoryId(repositoryId));
        return status(applicationId, repository);
    }

    public List<String> branches(
            String appId, String repositoryId, UserId userId, boolean privileged) {
        ApplicationId applicationId = requireApplicationAccess(appId, userId, privileged);
        CodeRepository repository = requireLinkedAutomationRepository(applicationId, repositoryId(repositoryId));
        return remoteTreeReader.listBranches(repository, userId);
    }

    public List<RepositoryRemoteTreeReader.TreeNode> tree(
            String appId,
            String repositoryId,
            String branch,
            String path,
            UserId userId,
            boolean privileged) {
        ApplicationId applicationId = requireApplicationAccess(appId, userId, privileged);
        CodeRepository repository = requireLinkedAutomationRepository(applicationId, repositoryId(repositoryId));
        String normalizedBranch = normalizeBranch(branch);
        String normalizedPath = normalizeRelativePath(path, true);
        ApplicationAutomationReferenceState state = automationRepository
                .findState(applicationId, repository.repositoryId())
                .orElse(null);
        if (state != null && state.activeGeneration() != null) {
            ApplicationAutomationReferenceGeneration active = automationRepository
                    .findGeneration(applicationId, repository.repositoryId(), state.activeGeneration())
                    .orElse(null);
            if (active != null
                    && active.status() == AutomationReferenceGenerationStatus.READY
                    && active.branch().equals(normalizedBranch)) {
                // 当前分支直接浏览本机共享只读副本；点选仓库不会触发 Git fetch 或创建新副本。
                Path root = requireReadyLocalRepositoryRoot(
                        applicationId, repository.repositoryId(), active.generation());
                return listLocalTree(root, normalizedPath);
            }
        }
        List<RepositoryRemoteTreeReader.TreeNode> tree = remoteTreeReader.listTree(repository, normalizedBranch, userId);
        if (normalizedPath.isEmpty()) {
            return tree;
        }
        return findTreeNode(tree, normalizedPath)
                .filter(node -> "directory".equals(node.type()))
                .map(RepositoryRemoteTreeReader.TreeNode::children)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "自动化引用目录不存在"));
    }

    private List<RepositoryRemoteTreeReader.TreeNode> listLocalTree(Path root, String relativePath) {
        Path directory = resolveSafeDirectory(root, relativePath);
        try (java.util.stream.Stream<Path> children = Files.list(directory)) {
            return children
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !".git".equals(path.getFileName().toString()))
                    .map(path -> new RepositoryRemoteTreeReader.TreeNode(
                            path.getFileName().toString(),
                            relativePath.isEmpty()
                                    ? path.getFileName().toString()
                                    : relativePath + "/" + path.getFileName(),
                            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? "directory" : "file",
                            List.of()))
                    .sorted(Comparator
                            .comparingInt((RepositoryRemoteTreeReader.TreeNode node) ->
                                    "directory".equals(node.type()) ? 0 : 1)
                            .thenComparing(RepositoryRemoteTreeReader.TreeNode::name))
                    .toList();
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取自动化引用目录失败", Map.of(), exception);
        }
    }

    public AutomationReferenceRepositoryResponses.Status configure(
            String appId,
            String repositoryId,
            String branch,
            String directoryPath,
            String description,
            boolean merge,
            long expectedGeneration,
            String operationId,
            UserId userId,
            boolean superAdmin,
            String traceId) {
        if (merge) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "自动化引用不支持合并");
        }
        ApplicationId applicationId = requireApplicationAccess(appId, userId, superAdmin);
        CodeRepository repository = requireLinkedAutomationRepository(applicationId, repositoryId(repositoryId));
        String normalizedBranch = normalizeBranch(branch);
        String normalizedDirectory = normalizeRelativePath(directoryPath, true);
        String normalizedOperationId = normalizeOperationId(operationId);
        String normalizedTraceId = requireTraceId(traceId);
        Optional<ApplicationAutomationReferenceGeneration> replay = automationRepository.findByOperationId(
                applicationId, repository.repositoryId(), normalizedOperationId);
        if (replay.isPresent()) {
            return status(applicationId, repository);
        }

        validateRemoteDirectory(repository, normalizedBranch, normalizedDirectory, userId);
        ApplicationAutomationReferenceState state = automationRepository.ensureState(
                applicationId, repository.repositoryId(), normalizedTraceId, clock.instant());
        requireExpectedGeneration(state, expectedGeneration);
        if (state.pendingGeneration() != null || state.status().active()) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用存在执行中的操作");
        }
        String targetCommit = resolveRemoteHead(repository, normalizedBranch, userId);
        Instant now = clock.instant();
        String normalizedDescription = normalizeDescription(
                description, repository, normalizedBranch, normalizedDirectory);
        ApplicationAutomationReferenceGeneration generation = new ApplicationAutomationReferenceGeneration(
                applicationId,
                repository.repositoryId(),
                state.nextGeneration(),
                normalizedBranch,
                normalizedDirectory,
                normalizedDescription,
                false,
                targetCommit,
                AutomationReferenceGenerationStatus.SYNCHRONIZING,
                AutomationReferenceOperationType.CONFIGURE,
                userId,
                normalizedOperationId,
                normalizedTraceId,
                null,
                null,
                now,
                now);
        Optional<ApplicationAutomationReferenceGeneration> reserved = automationRepository.reserveGeneration(
                generation, expectedGeneration, state.lockVersion(), now);
        if (reserved.isEmpty()) {
            Optional<ApplicationAutomationReferenceGeneration> concurrentReplay = automationRepository.findByOperationId(
                    applicationId, repository.repositoryId(), normalizedOperationId);
            if (concurrentReplay.isEmpty()) {
                throw new PlatformException(ErrorCode.CONFLICT, "自动化引用配置已被其他管理员修改，请刷新后重试");
            }
        }
        createTargetsAndDispatch(generation);
        return status(applicationId, repository);
    }

    public AutomationReferenceRepositoryResponses.Status synchronize(
            String appId,
            String repositoryId,
            long expectedGeneration,
            String operationId,
            UserId userId,
            boolean superAdmin,
            String traceId) {
        ApplicationId applicationId = requireApplicationAccess(appId, userId, superAdmin);
        CodeRepository repository = requireLinkedAutomationRepository(applicationId, repositoryId(repositoryId));
        String normalizedOperationId = normalizeOperationId(operationId);
        Optional<ApplicationAutomationReferenceGeneration> replay = automationRepository.findByOperationId(
                applicationId, repository.repositoryId(), normalizedOperationId);
        if (replay.isPresent()) {
            return status(applicationId, repository);
        }
        ApplicationAutomationReferenceState state = requireState(applicationId, repository.repositoryId());
        requireExpectedGeneration(state, expectedGeneration);
        requireIdle(state);
        ApplicationAutomationReferenceGeneration current = requireGeneration(
                applicationId, repository.repositoryId(), expectedGeneration);
        String targetCommit = resolveRemoteHead(repository, current.branch(), userId);
        Instant now = clock.instant();
        ApplicationAutomationReferenceGeneration next = new ApplicationAutomationReferenceGeneration(
                applicationId,
                repository.repositoryId(),
                state.nextGeneration(),
                current.branch(),
                current.directoryPath(),
                current.description(),
                false,
                targetCommit,
                AutomationReferenceGenerationStatus.SYNCHRONIZING,
                AutomationReferenceOperationType.SYNCHRONIZE,
                userId,
                normalizedOperationId,
                requireTraceId(traceId),
                null,
                null,
                now,
                now);
        if (automationRepository.reserveGeneration(
                next, expectedGeneration, state.lockVersion(), now).isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用已被其他管理员修改，请刷新后重试");
        }
        createTargetsAndDispatch(next);
        return status(applicationId, repository);
    }

    public AutomationReferenceRepositoryResponses.Status verify(
            String appId,
            String repositoryId,
            long expectedGeneration,
            UserId userId,
            boolean superAdmin,
            String traceId) {
        ApplicationId applicationId = requireApplicationAccess(appId, userId, superAdmin);
        CodeRepository repository = requireLinkedAutomationRepository(applicationId, repositoryId(repositoryId));
        ApplicationAutomationReferenceState state = requireState(applicationId, repository.repositoryId());
        requireExpectedGeneration(state, expectedGeneration);
        requireIdle(state);
        Set<LinuxServerId> targets = new LinkedHashSet<>(liveServerIds());
        targets.addAll(automationRepository.findKnownServerIds(applicationId, repository.repositoryId()));
        targets.add(localServerId());
        Set<LinuxServerId> live = liveServerIds();
        Set<LinuxServerId> onlineTargets = new LinkedHashSet<>(targets);
        onlineTargets.retainAll(live);
        if (live.contains(localServerId())) {
            onlineTargets.add(localServerId());
        }
        Instant now = clock.instant();
        if (!automationRepository.beginVerification(
                applicationId,
                repository.repositoryId(),
                expectedGeneration,
                state.lockVersion(),
                requireTraceId(traceId),
                onlineTargets,
                now)) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用已被其他管理员修改，请刷新后重试");
        }
        dispatch(applicationId, repository.repositoryId(), expectedGeneration, traceId);
        return status(applicationId, repository);
    }

    public AutomationReferenceRepositoryResponses.Status terminate(
            String appId,
            String repositoryId,
            long expectedGeneration,
            UserId userId,
            boolean superAdmin,
            String traceId) {
        ApplicationId applicationId = requireApplicationAccess(appId, userId, superAdmin);
        CodeRepository repository = requireLinkedAutomationRepository(applicationId, repositoryId(repositoryId));
        ApplicationAutomationReferenceState state = requireState(applicationId, repository.repositoryId());
        if (!state.status().active()) {
            return status(applicationId, repository);
        }
        long operationGeneration = state.pendingGeneration() == null
                ? Objects.requireNonNull(state.activeGeneration(), "activeGeneration")
                : state.pendingGeneration();
        if (operationGeneration != expectedGeneration) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用操作代次已变化，请刷新后重试");
        }
        String normalizedTraceId = requireTraceId(traceId);
        if (!automationRepository.terminateOperation(
                applicationId,
                repository.repositoryId(),
                expectedGeneration,
                state.lockVersion(),
                TERMINATED_MESSAGE,
                clock.instant())) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用操作已结束或已被替换");
        }
        publishCancel(applicationId, repository.repositoryId(), expectedGeneration, normalizedTraceId);
        return status(applicationId, repository);
    }

    @Override
    public boolean supports(String type) {
        return SYNC_REQUESTED_EVENT.equals(type) || CANCEL_REQUESTED_EVENT.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!supports(event.type())) {
            return;
        }
        Object appId = event.payload().get("appId");
        Object repositoryId = event.payload().get("repositoryId");
        Object generation = event.payload().get("generation");
        if (!(appId instanceof String applicationValue)
                || !(repositoryId instanceof String repositoryValue)
                || !(generation instanceof Number number)) {
            return;
        }
        ApplicationId applicationId = new ApplicationId(applicationValue);
        CodeRepositoryId parsedRepositoryId = new CodeRepositoryId(repositoryValue);
        if (CANCEL_REQUESTED_EVENT.equals(event.type())) {
            taskDispatcher.cancelScoped(scopeId(applicationId, parsedRepositoryId), number.longValue());
            return;
        }
        dispatch(
                applicationId,
                parsedRepositoryId,
                number.longValue(),
                event.traceId(),
                ReferenceRepositoryReplicaTaskDispatcher.WakeSource.SERVER_BROADCAST);
    }

    /** 广播丢失、Java 重启或服务器恢复在线后，由本地补偿器重新建立并认领当前副本。 */
    public void reconcileLocalReplicas(String traceId) {
        Instant now = clock.instant();
        Set<LinuxServerId> live = liveServerIds();
        LinuxServerId localServer = localServerId();
        for (ApplicationAutomationReferenceGeneration generation
                : automationRepository.findRecoverableGenerations(RECOVERY_LIMIT)) {
            Set<LinuxServerId> targets = new LinkedHashSet<>(
                    automationRepository.findKnownServerIds(generation.appId(), generation.repositoryId()));
            targets.addAll(live);
            targets.add(localServer);
            automationRepository.upsertTargets(
                    generation.appId(), generation.repositoryId(), generation.generation(), targets, now);
            automationRepository.deferOfflineReplicas(
                    generation.appId(), generation.repositoryId(), generation.generation(), live, now);
            refreshOverallStatus(generation.appId(), generation.repositoryId(), generation.generation(), live);
        }
        if (!live.contains(localServer)) {
            return;
        }
        for (ApplicationAutomationReferenceReplica replica
                : automationRepository.findClaimableReplicas(localServer, now, 100)) {
            dispatch(
                    replica.appId(),
                    replica.repositoryId(),
                    replica.generation(),
                    traceId,
                    ReferenceRepositoryReplicaTaskDispatcher.WakeSource.RECONCILIATION);
        }
    }

    /** 组合文件树和 JSONC 对账只可解析当前或受运行租约保护的 READY 代次。 */
    Path requireReadyLocalRepositoryRoot(ApplicationId appId, CodeRepositoryId repositoryId, long generation) {
        CodeRepository repository = requireLinkedAutomationRepository(appId, repositoryId);
        ApplicationAutomationReferenceGeneration configuration = requireGeneration(appId, repositoryId, generation);
        if (configuration.status() != AutomationReferenceGenerationStatus.READY) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用配置代次尚未就绪");
        }
        boolean ready = automationRepository.findReplicas(appId, repositoryId, generation).stream()
                .anyMatch(replica -> replica.linuxServerId().equals(localServerId())
                        && replica.status() == ReferenceRepositoryReplicaStatus.READY
                        && configuration.branch().equals(replica.currentBranch())
                        && configuration.targetCommitHash().equals(replica.currentCommitHash()));
        if (!ready) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前服务器自动化引用副本尚未就绪");
        }
        return repositoryRoot(appId, repository, generation);
    }

    Path requireReadyLocalDirectory(ApplicationId appId, CodeRepositoryId repositoryId, long generation) {
        Path root = requireReadyLocalRepositoryRoot(appId, repositoryId, generation);
        return resolveSafeDirectory(root, requireGeneration(appId, repositoryId, generation).directoryPath());
    }

    boolean isActiveGeneration(ApplicationId appId, CodeRepositoryId repositoryId, long generation) {
        return automationRepository.findState(appId, repositoryId)
                .map(state -> Objects.equals(state.activeGeneration(), generation))
                .orElse(false);
    }

    String logicalConfigurationPath(
            ApplicationId appId, CodeRepository repository, ApplicationAutomationReferenceGeneration generation) {
        return logicalPath(appId, repository, generation);
    }

    ApplicationAutomationReferenceGeneration requireActiveConfiguration(
            ApplicationId appId, CodeRepositoryId repositoryId) {
        ApplicationAutomationReferenceState state = requireState(appId, repositoryId);
        if (state.activeGeneration() == null) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用尚未配置");
        }
        return requireGeneration(appId, repositoryId, state.activeGeneration());
    }

    private void createTargetsAndDispatch(ApplicationAutomationReferenceGeneration generation) {
        Set<LinuxServerId> live = liveServerIds();
        Set<LinuxServerId> targets = new LinkedHashSet<>(live);
        targets.addAll(automationRepository.findKnownServerIds(generation.appId(), generation.repositoryId()));
        targets.add(localServerId());
        Instant now = clock.instant();
        automationRepository.upsertTargets(
                generation.appId(), generation.repositoryId(), generation.generation(), targets, now);
        automationRepository.deferOfflineReplicas(
                generation.appId(), generation.repositoryId(), generation.generation(), live, now);
        publishSync(generation);
    }

    private void dispatch(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation, String traceId) {
        dispatch(appId, repositoryId, generation, traceId,
                ReferenceRepositoryReplicaTaskDispatcher.WakeSource.LOCAL_REQUEST);
    }

    private void dispatch(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            String traceId,
            ReferenceRepositoryReplicaTaskDispatcher.WakeSource source) {
        taskDispatcher.dispatchScopedNow(
                scopeId(appId, repositoryId),
                generation,
                traceId,
                source,
                () -> claimAndSynchronize(appId, repositoryId, generation, traceId));
    }

    private void claimAndSynchronize(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation, String traceId) {
        Instant now = clock.instant();
        LinuxServerId localServer = localServerId();
        String leaseToken = "aarl_" + UUID.randomUUID().toString().replace("-", "");
        automationRepository.claimReplica(
                        appId,
                        repositoryId,
                        generation,
                        localServer,
                        leaseToken,
                        now.plus(LEASE_DURATION),
                        now)
                .ifPresent(replica -> synchronizeClaimedReplica(replica, traceId));
    }

    private void synchronizeClaimedReplica(ApplicationAutomationReferenceReplica claimed, String traceId) {
        ApplicationAutomationReferenceState state = automationRepository
                .findState(claimed.appId(), claimed.repositoryId())
                .orElse(null);
        ApplicationAutomationReferenceGeneration generation = automationRepository
                .findGeneration(claimed.appId(), claimed.repositoryId(), claimed.generation())
                .orElse(null);
        if (state == null || generation == null) {
            return;
        }
        CodeRepository repository;
        try {
            repository = requireLinkedAutomationRepository(claimed.appId(), claimed.repositoryId());
        } catch (PlatformException exception) {
            markBlocked(claimed, "自动化代码库未关联当前应用");
            return;
        }
        if (state.operationType() == AutomationReferenceOperationType.VERIFY_POINTERS
                && Objects.equals(state.activeGeneration(), claimed.generation())) {
            verifyClaimedReplica(claimed, repository, generation);
            return;
        }
        try (ReplicaFileLock ignored = acquireReplicaFileLock(claimed.appId(), repository, claimed.generation())) {
            renewLeaseOrThrow(claimed);
            Path repoRoot = repositoryRoot(claimed.appId(), repository, claimed.generation());
            synchronizeImmutableDirectory(claimed, repository, generation, repoRoot);
            renewLeaseOrThrow(claimed);
            ObservedPointer observed = observePointer(repoRoot);
            validatePointerAndDirectory(repository, generation, repoRoot, observed);
            Instant completedAt = clock.instant();
            boolean updated = automationRepository.markReady(
                    claimed.appId(),
                    claimed.repositoryId(),
                    claimed.generation(),
                    claimed.linuxServerId(),
                    claimed.leaseToken(),
                    observed.branch(),
                    observed.commitHash(),
                    completedAt,
                    completedAt);
            if (updated) {
                refreshOverallStatus(claimed.appId(), claimed.repositoryId(), claimed.generation());
            }
        } catch (LeaseLostException exception) {
            LOGGER.info(
                    "Automation reference worker lease lost appId={} repositoryId={} generation={} linuxServerId={}",
                    claimed.appId().value(), claimed.repositoryId().value(), claimed.generation(),
                    claimed.linuxServerId().value());
        } catch (ReplicaRetryException exception) {
            markRetry(claimed, exception.getMessage(), traceId);
        } catch (ReplicaBlockedException exception) {
            markBlocked(claimed, exception.getMessage());
        } catch (PlatformException exception) {
            if (isTransientGitFailure(exception)) {
                markRetry(claimed, safeError(exception.getMessage()), traceId);
            } else {
                markBlocked(claimed, safeError(exception.getMessage()));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Automation reference synchronization failed appId={} repositoryId={} generation={} linuxServerId={} traceId={}",
                    claimed.appId().value(), claimed.repositoryId().value(), claimed.generation(),
                    claimed.linuxServerId().value(), traceId);
            markBlocked(claimed, "自动化引用同步失败");
        }
    }

    private void synchronizeImmutableDirectory(
            ApplicationAutomationReferenceReplica claimed,
            CodeRepository repository,
            ApplicationAutomationReferenceGeneration generation,
            Path repoRoot) {
        if (Files.exists(repoRoot, LinkOption.NOFOLLOW_LINKS)) {
            ObservedPointer observed = observePointer(repoRoot);
            validatePointerAndDirectory(repository, generation, repoRoot, observed);
            return;
        }
        Path parent = repoRoot.getParent();
        Path temporary = parent.resolve("." + validatedEnglishName(repository) + "."
                + generation.generation() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(parent);
            renewLeaseOrThrow(claimed);
            String privateKey = privateKeyFor(repository, generation.operatedByUserId());
            gitWorkspaceService.cloneBranch(
                    effectiveGitUrl(repository, generation.operatedByUserId()),
                    generation.branch(),
                    temporary,
                    privateKey);
            renewLeaseOrThrow(claimed);
            String resolved = gitWorkspaceService.resolveCommit(temporary, generation.targetCommitHash());
            if (!generation.targetCommitHash().equals(resolved)) {
                throw new ReplicaBlockedException("自动化引用临时副本无法解析固定目标提交");
            }
            gitWorkspaceService.resetHardToCommit(temporary, generation.targetCommitHash());
            validatePointerAndDirectory(repository, generation, temporary, observePointer(temporary));
            renewLeaseOrThrow(claimed);
            moveAtomically(temporary, repoRoot);
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "写入自动化引用临时目录失败", Map.of(), exception);
        } finally {
            deleteTemporaryTree(temporary);
        }
    }

    /** 指针核验只读实际 branch、HEAD、origin、工作树和目录，不 fetch、不切换。 */
    private void verifyClaimedReplica(
            ApplicationAutomationReferenceReplica claimed,
            CodeRepository repository,
            ApplicationAutomationReferenceGeneration generation) {
        Path repoRoot = repositoryRoot(claimed.appId(), repository, claimed.generation());
        ObservedPointer observed = null;
        String error = null;
        try {
            observed = observePointer(repoRoot);
            validatePointerAndDirectory(repository, generation, repoRoot, observed);
        } catch (RuntimeException exception) {
            error = safeError(exception.getMessage() == null ? "自动化引用本地指针核验失败" : exception.getMessage());
        }
        Instant now = clock.instant();
        boolean updated = automationRepository.markVerificationResult(
                claimed.appId(),
                claimed.repositoryId(),
                claimed.generation(),
                claimed.linuxServerId(),
                claimed.leaseToken(),
                error == null ? ReferenceRepositoryReplicaStatus.READY : ReferenceRepositoryReplicaStatus.BLOCKED,
                observed == null ? null : observed.branch(),
                observed == null ? null : observed.commitHash(),
                now,
                error,
                now);
        if (updated) {
            refreshOverallStatus(claimed.appId(), claimed.repositoryId(), claimed.generation());
        }
    }

    private void validatePointerAndDirectory(
            CodeRepository repository,
            ApplicationAutomationReferenceGeneration generation,
            Path repoRoot,
            ObservedPointer observed) {
        if (Files.isSymbolicLink(repoRoot)
                || !Files.isDirectory(repoRoot, LinkOption.NOFOLLOW_LINKS)
                || !gitWorkspaceService.isGitRepository(repoRoot)) {
            throw new ReplicaBlockedException("自动化引用目标目录不是可信 Git 仓库");
        }
        if (!repository.matchesStoredOrigin(gitWorkspaceService.originUrl(repoRoot))) {
            throw new ReplicaBlockedException("自动化引用本地仓库 origin 与数据库不一致");
        }
        if (!gitWorkspaceService.isWorktreeCleanReadOnly(repoRoot)) {
            throw new ReplicaBlockedException("自动化引用共享副本存在未提交修改");
        }
        if (!generation.branch().equals(observed.branch())
                || !generation.targetCommitHash().equals(observed.commitHash())) {
            throw new ReplicaBlockedException("自动化引用实际 Git 指针与固定目标不一致");
        }
        resolveSafeDirectory(repoRoot, generation.directoryPath());
    }

    private void refreshOverallStatus(ApplicationId appId, CodeRepositoryId repositoryId, long generation) {
        refreshOverallStatus(appId, repositoryId, generation, liveServerIds());
    }

    private void refreshOverallStatus(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            Set<LinuxServerId> liveServers) {
        ApplicationAutomationReferenceState state = automationRepository.findState(appId, repositoryId).orElse(null);
        if (state == null || (!Objects.equals(state.pendingGeneration(), generation)
                && !Objects.equals(state.activeGeneration(), generation))) {
            return;
        }
        List<ApplicationAutomationReferenceReplica> online = automationRepository
                .findReplicas(appId, repositoryId, generation).stream()
                .filter(replica -> liveServers.contains(replica.linuxServerId()))
                .toList();
        if (online.stream().anyMatch(replica -> replica.status() == ReferenceRepositoryReplicaStatus.BLOCKED)) {
            String error = online.stream()
                    .filter(replica -> replica.status() == ReferenceRepositoryReplicaStatus.BLOCKED)
                    .map(ApplicationAutomationReferenceReplica::lastError)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("自动化引用副本同步失败");
            automationRepository.completeGeneration(
                    appId, repositoryId, generation, ReferenceRepositoryStatus.FAILED, error, clock.instant());
            return;
        }
        if (!online.isEmpty()
                && online.stream().allMatch(replica -> replica.status() == ReferenceRepositoryReplicaStatus.READY)) {
            automationRepository.completeGeneration(
                    appId, repositoryId, generation, ReferenceRepositoryStatus.READY, null, clock.instant());
        }
    }

    private AutomationReferenceRepositoryResponses.Status status(
            ApplicationId appId, CodeRepository repository) {
        ApplicationAutomationReferenceState state = automationRepository
                .findState(appId, repository.repositoryId())
                .orElse(null);
        ApplicationAutomationReferenceGeneration current = state == null || state.activeGeneration() == null
                ? null
                : automationRepository.findGeneration(
                        appId, repository.repositoryId(), state.activeGeneration()).orElse(null);
        ApplicationAutomationReferenceGeneration pending = state == null || state.pendingGeneration() == null
                ? null
                : automationRepository.findGeneration(
                        appId, repository.repositoryId(), state.pendingGeneration()).orElse(null);
        long operationGeneration = pending != null
                ? pending.generation()
                : current == null ? 0L : current.generation();
        List<ApplicationAutomationReferenceReplica> replicas = operationGeneration == 0L
                ? List.of()
                : automationRepository.findReplicas(appId, repository.repositoryId(), operationGeneration);
        Set<LinuxServerId> live = liveServerIds();
        ApplicationAutomationReferenceGeneration target = pending == null ? current : pending;
        List<AutomationReferenceRepositoryResponses.ServerStatus> servers = replicas.stream()
                .map(replica -> new AutomationReferenceRepositoryResponses.ServerStatus(
                        replica.linuxServerId().value(),
                        replica.status().name(),
                        live.contains(replica.linuxServerId()),
                        replica.currentBranch(),
                        replica.currentCommitHash(),
                        matchesTarget(replica, target),
                        replica.verifiedAt(),
                        replica.syncedAt(),
                        replica.lastError()))
                .toList();
        int readyCount = (int) servers.stream().filter(server -> "READY".equals(server.status())).count();
        return new AutomationReferenceRepositoryResponses.Status(
                appId.value(),
                repository.repositoryId().value(),
                repository.name(),
                repository.englishName(),
                repository.gitUrl(),
                state == null ? ReferenceRepositoryStatus.UNINITIALIZED.name() : state.status().name(),
                state == null ? AutomationReferenceOperationType.CONFIGURE.name() : state.operationType().name(),
                state == null ? 0L : state.lockVersion(),
                state == null ? null : state.activeGeneration(),
                state == null ? null : state.pendingGeneration(),
                configurationResponse(appId, repository, current),
                configurationResponse(appId, repository, pending),
                servers.size(),
                readyCount,
                servers,
                state == null ? null : state.traceId(),
                state == null ? null : state.lastError());
    }

    private AutomationReferenceRepositoryResponses.Configuration configurationResponse(
            ApplicationId appId,
            CodeRepository repository,
            ApplicationAutomationReferenceGeneration generation) {
        if (generation == null) {
            return null;
        }
        return new AutomationReferenceRepositoryResponses.Configuration(
                generation.generation(),
                generation.branch(),
                generation.directoryPath(),
                generation.description(),
                false,
                generation.targetCommitHash(),
                alias(repository),
                logicalPath(appId, repository, generation),
                directoryName(repository, generation.directoryPath()),
                generation.activatedAt(),
                generation.status().name());
    }

    private Boolean matchesTarget(
            ApplicationAutomationReferenceReplica replica,
            ApplicationAutomationReferenceGeneration generation) {
        if (generation == null || replica.currentBranch() == null || replica.currentCommitHash() == null) {
            return null;
        }
        return generation.branch().equals(replica.currentBranch())
                && generation.targetCommitHash().equals(replica.currentCommitHash());
    }

    private void requireExpectedGeneration(ApplicationAutomationReferenceState state, long expectedGeneration) {
        long active = state.activeGeneration() == null ? 0L : state.activeGeneration();
        if (active != expectedGeneration) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "自动化引用配置已变化，请刷新后重试",
                    Map.of("expectedGeneration", expectedGeneration, "actualGeneration", active));
        }
    }

    private void requireIdle(ApplicationAutomationReferenceState state) {
        if (state.pendingGeneration() != null || state.status().active()) {
            throw new PlatformException(ErrorCode.CONFLICT, "自动化引用存在执行中的操作");
        }
    }

    private ApplicationAutomationReferenceState requireState(ApplicationId appId, CodeRepositoryId repositoryId) {
        return automationRepository.findState(appId, repositoryId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "自动化引用尚未配置"));
    }

    ApplicationAutomationReferenceGeneration requireGeneration(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation) {
        return automationRepository.findGeneration(appId, repositoryId, generation)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "自动化引用配置代次不存在"));
    }

    private ApplicationId requireApplicationAccess(String appId, UserId userId, boolean privileged) {
        ApplicationId applicationId = applicationId(appId);
        configurationRepository.findApplication(applicationId)
                .filter(application -> application.enabled())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用不存在或未启用"));
        if (!privileged && !configurationRepository.isActiveMember(applicationId, userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前用户不是应用成员");
        }
        return applicationId;
    }

    private CodeRepository requireLinkedAutomationRepository(
            ApplicationId appId, CodeRepositoryId repositoryId) {
        CodeRepository repository = configurationRepository.findRepositoriesByApplication(appId).stream()
                .filter(candidate -> candidate.repositoryId().equals(repositoryId))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "自动化代码库未关联当前应用"));
        if (!isAutomationRepository(repository)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "代码库类型不是自动化代码库");
        }
        validatedEnglishName(repository);
        return repository;
    }

    private boolean isAutomationRepository(CodeRepository repository) {
        return CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value().equals(repository.repositoryType());
    }

    private void validateRemoteDirectory(
            CodeRepository repository, String branch, String directoryPath, UserId userId) {
        if (directoryPath.isEmpty()) {
            return;
        }
        List<RepositoryRemoteTreeReader.TreeNode> tree = remoteTreeReader.listTree(repository, branch, userId);
        RepositoryRemoteTreeReader.TreeNode node = findTreeNode(tree, directoryPath)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "所选自动化引用目录不存在"));
        if (!"directory".equals(node.type())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "自动化引用只能选择目录");
        }
    }

    private Optional<RepositoryRemoteTreeReader.TreeNode> findTreeNode(
            List<RepositoryRemoteTreeReader.TreeNode> nodes, String path) {
        for (RepositoryRemoteTreeReader.TreeNode node : nodes) {
            if (path.equals(node.path())) {
                return Optional.of(node);
            }
            Optional<RepositoryRemoteTreeReader.TreeNode> child = findTreeNode(node.children(), path);
            if (child.isPresent()) {
                return child;
            }
        }
        return Optional.empty();
    }

    private String normalizeDescription(
            String description, CodeRepository repository, String branch, String directoryPath) {
        String value = description == null ? "" : description.trim();
        if (value.isEmpty()) {
            value = repository.name() + " / " + branch + " / "
                    + (directoryPath.isEmpty() ? "." : directoryPath) + "，只读自动化引用";
        }
        if (value.length() > 2000) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "自动化引用描述不能超过 2000 个字符");
        }
        return value;
    }

    private String resolveRemoteHead(CodeRepository repository, String branch, UserId userId) {
        return gitWorkspaceService.resolveRemoteBranchCommit(
                effectiveGitUrl(repository, userId), branch, privateKeyFor(repository, userId));
    }

    private String privateKeyFor(CodeRepository repository, UserId userId) {
        if (!repository.internalDeployment() && !requiresSshKey(repository.gitUrl())) {
            return null;
        }
        UserSshKey key = configurationRepository.findSshKeys(userId).stream()
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "当前用户未配置 SSH key"));
        if (key.encryptedAesKey() == null || key.encryptedAesKey().isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key 使用的旧版加密格式，请重新添加");
        }
        return sshKeyEncryptionService.decrypt(
                key.encryptedPrivateKey(), key.encryptedAesKey(), key.encryptionNonce());
    }

    private String effectiveGitUrl(CodeRepository repository, UserId userId) {
        if (!repository.internalDeployment()) {
            return repository.gitUrl();
        }
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "凭据用户不存在"));
        return repository.effectiveGitUrl(user.unifiedAuthId());
    }

    private boolean requiresSshKey(String gitUrl) {
        return gitUrl != null
                && (gitUrl.startsWith("ssh://") || (gitUrl.contains("@") && gitUrl.contains(":")));
    }

    private void renewLeaseOrThrow(ApplicationAutomationReferenceReplica claimed) {
        Instant now = clock.instant();
        if (!automationRepository.renewLease(
                claimed.appId(),
                claimed.repositoryId(),
                claimed.generation(),
                claimed.linuxServerId(),
                claimed.leaseToken(),
                now.plus(LEASE_DURATION),
                now)) {
            throw new LeaseLostException();
        }
    }

    private void markRetry(ApplicationAutomationReferenceReplica claimed, String message, String traceId) {
        int retryCount = claimed.retryCount() + 1;
        Instant now = clock.instant();
        Instant nextRetryAt = now.plus(ApplicationAutomationReferenceReplica.retryDelay(retryCount));
        if (automationRepository.markRetry(
                claimed.appId(),
                claimed.repositoryId(),
                claimed.generation(),
                claimed.linuxServerId(),
                claimed.leaseToken(),
                retryCount,
                nextRetryAt,
                safeError(message),
                now)) {
            taskDispatcher.dispatchScopedAt(
                    scopeId(claimed.appId(), claimed.repositoryId()),
                    claimed.generation(),
                    traceId,
                    ReferenceRepositoryReplicaTaskDispatcher.WakeSource.RETRY,
                    nextRetryAt,
                    () -> claimAndSynchronize(
                            claimed.appId(), claimed.repositoryId(), claimed.generation(), traceId));
        }
    }

    private void markBlocked(ApplicationAutomationReferenceReplica claimed, String message) {
        if (automationRepository.markBlocked(
                claimed.appId(),
                claimed.repositoryId(),
                claimed.generation(),
                claimed.linuxServerId(),
                claimed.leaseToken(),
                safeError(message),
                clock.instant())) {
            refreshOverallStatus(claimed.appId(), claimed.repositoryId(), claimed.generation());
        }
    }

    private boolean isTransientGitFailure(PlatformException exception) {
        if (exception.errorCode() == ErrorCode.GIT_TIMEOUT) {
            return true;
        }
        if (exception.errorCode() != ErrorCode.GIT_UNAVAILABLE) {
            return false;
        }
        Object failureType = exception.details().get("gitFailureType");
        return "NETWORK_UNAVAILABLE".equals(failureType) || "TIMEOUT".equals(failureType);
    }

    private ObservedPointer observePointer(Path repoRoot) {
        String branch = gitWorkspaceService.currentBranch(repoRoot);
        String commit = gitWorkspaceService.headCommit(repoRoot);
        if (branch == null || branch.isBlank() || commit == null || commit.isBlank()) {
            throw new ReplicaBlockedException("自动化引用无法完整读取实际 Git 指针");
        }
        return new ObservedPointer(branch, commit);
    }

    private Path repositoryRoot(ApplicationId appId, CodeRepository repository, long generation) {
        Path root = referencesRoot();
        Path resolved = root.resolve("automation")
                .resolve(applicationPathFragment(appId))
                .resolve(validatedEnglishName(repository))
                .resolve(Long.toString(generation))
                .toAbsolutePath()
                .normalize();
        if (!resolved.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "自动化引用目录越界");
        }
        return resolved;
    }

    private Path referencesRoot() {
        String value = commonParameterValues.resolvedValue(REFERENCES_DIR_PARAMETER)
                .filter(path -> !path.isBlank())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "缺少引用资产根目录参数",
                        Map.of("parameter", REFERENCES_DIR_PARAMETER)));
        return Path.of(value).toAbsolutePath().normalize();
    }

    private Path resolveSafeDirectory(Path root, String relativePath) {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReplicaBlockedException("自动化引用共享副本目录不可用");
        }
        Path current = root;
        if (!relativePath.isEmpty()) {
            for (String segment : relativePath.split("/")) {
                current = current.resolve(segment);
                if (Files.isSymbolicLink(current)) {
                    throw new ReplicaBlockedException("自动化引用目录禁止经过符号链接");
                }
            }
        }
        Path normalized = current.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize())
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReplicaBlockedException("所选自动化引用目录不存在");
        }
        return normalized;
    }

    private ReplicaFileLock acquireReplicaFileLock(
            ApplicationId appId, CodeRepository repository, long generation) {
        Path lockDirectory = referencesRoot().resolve(".automation-reference-locks").normalize();
        String lockName = applicationPathFragment(appId) + "-" + validatedEnglishName(repository)
                + "-" + generation + ".lock";
        Path lockPath = lockDirectory.resolve(lockName).normalize();
        FileChannel channel = null;
        try {
            Files.createDirectories(lockDirectory);
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                lock = null;
            }
            if (lock == null) {
                channel.close();
                throw new ReplicaRetryException("本机自动化引用同步锁被占用");
            }
            return new ReplicaFileLock(channel, lock);
        } catch (ReplicaRetryException exception) {
            throw exception;
        } catch (IOException exception) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // 原始锁异常优先。
                }
            }
            throw new ReplicaRetryException("本机自动化引用同步锁暂时不可用");
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            directoryMover.moveAtomically(source, target);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new ReplicaBlockedException("自动化引用根目录不支持原子移动");
        }
    }

    private void deleteTemporaryTree(Path temporary) {
        if (temporary == null || !Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不覆盖原始同步结果。
                }
            });
        } catch (IOException ignored) {
            // 同上。
        }
    }

    private void publishSync(ApplicationAutomationReferenceGeneration generation) {
        try {
            broadcastPublisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    SYNC_REQUESTED_EVENT,
                    broadcastPublisher.instanceId(),
                    serverIdentity.linuxServerId(),
                    generation.traceId(),
                    clock.instant(),
                    Map.of(
                            "appId", generation.appId().value(),
                            "repositoryId", generation.repositoryId().value(),
                            "generation", generation.generation())));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Automation reference sync wakeup publish failed appId={} repositoryId={} generation={}",
                    generation.appId().value(), generation.repositoryId().value(), generation.generation());
        }
        dispatch(
                generation.appId(), generation.repositoryId(), generation.generation(), generation.traceId());
    }

    private void publishCancel(
            ApplicationId appId, CodeRepositoryId repositoryId, long generation, String traceId) {
        try {
            broadcastPublisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    CANCEL_REQUESTED_EVENT,
                    broadcastPublisher.instanceId(),
                    serverIdentity.linuxServerId(),
                    traceId,
                    clock.instant(),
                    Map.of(
                            "appId", appId.value(),
                            "repositoryId", repositoryId.value(),
                            "generation", generation)));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Automation reference cancel wakeup publish failed appId={} repositoryId={} generation={}",
                    appId.value(), repositoryId.value(), generation);
        }
        taskDispatcher.cancelScoped(scopeId(appId, repositoryId), generation);
    }

    private Set<LinuxServerId> liveServerIds() {
        Set<LinuxServerId> snapshot = heartbeatStore.liveBackendServerIds();
        Set<LinuxServerId> live = new LinkedHashSet<>(snapshot == null ? Set.of() : snapshot);
        // 当前 Java 能处理请求就属于在线节点，不能因心跳快照短暂缺失把本机错误标成 DEFERRED。
        live.add(localServerId());
        return Set.copyOf(live);
    }

    private LinuxServerId localServerId() {
        return new LinuxServerId(serverIdentity.linuxServerId());
    }

    private String logicalPath(
            ApplicationId appId,
            CodeRepository repository,
            ApplicationAutomationReferenceGeneration generation) {
        List<String> segments = new ArrayList<>();
        segments.add("{env:OPENCODE_REFERENCES_DIR}");
        segments.add("automation");
        segments.add(applicationPathFragment(appId));
        segments.add(validatedEnglishName(repository));
        segments.add(Long.toString(generation.generation()));
        if (!generation.directoryPath().isEmpty()) {
            segments.add(generation.directoryPath());
        }
        return String.join("/", segments);
    }

    private String alias(CodeRepository repository) {
        return "automation-" + validatedEnglishName(repository);
    }

    private String directoryName(CodeRepository repository, String directoryPath) {
        if (directoryPath.isEmpty()) {
            return validatedEnglishName(repository);
        }
        int slash = directoryPath.lastIndexOf('/');
        return slash < 0 ? directoryPath : directoryPath.substring(slash + 1);
    }

    private String applicationPathFragment(ApplicationId appId) {
        return sha256(appId.value()).substring(0, 16);
    }

    private String scopeId(ApplicationId appId, CodeRepositoryId repositoryId) {
        return "automation:" + appId.value() + ":" + repositoryId.value();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String validatedEnglishName(CodeRepository repository) {
        String value = repository.englishName();
        if (value == null || !ENGLISH_NAME_PATTERN.matcher(value).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "自动化代码库英文名称无效");
        }
        return value;
    }

    private String normalizeBranch(String branch) {
        String value = branch == null ? "" : branch.trim();
        if (!BRANCH_PATTERN.matcher(value).matches()
                || value.contains("..")
                || value.contains("//")
                || value.endsWith("/")
                || value.endsWith(".")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Git 分支名无效");
        }
        return value;
    }

    private String normalizeRelativePath(String path, boolean allowRoot) {
        String value = path == null ? "" : path.trim();
        if (value.isEmpty() || ".".equals(value)) {
            if (allowRoot) {
                return "";
            }
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录不能为空");
        }
        if (value.contains("\\")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录必须使用正斜杠");
        }
        Path parsed;
        try {
            parsed = Path.of(value);
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目录无效", Map.of(), exception);
        }
        if (parsed.isAbsolute()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "目录必须是相对路径");
        }
        for (Path segment : parsed) {
            String name = segment.toString();
            if (".".equals(name) || "..".equals(name) || ".git".equalsIgnoreCase(name)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "目录包含受保护路径");
            }
        }
        String normalized = parsed.normalize().toString().replace('\\', '/');
        if (normalized.equals("..") || normalized.startsWith("../")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "目录越界");
        }
        return normalized;
    }

    private String normalizeOperationId(String operationId) {
        String value = operationId == null ? "" : operationId.trim();
        if (!OPERATION_ID_PATTERN.matcher(value).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "幂等操作 ID 无效");
        }
        return value;
    }

    private String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "traceId 不能为空");
        }
        return traceId.trim();
    }

    private String safeError(String message) {
        String value = message == null || message.isBlank() ? "自动化引用同步失败" : message.trim();
        // 只保留本服务产生的稳定业务文案；Git/IO 异常可能携带凭据或服务器物理路径，不能进入数据库和 API。
        if (!(value.startsWith("自动化引用")
                || value.startsWith("本机自动化引用")
                || value.startsWith("所选自动化引用"))) {
            value = "自动化引用同步失败";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private ApplicationId applicationId(String value) {
        try {
            return new ApplicationId(value);
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "应用 ID 无效", Map.of(), exception);
        }
    }

    private CodeRepositoryId repositoryId(String value) {
        try {
            return new CodeRepositoryId(value);
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "代码库 ID 无效", Map.of(), exception);
        }
    }

    private static final class ReplicaBlockedException extends RuntimeException {
        private ReplicaBlockedException(String message) {
            super(message);
        }
    }

    private static final class ReplicaRetryException extends RuntimeException {
        private ReplicaRetryException(String message) {
            super(message);
        }
    }

    private static final class LeaseLostException extends RuntimeException {
    }

    private record ObservedPointer(String branch, String commitHash) {
    }

    private static final class ReplicaFileLock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private ReplicaFileLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            try {
                lock.release();
            } catch (IOException ignored) {
                // channel.close 仍会释放底层锁。
            }
            try {
                channel.close();
            } catch (IOException ignored) {
                // 数据库 fencing 是最终边界。
            }
        }
    }
}
