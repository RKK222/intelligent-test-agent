package com.enterprise.testagent.opencode.runtime.config;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutPreparation;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTarget;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessConfigLinkService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公共 Agent/Skill 配置发布协调器：持久化禁发、登记存量进程，并在 Session 空闲后 dispose。
 */
@Service
public class PublicAgentConfigRolloutService
        implements PublicAgentConfigRolloutCoordinator, PublicAgentConfigMessageGate {

    private static final int CLAIM_LIMIT = 1;
    /** 分页进程仓储单次查询硬上限；当前 manager 容量远低于该值。 */
    private static final int TOPOLOGY_LIMIT = PageRequest.MAX_SIZE;
    private static final Duration TARGET_LEASE = Duration.ofSeconds(60);
    private static final Duration SERVER_SYNC_LEASE = Duration.ofMinutes(3);
    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(10);

    private final PublicAgentConfigRolloutRepository repository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final OpencodeProcessManagementRepository processRepository;
    private final AgentRuntime runtime;
    private final BackendInstanceIdentity backendInstanceIdentity;
    private final ManagedWorkspacePathResolver workspacePathResolver;
    private final Duration retryDelay;
    private OpencodeProcessConfigLinkService configLinkService;

    /** 公共发布排空时把个人预览指针恢复到共享运行副本；方法注入保持既有测试构造器兼容。 */
    @Autowired
    void setConfigLinkService(OpencodeProcessConfigLinkService configLinkService) {
        this.configLinkService = Objects.requireNonNull(configLinkService, "configLinkService must not be null");
    }

    public PublicAgentConfigRolloutService(
            PublicAgentConfigRolloutRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessManagementRepository processRepository,
            AgentRuntimeRegistry runtimeRegistry,
            BackendInstanceIdentity backendInstanceIdentity,
            ManagedWorkspacePathResolver workspacePathResolver,
            @Value("${test-agent.public-agent-config.rollout.retry-delay-ms:5000}") long retryDelayMillis) {
        this.repository = repository;
        this.heartbeatStore = heartbeatStore;
        this.processRepository = processRepository;
        this.runtime = runtimeRegistry.require(AgentRuntimeRegistry.DEFAULT_AGENT_ID);
        this.backendInstanceIdentity = backendInstanceIdentity;
        this.workspacePathResolver = workspacePathResolver;
        this.retryDelay = Duration.ofMillis(Math.max(1000L, retryDelayMillis));
    }

    /** 在任何远端 push 或共享运行副本切换前建立 PREPARING 闸门。 */
    @Override
    @Transactional
    public String prepare(
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId) {
        return prepareRollout(
                AgentConfigRolloutScope.PUBLIC,
                null,
                branch,
                expectedCommitHash,
                previousCommitHash,
                localLinuxServerId,
                initiatedByUserId,
                traceId);
    }

    @Override
    @Transactional
    public String prepareApplication(
            String versionId,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId) {
        return prepareRollout(
                AgentConfigRolloutScope.APPLICATION,
                versionId,
                branch,
                expectedCommitHash,
                previousCommitHash,
                localLinuxServerId,
                initiatedByUserId,
                traceId);
    }

    private String prepareRollout(
            AgentConfigRolloutScope scope,
            String scopeKey,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId) {
        repository.findActiveRolloutId().ifPresent(active -> {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "已有共享 Agent/Skill 配置发布正在排空",
                    Map.of("rolloutId", active));
        });
        String rolloutId = RuntimeIdGenerator.publicAgentConfigRolloutId();
        Instant now = Instant.now();
        repository.registerServerMembership(localLinuxServerId, now);
        repository.createRollout(
                rolloutId,
                scope,
                scopeKey,
                branch,
                expectedCommitHash,
                previousCommitHash,
                initiatedByUserId,
                localLinuxServerId,
                traceId,
                now);

        Set<String> serverIds = new LinkedHashSet<>();
        serverIds.add(localLinuxServerId);
        // 发布成员独立于 linux_servers 历史拓扑；离线但未退役的成员仍会被保留，历史废弃记录不会误阻塞。
        serverIds.addAll(repository.findActiveServerMembershipIds());
        Set<LinuxServerId> liveBackendServerIds = heartbeatStore.liveBackendServerIds();
        if (liveBackendServerIds != null) {
            liveBackendServerIds.forEach(serverId -> serverIds.add(serverId.value()));
        }
        heartbeatStore.liveManagerSnapshots()
                .forEach(snapshot -> serverIds.add(snapshot.container().linuxServerId().value()));
        // 在线服务器立即补登记，消除刚升级实例尚未来得及执行周期 membership 刷新的窗口。
        serverIds.forEach(serverId -> repository.registerServerMembership(serverId, now));
        serverIds.forEach(serverId -> repository.addServer(rolloutId, serverId, now));

        return rolloutId;
    }

    @Override
    @Transactional
    public void activate(String rolloutId, String commitHash) {
        if (!repository.activateRollout(rolloutId, commitHash, Instant.now())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "公共 Agent/Skill 配置发布不再处于准备状态",
                    Map.of("rolloutId", rolloutId));
        }
    }

    @Override
    public void recordExpectedCommit(String rolloutId, String commitHash) {
        if (!repository.recordExpectedCommit(rolloutId, commitHash, Instant.now())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "公共 Agent/Skill 配置发布不再处于准备状态",
                    Map.of("rolloutId", rolloutId));
        }
    }

    @Override
    @Transactional
    public void abortPreparation(String rolloutId, String reason) {
        repository.abortPreparation(rolloutId, safeError(reason), Instant.now());
    }

    @Override
    public Optional<PublicAgentConfigRolloutPreparation> preparing(
            String linuxServerId,
            AgentConfigRolloutScope scope) {
        return repository.findPreparing(linuxServerId, scope);
    }

    @Override
    public Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(
            String linuxServerId,
            AgentConfigRolloutScope scope) {
        Instant now = Instant.now();
        return repository.claimPendingSync(linuxServerId, scope, now, now.plus(SERVER_SYNC_LEASE));
    }

    @Override
    public boolean renewServerSync(PublicAgentConfigRolloutSyncRequest request) {
        Instant now = Instant.now();
        return repository.renewServerSync(
                request.rolloutId(),
                backendInstanceIdentity.linuxServerId(),
                request.leaseToken(),
                now.plus(SERVER_SYNC_LEASE),
                now);
    }

    @Override
    @Transactional
    public void markServerSynced(PublicAgentConfigRolloutSyncRequest request) {
        if (!renewServerSync(request)) {
            return;
        }
        Instant now = Instant.now();
        // 每台服务器完成 Git 更新后才采集本机 manager 进程清单，确保表中对应的是切换窗口内的存量实例。
        snapshotServerTargets(
                request.rolloutId(),
                request.scope(),
                backendInstanceIdentity.linuxServerId(),
                request.traceId(),
                now,
                null);
        markServerSyncedAfterSnapshot(request, now);
    }

    /** 应用级同步只 dispose 已成功更新个人 worktree 的用户，不影响同机其它应用或本地脏配置用户。 */
    @Override
    @Transactional
    public void markServerSyncedForUsers(
            PublicAgentConfigRolloutSyncRequest request,
            Set<String> targetUserIds) {
        if (!renewServerSync(request)) {
            return;
        }
        Instant now = Instant.now();
        Set<String> normalizedUserIds = targetUserIds == null
                ? Set.of()
                : targetUserIds.stream()
                        .filter(userId -> userId != null && !userId.isBlank())
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!normalizedUserIds.isEmpty()) {
            snapshotServerTargets(
                    request.rolloutId(),
                    request.scope(),
                    backendInstanceIdentity.linuxServerId(),
                    request.traceId(),
                    now,
                    normalizedUserIds);
        }
        markServerSyncedAfterSnapshot(request, now);
    }

    private void markServerSyncedAfterSnapshot(PublicAgentConfigRolloutSyncRequest request, Instant now) {
        repository.markServerSynced(
                request.rolloutId(),
                backendInstanceIdentity.linuxServerId(),
                request.leaseToken(),
                now);
        repository.completeReadyRollouts(now);
    }

    @Override
    public void markServerSyncRetry(PublicAgentConfigRolloutSyncRequest request, String errorMessage) {
        Instant now = Instant.now();
        int retryCount = request.retryCount() + 1;
        repository.markServerSyncRetry(
                request.rolloutId(),
                backendInstanceIdentity.linuxServerId(),
                request.leaseToken(),
                retryCount,
                now.plus(retryDelay.multipliedBy(Math.min(retryCount, 6))),
                safeError(errorMessage),
                now);
    }

    /** 每台新版 Java 定期登记自身为发布成员；历史 linux_servers 行不会被自动导入。 */
    @Scheduled(
            fixedDelayString = "${test-agent.public-agent-config.rollout.membership-refresh-delay-ms:30000}",
            initialDelayString = "${test-agent.public-agent-config.rollout.membership-initial-delay-ms:1000}")
    public void registerLocalServerMembership() {
        repository.registerServerMembership(backendInstanceIdentity.linuxServerId(), Instant.now());
    }

    /** 只有已离线服务器可显式退役；退役是永久离开当前集群的运维确认。 */
    @Override
    @Transactional
    public void decommissionServer(String linuxServerId) {
        for (AgentConfigRolloutScope scope : AgentConfigRolloutScope.values()) {
            repository.findPreparing(linuxServerId, scope).ifPresent(preparation -> {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "服务器仍有待确认的共享配置发布，不能退役",
                    Map.of("linuxServerId", linuxServerId, "rolloutId", preparation.rolloutId()));
            });
        }
        boolean currentServer = backendInstanceIdentity.linuxServerId().equals(linuxServerId);
        Set<LinuxServerId> liveBackendServerIds = heartbeatStore.liveBackendServerIds();
        boolean liveBackend = liveBackendServerIds != null
                && liveBackendServerIds.stream().anyMatch(id -> id.value().equals(linuxServerId));
        boolean liveManager = heartbeatStore.liveManagerSnapshots().stream()
                .anyMatch(snapshot -> snapshot.container().linuxServerId().value().equals(linuxServerId));
        if (currentServer || liveBackend || liveManager) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "在线服务器不能退役，请先停止该服务器上的 Java 和 opencode-manager",
                    Map.of("linuxServerId", linuxServerId));
        }
        Instant now = Instant.now();
        repository.decommissionServerMembership(linuxServerId, now);
        repository.completeReadyRollouts(now);
    }

    private void snapshotServerTargets(
            String rolloutId,
            AgentConfigRolloutScope scope,
            String linuxServerId,
            String traceId,
            Instant now,
            Set<String> targetUserIds) {
        List<ManagerRuntimeSnapshot> managers = heartbeatStore.liveManagerSnapshots().stream()
                .filter(manager -> linuxServerId.equals(manager.container().linuxServerId().value()))
                .toList();
        if (managers.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器 manager 进程清单尚未就绪",
                    Map.of("linuxServerId", linuxServerId, "rolloutId", rolloutId));
        }
        Map<ProcessKey, String> usersByProcess = new HashMap<>();
        Map<ProcessLocation, String> usersByLocation = new HashMap<>();
        // 目标登记只允许读取本服务器进程；跨服务器历史脏行既不属于本 worker，也不能阻塞本机排空。
        List<OpencodeServerProcess> localProcesses = processRepository.findOpencodeServerProcesses(
                new OpencodeServerProcessFilter(null, new LinuxServerId(linuxServerId), null, null),
                new PageRequest(1, TOPOLOGY_LIMIT)).items();
        for (OpencodeServerProcess process : localProcesses) {
            usersByLocation.putIfAbsent(
                    new ProcessLocation(process.containerId().value(), process.port()),
                    process.userId().value());
            usersByProcess.putIfAbsent(
                    new ProcessKey(
                            process.linuxServerId().value(),
                            process.containerId().value(),
                            process.port(),
                            process.pid(),
                            normalizedStartedAt(process.startedAt())),
                    process.userId().value());
        }
        for (ManagerRuntimeSnapshot manager : managers) {
            String containerId = manager.container().containerId().value();
            for (ManagedOpencodeProcessSnapshot process : manager.managedProcesses()) {
                String locationUserId = usersByLocation.get(new ProcessLocation(containerId, process.port()));
                if (process.pid() == null
                        || process.pid() <= 0
                        || process.startedAt() == null
                        || process.baseUrl() == null
                        || process.baseUrl().isBlank()) {
                    if (targetUserIds != null
                            && (locationUserId == null || !targetUserIds.contains(locationUserId))) {
                        continue;
                    }
                    throw new PlatformException(
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "目标服务器 manager 进程清单缺少 PID 或启动时间，不能安全 dispose",
                            Map.of("linuxServerId", linuxServerId, "rolloutId", rolloutId));
                }
                String exactUserId = usersByProcess.get(new ProcessKey(
                        linuxServerId,
                        containerId,
                        process.port(),
                        process.pid(),
                        normalizedStartedAt(process.startedAt())));
                if (targetUserIds != null
                        && (exactUserId == null || !targetUserIds.contains(exactUserId))) {
                    if (locationUserId != null && targetUserIds.contains(locationUserId)) {
                        // 端口属于目标用户但精确进程身份尚未收敛时必须重试，不能漏掉本次热加载。
                        throw new PlatformException(
                                ErrorCode.OPENCODE_UNAVAILABLE,
                                "目标用户进程身份尚未收敛，暂不能安全 dispose",
                                Map.of("linuxServerId", linuxServerId, "rolloutId", rolloutId));
                    }
                    continue;
                }
                repository.addTarget(new PublicAgentConfigRolloutTarget(
                        RuntimeIdGenerator.publicAgentConfigRolloutTargetId(),
                        rolloutId,
                        scope,
                        exactUserId,
                        linuxServerId,
                        containerId,
                        process.port(),
                        process.pid(),
                        process.startedAt(),
                        process.baseUrl().trim(),
                        0,
                        null,
                        null,
                        traceId), now);
            }
        }
    }

    private record ProcessLocation(String containerId, int port) {
    }

    @Override
    public MessageGateStatus status(UserId userId) {
        Optional<String> blockingRollout = userId == null
                ? repository.findActiveRolloutId()
                : repository.findBlockingRolloutId(userId.value());
        return blockingRollout
                .map(MessageGateStatus::blocked)
                .orElseGet(MessageGateStatus::open);
    }

    /**
     * 每台 Java 服务只认领本服务器目标；数据库 SKIP LOCKED 与租约保证本机多进程下不重复处理且可恢复。
     */
    @Scheduled(
            fixedDelayString = "${test-agent.public-agent-config.rollout.poll-delay-ms:5000}",
            initialDelayString = "${test-agent.public-agent-config.rollout.initial-delay-ms:5000}")
    public void drainTargets() {
        Instant now = Instant.now();
        List<PublicAgentConfigRolloutTarget> targets = repository.claimTargets(
                backendInstanceIdentity.linuxServerId(),
                now,
                now.plus(TARGET_LEASE),
                CLAIM_LIMIT);
        for (PublicAgentConfigRolloutTarget target : targets) {
            drainTarget(target);
        }
        repository.completeReadyRollouts(Instant.now());
    }

    private void drainTarget(PublicAgentConfigRolloutTarget target) {
        Instant now = Instant.now();
        try {
            if (!renewTargetLease(target)) {
                return;
            }
            if (target.processPid() == null || target.processStartedAt() == null) {
                // 升级前已创建但无法可靠回填身份的 target 必须失败关闭，禁止把端口上的进程误判为已释放。
                retry(target, "TARGET_PROCESS_IDENTITY_MISSING", now);
                return;
            }
            ProcessPresence presence = processPresence(target);
            if (presence == ProcessPresence.UNKNOWN) {
                retry(target, "MANAGER_SNAPSHOT_UNAVAILABLE", now);
                return;
            }
            if (presence == ProcessPresence.ABSENT) {
                // manager 明确确认目标端口已不存在时等同于已经释放，无需向死地址重复调用 dispose。
                repository.markTargetDisposed(target.targetId(), target.leaseToken(), Instant.now());
                return;
            }
            ExecutionNode node = targetNode(target, now);
            List<String> rootPaths = repository.findTargetWorkspaceRootPaths(target.targetId());
            if (!renewTargetLease(target)) {
                return;
            }
            for (String rootPath : rootPaths) {
                if (!renewTargetLease(target)) {
                    return;
                }
                String directory = workspacePathResolver.resolve(rootPath).toString();
                JsonNode sessionStatus = runtime.runtime(new AgentRuntimeCommand(
                                node, "GET", "/session/status", directory, null, Map.of(), null,
                                target.traceId()))
                        .map(AgentRuntimeResult::body)
                        .block(RUNTIME_TIMEOUT);
                if (!renewTargetLease(target)) {
                    return;
                }
                SessionActivity activity = sessionActivity(sessionStatus);
                if (activity == SessionActivity.BUSY) {
                    retry(target, "SESSION_RUNNING", now);
                    return;
                }
                if (activity == SessionActivity.INVALID) {
                    retry(target, "SESSION_STATUS_INVALID", now);
                    return;
                }
            }
            // dispose 前再次续租并核对原进程身份，避免长工作区清单期间端口已被新进程复用。
            if (!renewTargetLease(target) || processPresence(target) != ProcessPresence.PRESENT) {
                retry(target, "PROCESS_IDENTITY_CHANGED", Instant.now());
                return;
            }
            if (!restoreSharedPublicConfig(target)) {
                retry(target, "PROCESS_CONFIG_IDENTITY_CHANGED", Instant.now());
                return;
            }
            JsonNode disposed = runtime.runtime(new AgentRuntimeCommand(
                            node, "POST", "/global/dispose", null, null, Map.of(), Map.of(),
                            target.traceId()))
                    .map(AgentRuntimeResult::body)
                    .block(RUNTIME_TIMEOUT);
            if (!renewTargetLease(target)) {
                return;
            }
            // 只有 opencode 明确返回 true 才确认释放；空值、非布尔或 false 均进入重试，避免误解除闸门。
            if (disposed == null || !disposed.isBoolean() || !disposed.booleanValue()) {
                retry(target, "DISPOSE_REJECTED", now);
                return;
            }
            repository.markTargetDisposed(target.targetId(), target.leaseToken(), Instant.now());
        } catch (Exception exception) {
            retry(target, safeError(exception.getMessage()), now);
        }
    }

    private boolean renewTargetLease(PublicAgentConfigRolloutTarget target) {
        Instant now = Instant.now();
        return repository.renewTargetLease(
                target.targetId(), target.leaseToken(), now.plus(TARGET_LEASE), now);
    }

    /** 应用发布不触碰公共配置指针；公共发布必须在 dispose 前把本人预览切回共享副本。 */
    private boolean restoreSharedPublicConfig(PublicAgentConfigRolloutTarget target) {
        if (target.configScope() != AgentConfigRolloutScope.PUBLIC) {
            return true;
        }
        if (configLinkService == null) {
            // 仅兼容未注入该依赖的旧单元测试；Spring 运行态必须注入。
            return true;
        }
        // manager 快照是当前受管进程身份和实际路径的权威来源。即使平台进程表因历史 PID 为空或
        // startedAt 时间源差异无法映射 userId，只要 PID/启动时间仍与 rollout target 完全一致，
        // 也必须能够先恢复共享配置再 dispose，不能因构造空 UserId 永久卡在 RETRY_WAIT。
        Optional<ManagedOpencodeProcessSnapshot> managedProcess = exactManagedProcess(target);
        if (managedProcess.isPresent()
                && restoreSharedConfigLink(
                        managedProcess.get().sessionPath(), managedProcess.get().configPath())) {
            return true;
        }

        // 兼容尚未上报 sessionPath/configPath 的旧 manager；只有 target 已映射出用户且数据库
        // 进程身份仍完全一致时，才允许复用历史平台进程路径。
        return exactTargetProcess(target)
                .map(process -> restoreSharedConfigLink(process.sessionPath(), process.configPath()))
                .orElse(false);
    }

    /** 只使用仍与 rollout target 精确匹配的 manager 进程，避免端口复用后操作替换实例。 */
    private Optional<ManagedOpencodeProcessSnapshot> exactManagedProcess(PublicAgentConfigRolloutTarget target) {
        return heartbeatStore.liveManagerSnapshots().stream()
                .filter(manager -> target.linuxServerId().equals(manager.container().linuxServerId().value()))
                .filter(manager -> target.containerId().equals(manager.container().containerId().value()))
                .flatMap(manager -> manager.managedProcesses().stream())
                .filter(process -> process.port() == target.port())
                .filter(process -> Objects.equals(process.pid(), target.processPid()))
                .filter(process -> Objects.equals(
                        normalizedStartedAt(process.startedAt()),
                        normalizedStartedAt(target.processStartedAt())))
                .findFirst();
    }

    /** 校验受管路径后恢复共享配置；路径缺失或越界时保持失败关闭并交给定时任务重试。 */
    private boolean restoreSharedConfigLink(String sessionPath, String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return false;
        }
        if (configLinkService.isSharedConfigPath(configPath)) {
            // 升级前启动的进程本来就直接读取共享副本，公共发布只需 dispose。
            return true;
        }
        if (sessionPath == null
                || sessionPath.isBlank()
                || !configLinkService.isManagedConfigPath(sessionPath, configPath)) {
            return false;
        }
        configLinkService.switchToShared(sessionPath, configPath);
        return true;
    }

    private Optional<OpencodeServerProcess> exactTargetProcess(PublicAgentConfigRolloutTarget target) {
        if (target.userId() == null || target.userId().isBlank()) {
            return Optional.empty();
        }
        return processRepository.findUserBinding(new UserId(target.userId()), "opencode")
                .flatMap(binding -> processRepository.findOpencodeServerProcessById(binding.processId()))
                .filter(process -> target.userId().equals(process.userId().value()))
                .filter(process -> target.linuxServerId().equals(process.linuxServerId().value()))
                .filter(process -> target.containerId().equals(process.containerId().value()))
                .filter(process -> target.port() == process.port())
                .filter(process -> Objects.equals(target.processPid(), process.pid()))
                .filter(process -> Objects.equals(
                        normalizedStartedAt(target.processStartedAt()),
                        normalizedStartedAt(process.startedAt())));
    }

    private void retry(PublicAgentConfigRolloutTarget target, String error, Instant now) {
        int retryCount = target.retryCount() + 1;
        long multiplier = Math.min(retryCount, 6);
        repository.markTargetRetry(
                target.targetId(),
                target.leaseToken(),
                retryCount,
                now.plus(retryDelay.multipliedBy(multiplier)),
                safeError(error),
                now);
    }

    private ExecutionNode targetNode(PublicAgentConfigRolloutTarget target, Instant now) {
        return new ExecutionNode(
                new ExecutionNodeId("node_" + target.targetId().replace("act_", "")),
                target.baseUrl(),
                ExecutionNodeStatus.READY,
                0,
                1,
                now);
    }

    /** Session status 必须是 sessionId 到合法状态对象的映射；未知结构一律失败关闭。 */
    private SessionActivity sessionActivity(JsonNode node) {
        if (node == null || !node.isObject()) {
            return SessionActivity.INVALID;
        }
        for (JsonNode status : node) {
            if (!status.isObject() || !status.path("type").isTextual()) {
                return SessionActivity.INVALID;
            }
            String type = status.path("type").textValue();
            if ("busy".equalsIgnoreCase(type) || "retry".equalsIgnoreCase(type)) {
                return SessionActivity.BUSY;
            }
            if (!"idle".equalsIgnoreCase(type)) {
                return SessionActivity.INVALID;
            }
        }
        return SessionActivity.IDLE;
    }

    /** 只有本服务器对应 container 的 manager 快照可以确认目标仍在或已经消失。 */
    private ProcessPresence processPresence(PublicAgentConfigRolloutTarget target) {
        for (ManagerRuntimeSnapshot manager : heartbeatStore.liveManagerSnapshots()) {
            if (!target.linuxServerId().equals(manager.container().linuxServerId().value())
                    || !target.containerId().equals(manager.container().containerId().value())) {
                continue;
            }
            Optional<ManagedOpencodeProcessSnapshot> current = manager.managedProcesses().stream()
                    .filter(process -> process.port() == target.port())
                    .findFirst();
            if (current.isEmpty()) {
                return ProcessPresence.ABSENT;
            }
            ManagedOpencodeProcessSnapshot process = current.get();
            if (process.pid() == null || process.startedAt() == null) {
                return ProcessPresence.UNKNOWN;
            }
            boolean sameIdentity = process.pid().equals(target.processPid())
                    && normalizedStartedAt(process.startedAt())
                            .equals(normalizedStartedAt(target.processStartedAt()));
            return sameIdentity ? ProcessPresence.PRESENT : ProcessPresence.ABSENT;
        }
        return ProcessPresence.UNKNOWN;
    }

    private String safeError(String value) {
        String normalized = value == null || value.isBlank() ? "UNKNOWN" : value.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    /** PostgreSQL timestamptz 按微秒保存，比较前统一精度，避免纳秒截断导致同一进程被误判。 */
    private Instant normalizedStartedAt(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    private record ProcessKey(
            String linuxServerId,
            String containerId,
            int port,
            Long processPid,
            Instant processStartedAt) {
    }

    private enum ProcessPresence {
        PRESENT,
        ABSENT,
        UNKNOWN
    }

    private enum SessionActivity {
        IDLE,
        BUSY,
        INVALID
    }
}
