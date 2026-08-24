package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.common.localclient.LocalClientUpdateDirection;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.notification.UserNotification;
import com.enterprise.testagent.domain.notification.UserNotificationActionType;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.notification.UserNotificationRepository;
import com.enterprise.testagent.domain.notification.UserNotificationStatus;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.notification.LocalClientUpdateNotificationInvalidationService;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** rollout 快照、跨节点唤醒、两阶段授权和 attempt 幂等状态机。 */
@Service
public class LocalClientUpdateCoordinator implements ServerBroadcastHandler {

    public static final String BROADCAST_TYPE = "local-client-update.wakeup";
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientUpdateCoordinator.class);
    private static final int DISPATCH_BATCH_SIZE = 100;
    private static final int MAX_DISPATCH_PAGES = 10;
    private static final Duration DELIVERY_DEADLINE = Duration.ofMinutes(30);

    private final LocalClientVersionRepository versionRepository;
    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final LocalClientConnectionRegistry connectionRegistry;
    private final ServerBroadcastPublisher broadcastPublisher;
    private final BackendInstanceIdentity identity;
    private final UserNotificationApplicationService notifications;
    private final UserNotificationRepository notificationRepository;
    private final LocalClientUpdateNotificationInvalidationService notificationInvalidation;
    private final LocalClientUpdateTerminalService terminalService;
    private final Clock clock;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();
    private final AtomicBoolean dispatching = new AtomicBoolean();

    @Autowired
    public LocalClientUpdateCoordinator(
            LocalClientVersionRepository versionRepository,
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientConnectionRegistry connectionRegistry,
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            UserNotificationApplicationService notifications,
            UserNotificationRepository notificationRepository,
            LocalClientUpdateNotificationInvalidationService notificationInvalidation,
            LocalClientUpdateTerminalService terminalService) {
        this(
                versionRepository,
                instanceRepository,
                connectionStore,
                connectionRegistry,
                broadcastPublisher,
                identity,
                notifications,
                notificationRepository,
                notificationInvalidation,
                terminalService,
                Clock.systemUTC());
    }

    LocalClientUpdateCoordinator(
            LocalClientVersionRepository versionRepository,
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientConnectionRegistry connectionRegistry,
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity,
            UserNotificationApplicationService notifications,
            UserNotificationRepository notificationRepository,
            LocalClientUpdateNotificationInvalidationService notificationInvalidation,
            LocalClientUpdateTerminalService terminalService,
            Clock clock) {
        this.versionRepository = Objects.requireNonNull(versionRepository);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry);
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher);
        this.identity = Objects.requireNonNull(identity);
        this.notifications = Objects.requireNonNull(notifications);
        this.notificationRepository = Objects.requireNonNull(notificationRepository);
        this.notificationInvalidation = Objects.requireNonNull(notificationInvalidation);
        this.terminalService = Objects.requireNonNull(terminalService);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 管理员 rollout 只快照点击时在线实例，并为每个实例冻结当时的有效策略。 */
    @Transactional
    public RolloutResult createRollout(
            LocalClientVersionModels.RolloutScope scope,
            UserId requestedUserId,
            UserId actorUserId,
            String traceId) {
        if (scope == LocalClientVersionModels.RolloutScope.USER && requestedUserId == null) {
            throw validation("指定用户 rollout 缺少 userId");
        }
        if (scope == LocalClientVersionModels.RolloutScope.ALL_ONLINE && requestedUserId != null) {
            throw validation("全量 rollout 不允许携带 userId");
        }
        List<LocalClientInstance> candidates = instanceRepository.findAll().stream()
                .filter(instance -> requestedUserId == null || instance.userId().equals(requestedUserId))
                .toList();
        return persistRollout(scope, requestedUserId, actorUserId, candidates, null, null, traceId);
    }

    /** 用户通知动作严格只为该实例建 attempt，不扩大到同用户其它设备。 */
    @Transactional
    public RolloutResult createUserRequestedUpdate(
            UserId actorUserId,
            LocalClientInstanceId clientInstanceId,
            UserNotificationId notificationId,
            String expectedTargetVersion,
            String traceId) {
        LocalClientInstance instance = requireOwned(actorUserId, clientInstanceId);
        UserNotification notification = notificationRepository.findByIdForRecipient(notificationId, actorUserId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "更新通知不存在"));
        if (notification.type() != UserNotificationType.LOCAL_CLIENT_UPDATE_AVAILABLE
                || notification.actionType() != UserNotificationActionType.LOCAL_CLIENT_UPDATE
                || notification.status() != UserNotificationStatus.ACTIVE
                || !notification.actionTargetId().equals(clientInstanceId.value())) {
            throw staleNotification(actorUserId, notificationId, traceId);
        }
        LocalClientVersionModels.EffectivePolicy effective = effectivePolicy(actorUserId);
        if (effective.targetVersion() == null
                || !effective.targetVersion().equals(expectedTargetVersion)
                || !notification.dedupKey().endsWith(
                        ":" + expectedTargetVersion + ":" + effective.policyRevision())) {
            throw staleNotification(actorUserId, notificationId, traceId);
        }
        LocalClientConnectionRoute clickedRoute = connectionStore.find(clientInstanceId)
                .filter(route -> route.userId().equals(actorUserId))
                .orElse(null);
        if (clickedRoute == null) {
            notificationInvalidation.invalidate(
                    actorUserId, notificationId, "OFFLINE_OR_STALE_GENERATION", traceId);
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端已离线、换代或不支持自更新");
        }
        RolloutResult result = persistRollout(
                LocalClientVersionModels.RolloutScope.USER,
                actorUserId,
                actorUserId,
                List.of(instance),
                clientInstanceId,
                clickedRoute.connectionGeneration(),
                traceId);
        if (result.attemptCount() != 1) {
            // Redis 在线代次可能在通知校验后立即变化；精确失效本次点击通知并独立提交。
            notificationInvalidation.invalidate(actorUserId, notificationId, "OFFLINE_OR_STALE_GENERATION", traceId);
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端已离线、换代或不支持自更新");
        }
        return result;
    }

    /** 注册后立即及每五分钟检查；非强制差异只创建去重站内信。 */
    public void handleVersionCheck(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            long generation,
            LocalClientPayloads.VersionCheck check,
            String traceId) {
        if (!clientInstanceId.value().equals(check.clientInstanceId())) {
            throw validation("版本检查实例 ID 不匹配");
        }
        LocalClientInstance instance = requireOwned(userId, clientInstanceId);
        if (!instance.selfUpdateSupported()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端未声明 SELF_UPDATE_V1 能力");
        }
        LocalClientVersionModels.EffectivePolicy effective = effectivePolicy(userId);
        LocalClientUpdateDirection direction = direction(instance, effective.targetVersion());
        // 新部署尚未设置任何版本策略时没有可下发的目标，不能把内部哨兵 revision=0
        // 编码成 VERSION_POLICY；已发布客户端会把非正 revision 视为失效 fencing 并断开连接。
        if (effective.targetVersion() == null) {
            notifications.invalidateLocalClientUpdate(
                    userId, clientInstanceId.value(), "POLICY_SATISFIED", traceId);
            return;
        }
        if (direction == LocalClientUpdateDirection.SAME) {
            notifications.invalidateLocalClientUpdate(userId, clientInstanceId.value(), "POLICY_SATISFIED", traceId);
        } else {
            notifications.syncLocalClientUpdateAvailable(
                    userId,
                    clientInstanceId.value(),
                    instance.clientName(),
                    instance.clientVersion(),
                    effective.targetVersion(),
                    direction.name(),
                    effective.policyRevision(),
                    traceId);
        }
        send(clientInstanceId, generation, new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.VERSION_POLICY,
                requestId("lcvp_"),
                traceId,
                generation,
                codec.payload(new LocalClientPayloads.VersionPolicy(
                        clientInstanceId.value(),
                        generation,
                        effective.targetVersion(),
                        direction.name(),
                        effective.policyRevision(),
                        false))));
    }

    /** PREPARED 后重新读取策略和 release 摘要，一致时才进入 APPLYING。 */
    public void handlePrepared(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            long generation,
            LocalClientPayloads.UpdatePrepared prepared,
            String traceId) {
        LocalClientVersionModels.Attempt attempt = requireAttempt(prepared.commandId(), userId, clientInstanceId);
        requireCommandCoordinates(
                attempt,
                clientInstanceId,
                generation,
                prepared.clientInstanceId(),
                prepared.connectionGeneration(),
                prepared.policyRevision(),
                prepared.targetVersion(),
                prepared.direction(),
                true);
        LocalClientVersionModels.Release release = versionRepository.findRelease(attempt.targetVersion())
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "更新目标版本已不可用"));
        LocalClientVersionModels.EffectivePolicy effective = effectivePolicy(userId);
        if (!Objects.equals(effective.targetVersion(), attempt.targetVersion())
                || effective.policyRevision() != attempt.policyRevision()
                || !release.manifestSha256().equals(prepared.releaseDigest())) {
            cancelAttempt(attempt, clientInstanceId, generation, "POLICY_OR_RELEASE_CHANGED", traceId);
            return;
        }
        LocalClientVersionModels.AttemptStatus status = attempt.status();
        if (status == LocalClientVersionModels.AttemptStatus.SENT
                || status == LocalClientVersionModels.AttemptStatus.PREPARING) {
            if (!versionRepository.transitionAttempt(
                    attempt.commandId(), status.name(), LocalClientVersionModels.AttemptStatus.PREPARED.name(),
                    prepared.releaseDigest(), null, Instant.now(clock))) {
                throw new PlatformException(ErrorCode.CONFLICT, "更新准备状态已变化");
            }
            status = LocalClientVersionModels.AttemptStatus.PREPARED;
        }
        if (status == LocalClientVersionModels.AttemptStatus.PREPARED) {
            if (!versionRepository.transitionAttempt(
                    attempt.commandId(), status.name(), LocalClientVersionModels.AttemptStatus.APPLYING.name(),
                    prepared.releaseDigest(), null, Instant.now(clock))) {
                throw new PlatformException(ErrorCode.CONFLICT, "更新应用状态已变化");
            }
        } else if (status != LocalClientVersionModels.AttemptStatus.APPLYING) {
            throw new PlatformException(ErrorCode.CONFLICT, "更新命令不处于可应用状态");
        }
        sendApply(attempt, generation, prepared.releaseDigest(), traceId);
    }

    /** 接受同 generation 的准备状态；终态只返回由关系库事实确认的 ACK，进度状态不返回 ACK。 */
    public LocalClientPayloads.UpdateStatusAck handleStatus(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            long generation,
            LocalClientPayloads.UpdateStatus payload,
            String traceId) {
        LocalClientVersionModels.Attempt attempt = requireAttempt(payload.commandId(), userId, clientInstanceId);
        String reported = requiredStatus(payload.status());
        boolean preparingStatus = List.of("DOWNLOADING", "SELF_CHECKING", "PREPARING").contains(reported);
        boolean applyingStatus = "APPLYING".equals(reported);
        requireCommandCoordinates(
                attempt,
                clientInstanceId,
                generation,
                payload.clientInstanceId(),
                payload.connectionGeneration(),
                payload.policyRevision(),
                payload.targetVersion(),
                payload.direction(),
                preparingStatus || applyingStatus);
        if (applyingStatus) {
            if (attempt.status() != LocalClientVersionModels.AttemptStatus.APPLYING) {
                throw new PlatformException(ErrorCode.CONFLICT, "更新命令尚未进入应用状态");
            }
            return null;
        }
        if (preparingStatus) {
            if (attempt.connectionGeneration() != generation) {
                throw new PlatformException(ErrorCode.CONFLICT, "更新状态 generation 已失效");
            }
            if (attempt.status() == LocalClientVersionModels.AttemptStatus.SENT) {
                versionRepository.transitionAttempt(
                        attempt.commandId(),
                        attempt.status().name(),
                        LocalClientVersionModels.AttemptStatus.PREPARING.name(),
                        null,
                        null,
                        Instant.now(clock));
            }
            return null;
        }
        LocalClientVersionModels.AttemptStatus target;
        try {
            target = LocalClientVersionModels.AttemptStatus.valueOf(reported);
        } catch (IllegalArgumentException exception) {
            throw validation("更新状态码无效");
        }
        if (!target.terminal()) {
            throw validation("更新状态码不是允许的终态");
        }
        LocalClientInstance instance = requireOwned(userId, clientInstanceId);
        if (target == LocalClientVersionModels.AttemptStatus.SUCCEEDED
                && !instance.clientVersion().equals(attempt.targetVersion())) {
            throw new PlatformException(ErrorCode.CONFLICT, "客户端尚未以目标版本重新注册");
        }
        if (attempt.status().terminal()) {
            if (attempt.status() == target) {
                return statusAck(attempt, target);
            }
            if (!deadlineFailure(attempt)) {
                throw new PlatformException(ErrorCode.CONFLICT, "更新终态与已持久化状态冲突");
            }
            return convergeLateTerminal(attempt, target, payload.errorCode(), traceId);
        }
        String errorCode = safeErrorCode(payload.errorCode());
        LocalClientUpdateTerminalService.TerminalResult terminal = terminalService.finalizeAttempt(
                attempt, target, errorCode, traceId);
        if (!terminal.persisted()) {
            throw new PlatformException(ErrorCode.CONFLICT, "更新终态已由其它请求写入");
        }
        return terminal.acknowledgement();
    }

    /** deadline 是平台观测失败；唯一允许客户端迟到的物理终态纠正该占位失败。 */
    private LocalClientPayloads.UpdateStatusAck convergeLateTerminal(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus target,
            String reportedErrorCode,
            String traceId) {
        String errorCode = safeErrorCode(reportedErrorCode);
        return terminalService.convergeDeadlineFailure(attempt, target, errorCode, traceId).acknowledgement();
    }

    /** ACK 坐标只从已持久化 attempt 派生，handler 不再回显不受信的客户端 payload。 */
    private static LocalClientPayloads.UpdateStatusAck statusAck(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus persistedStatus) {
        return new LocalClientPayloads.UpdateStatusAck(
                attempt.commandId(),
                attempt.clientInstanceId().value(),
                attempt.connectionGeneration(),
                persistedStatus.name());
    }

    private static boolean deadlineFailure(LocalClientVersionModels.Attempt attempt) {
        return attempt.status() == LocalClientVersionModels.AttemptStatus.FAILED
                && "DELIVERY_DEADLINE_EXCEEDED".equals(attempt.errorCode());
    }

    /** 管理页读取 rollout 列表，事实以关系库为准。 */
    @Transactional(readOnly = true)
    public List<LocalClientVersionModels.Rollout> rollouts() {
        return versionRepository.findRollouts();
    }

    /** 管理页读取指定 rollout 的实例级 attempt；未知 ID 失败关闭。 */
    @Transactional(readOnly = true)
    public List<LocalClientVersionModels.Attempt> attempts(String rolloutId) {
        if (rolloutId == null || rolloutId.isBlank() || rolloutId.length() > 128) {
            throw validation("rolloutId 无效");
        }
        versionRepository.findRollout(rolloutId.trim())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "客户端更新 rollout 不存在"));
        return versionRepository.findAttemptsByRollout(rolloutId.trim());
    }

    @Scheduled(fixedDelayString = "${test-agent.local-client.update-compensation-delay-ms:30000}")
    public void compensatePendingAttempts() {
        dispatchPending("trace_local_client_update_compensation");
    }

    @Override
    public boolean supports(String type) {
        return BROADCAST_TYPE.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (supports(event.type()) && !broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            dispatchPending(event.traceId());
        }
    }

    private RolloutResult persistRollout(
            LocalClientVersionModels.RolloutScope scope,
            UserId requestedUserId,
            UserId actorUserId,
            List<LocalClientInstance> candidates,
            LocalClientInstanceId onlyInstance,
            Long requiredGeneration,
            String traceId) {
        Instant now = Instant.now(clock);
        String rolloutId = requestId("lcrl_");
        List<LocalClientVersionModels.Attempt> attempts = new ArrayList<>();
        for (LocalClientInstance instance : candidates) {
            if (onlyInstance != null && !onlyInstance.equals(instance.clientInstanceId())) {
                continue;
            }
            LocalClientConnectionRoute route = connectionStore.find(instance.clientInstanceId())
                    .filter(candidate -> candidate.userId().equals(instance.userId()))
                    .filter(candidate -> requiredGeneration == null
                            || candidate.connectionGeneration() == requiredGeneration)
                    .orElse(null);
            if (route == null || !instance.selfUpdateSupported()) {
                continue;
            }
            LocalClientVersionModels.EffectivePolicy policy = effectivePolicy(instance.userId());
            LocalClientUpdateDirection direction = direction(instance, policy.targetVersion());
            if (policy.targetVersion() == null || direction == LocalClientUpdateDirection.SAME) {
                continue;
            }
            attempts.add(new LocalClientVersionModels.Attempt(
                    requestId("lcuc_"),
                    rolloutId,
                    instance.clientInstanceId(),
                    instance.userId(),
                    route.connectionGeneration(),
                    policy.policyRevision(),
                    instance.clientVersion(),
                    policy.targetVersion(),
                    direction,
                    LocalClientVersionModels.AttemptStatus.PENDING,
                    null,
                    null,
                    now,
                    now,
                    null));
        }
        LocalClientVersionModels.RolloutStatus status = attempts.isEmpty()
                ? LocalClientVersionModels.RolloutStatus.COMPLETED
                : LocalClientVersionModels.RolloutStatus.RUNNING;
        LocalClientVersionModels.Rollout rollout = new LocalClientVersionModels.Rollout(
                rolloutId,
                scope,
                requestedUserId,
                status,
                actorUserId,
                now,
                attempts.isEmpty() ? now : null);
        versionRepository.insertRollout(rollout);
        versionRepository.insertAttempts(attempts);
        if (!attempts.isEmpty()) {
            wakeAfterCommit(rolloutId, traceId);
        }
        return new RolloutResult(rollout, attempts.size());
    }

    private void wakeAfterCommit(String rolloutId, String traceId) {
        Runnable wakeup = () -> {
            dispatchPending(traceId);
            try {
                broadcastPublisher.publish(new ServerBroadcastEvent(
                        RuntimeIdGenerator.serverBroadcastEventId(),
                        BROADCAST_TYPE,
                        identity.instanceId(),
                        identity.linuxServerId(),
                        traceId,
                        Instant.now(clock),
                        Map.of("rolloutId", rolloutId)));
            } catch (RuntimeException exception) {
                LOGGER.warn("local_client_update_wakeup_publish_failed rolloutId={} traceId={}",
                        rolloutId, traceId, exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wakeup.run();
                }
            });
        } else {
            wakeup.run();
        }
    }

    private void dispatchPending(String traceId) {
        if (!dispatching.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant now = Instant.now(clock);
            for (int page = 0; page < MAX_DISPATCH_PAGES; page++) {
                List<LocalClientVersionModels.Attempt> attempts =
                        versionRepository.findDispatchableAttempts(
                                DISPATCH_BATCH_SIZE, page * DISPATCH_BATCH_SIZE, now);
                for (LocalClientVersionModels.Attempt attempt : attempts) {
                    dispatch(attempt, traceId);
                }
                if (attempts.size() < DISPATCH_BATCH_SIZE) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("local_client_update_compensation_failed traceId={}", traceId, exception);
        } finally {
            dispatching.set(false);
        }
    }

    private void dispatch(LocalClientVersionModels.Attempt attempt, String traceId) {
        // 补偿扫描返回的所有非终态共享同一硬 deadline；到期必须先 CAS 收口，不能因重新在线而再次下发。
        if (!attempt.createdAt().plus(DELIVERY_DEADLINE).isAfter(Instant.now(clock))) {
            terminal(
                    attempt,
                    LocalClientVersionModels.AttemptStatus.FAILED,
                    "DELIVERY_DEADLINE_EXCEEDED",
                    traceId);
            return;
        }
        LocalClientConnectionRoute route = connectionStore.find(attempt.clientInstanceId()).orElse(null);
        if (route == null) {
            return;
        }
        if (route.connectionGeneration() != attempt.connectionGeneration()
                || !route.userId().equals(attempt.userId())) {
            terminal(attempt, LocalClientVersionModels.AttemptStatus.CANCELLED, "STALE_GENERATION", traceId);
            return;
        }
        // 能力以最近一次持久化注册为准，避免旧 attempt 在不支持自更新的客户端重连后继续下发。
        LocalClientInstance registeredInstance = instanceRepository.findById(attempt.clientInstanceId()).orElse(null);
        if (registeredInstance == null || !registeredInstance.selfUpdateSupported()) {
            terminal(attempt, LocalClientVersionModels.AttemptStatus.CANCELLED, "CAPABILITY_UNAVAILABLE", traceId);
            return;
        }
        if (!route.backendProcessId().value().equals(identity.backendProcessId())) {
            return;
        }
        try {
            switch (attempt.status()) {
                case PENDING -> {
                    if (versionRepository.transitionAttempt(
                            attempt.commandId(),
                            LocalClientVersionModels.AttemptStatus.PENDING.name(),
                            LocalClientVersionModels.AttemptStatus.SENT.name(),
                            null,
                            null,
                            Instant.now(clock))) {
                        sendCommand(attempt, traceId);
                    }
                }
                case SENT, PREPARING -> sendCommand(attempt, traceId);
                case PREPARED -> authorizePreparedAttempt(attempt, traceId);
                case APPLYING -> sendApply(attempt, route.connectionGeneration(), attempt.releaseDigest(), traceId);
                default -> {
                    // 查询只返回可补偿非终态；未知值保持失败关闭。
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("local_client_update_dispatch_failed commandId={} clientInstanceId={} traceId={}",
                    attempt.commandId(), attempt.clientInstanceId().value(), traceId, exception);
        }
    }

    private void authorizePreparedAttempt(LocalClientVersionModels.Attempt attempt, String traceId) {
        LocalClientVersionModels.EffectivePolicy effective = effectivePolicy(attempt.userId());
        LocalClientVersionModels.Release release = versionRepository.findRelease(attempt.targetVersion()).orElse(null);
        if (release == null
                || !Objects.equals(effective.targetVersion(), attempt.targetVersion())
                || effective.policyRevision() != attempt.policyRevision()
                || !Objects.equals(release.manifestSha256(), attempt.releaseDigest())) {
            cancelAttempt(
                    attempt,
                    attempt.clientInstanceId(),
                    attempt.connectionGeneration(),
                    "POLICY_OR_RELEASE_CHANGED",
                    traceId);
            return;
        }
        if (versionRepository.transitionAttempt(
                attempt.commandId(),
                LocalClientVersionModels.AttemptStatus.PREPARED.name(),
                LocalClientVersionModels.AttemptStatus.APPLYING.name(),
                attempt.releaseDigest(),
                null,
                Instant.now(clock))) {
            sendApply(attempt, attempt.connectionGeneration(), attempt.releaseDigest(), traceId);
        }
    }

    private void sendCommand(LocalClientVersionModels.Attempt attempt, String traceId) {
        send(attempt.clientInstanceId(), attempt.connectionGeneration(), new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.UPDATE_COMMAND,
                attempt.commandId(),
                traceId,
                attempt.connectionGeneration(),
                codec.payload(new LocalClientPayloads.UpdateCommand(
                        attempt.commandId(),
                        attempt.clientInstanceId().value(),
                        attempt.connectionGeneration(),
                        attempt.policyRevision(),
                        attempt.targetVersion(),
                        attempt.direction().name()))));
    }

    private void sendApply(
            LocalClientVersionModels.Attempt attempt,
            long generation,
            String releaseDigest,
            String traceId) {
        send(attempt.clientInstanceId(), generation, new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.UPDATE_APPLY,
                attempt.commandId(),
                traceId,
                generation,
                codec.payload(new LocalClientPayloads.UpdateApply(
                        attempt.commandId(),
                        attempt.clientInstanceId().value(),
                        generation,
                        attempt.policyRevision(),
                        attempt.targetVersion(),
                        attempt.direction().name(),
                        releaseDigest))));
    }

    private void cancelAttempt(
            LocalClientVersionModels.Attempt attempt,
            LocalClientInstanceId clientInstanceId,
            long generation,
            String reason,
            String traceId) {
        terminal(attempt, LocalClientVersionModels.AttemptStatus.CANCELLED, reason, traceId);
        send(clientInstanceId, generation, new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.UPDATE_CANCEL,
                attempt.commandId(),
                traceId,
                generation,
                codec.payload(new LocalClientPayloads.UpdateCancel(
                        attempt.commandId(),
                        clientInstanceId.value(),
                        attempt.connectionGeneration(),
                        attempt.policyRevision(),
                        attempt.targetVersion(),
                        attempt.direction().name(),
                        reason))));
    }

    private void terminal(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus target,
            String errorCode,
            String traceId) {
        if (!attempt.status().terminal()) {
            terminalService.finalizeAttempt(attempt, target, errorCode, traceId);
        }
    }

    private LocalClientVersionModels.EffectivePolicy effectivePolicy(UserId userId) {
        return LocalClientVersionModels.resolveEffectivePolicy(
                versionRepository.findGlobalPolicy().orElse(null),
                versionRepository.findUserPolicy(userId).orElse(null));
    }

    private LocalClientUpdateDirection direction(LocalClientInstance instance, String targetVersion) {
        if (targetVersion == null || !instance.selfUpdateSupported()) {
            return LocalClientUpdateDirection.SAME;
        }
        try {
            return LocalClientReleaseVersion.parse(instance.clientVersion())
                    .directionTo(LocalClientReleaseVersion.parse(targetVersion));
        } catch (IllegalArgumentException exception) {
            return LocalClientUpdateDirection.SAME;
        }
    }

    private LocalClientInstance requireOwned(UserId userId, LocalClientInstanceId clientInstanceId) {
        LocalClientInstance instance = instanceRepository.findById(clientInstanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地客户端实例不存在"));
        if (!instance.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不属于当前用户");
        }
        return instance;
    }

    private LocalClientVersionModels.Attempt requireAttempt(
            String commandId,
            UserId userId,
            LocalClientInstanceId clientInstanceId) {
        LocalClientVersionModels.Attempt attempt = versionRepository.findAttempt(commandId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "更新命令不存在"));
        if (!attempt.userId().equals(userId) || !attempt.clientInstanceId().equals(clientInstanceId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "更新命令不属于当前客户端实例");
        }
        return attempt;
    }

    private void requireCommandCoordinates(
            LocalClientVersionModels.Attempt attempt,
            LocalClientInstanceId frameClientInstanceId,
            long frameGeneration,
            String payloadClientInstanceId,
            long payloadGeneration,
            long policyRevision,
            String targetVersion,
            String direction,
            boolean requireOriginalFrameGeneration) {
        if (!attempt.clientInstanceId().equals(frameClientInstanceId)
                || !attempt.clientInstanceId().value().equals(payloadClientInstanceId)
                || attempt.connectionGeneration() != payloadGeneration
                || (requireOriginalFrameGeneration && attempt.connectionGeneration() != frameGeneration)
                || attempt.policyRevision() != policyRevision
                || !attempt.targetVersion().equals(targetVersion)
                || !attempt.direction().name().equals(direction)) {
            throw new PlatformException(ErrorCode.CONFLICT, "更新命令坐标、generation 或目标策略已失效");
        }
    }

    private PlatformException staleNotification(
            UserId userId,
            UserNotificationId notificationId,
            String traceId) {
        notificationInvalidation.invalidate(userId, notificationId, "STALE_POLICY", traceId);
        return new PlatformException(ErrorCode.CONFLICT, "更新通知对应的版本策略已变化");
    }

    private void send(LocalClientInstanceId clientInstanceId, long generation, LocalClientFrame frame) {
        connectionRegistry.send(clientInstanceId, generation, frame);
    }

    private static String requiredStatus(String status) {
        if (status == null || status.isBlank() || status.length() > 32) {
            throw validation("更新状态码无效");
        }
        return status.trim().toUpperCase();
    }

    private static String safeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        String normalized = errorCode.trim().toUpperCase();
        if (normalized.length() > 128 || !normalized.matches("[A-Z0-9_]+")) {
            throw validation("更新错误码无效");
        }
        return normalized;
    }

    private static String requestId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static PlatformException validation(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record RolloutResult(LocalClientVersionModels.Rollout rollout, int attemptCount) {
    }
}
