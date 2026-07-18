package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplica;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryOperationType;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryRepository;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryState;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.beans.factory.annotation.Value;

/**
 * 引用资产库多服务器协调服务。广播只负责唤醒，磁盘写入前必须通过数据库 generation 与租约认领。
 */
@Service
public class ReferenceRepositoryApplicationService implements ServerBroadcastHandler {

    public static final String SYNC_REQUESTED_EVENT = "reference-repository.sync-requested";
    private static final String REFERENCES_DIR_PARAMETER = "OPENCODE_REFERENCES_DIR";
    private static final String SDD_FOLDERS_PARAMETER = "REFERENCES_SDD_FOLDER_NAMES";
    private static final int MAX_TREE_ENTRIES = 1000;
    private static final int STATE_SCAN_PAGE_SIZE = 200;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$");
    private static final Pattern REPOSITORY_ENGLISH_NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,126}[A-Za-z0-9])?$");
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceRepositoryApplicationService.class);

    private final ConfigurationManagementRepository configurationRepository;
    private final ReferenceRepositoryRepository referenceRepository;
    private final UserRepository userRepository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final CommonParameterValues commonParameterValues;
    private final GitWorkspaceService gitWorkspaceService;
    private final SshKeyEncryptionService sshKeyEncryptionService;
    private final WorkspaceServerIdentity serverIdentity;
    private final ServerBroadcastPublisher broadcastPublisher;
    private final Clock clock;
    private final ReferenceRepositoryDirectoryMover directoryMover;
    private final long maxFileBytes;

    @Autowired
    public ReferenceRepositoryApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ReferenceRepositoryRepository referenceRepository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            CommonParameterValues commonParameterValues,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            @Value("${test-agent.files.max-file-bytes:1048576}") long maxFileBytes) {
        this(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                commonParameterValues,
                new GitWorkspaceService(),
                sshKeyEncryptionService,
                serverIdentity,
                broadcastPublisher,
                Clock.systemUTC(),
                ReferenceRepositoryDirectoryMover.filesystem(),
                maxFileBytes);
    }

    /** 测试构造器允许固定时间，以验证 generation、租约和退避边界。 */
    ReferenceRepositoryApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ReferenceRepositoryRepository referenceRepository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            CommonParameterValues commonParameterValues,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            Clock clock) {
        this(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                commonParameterValues,
                gitWorkspaceService,
                sshKeyEncryptionService,
                serverIdentity,
                broadcastPublisher,
                clock,
                ReferenceRepositoryDirectoryMover.filesystem(),
                1024 * 1024L);
    }

    /** 测试构造器允许模拟不支持原子 rename 的文件系统。 */
    ReferenceRepositoryApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ReferenceRepositoryRepository referenceRepository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            CommonParameterValues commonParameterValues,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            Clock clock,
            ReferenceRepositoryDirectoryMover directoryMover) {
        this(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                commonParameterValues,
                gitWorkspaceService,
                sshKeyEncryptionService,
                serverIdentity,
                broadcastPublisher,
                clock,
                directoryMover,
                1024 * 1024L);
    }

    /** 测试构造器允许缩小引用文件读取上限。 */
    ReferenceRepositoryApplicationService(
            ConfigurationManagementRepository configurationRepository,
            ReferenceRepositoryRepository referenceRepository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            CommonParameterValues commonParameterValues,
            GitWorkspaceService gitWorkspaceService,
            SshKeyEncryptionService sshKeyEncryptionService,
            WorkspaceServerIdentity serverIdentity,
            ServerBroadcastPublisher broadcastPublisher,
            Clock clock,
            ReferenceRepositoryDirectoryMover directoryMover,
            long maxFileBytes) {
        this.configurationRepository = Objects.requireNonNull(configurationRepository);
        this.referenceRepository = Objects.requireNonNull(referenceRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore);
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues);
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService);
        this.sshKeyEncryptionService = Objects.requireNonNull(sshKeyEncryptionService);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher);
        this.clock = Objects.requireNonNull(clock);
        this.directoryMover = Objects.requireNonNull(directoryMover);
        if (maxFileBytes < 1L) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        this.maxFileBytes = maxFileBytes;
    }

    /** 列表只读取当前应用已关联的应用资产库；其它仓库类型不会触发状态查询。 */
    public List<ReferenceRepositoryResponses.Status> list(String appId) {
        ApplicationId parsedAppId = applicationId(appId);
        requireApplication(parsedAppId);
        return configurationRepository.findRepositoriesByApplication(parsedAppId).stream()
                .filter(this::isAssetRepository)
                .map(repository -> status(repository, referenceRepository.findState(repository.repositoryId()).orElse(null)))
                .toList();
    }

    /** 首次确定分支并固定远端 HEAD；后续 initialize 只能幂等读取同一分支。 */
    public ReferenceRepositoryResponses.Status initialize(
            String appId,
            String repositoryId,
            String branch,
            UserId userId,
            String traceId) {
        CodeRepository repository = requireLinkedAssetRepository(applicationId(appId), repositoryId(repositoryId));
        String normalizedBranch = normalizeBranch(branch);
        Optional<ReferenceRepositoryState> existing = referenceRepository.findState(repository.repositoryId());
        if (existing.isPresent() && existing.get().branch() != null) {
            ReferenceRepositoryState current = existing.get();
            if (!current.branch().equals(normalizedBranch)) {
                throw new PlatformException(ErrorCode.CONFLICT, "引用资产库初始化后禁止切换分支", Map.of(
                        "repositoryId", repository.repositoryId().value(),
                        "branch", current.branch()));
            }
            return status(repository, current);
        }
        Instant now = clock.instant();
        String targetCommit = resolveRemoteHead(repository, normalizedBranch, userId);
        ReferenceRepositoryState state = new ReferenceRepositoryState(
                repository.repositoryId(),
                normalizedBranch,
                targetCommit,
                1L,
                ReferenceRepositoryStatus.INITIALIZING,
                ReferenceRepositoryOperationType.INITIALIZE,
                userId,
                requireTraceId(traceId),
                null,
                now,
                existing.map(ReferenceRepositoryState::createdAt).orElse(now),
                now);
        Optional<ReferenceRepositoryState> initialized = referenceRepository.initializeIfAbsent(state);
        if (initialized.isEmpty()) {
            ReferenceRepositoryState winner = referenceRepository.findState(repository.repositoryId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库初始化竞争状态不存在"));
            if (!normalizedBranch.equals(winner.branch())) {
                throw new PlatformException(ErrorCode.CONFLICT, "引用资产库初始化后禁止切换分支", Map.of(
                        "repositoryId", repository.repositoryId().value(),
                        "branch", winner.branch()));
            }
            return status(repository, winner);
        }
        ReferenceRepositoryState saved = initialized.orElseThrow();
        Set<LinuxServerId> targets = liveServerIds();
        referenceRepository.upsertTargets(saved.repositoryId(), saved.generation(), saved.branch(), targets, now);
        publishSyncRequested(saved);
        return status(repository, saved);
    }

    /** 每次非活动同步重新解析数据库分支的远端 HEAD，并以新 generation 建立副本目标。 */
    public ReferenceRepositoryResponses.Status synchronize(
            String appId,
            String repositoryId,
            UserId userId,
            String traceId) {
        CodeRepository repository = requireLinkedAssetRepository(applicationId(appId), repositoryId(repositoryId));
        ReferenceRepositoryState current = referenceRepository.findState(repository.repositoryId())
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库尚未初始化"));
        if (current.status().active()) {
            return status(repository, current);
        }
        Instant now = clock.instant();
        String targetCommit = resolveRemoteHead(repository, current.branch(), userId);
        ReferenceRepositoryState next = new ReferenceRepositoryState(
                repository.repositoryId(),
                current.branch(),
                targetCommit,
                current.generation() + 1L,
                ReferenceRepositoryStatus.SYNCHRONIZING,
                ReferenceRepositoryOperationType.SYNCHRONIZE,
                userId,
                requireTraceId(traceId),
                null,
                current.initializedAt(),
                current.createdAt(),
                now);
        Optional<ReferenceRepositoryState> advanced = referenceRepository.advanceGenerationIfCurrent(
                current.generation(), current.branch(), next);
        if (advanced.isEmpty()) {
            ReferenceRepositoryState winner = referenceRepository.findState(repository.repositoryId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库同步竞争状态不存在"));
            return status(repository, winner);
        }
        ReferenceRepositoryState saved = advanced.orElseThrow();
        Set<LinuxServerId> targets = new LinkedHashSet<>(liveServerIds());
        referenceRepository.findReplicas(saved.repositoryId()).stream()
                .map(ReferenceRepositoryReplica::linuxServerId)
                .forEach(targets::add);
        referenceRepository.upsertTargets(saved.repositoryId(), saved.generation(), saved.branch(), targets, now);
        publishSyncRequested(saved);
        return status(repository, saved);
    }

    /** 受控切换分支：先固定远端 HEAD，再用旧分支与 generation 双重 CAS 推进新代次。 */
    public ReferenceRepositoryResponses.Status switchBranch(
            String appId,
            String repositoryId,
            String branch,
            UserId userId,
            String traceId) {
        CodeRepository repository = requireLinkedAssetRepository(applicationId(appId), repositoryId(repositoryId));
        String normalizedBranch = normalizeBranch(branch);
        ReferenceRepositoryState current = referenceRepository.findState(repository.repositoryId())
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库尚未初始化"));
        if (current.status().active()) {
            if (current.operationType() == ReferenceRepositoryOperationType.SWITCH_BRANCH
                    && normalizedBranch.equals(current.branch())) {
                return status(repository, current);
            }
            throw new PlatformException(ErrorCode.CONFLICT, "引用资产库存在执行中的操作");
        }
        requireTerminalState(current);
        if (normalizedBranch.equals(current.branch())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目标分支与当前分支相同");
        }
        String targetCommit = resolveRemoteHead(repository, normalizedBranch, userId);
        Instant now = clock.instant();
        ReferenceRepositoryState next = new ReferenceRepositoryState(
                repository.repositoryId(), normalizedBranch, targetCommit, current.generation() + 1L,
                ReferenceRepositoryStatus.SYNCHRONIZING, ReferenceRepositoryOperationType.SWITCH_BRANCH,
                userId, requireTraceId(traceId), null, current.initializedAt(), current.createdAt(), now);
        Optional<ReferenceRepositoryState> advanced = referenceRepository.advanceGenerationIfCurrent(
                current.generation(), current.branch(), next);
        if (advanced.isEmpty()) {
            ReferenceRepositoryState winner = referenceRepository.findState(repository.repositoryId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库切换竞争状态不存在"));
            if (winner.operationType() == ReferenceRepositoryOperationType.SWITCH_BRANCH
                    && winner.status().active()
                    && normalizedBranch.equals(winner.branch())) {
                return status(repository, winner);
            }
            throw new PlatformException(ErrorCode.CONFLICT, "引用资产库切换与其它操作冲突");
        }
        ReferenceRepositoryState saved = advanced.orElseThrow();
        createTargetsForNewGeneration(saved, now);
        publishSyncRequested(saved);
        return status(repository, saved);
    }

    /** 主动核验只推进代次，不解析远端、不修改磁盘，并保留上一代实际指针快照。 */
    public ReferenceRepositoryResponses.Status verify(String appId, String repositoryId, String traceId) {
        CodeRepository repository = requireLinkedAssetRepository(applicationId(appId), repositoryId(repositoryId));
        ReferenceRepositoryState current = referenceRepository.findState(repository.repositoryId())
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库尚未初始化"));
        if (current.status().active()) {
            if (current.operationType() == ReferenceRepositoryOperationType.VERIFY_POINTERS) {
                return status(repository, current);
            }
            throw new PlatformException(ErrorCode.CONFLICT, "引用资产库存在执行中的操作");
        }
        requireTerminalState(current);
        Instant now = clock.instant();
        ReferenceRepositoryState next = new ReferenceRepositoryState(
                current.repositoryId(), current.branch(), current.targetCommitHash(), current.generation() + 1L,
                ReferenceRepositoryStatus.VERIFYING, ReferenceRepositoryOperationType.VERIFY_POINTERS,
                current.credentialUserId(), requireTraceId(traceId), null,
                current.initializedAt(), current.createdAt(), now);
        Optional<ReferenceRepositoryState> advanced = referenceRepository.advanceGenerationIfCurrent(
                current.generation(), current.branch(), next);
        if (advanced.isEmpty()) {
            ReferenceRepositoryState winner = referenceRepository.findState(repository.repositoryId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库核验竞争状态不存在"));
            if (winner.operationType() == ReferenceRepositoryOperationType.VERIFY_POINTERS && winner.status().active()) {
                return status(repository, winner);
            }
            throw new PlatformException(ErrorCode.CONFLICT, "引用资产库核验与其它操作冲突");
        }
        ReferenceRepositoryState saved = advanced.orElseThrow();
        createTargetsForNewGeneration(saved, now);
        publishSyncRequested(saved);
        return status(repository, saved);
    }

    private void createTargetsForNewGeneration(ReferenceRepositoryState state, Instant now) {
        Set<LinuxServerId> live = liveServerIds();
        Set<LinuxServerId> targets = new LinkedHashSet<>(live);
        referenceRepository.findReplicas(state.repositoryId()).stream()
                .map(ReferenceRepositoryReplica::linuxServerId)
                .forEach(targets::add);
        referenceRepository.upsertTargets(state.repositoryId(), state.generation(), state.branch(), targets, now);
        referenceRepository.deferOfflineReplicas(state.repositoryId(), state.generation(), live, now);
    }

    private void requireTerminalState(ReferenceRepositoryState state) {
        if (state.status() != ReferenceRepositoryStatus.READY
                && state.status() != ReferenceRepositoryStatus.FAILED) {
            throw new PlatformException(ErrorCode.CONFLICT, "引用资产库当前状态不允许执行该操作");
        }
    }

    public ReferenceRepositoryResponses.Status status(String appId, String repositoryId) {
        CodeRepository repository = requireLinkedAssetRepository(applicationId(appId), repositoryId(repositoryId));
        return status(repository, referenceRepository.findState(repository.repositoryId()).orElse(null));
    }

    /** READY 时仅列出当前服务器本地副本的单层安全目录。 */
    public List<ReferenceRepositoryResponses.TreeNode> tree(String appId, String repositoryId, String path) {
        CodeRepository repository = requireLinkedAssetRepository(applicationId(appId), repositoryId(repositoryId));
        requireReadyLocalState(repository);
        // 既有配置管理 tree API 保留历史的输入 trim 兼容；工作区 view locator 则必须逐字保留文件名。
        String normalizedPath = normalizeRelativePath(path == null ? null : path.trim());
        Path root = repositoryRoot(repository);
        Path directory = resolveSafeDirectory(root, normalizedPath);
        Set<String> highlightedNames = normalizedPath.isEmpty() ? sddFolderNames() : Set.of();
        try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(entry -> !".git".equalsIgnoreCase(entry.getFileName().toString()))
                    .filter(entry -> !Files.isSymbolicLink(entry))
                    .sorted(Comparator
                            .comparing((Path entry) -> !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
                            .thenComparing(entry -> entry.getFileName().toString()))
                    .limit(MAX_TREE_ENTRIES)
                    .map(entry -> treeNode(root, entry, normalizedPath.isEmpty(), highlightedNames))
                    .toList();
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取引用资产目录失败", Map.of(), exception);
        }
    }

    /**
     * 为工作区组合视图列出某个受管规格目录的一层内容。
     *
     * <p>仓库链接、类型、总体 generation、本机副本和规格目录白名单都在每次调用时重新校验；
     * 客户端只提供配置中的英文名与逻辑目录，不接触物理路径或 repositoryId。
     */
    public ViewListing listView(
            String appId,
            String repositoryEnglishName,
            String folder,
            String path,
            int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        ViewRoot viewRoot = requireReadyViewRoot(appId, repositoryEnglishName, folder);
        String normalizedPath = normalizeRelativePath(path);
        Path directory = resolveSafeDirectory(viewRoot.folderRoot(), normalizedPath);
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            List<ViewEntry> entries = stream
                    .filter(entry -> !".git".equalsIgnoreCase(entry.getFileName().toString()))
                    .filter(entry -> !Files.isSymbolicLink(entry))
                    .sorted(Comparator.comparing(entry -> entry.getFileName().toString()))
                    .limit((long) limit + 1L)
                    .map(entry -> viewEntry(viewRoot.folderRoot(), entry))
                    .toList();
            boolean truncated = entries.size() > limit;
            return new ViewListing(truncated ? entries.subList(0, limit) : entries, truncated);
        } catch (PlatformException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取引用资产目录失败", Map.of(), exception);
        }
    }

    /**
     * 为工作区组合视图读取安全 UTF-8 引用文件；禁止绝对路径、穿越、.git、符号链接和超限文件。
     */
    public ViewContent readView(
            String appId,
            String repositoryEnglishName,
            String folder,
            String path) {
        ViewRoot viewRoot = requireReadyViewRoot(appId, repositoryEnglishName, folder);
        String normalizedPath = normalizeRelativePath(path);
        if (normalizedPath.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "引用资产文件路径不能为空");
        }
        Path target = resolveSafeEntry(viewRoot.folderRoot(), normalizedPath);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "引用资产文件不存在");
        }
        try {
            long size = Files.size(target);
            if (size > maxFileBytes) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "引用资产文件超过读取大小限制",
                        Map.of("maxFileBytes", maxFileBytes));
            }
            return new ViewContent(normalizedPath, Files.readString(target, StandardCharsets.UTF_8), size);
        } catch (PlatformException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取引用资产文件失败", Map.of(), exception);
        }
    }

    @Override
    public boolean supports(String type) {
        return SYNC_REQUESTED_EVENT.equals(type);
    }

    /** 广播载荷只作为低延迟唤醒；实际任务仍通过 DB 租约认领。 */
    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!supports(event.type())) {
            return;
        }
        Object repositoryId = event.payload().get("repositoryId");
        Object generation = event.payload().get("generation");
        if (!(repositoryId instanceof String id) || !(generation instanceof Number number)) {
            return;
        }
        claimAndSynchronize(new CodeRepositoryId(id), number.longValue(), event.traceId());
    }

    /** 定时补偿丢广播，并在服务器重新上线时为当前 generation 补建本机目标。 */
    public void reconcileLocalReplicas(String traceId) {
        LinuxServerId localServer = new LinuxServerId(serverIdentity.linuxServerId());
        Set<LinuxServerId> live = liveServerIds();
        Instant now = clock.instant();
        CodeRepositoryId cursor = null;
        while (true) {
            List<ReferenceRepositoryState> page = referenceRepository.findStatesAfter(cursor, STATE_SCAN_PAGE_SIZE);
            for (ReferenceRepositoryState state : page) {
                if (state.branch() != null && state.status() != ReferenceRepositoryStatus.UNINITIALIZED) {
                    referenceRepository.deferOfflineReplicas(
                            state.repositoryId(), state.generation(), live, now);
                    if (!live.isEmpty()) {
                        referenceRepository.upsertTargets(
                                state.repositoryId(), state.generation(), state.branch(), live, now);
                    }
                    refreshOverallStatus(state.repositoryId(), state.generation(), live);
                }
            }
            if (page.size() < STATE_SCAN_PAGE_SIZE) {
                break;
            }
            CodeRepositoryId nextCursor = page.get(page.size() - 1).repositoryId();
            if (nextCursor.equals(cursor)) {
                LOGGER.warn("Reference repository state cursor did not advance repositoryId={}", nextCursor.value());
                break;
            }
            cursor = nextCursor;
        }
        if (!live.contains(localServer)) {
            return;
        }
        for (ReferenceRepositoryReplica replica : referenceRepository.findClaimableReplicas(localServer, now, 100)) {
            claimAndSynchronize(replica.repositoryId(), replica.generation(), traceId);
        }
    }

    private void claimAndSynchronize(CodeRepositoryId repositoryId, long generation, String traceId) {
        Instant now = clock.instant();
        LinuxServerId localServer = new LinuxServerId(serverIdentity.linuxServerId());
        String leaseToken = "rrl_" + UUID.randomUUID().toString().replace("-", "");
        referenceRepository.claimReplica(
                        repositoryId,
                        generation,
                        localServer,
                        leaseToken,
                        now.plus(LEASE_DURATION),
                        now)
                .ifPresent(replica -> synchronizeClaimedReplica(replica, traceId));
    }

    private void synchronizeClaimedReplica(ReferenceRepositoryReplica claimed, String traceId) {
        ReferenceRepositoryState state = referenceRepository.findState(claimed.repositoryId()).orElse(null);
        if (state == null || state.generation() != claimed.generation() || state.targetCommitHash() == null) {
            return;
        }
        CodeRepository repository = configurationRepository.findRepository(claimed.repositoryId()).orElse(null);
        if (repository == null || !isAssetRepository(repository)) {
            markBlocked(claimed, "引用资产库配置不存在");
            return;
        }
        if (state.operationType() == ReferenceRepositoryOperationType.VERIFY_POINTERS) {
            verifyClaimedReplica(claimed, repository, state, traceId);
            return;
        }
        if (state.credentialUserId() == null) {
            markBlocked(claimed, "引用资产库配置或凭据用户不存在");
            return;
        }
        try (ReplicaFileLock ignored = acquireReplicaFileLock(repository)) {
            renewLeaseOrThrow(claimed);
            Path repoRoot = repositoryRoot(repository);
            String privateKey = privateKeyFor(repository, state.credentialUserId());
            String gitUrl = effectiveGitUrl(repository, state.credentialUserId());
            synchronizeDirectory(claimed, repository, state, repoRoot, gitUrl, privateKey);
            boolean updated = referenceRepository.markReady(
                    claimed.repositoryId(),
                    claimed.generation(),
                    claimed.linuxServerId(),
                    claimed.leaseToken(),
                    state.branch(),
                    state.targetCommitHash(),
                    clock.instant(),
                    clock.instant());
            if (updated) {
                refreshOverallStatus(state.repositoryId(), state.generation());
            }
        } catch (LeaseLostException exception) {
            LOGGER.info(
                    "Reference repository worker lease lost repositoryId={} linuxServerId={} generation={}",
                    claimed.repositoryId().value(),
                    claimed.linuxServerId().value(),
                    claimed.generation());
        } catch (ReplicaRetryException exception) {
            markRetry(claimed, exception.getMessage());
        } catch (ReplicaBlockedException exception) {
            markBlocked(claimed, exception.getMessage());
        } catch (PlatformException exception) {
            if (isTransientGitFailure(exception)) {
                markRetry(claimed, safeError(exception.getMessage()));
            } else {
                markBlocked(claimed, safeError(exception.getMessage()));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Reference repository synchronization failed repositoryId={} linuxServerId={} generation={} traceId={}",
                    claimed.repositoryId().value(),
                    claimed.linuxServerId().value(),
                    claimed.generation(),
                    traceId);
            markBlocked(claimed, "引用资产同步失败");
        }
    }

    /** 主动核验全程只读 Git 与文件系统；任何不可信状态都按 BLOCKED fenced 写回实际快照。 */
    private void verifyClaimedReplica(
            ReferenceRepositoryReplica claimed,
            CodeRepository repository,
            ReferenceRepositoryState state,
            String traceId) {
        String actualBranch = claimed.currentBranch();
        String actualCommit = claimed.currentCommitHash();
        String error = null;
        try {
            Path repoRoot = repositoryRoot(repository);
            if (Files.isSymbolicLink(repoRoot)
                    || !Files.isDirectory(repoRoot, LinkOption.NOFOLLOW_LINKS)
                    || !gitWorkspaceService.isGitRepository(repoRoot)) {
                error = "引用资产目标目录不是可信 Git 仓库";
            } else {
                actualBranch = gitWorkspaceService.currentBranch(repoRoot);
                actualCommit = gitWorkspaceService.headCommit(repoRoot);
                if (!repository.matchesStoredOrigin(gitWorkspaceService.originUrl(repoRoot))) {
                    error = "引用资产本地仓库 origin 与数据库不一致";
                } else if (!gitWorkspaceService.isWorktreeClean(repoRoot)) {
                    error = "引用资产本地仓库存在未提交修改";
                } else if (!state.branch().equals(actualBranch)
                        || !state.targetCommitHash().equals(actualCommit)) {
                    error = "引用资产本地实际指针与目标不一致";
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Reference repository pointer verification failed repositoryId={} linuxServerId={} generation={} traceId={}",
                    claimed.repositoryId().value(), claimed.linuxServerId().value(), claimed.generation(), traceId);
            error = "引用资产本地指针核验失败";
        }
        Instant now = clock.instant();
        ReferenceRepositoryReplicaStatus resultStatus = error == null
                ? ReferenceRepositoryReplicaStatus.READY
                : ReferenceRepositoryReplicaStatus.BLOCKED;
        boolean updated = referenceRepository.markVerificationResult(
                claimed.repositoryId(), claimed.generation(), claimed.linuxServerId(), claimed.leaseToken(),
                resultStatus, actualBranch, actualCommit, now, error, now);
        if (updated) {
            refreshOverallStatus(state.repositoryId(), state.generation());
        }
    }

    /** 只有明确的 Git 网络或超时错误自动重试；凭据、仓库、分支和参数错误等待管理员开启新 generation。 */
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

    /** 新目录先在同根临时目录校验再原子移动；已有目录按操作类型执行同分支快进或受控跨分支切换。 */
    private void synchronizeDirectory(
            ReferenceRepositoryReplica claimed,
            CodeRepository repository,
            ReferenceRepositoryState state,
            Path repoRoot,
            String gitUrl,
            String privateKey) {
        if (Files.exists(repoRoot, LinkOption.NOFOLLOW_LINKS)) {
            validateAndFastForward(claimed, repository, state, repoRoot, privateKey);
            return;
        }
        Path referencesRoot = repoRoot.getParent();
        Path temporary = referencesRoot.resolve("." + validatedRepositoryEnglishName(repository)
                + "." + state.generation() + "." + UUID.randomUUID() + ".tmp");
        try {
            renewLeaseOrThrow(claimed);
            Files.createDirectories(referencesRoot);
            renewLeaseOrThrow(claimed);
            gitWorkspaceService.cloneBranch(gitUrl, state.branch(), temporary, privateKey);
            String resolved = gitWorkspaceService.resolveCommit(temporary, state.targetCommitHash());
            if (!state.targetCommitHash().equals(resolved)) {
                throw new ReplicaBlockedException("临时副本无法解析目标提交");
            }
            renewLeaseOrThrow(claimed);
            gitWorkspaceService.resetHardToCommit(temporary, state.targetCommitHash());
            if (!state.targetCommitHash().equals(gitWorkspaceService.headCommit(temporary))) {
                throw new ReplicaBlockedException("临时副本目标提交校验失败");
            }
            renewLeaseOrThrow(claimed);
            moveAtomically(temporary, repoRoot);
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "写入引用资产临时目录失败", Map.of(), exception);
        } finally {
            deleteTemporaryTree(temporary);
        }
    }

    private void validateAndFastForward(
            ReferenceRepositoryReplica claimed,
            CodeRepository repository,
            ReferenceRepositoryState state,
            Path repoRoot,
            String privateKey) {
        if (Files.isSymbolicLink(repoRoot)
                || !Files.isDirectory(repoRoot, LinkOption.NOFOLLOW_LINKS)
                || !gitWorkspaceService.isGitRepository(repoRoot)) {
            throw new ReplicaBlockedException("引用资产目标目录不是可接管的 Git 仓库");
        }
        if (!gitWorkspaceService.isWorktreeClean(repoRoot)) {
            throw new ReplicaBlockedException("引用资产本地仓库存在未提交修改");
        }
        if (!repository.matchesStoredOrigin(gitWorkspaceService.originUrl(repoRoot))) {
            throw new ReplicaBlockedException("引用资产本地仓库 origin 与数据库不一致");
        }
        if (state.operationType() != ReferenceRepositoryOperationType.SWITCH_BRANCH
                && !state.branch().equals(gitWorkspaceService.currentBranch(repoRoot))) {
            throw new ReplicaBlockedException("引用资产本地仓库分支与初始化分支不一致");
        }
        if (repository.internalDeployment()) {
            renewLeaseOrThrow(claimed);
            gitWorkspaceService.setOriginUrl(
                    repoRoot,
                    effectiveGitUrl(repository, state.credentialUserId()),
                    privateKey);
        }
        String currentCommit = gitWorkspaceService.headCommit(repoRoot);
        renewLeaseOrThrow(claimed);
        gitWorkspaceService.fetch(repoRoot, privateKey);
        String targetCommit = gitWorkspaceService.resolveCommit(repoRoot, state.targetCommitHash());
        if (!state.targetCommitHash().equals(targetCommit)) {
            throw new ReplicaBlockedException("引用资产无法解析固定目标提交");
        }
        if (state.operationType() == ReferenceRepositoryOperationType.SWITCH_BRANCH) {
            renewLeaseOrThrow(claimed);
            gitWorkspaceService.checkoutBranchAtFixedCommit(repoRoot, state.branch(), targetCommit, privateKey);
            return;
        }
        if (!gitWorkspaceService.isAncestor(repoRoot, currentCommit, targetCommit)) {
            throw new ReplicaBlockedException("引用资产本地仓库与目标提交发生分叉");
        }
        renewLeaseOrThrow(claimed);
        gitWorkspaceService.resetHardToCommit(repoRoot, targetCommit);
    }

    /**
     * 每个共享仓库写步骤前通过数据库 CAS 续租；一旦 token/generation 被替换，旧 worker 立即停止。
     */
    private void renewLeaseOrThrow(ReferenceRepositoryReplica claimed) {
        Instant now = clock.instant();
        boolean renewed = referenceRepository.renewLease(
                claimed.repositoryId(),
                claimed.generation(),
                claimed.linuxServerId(),
                claimed.leaseToken(),
                now.plus(LEASE_DURATION),
                now);
        if (!renewed) {
            throw new LeaseLostException();
        }
    }

    /**
     * 数据库租约负责集群 fencing，本机文件锁补上租约过期边界，避免同一 Linux 服务器多 Java 进程并发写同一目录。
     */
    private ReplicaFileLock acquireReplicaFileLock(CodeRepository repository) {
        Path referencesRoot = referencesRoot();
        Path lockDirectory = referencesRoot.resolve(".reference-repository-locks").normalize();
        String englishName = validatedRepositoryEnglishName(repository);
        Path lockPath = lockDirectory.resolve(englishName + ".lock").normalize();
        if (!lockPath.startsWith(lockDirectory)) {
            throw new ReplicaBlockedException("引用资产库锁路径无效");
        }
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
                throw new ReplicaRetryException("本机引用资产同步锁被占用");
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
            throw new ReplicaRetryException("本机引用资产同步锁暂时不可用");
        }
    }

    private void markRetry(ReferenceRepositoryReplica claimed, String message) {
        int retryCount = claimed.retryCount() + 1;
        Instant now = clock.instant();
        boolean updated = referenceRepository.markRetry(
                claimed.repositoryId(),
                claimed.generation(),
                claimed.linuxServerId(),
                claimed.leaseToken(),
                retryCount,
                now.plus(ReferenceRepositoryReplica.retryDelay(retryCount)),
                message,
                now);
        if (updated) {
            refreshOverallStatus(claimed.repositoryId(), claimed.generation());
        }
    }

    private void markBlocked(ReferenceRepositoryReplica claimed, String message) {
        boolean updated = referenceRepository.markBlocked(
                claimed.repositoryId(),
                claimed.generation(),
                claimed.linuxServerId(),
                claimed.leaseToken(),
                safeError(message),
                clock.instant());
        if (updated) {
            refreshOverallStatus(claimed.repositoryId(), claimed.generation());
        }
    }

    /** 总体 READY/FAILED 只计算当前在线服务器；离线目标保留并在恢复后补齐，但不阻塞在线完成。 */
    private void refreshOverallStatus(CodeRepositoryId repositoryId, long generation) {
        refreshOverallStatus(repositoryId, generation, liveServerIds());
    }

    /** 补偿扫描复用同一轮在线快照，避免每个仓库重复读取心跳且保证协调与汇总口径一致。 */
    private void refreshOverallStatus(
            CodeRepositoryId repositoryId,
            long generation,
            Set<LinuxServerId> live) {
        ReferenceRepositoryState state = referenceRepository.findState(repositoryId).orElse(null);
        if (state == null || state.generation() != generation) {
            return;
        }
        List<ReferenceRepositoryReplica> online = referenceRepository.findReplicas(repositoryId).stream()
                .filter(replica -> replica.generation() == generation && live.contains(replica.linuxServerId()))
                .toList();
        if (online.stream().anyMatch(replica -> replica.status() == ReferenceRepositoryReplicaStatus.BLOCKED)) {
            String error = online.stream()
                    .filter(replica -> replica.status() == ReferenceRepositoryReplicaStatus.BLOCKED)
                    .map(ReferenceRepositoryReplica::lastError)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("引用资产副本被阻塞");
            referenceRepository.updateOverallStatus(
                    repositoryId, generation, ReferenceRepositoryStatus.FAILED, error, clock.instant());
        } else if (!online.isEmpty()
                && online.stream().allMatch(replica -> replica.status() == ReferenceRepositoryReplicaStatus.READY)) {
            referenceRepository.updateOverallStatus(
                    repositoryId, generation, ReferenceRepositoryStatus.READY, null, clock.instant());
        } else if (!online.isEmpty()) {
            ReferenceRepositoryStatus activeStatus = switch (state.operationType()) {
                case INITIALIZE -> ReferenceRepositoryStatus.INITIALIZING;
                case VERIFY_POINTERS -> ReferenceRepositoryStatus.VERIFYING;
                case SYNCHRONIZE, SWITCH_BRANCH -> ReferenceRepositoryStatus.SYNCHRONIZING;
            };
            referenceRepository.updateOverallStatus(
                    repositoryId, generation, activeStatus, null, clock.instant());
        }
    }

    private ReferenceRepositoryResponses.Status status(CodeRepository repository, ReferenceRepositoryState state) {
        List<ReferenceRepositoryReplica> replicas = state == null
                ? List.of()
                : referenceRepository.findReplicas(repository.repositoryId());
        long generation = state == null ? 0L : state.generation();
        Set<LinuxServerId> live = liveServerIds();
        List<ReferenceRepositoryResponses.ServerStatus> servers = replicas.stream()
                .filter(replica -> replica.generation() == generation)
                .sorted(Comparator.comparing(replica -> replica.linuxServerId().value()))
                .map(replica -> new ReferenceRepositoryResponses.ServerStatus(
                        replica.linuxServerId().value(),
                        replica.status().name(),
                        live.contains(replica.linuxServerId()),
                        replica.currentBranch(),
                        replica.currentCommitHash(),
                        matchesTarget(replica, state),
                        replica.verifiedAt(),
                        replica.syncedAt(),
                        replica.lastError()))
                .toList();
        int ready = (int) servers.stream()
                .filter(server -> ReferenceRepositoryReplicaStatus.READY.name().equals(server.status()))
                .count();
        return new ReferenceRepositoryResponses.Status(
                repository.repositoryId().value(),
                repository.name(),
                repository.englishName(),
                repository.gitUrl(),
                state != null && state.branch() != null,
                state == null ? null : state.branch(),
                state == null ? null : state.targetCommitHash(),
                generation,
                state == null ? ReferenceRepositoryStatus.UNINITIALIZED.name() : state.status().name(),
                state == null ? null : state.operationType().name(),
                servers.size(),
                ready,
                servers,
                state == null ? null : state.traceId(),
                state == null ? null : state.lastError());
    }

    private Boolean matchesTarget(ReferenceRepositoryReplica replica, ReferenceRepositoryState state) {
        if (state == null || replica.currentBranch() == null || replica.currentCommitHash() == null) {
            return null;
        }
        return Objects.equals(state.branch(), replica.currentBranch())
                && Objects.equals(state.targetCommitHash(), replica.currentCommitHash());
    }

    private CodeRepository requireLinkedAssetRepository(ApplicationId appId, CodeRepositoryId repositoryId) {
        requireApplication(appId);
        CodeRepository repository = configurationRepository.findRepositoriesByApplication(appId).stream()
                .filter(candidate -> candidate.repositoryId().equals(repositoryId))
                .findFirst()
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "引用资产库未关联当前应用",
                        Map.of("appId", appId.value(), "repositoryId", repositoryId.value())));
        if (!isAssetRepository(repository)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "代码库类型不是应用资产库");
        }
        return repository;
    }

    private void requireApplication(ApplicationId appId) {
        configurationRepository.findApplication(appId)
                .filter(application -> application.enabled())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用不存在或未启用"));
    }

    private boolean isAssetRepository(CodeRepository repository) {
        return CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value().equals(repository.repositoryType());
    }

    private String resolveRemoteHead(CodeRepository repository, String branch, UserId userId) {
        String privateKey = privateKeyFor(repository, userId);
        return gitWorkspaceService.resolveRemoteBranchCommit(effectiveGitUrl(repository, userId), branch, privateKey);
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

    private Set<LinuxServerId> liveServerIds() {
        Set<LinuxServerId> live = heartbeatStore.liveBackendServerIds();
        return live == null ? Set.of() : Set.copyOf(live);
    }

    private void publishSyncRequested(ReferenceRepositoryState state) {
        try {
            broadcastPublisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    SYNC_REQUESTED_EVENT,
                    broadcastPublisher.instanceId(),
                    serverIdentity.linuxServerId(),
                    state.traceId(),
                    clock.instant(),
                    Map.of(
                            "repositoryId", state.repositoryId().value(),
                            "generation", state.generation(),
                            "traceId", state.traceId())));
        } catch (RuntimeException exception) {
            // 广播只是低延迟唤醒，DB 已是事实来源；日志不输出异常文本，避免 connector/Redis 细节泄漏。
            LOGGER.warn(
                    "Reference repository sync wakeup publish failed repositoryId={} generation={}",
                    state.repositoryId().value(),
                    state.generation());
        }
    }

    private Path repositoryRoot(CodeRepository repository) {
        String englishName = validatedRepositoryEnglishName(repository);
        Path root = referencesRoot();
        Path resolved = root.resolve(englishName).normalize();
        if (!resolved.startsWith(root)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产库目录越界");
        }
        return resolved;
    }

    private ViewRoot requireReadyViewRoot(String appId, String repositoryEnglishName, String folder) {
        String normalizedEnglishName = repositoryEnglishName == null ? "" : repositoryEnglishName.trim();
        CodeRepository repository = configurationRepository.findRepositoryByEnglishName(normalizedEnglishName)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "引用资产库不存在"));
        repository = requireLinkedAssetRepository(applicationId(appId), repository.repositoryId());
        String normalizedFolder = normalizeRelativePath(folder);
        if (normalizedFolder.isEmpty()
                || normalizedFolder.contains("/")
                || !sddFolderNames().contains(normalizedFolder)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产目录不在当前规格目录白名单中");
        }
        requireReadyLocalState(repository);
        Path repositoryRoot = repositoryRoot(repository);
        return new ViewRoot(repository, resolveSafeDirectory(repositoryRoot, normalizedFolder));
    }

    private ReferenceRepositoryState requireReadyLocalState(CodeRepository repository) {
        ReferenceRepositoryState state = referenceRepository.findState(repository.repositoryId())
                .filter(value -> value.status() == ReferenceRepositoryStatus.READY)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "引用资产库尚未就绪"));
        LinuxServerId localServer = new LinuxServerId(serverIdentity.linuxServerId());
        boolean localReady = referenceRepository.findReplicas(repository.repositoryId()).stream()
                .anyMatch(replica -> replica.linuxServerId().equals(localServer)
                        && replica.generation() == state.generation()
                        && replica.status() == ReferenceRepositoryReplicaStatus.READY);
        if (!localReady) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前服务器引用资产副本尚未就绪");
        }
        return state;
    }

    private String validatedRepositoryEnglishName(CodeRepository repository) {
        String englishName = repository.englishName();
        if (englishName == null || !REPOSITORY_ENGLISH_NAME_PATTERN.matcher(englishName).matches()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "版本库英文名称只能使用字母、数字和连字符，长度 1 到 128，且不能以连字符开头或结尾",
                    Map.of("repositoryId", repository.repositoryId().value(), "field", "englishName"));
        }
        return englishName;
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

    private Set<String> sddFolderNames() {
        String value = commonParameterValues.resolvedValue(SDD_FOLDERS_PARAMETER).orElse("");
        Set<String> names = new LinkedHashSet<>();
        for (String segment : value.split(",")) {
            String name = segment.trim();
            if (!name.isEmpty() && name.equals(name.toLowerCase(java.util.Locale.ROOT))) {
                names.add(name);
            }
        }
        return Set.copyOf(names);
    }

    private Path resolveSafeDirectory(Path root, String relativePath) {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前服务器引用资产目录不可用");
        }
        Path current = root;
        if (!relativePath.isEmpty()) {
            for (String segment : relativePath.split("/")) {
                current = current.resolve(segment);
                if (Files.isSymbolicLink(current)) {
                    throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产路径禁止经过符号链接");
                }
            }
        }
        Path normalized = current.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize())
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "引用资产目录不存在");
        }
        return normalized;
    }

    private Path resolveSafeEntry(Path root, String relativePath) {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlatformException(ErrorCode.CONFLICT, "当前服务器引用资产目录不可用");
        }
        Path current = root;
        for (String segment : relativePath.split("/")) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产路径禁止经过符号链接");
            }
        }
        Path normalized = current.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产路径越界");
        }
        return normalized;
    }

    private ViewEntry viewEntry(Path root, Path entry) {
        try {
            boolean directory = Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
            return new ViewEntry(
                    root.relativize(entry).toString().replace('\\', '/'),
                    entry.getFileName().toString(),
                    directory,
                    directory ? 0L : Files.size(entry),
                    Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS).toInstant());
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取引用资产条目失败", Map.of(), exception);
        }
    }

    private ReferenceRepositoryResponses.TreeNode treeNode(
            Path root,
            Path entry,
            boolean rootLevel,
            Set<String> highlightedNames) {
        boolean directory = Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
        String name = entry.getFileName().toString();
        boolean marked = rootLevel && directory && highlightedNames.contains(name);
        try {
            return new ReferenceRepositoryResponses.TreeNode(
                    root.relativize(entry).toString().replace('\\', '/'),
                    name,
                    directory,
                    directory ? 0L : Files.size(entry),
                    marked,
                    marked);
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "读取引用资产条目失败", Map.of(), exception);
        }
    }

    private String normalizeRelativePath(String path) {
        // 引用视图的 locator 由目录列表生成，文件名中的合法首尾空格必须保持原样。
        String value = path == null ? "" : path;
        if (value.isEmpty()) {
            return "";
        }
        if (value.contains("\\")) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "引用资产路径必须使用正斜杠");
        }
        Path parsed;
        try {
            parsed = Path.of(value);
        } catch (RuntimeException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "引用资产路径无效", Map.of(), exception);
        }
        if (parsed.isAbsolute()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产路径必须是相对路径");
        }
        for (Path segment : parsed) {
            String name = segment.toString();
            if (".".equals(name) || "..".equals(name) || ".git".equalsIgnoreCase(name)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产路径包含受保护目录");
            }
        }
        String normalized = parsed.normalize().toString().replace('\\', '/');
        if (normalized.startsWith("../") || normalized.equals("..")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "引用资产路径越界");
        }
        return normalized;
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

    private String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "traceId 不能为空");
        }
        return traceId.trim();
    }

    private String safeError(String message) {
        String value = message == null || message.isBlank() ? "引用资产同步失败" : message.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            directoryMover.moveAtomically(source, target);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new ReplicaBlockedException("引用资产根目录不支持原子移动");
        }
    }

    private void deleteTemporaryTree(Path temporary) {
        if (temporary == null || !Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
            paths
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 临时目录清理失败不覆盖原始同步异常，交由下次同根临时目录清理/运维排查。
                        }
                    });
        } catch (IOException ignored) {
            // 同上，禁止在 finally 中反转任务状态。
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
                // channel.close 仍会释放 JVM 持有的底层锁。
            }
            try {
                channel.close();
            } catch (IOException ignored) {
                // 任务状态由数据库 fencing 决定，关闭告警不覆盖同步结果。
            }
        }
    }

    /** 工作区组合视图使用的安全引用目录列表。 */
    public record ViewListing(List<ViewEntry> entries, boolean truncated) {
        public ViewListing {
            entries = List.copyOf(entries);
        }
    }

    /** 工作区组合视图使用的引用文件元数据。 */
    public record ViewEntry(
            String path,
            String name,
            boolean directory,
            long size,
            Instant lastModifiedAt) {
    }

    /** 工作区组合视图使用的只读 UTF-8 文件正文。 */
    public record ViewContent(String path, String content, long size) {
    }

    private record ViewRoot(CodeRepository repository, Path folderRoot) {
    }
}
