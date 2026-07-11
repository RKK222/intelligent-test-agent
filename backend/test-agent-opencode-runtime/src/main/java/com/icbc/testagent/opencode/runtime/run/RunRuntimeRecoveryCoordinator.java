package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunRuntimeInput;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 按本服务器 active 索引恢复 Redis Run。只有公共路由器选中的同服务器 Java 才能取得 owner lease；
 * 远端 dispatch 无法确认时保守释放租约并等待下轮，绝不盲目重发 prompt。
 */
@Service
public class RunRuntimeRecoveryCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunRuntimeRecoveryCoordinator.class);
    private static final Duration ORPHAN_ANCHOR_GRACE = Duration.ofSeconds(30);

    private final RunRuntimeStore runtimeStore;
    private final BackendJavaRouteResolver routeResolver;
    private final RunDispatchAcceptanceProbe acceptanceProbe;
    private final RunRecoveryTakeoverExecutor takeoverExecutor;
    private final RunRepository runRepository;
    private final RunApplicationService runApplicationService;
    private final Clock clock;
    private RunOwnerLeaseSupervisor leaseSupervisor;

    /** 生产装配允许恢复适配器后置接入；缺少适配器时默认 UNKNOWN/no-op，保证不会重发。 */
    @Autowired
    public RunRuntimeRecoveryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            ObjectProvider<RunDispatchAcceptanceProbe> acceptanceProbes,
            ObjectProvider<RunRecoveryTakeoverExecutor> takeoverExecutors,
            RunOwnerLeaseSupervisor leaseSupervisor,
            RunRepository runRepository,
            RunApplicationService runApplicationService) {
        this(
                runtimeStore,
                routeResolver,
                acceptanceProbes.orderedStream().findFirst()
                        .orElse(request -> RunDispatchAcceptance.UNKNOWN),
                takeoverExecutors.orderedStream().findFirst()
                        .orElse((manifest, lease, traceId) -> false),
                runRepository,
                Clock.systemUTC(),
                runApplicationService);
        this.leaseSupervisor = Objects.requireNonNull(leaseSupervisor, "leaseSupervisor must not be null");
    }

    /** 测试和显式装配构造器。 */
    public RunRuntimeRecoveryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            RunDispatchAcceptanceProbe acceptanceProbe,
            RunRecoveryTakeoverExecutor takeoverExecutor) {
        this(runtimeStore, routeResolver, acceptanceProbe, takeoverExecutor, null, Clock.systemUTC(), null);
    }

    /** 测试构造允许固定数据库锚点与时钟，覆盖进程退出窗口。 */
    RunRuntimeRecoveryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            RunDispatchAcceptanceProbe acceptanceProbe,
            RunRecoveryTakeoverExecutor takeoverExecutor,
            RunRepository runRepository,
            Clock clock) {
        this(runtimeStore, routeResolver, acceptanceProbe, takeoverExecutor, runRepository, clock, null);
    }

    /** 测试构造允许注入历史终态补偿服务，验证重启后不会盲目恢复已完成 Run。 */
    RunRuntimeRecoveryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            RunDispatchAcceptanceProbe acceptanceProbe,
            RunRecoveryTakeoverExecutor takeoverExecutor,
            RunRepository runRepository,
            Clock clock,
            RunApplicationService runApplicationService) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.acceptanceProbe = Objects.requireNonNull(acceptanceProbe, "acceptanceProbe must not be null");
        this.takeoverExecutor = Objects.requireNonNull(takeoverExecutor, "takeoverExecutor must not be null");
        this.runRepository = runRepository;
        this.runApplicationService = runApplicationService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.leaseSupervisor = null;
    }

    /** 测试构造允许验证本机已接管 Run 不会被周期扫描重复 adopt。 */
    RunRuntimeRecoveryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            RunDispatchAcceptanceProbe acceptanceProbe,
            RunRecoveryTakeoverExecutor takeoverExecutor,
            RunOwnerLeaseSupervisor leaseSupervisor) {
        this(runtimeStore, routeResolver, acceptanceProbe, takeoverExecutor, null, Clock.systemUTC());
        this.leaseSupervisor = Objects.requireNonNull(leaseSupervisor, "leaseSupervisor must not be null");
    }

    /** 扫描并尝试接管当前服务器 active Run；单个 Run 失败不会中断其余候选。 */
    public Result recoverCurrentServer(String traceId, BooleanSupplier stopRequested) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        BooleanSupplier shouldStop = stopRequested == null ? () -> false : stopRequested;
        String serverId = routeResolver.currentLinuxServerIdValue();
        String currentProcessId = routeResolver.currentBackendProcessIdValue();
        // 同一 JVM 的初始执行、恢复和到期收敛必须使用相同 owner，才能安全复用并最终释放当前订阅。
        String recoveryOwnerId = currentProcessId;
        Counter counter = new Counter();
        for (RunRuntimeManifest manifest : runtimeStore.findActiveByServer(serverId)) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            counter.scannedCount++;
            recoverOne(manifest, recoveryOwnerId, traceId.trim(), counter);
        }
        return counter.result();
    }

    private void recoverOne(
            RunRuntimeManifest manifest,
            String recoveryOwnerId,
            String traceId,
            Counter counter) {
        if (manifest.storageMode() != RunStorageMode.REDIS_SUMMARY || !manifest.active()) {
            counter.ineligibleSkippedCount++;
            return;
        }
        // 当前进程创建的 Run 已由 RunApplicationService 持续续租，避免启动扫描与首次派发发生竞态。
        if (leaseSupervisor != null && leaseSupervisor.isOwned(manifest.runId())) {
            counter.currentOwnerSkippedCount++;
            return;
        }
        RunOwnerLease lease = null;
        try {
            // remoteTarget 在同服务器选中其它 Java 时也返回目标服务器，因此不能只比较 serverId。
            if (routeResolver.remoteTarget(manifest.producerLinuxServerId()).isPresent()) {
                counter.routedAwayCount++;
                return;
            }
            if (reconcileDatabaseAnchor(manifest, recoveryOwnerId, traceId, counter)) {
                return;
            }
            if (reconcileRemoteFinalMessage(manifest, traceId, counter)) {
                return;
            }
            Optional<RunDispatchProbeRequest> request = probeRequest(manifest, traceId);
            if (request.isEmpty()) {
                counter.unknownSkippedCount++;
                return;
            }
            RunDispatchAcceptance acceptance = request
                    .map(acceptanceProbe::probe)
                    .orElse(RunDispatchAcceptance.UNKNOWN);
            if (acceptance == null) {
                acceptance = RunDispatchAcceptance.UNKNOWN;
            }
            switch (acceptance) {
                case ACCEPTED -> {
                    // OpenCode 只读探测可能分页或超时；确认已接收后才占用 15 秒执行租约。
                    // 远端探测可能分页或耗时；只接管探测期间仍未变化的 active manifest。
                    Optional<RunOwnerLease> claimed = runtimeStore.claimOwnerLeaseIfUnchanged(
                            manifest, recoveryOwnerId);
                    if (claimed.isEmpty()) {
                        counter.leaseBusyCount++;
                        return;
                    }
                    lease = claimed.orElseThrow();
                    if (takeoverExecutor.resumeAcceptedRun(manifest, lease, traceId)) {
                        counter.resumedCount++;
                        return;
                    }
                    counter.executorUnavailableCount++;
                }
                case NOT_ACCEPTED -> {
                    Optional<RunOwnerLease> claimed = runtimeStore.claimOwnerLeaseIfUnchanged(
                            manifest, recoveryOwnerId);
                    if (claimed.isEmpty()) {
                        counter.leaseBusyCount++;
                        return;
                    }
                    lease = claimed.orElseThrow();
                    if (takeoverExecutor.failUnacceptedRun(manifest, lease, traceId)) {
                        counter.notAcceptedConvergedCount++;
                        return;
                    }
                    counter.notAcceptedSkippedCount++;
                    counter.executorUnavailableCount++;
                }
                case UNKNOWN -> counter.unknownSkippedCount++;
            }
        } catch (RuntimeException exception) {
            counter.failedCount++;
            LOGGER.warn(
                    "Redis Run 恢复失败，等待下轮重试，runId={}, traceId={}, exceptionType={}",
                    manifest.runId().value(), traceId, exception.getClass().getSimpleName());
        }
        releaseBestEffort(lease, traceId);
    }

    /**
     * Redis manifest 仍是 active 时，先读远端最新 assistant 终态；重启前已完成的 Run 不应重新等待 SSE 或人工终止。
     */
    private boolean reconcileRemoteFinalMessage(
            RunRuntimeManifest manifest,
            String traceId,
            Counter counter) {
        if (runApplicationService == null) {
            return false;
        }
        try {
            if (runApplicationService.findActiveRun(manifest.sessionId()).isEmpty()) {
                counter.databaseTerminalConvergedCount++;
                return true;
            }
        } catch (RuntimeException exception) {
            LOGGER.debug(
                    "历史 Run 终态补偿探测失败，继续执行安全接管探针，runId={}, traceId={}, exceptionType={}",
                    manifest.runId().value(),
                    traceId,
                    exception.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * 仅故障恢复扫描读取一次 PostgreSQL 控制面：数据库已终态时禁止重新接管；锚点缺失说明
     * Java 在 initialize 与 INSERT 之间退出，保护期结束后可安全删除，因为此时尚未发生远端副作用。
     */
    private boolean reconcileDatabaseAnchor(
            RunRuntimeManifest manifest,
            String recoveryOwnerId,
            String traceId,
            Counter counter) {
        if (runRepository == null) {
            return false;
        }
        Optional<Run> persisted = runRepository.findById(manifest.runId());
        if (persisted.isEmpty()) {
            if (clock.instant().isBefore(manifest.createdAt().plus(ORPHAN_ANCHOR_GRACE))) {
                counter.anchorPendingSkippedCount++;
                return true;
            }
            runtimeStore.discardBeforeDispatch(manifest.runId());
            counter.orphanManifestDiscardedCount++;
            return true;
        }
        Run current = persisted.orElseThrow();
        if (!current.status().isTerminal()) {
            return false;
        }
        Optional<RunOwnerLease> claimed = runtimeStore.claimOwnerLeaseIfUnchanged(manifest, recoveryOwnerId);
        if (claimed.isEmpty()) {
            counter.leaseBusyCount++;
            return true;
        }
        RunOwnerLease lease = claimed.orElseThrow();
        try {
            runtimeStore.updateStatus(
                    manifest.runId(), current.status(), manifest.statusVersion(), null);
            counter.databaseTerminalConvergedCount++;
        } finally {
            releaseBestEffort(lease, traceId);
        }
        return true;
    }

    private Optional<RunDispatchProbeRequest> probeRequest(RunRuntimeManifest manifest, String traceId) {
        if (isBlank(manifest.rootRemoteSessionId()) || isBlank(manifest.dispatchMessageId())) {
            return Optional.empty();
        }
        return runtimeStore.findInput(manifest.runId())
                .map(RunRuntimeInput::executionNodeBaseUrl)
                .filter(baseUrl -> !isBlank(baseUrl))
                .map(baseUrl -> new RunDispatchProbeRequest(
                        manifest.runId(), manifest.agentId(), manifest.rootRemoteSessionId(),
                        manifest.dispatchMessageId(), manifest.executionNodeId(), baseUrl,
                        manifest.opencodeProcessId(), manifest.producerLinuxServerId(), traceId));
    }

    private void releaseBestEffort(RunOwnerLease lease, String traceId) {
        if (lease == null) {
            return;
        }
        try {
            runtimeStore.releaseOwnerLease(lease);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "释放 Run owner lease 失败，依赖 15 秒 TTL 自动过期，runId={}, fencingToken={}, traceId={}, exceptionType={}",
                    lease.runId().value(), lease.fencingToken(), traceId, exception.getClass().getSimpleName());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 单轮恢复统计，可直接写入 scheduler 结果而不包含 prompt 或远端内容。 */
    public record Result(
            int scannedCount,
            int ineligibleSkippedCount,
            int currentOwnerSkippedCount,
            int routedAwayCount,
            int leaseBusyCount,
            int resumedCount,
            int executorUnavailableCount,
            int databaseTerminalConvergedCount,
            int orphanManifestDiscardedCount,
            int anchorPendingSkippedCount,
            int notAcceptedConvergedCount,
            int notAcceptedSkippedCount,
            int unknownSkippedCount,
            int failedCount) {

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("scannedCount", scannedCount);
            values.put("ineligibleSkippedCount", ineligibleSkippedCount);
            values.put("currentOwnerSkippedCount", currentOwnerSkippedCount);
            values.put("routedAwayCount", routedAwayCount);
            values.put("leaseBusyCount", leaseBusyCount);
            values.put("resumedCount", resumedCount);
            values.put("executorUnavailableCount", executorUnavailableCount);
            values.put("databaseTerminalConvergedCount", databaseTerminalConvergedCount);
            values.put("orphanManifestDiscardedCount", orphanManifestDiscardedCount);
            values.put("anchorPendingSkippedCount", anchorPendingSkippedCount);
            values.put("notAcceptedConvergedCount", notAcceptedConvergedCount);
            values.put("notAcceptedSkippedCount", notAcceptedSkippedCount);
            values.put("unknownSkippedCount", unknownSkippedCount);
            values.put("failedCount", failedCount);
            return Map.copyOf(values);
        }
    }

    private static final class Counter {
        private int scannedCount;
        private int ineligibleSkippedCount;
        private int currentOwnerSkippedCount;
        private int routedAwayCount;
        private int leaseBusyCount;
        private int resumedCount;
        private int executorUnavailableCount;
        private int databaseTerminalConvergedCount;
        private int orphanManifestDiscardedCount;
        private int anchorPendingSkippedCount;
        private int notAcceptedConvergedCount;
        private int notAcceptedSkippedCount;
        private int unknownSkippedCount;
        private int failedCount;

        private Result result() {
            return new Result(
                    scannedCount,
                    ineligibleSkippedCount,
                    currentOwnerSkippedCount,
                    routedAwayCount,
                    leaseBusyCount,
                    resumedCount,
                    executorUnavailableCount,
                    databaseTerminalConvergedCount,
                    orphanManifestDiscardedCount,
                    anchorPendingSkippedCount,
                    notAcceptedConvergedCount,
                    notAcceptedSkippedCount,
                    unknownSkippedCount,
                    failedCount);
        }
    }
}
