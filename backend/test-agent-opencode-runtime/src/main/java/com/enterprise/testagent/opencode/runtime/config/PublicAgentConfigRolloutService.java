package com.enterprise.testagent.opencode.runtime.config;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutCoordinator;
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
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final int CLAIM_LIMIT = 20;
    private static final Duration TARGET_LEASE = Duration.ofSeconds(30);
    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(10);

    private final PublicAgentConfigRolloutRepository repository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final AgentRuntime runtime;
    private final BackendInstanceIdentity backendInstanceIdentity;
    private final Duration retryDelay;

    public PublicAgentConfigRolloutService(
            PublicAgentConfigRolloutRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            AgentRuntimeRegistry runtimeRegistry,
            BackendInstanceIdentity backendInstanceIdentity,
            @Value("${test-agent.public-agent-config.rollout.retry-delay-ms:5000}") long retryDelayMillis) {
        this.repository = repository;
        this.heartbeatStore = heartbeatStore;
        this.runtime = runtimeRegistry.require(AgentRuntimeRegistry.DEFAULT_AGENT_ID);
        this.backendInstanceIdentity = backendInstanceIdentity;
        this.retryDelay = Duration.ofMillis(Math.max(1000L, retryDelayMillis));
    }

    /**
     * 推送成功后立即建立持久化闸门，并登记当前应参与同步的服务器。
     */
    @Override
    @Transactional
    public String begin(String branch, String commitHash, String localLinuxServerId, String traceId) {
        repository.findActiveRolloutId().ifPresent(active -> {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "已有公共 Agent/Skill 配置发布正在排空",
                    Map.of("rolloutId", active));
        });
        String rolloutId = RuntimeIdGenerator.publicAgentConfigRolloutId();
        Instant now = Instant.now();
        repository.createRollout(rolloutId, branch, commitHash, traceId, now);

        List<ManagerRuntimeSnapshot> snapshots = heartbeatStore.liveManagerSnapshots();
        Set<String> serverIds = new LinkedHashSet<>();
        serverIds.add(localLinuxServerId);
        Set<LinuxServerId> liveBackendServerIds = heartbeatStore.liveBackendServerIds();
        if (liveBackendServerIds != null) {
            liveBackendServerIds.forEach(serverId -> serverIds.add(serverId.value()));
        }
        snapshots.forEach(snapshot -> serverIds.add(snapshot.container().linuxServerId().value()));
        serverIds.forEach(serverId -> repository.addServer(rolloutId, serverId, now));

        return rolloutId;
    }

    @Override
    @Transactional
    public void markServerSynced(String rolloutId, String linuxServerId, String traceId) {
        Instant now = Instant.now();
        // 每台服务器完成 Git 更新后才采集本机 manager 进程清单，确保表中对应的是切换窗口内的存量实例。
        snapshotServerTargets(rolloutId, linuxServerId, now);
        repository.markServerSynced(rolloutId, linuxServerId, now);
        repository.completeReadyRollouts(now);
    }

    @Override
    public Optional<PublicAgentConfigRolloutSyncRequest> pendingSync(String linuxServerId) {
        return repository.findPendingSync(linuxServerId);
    }

    private void snapshotServerTargets(String rolloutId, String linuxServerId, Instant now) {
        for (ManagerRuntimeSnapshot manager : heartbeatStore.liveManagerSnapshots()) {
            if (!linuxServerId.equals(manager.container().linuxServerId().value())) {
                continue;
            }
            String containerId = manager.container().containerId().value();
            for (ManagedOpencodeProcessSnapshot process : manager.managedProcesses()) {
                if (process.baseUrl() == null || process.baseUrl().isBlank()) {
                    continue;
                }
                repository.addTarget(new PublicAgentConfigRolloutTarget(
                        RuntimeIdGenerator.publicAgentConfigRolloutTargetId(),
                        rolloutId,
                        linuxServerId,
                        containerId,
                        process.port(),
                        process.baseUrl().trim(),
                        0,
                        null), now);
            }
        }
    }

    @Override
    public void fail(String rolloutId, String reason, String traceId) {
        repository.markFailed(rolloutId, safeError(reason), Instant.now());
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
            ExecutionNode node = targetNode(target, now);
            JsonNode sessionStatus = runtime.runtime(new AgentRuntimeCommand(
                            node, "GET", "/session/status", null, null, Map.of(), null,
                            target.rolloutId()))
                    .map(AgentRuntimeResult::body)
                    .block(RUNTIME_TIMEOUT);
            if (hasRunningSession(sessionStatus)) {
                retry(target, "SESSION_RUNNING", now);
                return;
            }
            JsonNode disposed = runtime.runtime(new AgentRuntimeCommand(
                            node, "POST", "/global/dispose", null, null, Map.of(), Map.of(),
                            target.rolloutId()))
                    .map(AgentRuntimeResult::body)
                    .block(RUNTIME_TIMEOUT);
            // 只有 opencode 明确返回 true 才确认释放；空值、非布尔或 false 均进入重试，避免误解除闸门。
            if (disposed == null || !disposed.isBoolean() || !disposed.booleanValue()) {
                retry(target, "DISPOSE_REJECTED", now);
                return;
            }
            repository.markTargetDisposed(target.targetId(), Instant.now());
        } catch (Exception exception) {
            retry(target, safeError(exception.getMessage()), now);
        }
    }

    private void retry(PublicAgentConfigRolloutTarget target, String error, Instant now) {
        int retryCount = target.retryCount() + 1;
        long multiplier = Math.min(retryCount, 6);
        repository.markTargetRetry(
                target.targetId(),
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

    /** Session status 是 sessionId 到状态对象的映射；busy/retry 都表示仍不可排空。 */
    private boolean hasRunningSession(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (type != null && type.isTextual()) {
                String value = type.textValue();
                if ("busy".equalsIgnoreCase(value) || "retry".equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        for (JsonNode child : node) {
            if (hasRunningSession(child)) {
                return true;
            }
        }
        return false;
    }

    private String safeError(String value) {
        String normalized = value == null || value.isBlank() ? "UNKNOWN" : value.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }
}
