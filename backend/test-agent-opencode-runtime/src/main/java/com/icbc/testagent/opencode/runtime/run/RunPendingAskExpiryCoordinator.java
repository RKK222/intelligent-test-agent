package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Clock;
import java.time.Instant;
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
 * 扫描本服务器 Redis active 索引并收敛等待满 7 天的 permission/question。
 *
 * <p>active 索引只用于发现候选；协调器仍回读 manifest 条件、复用公共 Java 路由选择，并竞争带 fencing
 * token 的 owner lease，保证同服务器多 Java 不会重复执行终态副作用。
 */
@Service
public class RunPendingAskExpiryCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunPendingAskExpiryCoordinator.class);

    private final RunRuntimeStore runtimeStore;
    private final BackendJavaRouteResolver routeResolver;
    private final RunPendingAskExpiryExecutor executor;
    private final Clock clock;

    /** 生产执行器允许后置装配；没有实现时保持 no-op，等待下一轮安全重试。 */
    @Autowired
    public RunPendingAskExpiryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            ObjectProvider<RunPendingAskExpiryExecutor> executors,
            Clock clock) {
        this(
                runtimeStore,
                routeResolver,
                executors.orderedStream().findFirst()
                        .orElse((manifest, lease, traceId) -> false),
                clock);
    }

    /** 测试和显式装配构造器。 */
    public RunPendingAskExpiryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            RunPendingAskExpiryExecutor executor,
            Clock clock) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 扫描当前服务器并尝试收敛超期待处理 Run；单个候选失败不会阻断同轮其它 Run。 */
    public Result expireCurrentServer(String traceId, BooleanSupplier stopRequested) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        BooleanSupplier shouldStop = stopRequested == null ? () -> false : stopRequested;
        String serverId = routeResolver.currentLinuxServerIdValue();
        String ownerId = routeResolver.currentBackendProcessIdValue();
        Instant cutoff = clock.instant().minus(RunRuntimeStore.PENDING_ASK_TTL);
        Counter counter = new Counter();
        for (RunRuntimeManifest manifest : runtimeStore.findActiveByServer(serverId)) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            counter.scannedCount++;
            expireOne(manifest, ownerId, cutoff, traceId.trim(), counter);
        }
        return counter.result();
    }

    private void expireOne(
            RunRuntimeManifest manifest,
            String ownerId,
            Instant cutoff,
            String traceId,
            Counter counter) {
        if (!eligible(manifest, cutoff)) {
            counter.ineligibleSkippedCount++;
            return;
        }
        RunOwnerLease lease = null;
        try {
            // 同一 linuxServerId 上仍可能有多个 Java，必须由公共 resolver 选中的 Java 执行。
            if (routeResolver.remoteTarget(manifest.producerLinuxServerId()).isPresent()) {
                counter.routedAwayCount++;
                return;
            }
            // eligibility 与 claim 必须在 Redis 同一 Lua 内校验；用户回复不能落在扫描与接管之间被误取消。
            Optional<RunOwnerLease> claimed = runtimeStore.claimOwnerLeaseIfUnchanged(manifest, ownerId);
            if (claimed.isEmpty()) {
                counter.leaseBusyCount++;
                return;
            }
            lease = claimed.orElseThrow();
            if (executor.expirePendingAsk(manifest, lease, traceId)) {
                counter.expiredCount++;
                return;
            }
            counter.executorUnavailableCount++;
        } catch (RuntimeException exception) {
            counter.failedCount++;
            LOGGER.warn(
                    "超期待处理 Run 收敛失败，等待下轮重试，runId={}, traceId={}, exceptionType={}",
                    manifest.runId().value(), traceId, exception.getClass().getSimpleName());
        }
        releaseBestEffort(lease, traceId);
    }

    private boolean eligible(RunRuntimeManifest manifest, Instant cutoff) {
        return manifest.storageMode() == RunStorageMode.REDIS_SUMMARY
                && manifest.active()
                && manifest.attention() != null
                && !manifest.attention().isBlank()
                && manifest.attentionAt() != null
                && !manifest.attentionAt().isAfter(cutoff);
    }

    private void releaseBestEffort(RunOwnerLease lease, String traceId) {
        if (lease == null) {
            return;
        }
        try {
            runtimeStore.releaseOwnerLease(lease);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "释放超期待处理 Run owner lease 失败，等待 TTL 自动过期，runId={}, fencingToken={}, traceId={}, exceptionType={}",
                    lease.runId().value(), lease.fencingToken(), traceId, exception.getClass().getSimpleName());
        }
    }

    /** 单轮扫描统计，不包含 prompt、问题正文或 permission 参数。 */
    public record Result(
            int scannedCount,
            int ineligibleSkippedCount,
            int routedAwayCount,
            int leaseBusyCount,
            int expiredCount,
            int executorUnavailableCount,
            int failedCount) {

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("scannedCount", scannedCount);
            values.put("ineligibleSkippedCount", ineligibleSkippedCount);
            values.put("routedAwayCount", routedAwayCount);
            values.put("leaseBusyCount", leaseBusyCount);
            values.put("expiredCount", expiredCount);
            values.put("executorUnavailableCount", executorUnavailableCount);
            values.put("failedCount", failedCount);
            return Map.copyOf(values);
        }
    }

    private static final class Counter {
        private int scannedCount;
        private int ineligibleSkippedCount;
        private int routedAwayCount;
        private int leaseBusyCount;
        private int expiredCount;
        private int executorUnavailableCount;
        private int failedCount;

        private Result result() {
            return new Result(
                    scannedCount,
                    ineligibleSkippedCount,
                    routedAwayCount,
                    leaseBusyCount,
                    expiredCount,
                    executorUnavailableCount,
                    failedCount);
        }
    }
}
