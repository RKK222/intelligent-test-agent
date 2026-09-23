package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户确认型公共能力更新：版本上报、通知、逐片传输、generation fencing 和终态收敛。 */
@Service
public class LocalClientPublicCapabilityCoordinator {

    public static final String PROTOCOL_CAPABILITY = "PUBLIC_CAPABILITY_SYNC_V1";
    public static final String PERSONAL_EDIT_CAPABILITY = "PUBLIC_CAPABILITY_PERSONAL_EDIT_V1";
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientPublicCapabilityCoordinator.class);
    private static final int DISPATCH_LIMIT = 200;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final LocalClientPublicCapabilityRepository repository;
    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final LocalClientConnectionRegistry connectionRegistry;
    private final UserNotificationApplicationService notifications;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();
    private final Clock clock;

    @Autowired
    public LocalClientPublicCapabilityCoordinator(
            LocalClientPublicCapabilityRepository repository,
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientConnectionRegistry connectionRegistry,
            UserNotificationApplicationService notifications) {
        this(repository, instanceRepository, connectionStore, connectionRegistry, notifications, Clock.systemUTC());
    }

    LocalClientPublicCapabilityCoordinator(
            LocalClientPublicCapabilityRepository repository,
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientConnectionRegistry connectionRegistry,
            UserNotificationApplicationService notifications,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry);
        this.notifications = Objects.requireNonNull(notifications);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 客户端重连后上报激活版本；平台以最新 AVAILABLE 版本重新计算待确认状态。 */
    @Transactional
    public void handleVersion(
            UserId userId,
            LocalClientInstanceId instanceId,
            long generation,
            LocalClientPayloads.PublicCapabilityVersion payload,
            String traceId) {
        requireCoordinates(instanceId, generation, payload.clientInstanceId(), payload.connectionGeneration());
        LocalClientInstance instance = requireCapableOwned(userId, instanceId, false);
        Instant now = Instant.now(clock);
        LocalClientPublicCapabilityModels.Release latest = repository.findLatestAvailableRelease().orElse(null);
        String activeCommit = optionalCommit(payload.activeCommit());
        String activeDigest = optionalDigest(payload.activeDigest());
        String pendingCommit = optionalCommit(payload.pendingCommit());
        String pendingDigest = optionalDigest(payload.pendingDigest());
        Instant reportedAt = requireObservedAt(payload.observedAt());
        reconcileInterruptedAttempt(
                userId, instanceId, activeDigest, pendingCommit, pendingDigest,
                payload.pendingCommandId(), payload.status(), safeError(payload.errorCode()), reportedAt);
        boolean current = latest != null && latest.bundleDigest().equals(activeDigest);
        LocalClientPublicCapabilityModels.InstanceState state = new LocalClientPublicCapabilityModels.InstanceState(
                instanceId,
                activeCommit,
                activeDigest,
                current ? null : latest == null ? null : latest.sourceCommit(),
                current ? null : latest == null ? null : latest.bundleDigest(),
                current ? LocalClientPublicCapabilityModels.InstanceStatus.CURRENT
                        : latest == null ? LocalClientPublicCapabilityModels.InstanceStatus.CURRENT
                        : LocalClientPublicCapabilityModels.InstanceStatus.UPDATE_AVAILABLE,
                safeError(payload.errorCode()),
                reportedAt,
                now);
        repository.saveInstanceState(state);
        if (current) {
            notifications.invalidateLocalClientPublicCapability(
                    userId, instanceId.value(), "CAPABILITY_CURRENT", traceId);
        } else if (latest != null) {
            boolean requiresRestart = requiresRestart(activeDigest, latest);
            notifyAvailable(instance, latest, requiresRestart, traceId);
            sendAvailable(instanceId, generation, latest, requiresRestart, traceId);
        }
        dispatchFor(instanceId, traceId);
    }

    /** 用户可以离线确认；命令先以 generation=0 持久化，重连后再绑定当前代次。 */
    @Transactional
    public UpdateRequestResult requestUpdate(
            UserId userId,
            LocalClientInstanceId instanceId,
            String expectedBundleDigest,
            String traceId) {
        return requestUpdate(userId, instanceId, expectedBundleDigest, false, traceId);
    }

    /**
     * 用户确认清空个人副本时，标记编码到 commandId，复用既有 attempt 持久化而不新增数据库字段。
     */
    @Transactional
    public UpdateRequestResult requestUpdate(
            UserId userId,
            LocalClientInstanceId instanceId,
            String expectedBundleDigest,
            boolean confirmedDiscardPersonalChanges,
            String traceId) {
        LocalClientInstance instance = requireCapableOwned(userId, instanceId, confirmedDiscardPersonalChanges);
        String digest = requiredDigest(expectedBundleDigest);
        LocalClientPublicCapabilityModels.InstanceState state = repository.findInstanceState(instanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "客户端尚未上报公共能力状态"));
        if (!digest.equals(state.pendingDigest())
                || state.pendingCommit() == null
                || state.status() == LocalClientPublicCapabilityModels.InstanceStatus.CURRENT) {
            throw new PlatformException(ErrorCode.CONFLICT, "待更新公共能力版本已经变化");
        }
        LocalClientPublicCapabilityModels.Release release = repository.findReleaseByDigest(digest)
                .filter(candidate -> candidate.compatibility()
                        == LocalClientPublicCapabilityModels.Compatibility.AVAILABLE)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "目标公共能力包不可用于客户端"));
        LocalClientPublicCapabilityModels.Attempt existing = repository.findAttempt(instanceId, digest)
                .filter(attempt -> !attempt.status().terminal())
                .orElse(null);
        if (existing != null) {
            if (confirmedDiscardPersonalChanges && !isConfirmedDiscardCommand(existing.commandId())) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "已有未确认清空个人修改的公共能力更新，请等待其失败后重试");
            }
            dispatchFor(instanceId, traceId);
            return new UpdateRequestResult(existing.commandId(), existing.status().name(), digest);
        }
        long generation = connectionStore.find(instanceId)
                .filter(route -> route.userId().equals(userId))
                .map(LocalClientConnectionRoute::connectionGeneration)
                .orElse(0L);
        Instant now = Instant.now(clock);
        LocalClientPublicCapabilityModels.Attempt attempt = new LocalClientPublicCapabilityModels.Attempt(
                requestId(confirmedDiscardPersonalChanges ? "lcpcd_" : "lcpc_"),
                instanceId, userId, generation, release.sourceCommit(), release.bundleDigest(),
                LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, now, now, null);
        repository.insertAttempt(attempt);
        repository.saveInstanceState(new LocalClientPublicCapabilityModels.InstanceState(
                instanceId, state.activeCommit(), state.activeDigest(), release.sourceCommit(), release.bundleDigest(),
                LocalClientPublicCapabilityModels.InstanceStatus.PENDING, null, state.reportedAt(), now));
        dispatchFor(instanceId, traceId);
        return new UpdateRequestResult(attempt.commandId(), attempt.status().name(), digest);
    }

    /** 客户端每次只拉一个分片，避免 128 帧出站队列被完整制品占满。 */
    public LocalClientPayloads.BinaryChunk handleChunkRequest(
            UserId userId,
            LocalClientInstanceId instanceId,
            long generation,
            LocalClientPayloads.PublicCapabilityChunkRequest request,
            String traceId) {
        requireCoordinates(instanceId, generation, request.clientInstanceId(), request.connectionGeneration());
        LocalClientPublicCapabilityModels.Attempt attempt = requireAttempt(
                request.commandId(), userId, instanceId, generation, request.bundleDigest());
        LocalClientPublicCapabilityModels.Release release = requireAvailableRelease(attempt.targetDigest());
        byte[] artifact = release.artifact();
        long chunkCount = (artifact.length + (long) LocalClientProtocol.BINARY_CHUNK_BYTES - 1)
                / LocalClientProtocol.BINARY_CHUNK_BYTES;
        if (request.sequence() < 0 || request.sequence() >= chunkCount) {
            throw validation("能力包分片序号无效");
        }
        if (attempt.status() == LocalClientPublicCapabilityModels.AttemptStatus.PENDING
                || attempt.status() == LocalClientPublicCapabilityModels.AttemptStatus.SENT) {
            repository.transitionAttempt(
                    attempt.commandId(), attempt.status().name(),
                    LocalClientPublicCapabilityModels.AttemptStatus.DOWNLOADING.name(), null, Instant.now(clock));
        } else if (attempt.status() != LocalClientPublicCapabilityModels.AttemptStatus.DOWNLOADING) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共能力命令不处于下载状态");
        }
        int offset = Math.toIntExact(request.sequence() * LocalClientProtocol.BINARY_CHUNK_BYTES);
        int end = Math.min(artifact.length, offset + LocalClientProtocol.BINARY_CHUNK_BYTES);
        byte[] bytes = Arrays.copyOfRange(artifact, offset, end);
        return new LocalClientPayloads.BinaryChunk(
                request.sequence(), java.util.Base64.getEncoder().encodeToString(bytes), end == artifact.length);
    }

    /** 状态只允许单向推进；成功后由关系库更新 active 版本并关闭该实例通知。 */
    @Transactional
    public LocalClientPayloads.PublicCapabilityUpdateStatusAck handleStatus(
            UserId userId,
            LocalClientInstanceId instanceId,
            long generation,
            LocalClientPayloads.PublicCapabilityUpdateStatus payload,
            String traceId) {
        requireCoordinates(instanceId, generation, payload.clientInstanceId(), payload.connectionGeneration());
        LocalClientPublicCapabilityModels.Attempt attempt = requireAttempt(
                payload.commandId(), userId, instanceId, generation, payload.bundleDigest());
        if (!attempt.targetCommit().equals(optionalCommit(payload.sourceCommit()))) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共能力命令 commit 坐标失效");
        }
        LocalClientPublicCapabilityModels.AttemptStatus target = parseStatus(payload.status());
        if (!allowed(attempt.status(), target)) {
            if (attempt.status() == target) {
                return ack(attempt, target);
            }
            throw new PlatformException(ErrorCode.CONFLICT, "公共能力更新状态迁移无效");
        }
        if (!repository.transitionAttempt(
                attempt.commandId(), attempt.status().name(), target.name(), safeError(payload.errorCode()),
                requireObservedAt(payload.observedAt()))) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共能力更新状态已由其它请求写入");
        }
        LocalClientPublicCapabilityModels.InstanceState previous = repository.findInstanceState(instanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "客户端公共能力状态不存在"));
        Instant now = Instant.now(clock);
        if (target == LocalClientPublicCapabilityModels.AttemptStatus.SUCCEEDED) {
            repository.saveInstanceState(new LocalClientPublicCapabilityModels.InstanceState(
                    instanceId, attempt.targetCommit(), attempt.targetDigest(), null, null,
                    LocalClientPublicCapabilityModels.InstanceStatus.CURRENT, null,
                    requireObservedAt(payload.observedAt()), now));
            notifications.invalidateLocalClientPublicCapability(
                    userId, instanceId.value(), "CAPABILITY_UPDATED", traceId);
        } else {
            repository.saveInstanceState(new LocalClientPublicCapabilityModels.InstanceState(
                    instanceId, previous.activeCommit(), previous.activeDigest(), attempt.targetCommit(),
                    attempt.targetDigest(), instanceStatus(target), safeError(payload.errorCode()),
                    requireObservedAt(payload.observedAt()), now));
        }
        return new LocalClientPayloads.PublicCapabilityUpdateStatusAck(
                attempt.commandId(), instanceId.value(), generation, target.name());
    }

    /** 新发布版本的离线实例也要收到站内信；在线下发由当前连接所属 Java 自行认领。 */
    @Scheduled(fixedDelayString = "${test-agent.local-client.public-capabilities.reconcile-delay-ms:30000}")
    public void reconcile() {
        String traceId = "trace_public_capability_reconcile";
        try {
            for (LocalClientPublicCapabilityModels.InstanceState state : repository.findUpdateAvailableStates(1000)) {
                LocalClientInstance instance = instanceRepository.findById(state.clientInstanceId()).orElse(null);
                LocalClientPublicCapabilityModels.Release release = state.pendingDigest() == null ? null
                        : repository.findReleaseByDigest(state.pendingDigest()).orElse(null);
                if (instance != null && release != null && supports(instance)) {
                    boolean requiresRestart = requiresRestart(state.activeDigest(), release);
                    notifyAvailable(instance, release, requiresRestart, traceId);
                    // 能力包可能在客户端完成版本上报后才生成；定时收敛必须同时补发桌面通知，
                    // 不能只刷新网页通知，否则在线客户端会一直显示“当前版本”。
                    sendAvailableToCurrentConnection(instance, release, requiresRestart, traceId);
                }
            }
            for (LocalClientPublicCapabilityModels.Attempt attempt : repository.findDispatchableAttempts(DISPATCH_LIMIT)) {
                dispatch(attempt, traceId);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("event=public_capability_reconcile_failed traceId={}", traceId, exception);
        }
    }

    private void dispatchFor(LocalClientInstanceId instanceId, String traceId) {
        repository.findDispatchableAttempts(DISPATCH_LIMIT).stream()
                .filter(attempt -> attempt.clientInstanceId().equals(instanceId))
                .forEach(attempt -> dispatch(attempt, traceId));
    }

    private void dispatch(LocalClientPublicCapabilityModels.Attempt attempt, String traceId) {
        LocalClientConnectionRegistry.ConnectionSnapshot connection =
                connectionRegistry.find(attempt.clientInstanceId()).orElse(null);
        if (connection == null || !connection.userId().equals(attempt.userId())) {
            return;
        }
        LocalClientInstance instance = instanceRepository.findById(attempt.clientInstanceId()).orElse(null);
        // 只有携带“清空个人副本”语义的命令要求新 capability；普通旧更新仍保持兼容。
        if (instance == null || !supportsUpdateCommands(instance, attempt.commandId())) {
            return;
        }
        long generation = connection.generation();
        if (attempt.connectionGeneration() != generation) {
            if ((attempt.status() != LocalClientPublicCapabilityModels.AttemptStatus.PENDING
                    && attempt.status() != LocalClientPublicCapabilityModels.AttemptStatus.SENT
                    && attempt.status() != LocalClientPublicCapabilityModels.AttemptStatus.DOWNLOADING)
                    || !repository.bindAttemptGeneration(
                            attempt.commandId(), attempt.connectionGeneration(), generation, Instant.now(clock))) {
                return;
            }
            attempt = repository.findAttempt(attempt.commandId()).orElseThrow();
        }
        LocalClientPublicCapabilityModels.Release release = requireAvailableRelease(attempt.targetDigest());
        if (attempt.status() == LocalClientPublicCapabilityModels.AttemptStatus.PENDING) {
            repository.transitionAttempt(
                    attempt.commandId(), attempt.status().name(),
                    LocalClientPublicCapabilityModels.AttemptStatus.SENT.name(), null, Instant.now(clock));
        }
        int chunks = Math.toIntExact((release.compressedSize() + LocalClientProtocol.BINARY_CHUNK_BYTES - 1)
                / LocalClientProtocol.BINARY_CHUNK_BYTES);
        String activeDigest = repository.findInstanceState(attempt.clientInstanceId())
                .map(LocalClientPublicCapabilityModels.InstanceState::activeDigest)
                .orElse(null);
        boolean requiresRestart = requiresRestart(activeDigest, release);
        connectionRegistry.send(attempt.clientInstanceId(), generation, new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.PUBLIC_CAPABILITY_UPDATE_COMMAND,
                attempt.commandId(),
                traceId,
                generation,
                codec.payload(new LocalClientPayloads.PublicCapabilityUpdateCommand(
                        attempt.commandId(), attempt.clientInstanceId().value(), generation,
                        release.sourceCommit(), release.bundleDigest(), release.artifactSha256(),
                        release.compressedSize(), chunks, requiresRestart, release.manifestJson(),
                        isConfirmedDiscardCommand(attempt.commandId())))));
    }

    private void sendAvailable(
            LocalClientInstanceId instanceId,
            long generation,
            LocalClientPublicCapabilityModels.Release release,
            boolean requiresRestart,
            String traceId) {
        if (connectionRegistry.find(instanceId).filter(value -> value.generation() == generation).isEmpty()) {
            return;
        }
        connectionRegistry.send(instanceId, generation, new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.PUBLIC_CAPABILITY_AVAILABLE,
                requestId("lcpv_"),
                traceId,
                generation,
                codec.payload(new LocalClientPayloads.PublicCapabilityAvailable(
                        instanceId.value(), generation, release.sourceCommit(), release.bundleDigest(),
                        release.counts().agents(), release.counts().skills(), release.counts().tools(),
                        requiresRestart, release.changeSummaryJson()))));
    }

    private void sendAvailableToCurrentConnection(
            LocalClientInstance instance,
            LocalClientPublicCapabilityModels.Release release,
            boolean requiresRestart,
            String traceId) {
        LocalClientConnectionRegistry.ConnectionSnapshot connection =
                connectionRegistry.find(instance.clientInstanceId()).orElse(null);
        if (connection == null || !connection.userId().equals(instance.userId())) {
            return;
        }
        try {
            sendAvailable(instance.clientInstanceId(), connection.generation(), release, requiresRestart, traceId);
        } catch (PlatformException exception) {
            if (exception.errorCode() != ErrorCode.LOCAL_CLIENT_DISCONNECTED) {
                throw exception;
            }
            // find 与 send 之间发生断线或换代属于正常竞争，下一个重连上报或调度周期会继续补发。
            LOGGER.debug(
                    "event=public_capability_available_connection_changed traceId={} clientInstanceId={}",
                    traceId,
                    instance.clientInstanceId().value());
        }
    }

    private void notifyAvailable(
            LocalClientInstance instance,
            LocalClientPublicCapabilityModels.Release release,
            boolean requiresRestart,
            String traceId) {
        notifications.syncLocalClientPublicCapabilityAvailable(
                instance.userId(), instance.clientInstanceId().value(), instance.clientName(), release.bundleDigest(),
                release.counts().agents(), release.counts().skills(), release.counts().tools(),
                requiresRestart, traceId);
    }

    /**
     * release 的变更摘要只描述它相对发布时上一全局版本的差异；客户端跳过中间版本时不能直接复用该结论。
     * 只有实例当前摘要与 previousDigest 精确一致，才允许沿用 Agent/Skill-only 的热加载判断；未知或非连续升级失败关闭为重启。
     */
    static boolean requiresRestart(
            String activeDigest,
            LocalClientPublicCapabilityModels.Release release) {
        if (release.requiresRestart()) {
            return true;
        }
        String previousDigest = previousDigest(release.changeSummaryJson());
        return activeDigest == null || previousDigest == null || !activeDigest.equals(previousDigest);
    }

    private static String previousDigest(String changeSummaryJson) {
        if (changeSummaryJson == null || changeSummaryJson.isBlank()) {
            return null;
        }
        try {
            JsonNode value = OBJECT_MAPPER.readTree(changeSummaryJson).path("previousDigest");
            String digest = value.isTextual() ? value.asText() : null;
            return digest != null && digest.matches("[0-9a-f]{64}") ? digest : null;
        } catch (Exception ignored) {
            // 历史或损坏摘要不能证明升级连续，调用方会保守重启，不能因此阻断能力更新。
            return null;
        }
    }

    private LocalClientInstance requireCapableOwned(
            UserId userId, LocalClientInstanceId instanceId, boolean requirePersonalEdit) {
        LocalClientInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地客户端实例不存在"));
        if (!instance.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不属于当前用户");
        }
        boolean capable = requirePersonalEdit
                ? supports(instance) && instance.selfUpdateCapabilities().contains(PERSONAL_EDIT_CAPABILITY)
                : supports(instance);
        if (!capable) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    requirePersonalEdit
                            ? "本地客户端未声明 PUBLIC_CAPABILITY_PERSONAL_EDIT_V1 能力"
                            : "本地客户端未声明 PUBLIC_CAPABILITY_SYNC_V1 能力");
        }
        return instance;
    }

    private static boolean supports(LocalClientInstance instance) {
        return instance.selfUpdateCapabilities().contains(PROTOCOL_CAPABILITY);
    }

    private static boolean supportsUpdateCommands(LocalClientInstance instance, String commandId) {
        return supports(instance)
                && (!isConfirmedDiscardCommand(commandId)
                || instance.selfUpdateCapabilities().contains(PERSONAL_EDIT_CAPABILITY));
    }

    private static boolean isConfirmedDiscardCommand(String commandId) {
        return commandId != null && commandId.startsWith("lcpcd_");
    }

    private LocalClientPublicCapabilityModels.Attempt requireAttempt(
            String commandId,
            UserId userId,
            LocalClientInstanceId instanceId,
            long generation,
            String digest) {
        LocalClientPublicCapabilityModels.Attempt attempt = repository.findAttempt(commandId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "公共能力更新命令不存在"));
        if (!attempt.userId().equals(userId)
                || !attempt.clientInstanceId().equals(instanceId)
                || attempt.connectionGeneration() != generation
                || !attempt.targetDigest().equals(requiredDigest(digest))) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共能力命令坐标或 generation 已失效");
        }
        return attempt;
    }

    /** 客户端重启后以本地原子链接和恢复结果为权威，关闭断线前遗留的非终态尝试。 */
    private void reconcileInterruptedAttempt(
            UserId userId,
            LocalClientInstanceId instanceId,
            String activeDigest,
            String pendingCommit,
            String pendingDigest,
            String pendingCommandId,
            String reportedStatus,
            String errorCode,
            Instant reportedAt) {
        LocalClientPublicCapabilityModels.AttemptStatus terminal = reportedTerminalStatus(reportedStatus);
        if (terminal == null) {
            return;
        }
        LocalClientPublicCapabilityModels.Attempt attempt = null;
        if (pendingCommandId != null && !pendingCommandId.isBlank()) {
            if (!pendingCommandId.matches("lcpc(?:d)?_[a-f0-9]{32}")) {
                throw validation("公共能力待恢复 commandId 无效");
            }
            attempt = repository.findAttempt(pendingCommandId).orElse(null);
        }
        String lookupDigest = terminal == LocalClientPublicCapabilityModels.AttemptStatus.SUCCEEDED
                ? activeDigest : pendingDigest;
        if (attempt == null && lookupDigest != null) {
            attempt = repository.findAttempt(instanceId, lookupDigest).orElse(null);
        }
        if (attempt == null || attempt.status().terminal()) {
            return;
        }
        if (!attempt.userId().equals(userId) || !attempt.clientInstanceId().equals(instanceId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共能力恢复命令不属于当前客户端");
        }
        if (terminal == LocalClientPublicCapabilityModels.AttemptStatus.SUCCEEDED) {
            if (!attempt.targetDigest().equals(activeDigest)) {
                return;
            }
        } else if (!attempt.targetDigest().equals(pendingDigest)
                || (pendingCommit != null && !attempt.targetCommit().equals(pendingCommit))) {
            return;
        }
        repository.transitionAttempt(
                attempt.commandId(), attempt.status().name(), terminal.name(), errorCode, reportedAt);
    }

    private static LocalClientPublicCapabilityModels.AttemptStatus reportedTerminalStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CURRENT", "SUCCEEDED" -> LocalClientPublicCapabilityModels.AttemptStatus.SUCCEEDED;
            case "FAILED" -> LocalClientPublicCapabilityModels.AttemptStatus.FAILED;
            case "ROLLED_BACK" -> LocalClientPublicCapabilityModels.AttemptStatus.ROLLED_BACK;
            default -> null;
        };
    }

    private LocalClientPublicCapabilityModels.Release requireAvailableRelease(String digest) {
        return repository.findReleaseByDigest(digest)
                .filter(release -> release.compatibility() == LocalClientPublicCapabilityModels.Compatibility.AVAILABLE)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "公共能力包不可用"));
    }

    private static boolean allowed(
            LocalClientPublicCapabilityModels.AttemptStatus current,
            LocalClientPublicCapabilityModels.AttemptStatus target) {
        return switch (current) {
            case PENDING, SENT -> target == LocalClientPublicCapabilityModels.AttemptStatus.DOWNLOADING;
            case DOWNLOADING -> target == LocalClientPublicCapabilityModels.AttemptStatus.APPLYING
                    || target == LocalClientPublicCapabilityModels.AttemptStatus.FAILED;
            case APPLYING -> target == LocalClientPublicCapabilityModels.AttemptStatus.SUCCEEDED
                    || target == LocalClientPublicCapabilityModels.AttemptStatus.FAILED
                    || target == LocalClientPublicCapabilityModels.AttemptStatus.ROLLED_BACK;
            default -> false;
        };
    }

    private static LocalClientPublicCapabilityModels.AttemptStatus parseStatus(String status) {
        try {
            return LocalClientPublicCapabilityModels.AttemptStatus.valueOf(
                    status == null ? "" : status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validation("公共能力更新状态无效");
        }
    }

    private static LocalClientPublicCapabilityModels.InstanceStatus instanceStatus(
            LocalClientPublicCapabilityModels.AttemptStatus status) {
        return switch (status) {
            case PENDING, SENT -> LocalClientPublicCapabilityModels.InstanceStatus.PENDING;
            case DOWNLOADING -> LocalClientPublicCapabilityModels.InstanceStatus.DOWNLOADING;
            case APPLYING -> LocalClientPublicCapabilityModels.InstanceStatus.APPLYING;
            case SUCCEEDED -> LocalClientPublicCapabilityModels.InstanceStatus.SUCCEEDED;
            case FAILED -> LocalClientPublicCapabilityModels.InstanceStatus.FAILED;
            case ROLLED_BACK -> LocalClientPublicCapabilityModels.InstanceStatus.ROLLED_BACK;
        };
    }

    private static LocalClientPayloads.PublicCapabilityUpdateStatusAck ack(
            LocalClientPublicCapabilityModels.Attempt attempt,
            LocalClientPublicCapabilityModels.AttemptStatus status) {
        return new LocalClientPayloads.PublicCapabilityUpdateStatusAck(
                attempt.commandId(), attempt.clientInstanceId().value(), attempt.connectionGeneration(), status.name());
    }

    private static void requireCoordinates(
            LocalClientInstanceId instanceId,
            long generation,
            String payloadInstanceId,
            long payloadGeneration) {
        if (!instanceId.value().equals(payloadInstanceId) || generation != payloadGeneration) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共能力帧 generation 已失效");
        }
    }

    private static Instant requireObservedAt(Instant observedAt) {
        if (observedAt == null) {
            throw validation("公共能力状态缺少上报时间");
        }
        return observedAt;
    }

    private static String requiredDigest(String digest) {
        String normalized = optionalDigest(digest);
        if (normalized == null) {
            throw validation("公共能力摘要无效");
        }
        return normalized;
    }

    private static String optionalDigest(String digest) {
        if (digest == null || digest.isBlank()) {
            return null;
        }
        String normalized = digest.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw validation("公共能力摘要无效");
        }
        return normalized;
    }

    private static String optionalCommit(String commit) {
        if (commit == null || commit.isBlank()) {
            return null;
        }
        String normalized = commit.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40,64}")) {
            throw validation("公共能力 commit 无效");
        }
        return normalized;
    }

    private static String safeError(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        String normalized = errorCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 128 || !normalized.matches("[A-Z0-9_]+")) {
            throw validation("公共能力错误码无效");
        }
        return normalized;
    }

    private static String requestId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static PlatformException validation(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record UpdateRequestResult(String commandId, String status, String targetDigest) {
    }
}
