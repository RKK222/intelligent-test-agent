package com.enterprise.testagent.opencode.runtime.config;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreeClaim;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreePending;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRuntimeImpactResolver;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutPreparation;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTarget;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigWorktreeClaim;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigWorktreePending;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessConfigLinkService;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStopRequest;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStopService;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementCommandService;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Agent 配置发布与个人拉取重载协调器：持久化禁发、登记存量进程，并在 Session 空闲后 dispose。
 */
@Service
public class PublicAgentConfigRolloutService
        implements PublicAgentConfigRolloutCoordinator, PublicAgentConfigMessageGate {

    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final int MAX_DISPOSE_PARALLELISM = 32;
    /** 分页进程仓储单次查询硬上限；当前 manager 容量远低于该值。 */
    private static final int TOPOLOGY_LIMIT = PageRequest.MAX_SIZE;
    private static final Duration TARGET_LEASE = Duration.ofSeconds(60);
    private static final Duration SERVER_SYNC_LEASE = Duration.ofMinutes(3);
    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_SUPERSEDE_REASON_LENGTH = 500;
    /** 兼容旧 Java 用回包观察时间落库造成的毫秒级偏差；超过该窗口仍按替换进程失败关闭。 */
    private static final Duration LEGACY_PROCESS_START_TIME_SKEW = Duration.ofSeconds(1);

    private final PublicAgentConfigRolloutRepository repository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final OpencodeProcessManagementRepository processRepository;
    private final AgentRuntime runtime;
    private final BackendInstanceIdentity backendInstanceIdentity;
    private final ManagedWorkspacePathResolver workspacePathResolver;
    private final Duration retryDelay;
    private final int disposeParallelism;
    private final Scheduler disposeScheduler;
    private OpencodeProcessConfigLinkService configLinkService;
    private OpencodeProcessStopService stopService;
    private PublicAgentConfigRuntimeImpactResolver runtimeImpactResolver;
    private RuntimeManagementCommandService runtimeManagementCommandService;
    private UserNotificationApplicationService notificationService;

    /** 公共发布排空时把个人预览指针恢复到共享运行副本；方法注入保持既有测试构造器兼容。 */
    @Autowired
    void setConfigLinkService(OpencodeProcessConfigLinkService configLinkService) {
        this.configLinkService = Objects.requireNonNull(configLinkService, "configLinkService must not be null");
    }

    /** 纠错发布强制终止目标时必须复用公共停止程序，保持 manager、数据库和 heartbeat 状态一致。 */
    @Autowired
    void setStopService(OpencodeProcessStopService stopService) {
        this.stopService = Objects.requireNonNull(stopService, "stopService must not be null");
    }

    /** Git 提交差异由工作区模块解释，运行时只根据影响结论选择 dispose 或受管重启。 */
    @Autowired(required = false)
    void setRuntimeImpactResolver(PublicAgentConfigRuntimeImpactResolver resolver) {
        this.runtimeImpactResolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /** Tool 脚本发布重启复用运行管理已封装的公共停止、启动与 health 确认。 */
    @Autowired(required = false)
    void setRuntimeManagementCommandService(@Lazy RuntimeManagementCommandService commandService) {
        this.runtimeManagementCommandService = Objects.requireNonNull(
                commandService, "commandService must not be null");
    }

    /** dispose 生命周期只通过通用通知应用服务写入，不让运行时模块直接访问通知仓储。 */
    @Autowired
    void setNotificationService(UserNotificationApplicationService notificationService) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
    }

    @Autowired
    public PublicAgentConfigRolloutService(
            PublicAgentConfigRolloutRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessManagementRepository processRepository,
            AgentRuntimeRegistry runtimeRegistry,
            BackendInstanceIdentity backendInstanceIdentity,
            ManagedWorkspacePathResolver workspacePathResolver,
            @Value("${test-agent.public-agent-config.rollout.retry-delay-ms:5000}") long retryDelayMillis,
            @Value("${test-agent.public-agent-config.rollout.dispose-parallelism:8}") int disposeParallelism) {
        this(
                repository,
                heartbeatStore,
                processRepository,
                runtimeRegistry,
                backendInstanceIdentity,
                workspacePathResolver,
                retryDelayMillis,
                disposeParallelism,
                Schedulers.boundedElastic());
    }

    /** 测试可注入同步或专用调度器，生产统一复用 boundedElastic 承载阻塞式 OpenCode 调用。 */
    PublicAgentConfigRolloutService(
            PublicAgentConfigRolloutRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessManagementRepository processRepository,
            AgentRuntimeRegistry runtimeRegistry,
            BackendInstanceIdentity backendInstanceIdentity,
            ManagedWorkspacePathResolver workspacePathResolver,
            long retryDelayMillis,
            int disposeParallelism,
            Scheduler disposeScheduler) {
        this.repository = repository;
        this.heartbeatStore = heartbeatStore;
        this.processRepository = processRepository;
        this.runtime = runtimeRegistry.require(AgentRuntimeRegistry.DEFAULT_AGENT_ID);
        this.backendInstanceIdentity = backendInstanceIdentity;
        this.workspacePathResolver = workspacePathResolver;
        this.retryDelay = Duration.ofMillis(Math.max(1000L, retryDelayMillis));
        this.disposeParallelism = Math.max(1, Math.min(MAX_DISPOSE_PARALLELISM, disposeParallelism));
        this.disposeScheduler = Objects.requireNonNull(disposeScheduler, "disposeScheduler must not be null");
    }

    /** 在任何远端 push 或共享运行副本切换前建立 PREPARING 闸门。 */
    @Override
    @Transactional
    public String prepare(
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            boolean discardSharedRuntimeChanges,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId) {
        return prepareRollout(
                AgentConfigRolloutScope.PUBLIC,
                null,
                branch,
                expectedCommitHash,
                previousCommitHash,
                discardSharedRuntimeChanges,
                localLinuxServerId,
                initiatedByUserId,
                traceId);
    }

    @Override
    @Transactional
    public String supersede(
            String activeRolloutId,
            String branch,
            String commitHash,
            String previousCommitHash,
            boolean discardSharedRuntimeChanges,
            String reason,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId) {
        String expectedActiveRolloutId = requireText(activeRolloutId, "待替换 rolloutId 不能为空");
        String normalizedBranch = requireText(branch, "纠错发布分支不能为空");
        String normalizedCommitHash = requireText(commitHash, "纠错发布目标提交不能为空");
        String normalizedReason = requireText(reason, "纠错发布原因不能为空");
        if (normalizedReason.length() > MAX_SUPERSEDE_REASON_LENGTH) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "纠错发布原因不能超过 " + MAX_SUPERSEDE_REASON_LENGTH + " 个字符");
        }
        String targetServer = requireText(localLinuxServerId, "纠错发布缺少发起服务器");
        String replacementRolloutId = RuntimeIdGenerator.publicAgentConfigRolloutId();
        Instant now = Instant.now();
        Set<String> supersededUsers = pendingUserIds(expectedActiveRolloutId);

        Set<String> serverIds = new LinkedHashSet<>(repository.findRolloutServerIds(expectedActiveRolloutId));
        serverIds.add(targetServer);
        serverIds.addAll(repository.findActiveServerMembershipIds());
        Set<LinuxServerId> liveBackendServerIds = heartbeatStore.liveBackendServerIds();
        if (liveBackendServerIds != null) {
            liveBackendServerIds.forEach(serverId -> serverIds.add(serverId.value()));
        }
        heartbeatStore.liveManagerSnapshots()
                .forEach(snapshot -> serverIds.add(snapshot.container().linuxServerId().value()));
        serverIds.forEach(serverId -> repository.registerServerMembership(serverId, now));

        boolean replaced = repository.supersedePublicRollout(
                expectedActiveRolloutId,
                replacementRolloutId,
                normalizedBranch,
                normalizedCommitHash,
                previousCommitHash,
                discardSharedRuntimeChanges,
                requireText(initiatedByUserId, "纠错发布发起用户不能为空"),
                targetServer,
                traceId,
                normalizedReason,
                List.copyOf(serverIds),
                now);
        if (!replaced) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "待替换的公共 Agent/Skill 发布已变化或不再处于排空状态",
                    Map.of("rolloutId", expectedActiveRolloutId));
        }
        supersededUsers.forEach(userId -> notifyDispose(
                userId,
                expectedActiveRolloutId,
                UserNotificationType.AGENT_CONFIG_DISPOSE_SUPERSEDED,
                traceId));
        return replacementRolloutId;
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
                false,
                localLinuxServerId,
                initiatedByUserId,
                traceId);
    }

    /**
     * 个人拉取不建立全应用服务器成员，只复用既有 server/target 租约链登记当前用户。
     * server 行先持久化进程快照重试；成功后 target 继续复用统一空闲检查和 dispose。
     */
    @Override
    @Transactional
    public Optional<String> schedulePersonalApplicationReload(
            String personalWorkspaceId,
            String branch,
            String commitHash,
            String localLinuxServerId,
            String userId,
            String traceId) {
        String targetServer = requireText(localLinuxServerId, "个人应用 Agent 重载缺少服务器归属");
        if (!backendInstanceIdentity.linuxServerId().equals(targetServer)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "个人应用 Agent 重载必须在当前用户进程所属服务器登记",
                    Map.of("linuxServerId", targetServer));
        }
        if (runningUserProcess(userId, targetServer, true).isEmpty()) {
            return Optional.empty();
        }

        String rolloutId = RuntimeIdGenerator.publicAgentConfigRolloutId();
        Instant now = Instant.now();
        repository.createRollout(
                rolloutId,
                AgentConfigRolloutScope.PERSONAL_APPLICATION,
                requireText(personalWorkspaceId, "个人工作区 ID 不能为空"),
                requireText(branch, "个人工作区分支不能为空"),
                requireText(commitHash, "个人工作区提交不能为空"),
                commitHash,
                false,
                requireText(userId, "用户 ID 不能为空"),
                targetServer,
                traceId,
                now);
        // 单用户任务只登记当前服务器；不能复用 prepareApplication 的全成员枚举。
        repository.addServer(rolloutId, targetServer, now);
        if (!repository.activateRollout(rolloutId, commitHash, now)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "个人应用 Agent 运行态重载登记失败",
                    Map.of("rolloutId", rolloutId));
        }
        return Optional.of(rolloutId);
    }

    private String prepareRollout(
            AgentConfigRolloutScope scope,
            String scopeKey,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            boolean discardSharedRuntimeChanges,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId) {
        repository.findActiveRolloutId(scope, scopeKey).ifPresent(active -> {
            String message = scope == AgentConfigRolloutScope.PUBLIC
                    ? "已有公共 Agent/Skill 配置发布正在排空"
                    : "当前应用版本已有 Agent/Skill 配置发布正在收敛";
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    message,
                    Map.of(
                            "rolloutId", active,
                            "configScope", scope.name(),
                            "scopeKey", scopeKey == null ? "PUBLIC" : scopeKey));
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
                discardSharedRuntimeChanges,
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
    public Optional<PublicAgentConfigRolloutStatus> latestPublicRolloutStatus() {
        return repository.findLatestRolloutStatus(AgentConfigRolloutScope.PUBLIC, null)
                .map(status -> status.withServers(
                        repository.findRolloutServerStatuses(status.rolloutId())));
    }

    @Override
    public List<PublicAgentConfigRolloutStatus> recentApplicationRolloutStatuses() {
        return repository.findRecentRolloutStatuses(AgentConfigRolloutScope.APPLICATION, 20).stream()
                .map(status -> status.withServers(
                        repository.findRolloutServerStatuses(status.rolloutId())))
                .toList();
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
        markServerSyncedForUsers(request, targetUserIds, List.of());
    }

    @Override
    @Transactional
    public void markServerSyncedForUsers(
            PublicAgentConfigRolloutSyncRequest request,
            Set<String> targetUserIds,
            List<AgentConfigRolloutWorktreePending> pendingWorktrees) {
        if (!renewServerSync(request)) {
            return;
        }
        Instant now = Instant.now();
        repository.savePendingApplicationWorktrees(
                request.rolloutId(),
                backendInstanceIdentity.linuxServerId(),
                request.commitHash(),
                request.traceId(),
                pendingWorktrees,
                now);
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

    /** 公共运行副本继续对全用户 dispose；个人 worktree 冲突单独持久化，不拖住主排空。 */
    @Override
    @Transactional
    public void markPublicServerSynced(
            PublicAgentConfigRolloutSyncRequest request,
            List<PublicAgentConfigWorktreePending> pendingWorktrees) {
        if (!renewServerSync(request)) {
            return;
        }
        Instant now = Instant.now();
        repository.savePendingPublicWorktrees(
                request.rolloutId(),
                backendInstanceIdentity.linuxServerId(),
                request.commitHash(),
                request.traceId(),
                pendingWorktrees,
                now);
        snapshotServerTargets(
                request.rolloutId(),
                request.scope(),
                backendInstanceIdentity.linuxServerId(),
                request.traceId(),
                now,
                null);
        markServerSyncedAfterSnapshot(request, now);
    }

    @Override
    public Optional<PublicAgentConfigWorktreeClaim> claimPendingPublicWorktree(String linuxServerId) {
        Instant now = Instant.now();
        return repository.claimPendingPublicWorktree(
                linuxServerId, now, now.plus(SERVER_SYNC_LEASE));
    }

    @Override
    public void markPublicWorktreeRetry(PublicAgentConfigWorktreeClaim claim, String reason) {
        Instant now = Instant.now();
        int retryCount = claim.retryCount() + 1;
        repository.markPublicWorktreeRetry(
                claim,
                retryCount,
                now.plus(retryDelay.multipliedBy(Math.min(retryCount, 6))),
                safeError(reason),
                now);
    }

    @Override
    public void markPublicWorktreeSynchronized(PublicAgentConfigWorktreeClaim claim) {
        repository.markPublicWorktreeSynchronized(claim, Instant.now());
    }

    @Override
    public void abandonPublicWorktree(PublicAgentConfigWorktreeClaim claim, String reason) {
        repository.abandonPublicWorktree(claim, safeError(reason), Instant.now());
    }

    @Override
    public Optional<AgentConfigRolloutWorktreeClaim> claimPendingApplicationWorktree(String linuxServerId) {
        Instant now = Instant.now();
        return repository.claimPendingApplicationWorktree(
                linuxServerId, now, now.plus(SERVER_SYNC_LEASE));
    }

    @Override
    public void markApplicationWorktreeRetry(AgentConfigRolloutWorktreeClaim claim, String reason) {
        Instant now = Instant.now();
        int retryCount = claim.retryCount() + 1;
        repository.markApplicationWorktreeRetry(
                claim,
                retryCount,
                now.plus(retryDelay.multipliedBy(Math.min(retryCount, 6))),
                safeError(reason),
                now);
    }

    @Override
    @Transactional
    public void markApplicationWorktreeSynchronized(AgentConfigRolloutWorktreeClaim claim) {
        Instant now = Instant.now();
        if (!repository.markApplicationWorktreeSynchronized(claim, now)) {
            return;
        }
        // 同一用户可能有多个个人 worktree；全部包含目标提交后才登记一次旧进程 dispose，避免提前清缓存。
        if (!repository.hasIncompleteApplicationWorktrees(
                claim.rolloutId(), claim.linuxServerId(), claim.userId())) {
            snapshotServerTargets(
                    claim.rolloutId(),
                    AgentConfigRolloutScope.APPLICATION,
                    claim.linuxServerId(),
                    claim.traceId(),
                    now,
                    Set.of(claim.userId()));
        }
    }

    @Override
    public void abandonApplicationWorktree(AgentConfigRolloutWorktreeClaim claim, String reason) {
        repository.abandonApplicationWorktree(claim, safeError(reason), Instant.now());
    }

    private void markServerSyncedAfterSnapshot(PublicAgentConfigRolloutSyncRequest request, Instant now) {
        repository.markServerSynced(
                request.rolloutId(),
                backendInstanceIdentity.linuxServerId(),
                request.leaseToken(),
                now);
        repository.completeReadyRollouts(now);
    }

    /**
     * 个人拉取的 Git merge 已在请求线程完成；此处只持久化捕获当前用户进程，不执行任何仓库同步。
     */
    @Scheduled(
            fixedDelayString = "${test-agent.public-agent-config.rollout.poll-delay-ms:5000}",
            initialDelayString = "${test-agent.public-agent-config.rollout.initial-delay-ms:5000}")
    public void registerPersonalApplicationReloadTargets() {
        repository.claimPendingSync(
                        backendInstanceIdentity.linuxServerId(),
                        AgentConfigRolloutScope.PERSONAL_APPLICATION,
                        Instant.now(),
                        Instant.now().plus(SERVER_SYNC_LEASE))
                .ifPresent(this::registerPersonalApplicationReloadTarget);
    }

    private void registerPersonalApplicationReloadTarget(PublicAgentConfigRolloutSyncRequest request) {
        try {
            if (!renewServerSync(request)) {
                return;
            }
            Instant now = Instant.now();
            String userId = request.initiatedByUserId();
            // 用户进程已停止或解绑时无需 dispose；下次受管启动会直接读取最新 worktree。
            if (runningUserProcess(userId, backendInstanceIdentity.linuxServerId(), false).isPresent()) {
                snapshotServerTargets(
                        request.rolloutId(),
                        AgentConfigRolloutScope.PERSONAL_APPLICATION,
                        backendInstanceIdentity.linuxServerId(),
                        request.traceId(),
                        now,
                        Set.of(userId));
            }
            markServerSyncedAfterSnapshot(request, now);
        } catch (Exception exception) {
            markServerSyncRetry(request, safeError(exception.getMessage()));
        }
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
        List<PublicAgentConfigRolloutTarget> abandonedTargets = repository.findPendingTargetsByServer(linuxServerId);
        repository.decommissionServerMembership(linuxServerId, now);
        Set<String> notified = new LinkedHashSet<>();
        for (PublicAgentConfigRolloutTarget target : abandonedTargets) {
            String notificationKey = target.rolloutId() + ":" + target.userId();
            if (notified.add(notificationKey)) {
                notifyDispose(target, UserNotificationType.AGENT_CONFIG_DISPOSE_SUPERSEDED);
            }
        }
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
        Map<ProcessLocation, OpencodeServerProcess> processesByLocation = new HashMap<>();
        // 目标登记只允许读取本服务器进程；跨服务器历史脏行既不属于本 worker，也不能阻塞本机排空。
        List<OpencodeServerProcess> localProcesses = processRepository.findOpencodeServerProcesses(
                new OpencodeServerProcessFilter(null, new LinuxServerId(linuxServerId), null, null),
                new PageRequest(1, TOPOLOGY_LIMIT)).items();
        for (OpencodeServerProcess process : localProcesses) {
            processesByLocation.putIfAbsent(
                    new ProcessLocation(process.containerId().value(), process.port()),
                    process);
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
                OpencodeServerProcess locationProcess = processesByLocation.get(
                        new ProcessLocation(containerId, process.port()));
                String locationUserId = locationProcess == null ? null : locationProcess.userId().value();
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
                        if (sameLegacyProcessIdentity(locationProcess, process)) {
                            // 旧 Java 把 manager 回包到达时间写成 startedAt；只在完整坐标、PID、用户归属
                            // 均一致且偏差不超过一秒时，使用 manager 权威时间建立本次 target。
                            exactUserId = locationUserId;
                        } else {
                            // 端口属于目标用户但精确进程身份尚未收敛时必须重试，不能漏掉本次热加载。
                            throw new PlatformException(
                                    ErrorCode.OPENCODE_UNAVAILABLE,
                                    "目标用户进程身份尚未收敛，暂不能安全 dispose",
                                    Map.of("linuxServerId", linuxServerId, "rolloutId", rolloutId));
                        }
                    }
                    if (exactUserId == null || !targetUserIds.contains(exactUserId)) {
                        continue;
                    }
                }
                PublicAgentConfigRolloutTarget target = new PublicAgentConfigRolloutTarget(
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
                        traceId);
                repository.addTarget(target, now);
                notifyDispose(
                        target,
                        UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING);
            }
        }
    }

    private record ProcessLocation(String containerId, int port) {
    }

    /** 个人拉取只认当前 ACTIVE binding 对应的 RUNNING 进程，不能扫描或 dispose 其他用户。 */
    private Optional<OpencodeServerProcess> runningUserProcess(
            String userId,
            String linuxServerId,
            boolean rejectOtherServer) {
        UserId targetUser = new UserId(requireText(userId, "用户 ID 不能为空"));
        Optional<UserOpencodeProcessBinding> binding = processRepository
                .findUserBinding(targetUser, OPENCODE_AGENT_ID)
                .filter(candidate -> candidate.status() == UserOpencodeProcessBindingStatus.ACTIVE);
        Optional<OpencodeServerProcess> process = binding
                .flatMap(candidate -> processRepository.findOpencodeServerProcessById(candidate.processId()))
                .filter(candidate -> targetUser.equals(candidate.userId()))
                .filter(candidate -> binding
                        .filter(active -> active.linuxServerId().equals(candidate.linuxServerId()))
                        .filter(active -> active.port() == candidate.port())
                        .isPresent())
                .filter(candidate -> candidate.status() == OpencodeServerProcessStatus.RUNNING);
        if (rejectOtherServer
                && process.isPresent()
                && !linuxServerId.equals(process.get().linuxServerId().value())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "个人工作区与当前用户 TestAgent 进程不在同一服务器",
                    Map.of(
                            "worktreeLinuxServerId", linuxServerId,
                            "processLinuxServerId", process.get().linuxServerId().value()));
        }
        return process.filter(candidate -> linuxServerId.equals(candidate.linuxServerId().value()));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private boolean sameLegacyProcessIdentity(
            OpencodeServerProcess platformProcess,
            ManagedOpencodeProcessSnapshot managerProcess) {
        if (platformProcess == null
                || platformProcess.pid() == null
                || managerProcess.pid() == null
                || !platformProcess.pid().equals(managerProcess.pid())
                || platformProcess.startedAt() == null
                || managerProcess.startedAt() == null) {
            return false;
        }
        Duration skew = Duration.between(
                normalizedStartedAt(platformProcess.startedAt()),
                normalizedStartedAt(managerProcess.startedAt())).abs();
        return skew.compareTo(LEGACY_PROCESS_START_TIME_SKEW) <= 0;
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
     * 不同 OpenCode 进程并行排空，同一进程的多个配置目标保持串行，避免重复 dispose 或重启相互干扰。
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
                disposeParallelism);
        drainTargetsInParallel(targets);
        repository.completeReadyRollouts(Instant.now());
    }

    /** 以精确进程代次分组后有界并行；共享 boundedElastic 由 Reactor 统一管理线程生命周期。 */
    private void drainTargetsInParallel(List<PublicAgentConfigRolloutTarget> targets) {
        Map<ProcessKey, List<PublicAgentConfigRolloutTarget>> targetsByProcess = new HashMap<>();
        for (PublicAgentConfigRolloutTarget target : targets) {
            ProcessKey processKey = new ProcessKey(
                    target.linuxServerId(),
                    target.containerId(),
                    target.port(),
                    target.processPid(),
                    normalizedStartedAt(target.processStartedAt()));
            targetsByProcess.computeIfAbsent(processKey, ignored -> new ArrayList<>()).add(target);
        }
        Flux.fromIterable(targetsByProcess.values())
                .flatMap(targetsForProcess -> Mono.fromRunnable(
                                () -> targetsForProcess.forEach(this::drainTarget))
                        .subscribeOn(disposeScheduler), disposeParallelism)
                .then()
                .block();
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
            boolean processRestartRequired = !target.forceStop() && requiresProcessRestart(target);
            if (presence == ProcessPresence.ABSENT) {
                if (processRestartRequired) {
                    resumeToolModuleRestart(target, now);
                    return;
                }
                // manager 明确确认目标端口已不存在时等同于已经释放，无需向死地址重复调用 dispose。
                markTargetDisposed(target);
                return;
            }
            if (target.forceStop()) {
                forceStopSupersededTarget(target, now);
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
            if (processRestartRequired) {
                restartToolModuleTarget(target, now);
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
            markTargetDisposed(target);
        } catch (Exception exception) {
            retry(target, safeError(exception.getMessage()), now);
        }
    }

    private boolean requiresProcessRestart(PublicAgentConfigRolloutTarget target) {
        return runtimeImpactResolver != null
                && (target.configScope() == AgentConfigRolloutScope.PUBLIC
                        || target.configScope() == AgentConfigRolloutScope.APPLICATION)
                && runtimeImpactResolver.requiresProcessRestart(
                        target.configScope(), target.scopeKey(), target.previousCommitHash(), target.commitHash());
    }

    /**
     * Tool 模块 URL 被 Node/Bun 的进程级 ESM 缓存后，原生 dispose 无法重新导入同一路径；
     * 这里固定旧进程身份后复用公共受管重启，并强制新进程从共享发布副本启动。
     */
    private void restartToolModuleTarget(PublicAgentConfigRolloutTarget target, Instant now) {
        if (runtimeManagementCommandService == null) {
            retry(target, "PROCESS_RESTART_SERVICE_UNAVAILABLE", now);
            return;
        }
        Optional<OpencodeServerProcess> process = exactTargetProcess(target);
        if (process.isEmpty()) {
            retry(target, "PROCESS_IDENTITY_CHANGED", now);
            return;
        }
        runtimeManagementCommandService.restartTrackedProcess(
                process.get(), target.traceId(), requiresSharedPublicConfig(target));
        completeToolRestartAfterCatalogProbe(target, now);
    }

    /**
     * 停止成功、重新拉起失败时旧 manager 快照已经消失；根据同一用户当前 binding 恢复启动，
     * 已由本次操作拉起且仍指向共享副本的实例则直接幂等收口。
     */
    private void resumeToolModuleRestart(PublicAgentConfigRolloutTarget target, Instant now) {
        if (runtimeManagementCommandService == null || target.userId() == null || target.userId().isBlank()) {
            retry(target, "PROCESS_RESTART_RECOVERY_UNAVAILABLE", now);
            return;
        }
        Optional<OpencodeServerProcess> bound = currentBoundProcess(target);
        if (bound.isEmpty()) {
            // 用户已经解绑或迁移到其它坐标；旧实例消失后下次受管启动会读取已同步配置。
            markTargetDisposed(target);
            return;
        }
        OpencodeServerProcess process = bound.get();
        if (!target.linuxServerId().equals(process.linuxServerId().value())
                || !target.containerId().equals(process.containerId().value())
                || target.port() != process.port()) {
            markTargetDisposed(target);
            return;
        }
        if (process.status() == OpencodeServerProcessStatus.RUNNING
                && !Objects.equals(
                        normalizedStartedAt(target.processStartedAt()),
                        normalizedStartedAt(process.startedAt()))) {
            boolean configReady = !requiresSharedPublicConfig(target)
                    || (configLinkService != null
                            && configLinkService.isLinkedToShared(process.sessionPath(), process.configPath()));
            if (configReady) {
                if (toolCatalogHealthy(target, process)) {
                    markTargetDisposed(target);
                } else {
                    retry(target, "TOOL_CATALOG_INVALID", now);
                }
                return;
            }
        }
        if (process.status() == OpencodeServerProcessStatus.RUNNING
                && !currentProcessSessionsIdle(target, process, now)) {
            return;
        }
        if (requiresSharedPublicConfig(target)
                && !restoreSharedConfigLink(process.sessionPath(), process.configPath())) {
            retry(target, "PROCESS_CONFIG_IDENTITY_CHANGED", now);
            return;
        }
        runtimeManagementCommandService.restartTrackedProcess(
                process, target.traceId(), requiresSharedPublicConfig(target));
        completeToolRestartAfterCatalogProbe(target, now);
    }

    /**
     * 受管重启完成后必须用新进程读取一次 Tool 目录；仅 health 正常不足以证明 TS/JS 导入成功。
     * 失败目标不置终态，交给 {@link #drainTargets()} 的周期任务持续补偿并暴露给管理员。
     */
    private void completeToolRestartAfterCatalogProbe(PublicAgentConfigRolloutTarget target, Instant now) {
        Optional<OpencodeServerProcess> replacement = currentBoundProcess(target)
                .filter(process -> process.status() == OpencodeServerProcessStatus.RUNNING)
                .filter(process -> !Objects.equals(
                        normalizedStartedAt(target.processStartedAt()),
                        normalizedStartedAt(process.startedAt())));
        if (replacement.isEmpty()) {
            retry(target, "PROCESS_RESTART_NOT_OBSERVED", now);
            return;
        }
        if (!toolCatalogHealthy(target, replacement.get())) {
            retry(target, "TOOL_CATALOG_INVALID", now);
            return;
        }
        markTargetDisposed(target);
    }

    /** 对本次发布实际关联过的每个 workspace 查询 Tool ID，数组响应表示新进程已完成模块目录装载。 */
    private boolean toolCatalogHealthy(PublicAgentConfigRolloutTarget target, OpencodeServerProcess process) {
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_" + target.targetId().replace("act_", "") + "_tool_audit"),
                process.baseUrl(),
                ExecutionNodeStatus.READY,
                0,
                1,
                Instant.now());
        List<String> rootPaths = repository.findTargetWorkspaceRootPaths(target.targetId());
        if (rootPaths.isEmpty()) {
            JsonNode tools = runtime.runtime(new AgentRuntimeCommand(
                            node, "GET", "/experimental/tool/ids", null, null, Map.of(), null,
                            target.traceId()))
                    .map(AgentRuntimeResult::body)
                    .block(RUNTIME_TIMEOUT);
            return tools != null && tools.isArray() && !tools.isEmpty();
        }
        for (String rootPath : rootPaths) {
            if (!renewTargetLease(target)) {
                return false;
            }
            String directory = workspacePathResolver.resolve(rootPath).toString();
            JsonNode tools = runtime.runtime(new AgentRuntimeCommand(
                            node, "GET", "/experimental/tool/ids", directory, null, Map.of(), null,
                            target.traceId()))
                    .map(AgentRuntimeResult::body)
                    .block(RUNTIME_TIMEOUT);
            if (tools == null || !tools.isArray() || tools.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean requiresSharedPublicConfig(PublicAgentConfigRolloutTarget target) {
        return target.configScope() == AgentConfigRolloutScope.PUBLIC;
    }

    /**
     * 重启恢复期间可能已有新代次进程占用原 binding；仍按发布目标的工作区逐一确认空闲，
     * 禁止仅凭旧 manager 快照消失就打断新进程中的会话。
     */
    private boolean currentProcessSessionsIdle(
            PublicAgentConfigRolloutTarget target,
            OpencodeServerProcess process,
            Instant now) {
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_" + target.targetId().replace("act_", "") + "_recovery"),
                process.baseUrl(),
                ExecutionNodeStatus.READY,
                0,
                1,
                now);
        for (String rootPath : repository.findTargetWorkspaceRootPaths(target.targetId())) {
            if (!renewTargetLease(target)) {
                return false;
            }
            String directory = workspacePathResolver.resolve(rootPath).toString();
            JsonNode status = runtime.runtime(new AgentRuntimeCommand(
                            node, "GET", "/session/status", directory, null, Map.of(), null, target.traceId()))
                    .map(AgentRuntimeResult::body)
                    .block(RUNTIME_TIMEOUT);
            if (!renewTargetLease(target)) {
                return false;
            }
            SessionActivity activity = sessionActivity(status);
            if (activity == SessionActivity.BUSY) {
                retry(target, "SESSION_RUNNING", now);
                return false;
            }
            if (activity == SessionActivity.INVALID) {
                retry(target, "SESSION_STATUS_INVALID", now);
                return false;
            }
        }
        return true;
    }

    private Optional<OpencodeServerProcess> currentBoundProcess(PublicAgentConfigRolloutTarget target) {
        return processRepository.findUserBinding(new UserId(target.userId()), OPENCODE_AGENT_ID)
                .flatMap(binding -> processRepository.findOpencodeServerProcessById(binding.processId()))
                .filter(process -> target.userId().equals(process.userId().value()));
    }

    private boolean renewTargetLease(PublicAgentConfigRolloutTarget target) {
        Instant now = Instant.now();
        return repository.renewTargetLease(
                target.targetId(), target.leaseToken(), now.plus(TARGET_LEASE), now);
    }

    /**
     * 只强制停止被替换发布中仍未排空、且 PID/startedAt 与新快照精确一致的进程。
     * 不调用 /session/status；公共停止服务会再次固定数据库代次、按 owner+PID 停止并确认 health 已不可达。
     */
    private void forceStopSupersededTarget(PublicAgentConfigRolloutTarget target, Instant now) {
        if (stopService == null) {
            retry(target, "PROCESS_STOP_SERVICE_UNAVAILABLE", now);
            return;
        }
        Optional<OpencodeServerProcess> process = exactTargetProcess(target);
        if (process.isEmpty()) {
            retry(target, "PROCESS_IDENTITY_CHANGED", now);
            return;
        }
        stopService.stopAndVerify(OpencodeProcessStopRequest.tracked(process.get(), target.traceId()));
        markTargetDisposed(target);
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
        boolean updated = repository.markTargetRetry(
                target.targetId(),
                target.leaseToken(),
                retryCount,
                now.plus(retryDelay.multipliedBy(multiplier)),
                safeError(error),
                now);
        if (updated) {
            // lease 条件更新成功才允许推进通知，避免迟到 worker 覆盖新一轮 rollout 状态。
            notifyDispose(
                    target,
                    "SESSION_RUNNING".equals(error)
                            ? UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING
                            : UserNotificationType.AGENT_CONFIG_DISPOSE_FAILED);
        }
    }

    /** 只有持有当前 lease 的目标完成写回后才发送成功通知，避免迟到 worker 覆盖新状态。 */
    private void markTargetDisposed(PublicAgentConfigRolloutTarget target) {
        if (repository.markTargetDisposed(target.targetId(), target.leaseToken(), Instant.now())) {
            notifyDispose(target, UserNotificationType.AGENT_CONFIG_DISPOSE_SUCCEEDED);
        }
    }

    private void notifyDispose(PublicAgentConfigRolloutTarget target, UserNotificationType type) {
        notifyDispose(target.userId(), target.rolloutId(), type, target.traceId());
    }

    /** 未映射到平台用户的历史 manager 进程没有合法通知接收人，继续只做安全 dispose。 */
    private void notifyDispose(String userId, String rolloutId, UserNotificationType type, String traceId) {
        if (notificationService == null || userId == null || userId.isBlank()) {
            return;
        }
        notificationService.syncAgentConfigDispose(new UserId(userId), rolloutId, type, traceId);
    }

    private Set<String> pendingUserIds(String rolloutId) {
        Set<String> userIds = new LinkedHashSet<>();
        repository.findRolloutServerStatuses(rolloutId).forEach(server ->
                server.pendingTargets().forEach(target -> {
                    if (target.userId() != null && !target.userId().isBlank()) {
                        userIds.add(target.userId());
                    }
                }));
        return Set.copyOf(userIds);
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
