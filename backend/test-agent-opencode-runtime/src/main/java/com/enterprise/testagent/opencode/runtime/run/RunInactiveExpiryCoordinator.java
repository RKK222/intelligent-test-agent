package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 扫描 Redis manifest；普通 Run 两小时无任何事件且没有 pending ask 时执行终态收敛。 */
@Service
public class RunInactiveExpiryCoordinator {

    public static final Duration INACTIVE_TIMEOUT = Duration.ofHours(2);

    private static final Logger LOGGER = LoggerFactory.getLogger(RunInactiveExpiryCoordinator.class);

    private final RunRuntimeStore runtimeStore;
    private final BackendJavaRouteResolver routeResolver;
    private final RunInactiveExpiryExecutor executor;
    private final Clock clock;

    @Autowired
    public RunInactiveExpiryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            ObjectProvider<RunInactiveExpiryExecutor> executors,
            Clock clock) {
        this(
                runtimeStore,
                routeResolver,
                executors.orderedStream().findFirst()
                        .orElse((manifest, lease, traceId) -> false),
                clock);
    }

    RunInactiveExpiryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            RunInactiveExpiryExecutor executor,
            Clock clock) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 每轮只读取 Redis active 索引/manifest；无到期候选时不访问 PostgreSQL。 */
    public Result expireCurrentServer(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        String serverId = routeResolver.currentLinuxServerIdValue();
        String ownerId = routeResolver.currentBackendProcessIdValue();
        Instant cutoff = clock.instant().minus(INACTIVE_TIMEOUT);
        AtomicInteger scanned = new AtomicInteger();
        AtomicInteger expired = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (RunRuntimeManifest manifest : runtimeStore.findActiveByServer(serverId)) {
            scanned.incrementAndGet();
            if (!eligible(manifest, cutoff)) {
                skipped.incrementAndGet();
                continue;
            }
            RunOwnerLease lease = null;
            boolean handedOff = false;
            try {
                if (routeResolver.remoteTarget(manifest.producerLinuxServerId()).isPresent()) {
                    skipped.incrementAndGet();
                    continue;
                }
                // 新事件刷新 manifest 后，扫描得到的旧快照必须原子失效，不能误杀已恢复活动的 Run。
                Optional<RunOwnerLease> claimed = runtimeStore.claimOwnerLeaseIfUnchanged(manifest, ownerId);
                if (claimed.isEmpty()) {
                    skipped.incrementAndGet();
                    continue;
                }
                lease = claimed.orElseThrow();
                handedOff = executor.expireInactiveRun(manifest, lease, traceId.trim());
                if (handedOff) {
                    expired.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
            } catch (RuntimeException exception) {
                failed.incrementAndGet();
                LOGGER.warn(
                        "Inactive Redis Run expiry deferred, runId={}, errorType={}, traceId={}",
                        manifest.runId().value(),
                        exception.getClass().getSimpleName(),
                        traceId);
            } finally {
                if (lease != null && !handedOff) {
                    try {
                        runtimeStore.releaseOwnerLease(lease);
                    } catch (RuntimeException exception) {
                        LOGGER.warn(
                                "Inactive Redis Run lease release deferred, runId={}, errorType={}, traceId={}",
                                manifest.runId().value(),
                                exception.getClass().getSimpleName(),
                                traceId);
                    }
                }
            }
        }
        return new Result(scanned.get(), expired.get(), skipped.get(), failed.get());
    }

    private boolean eligible(RunRuntimeManifest manifest, Instant cutoff) {
        return manifest.storageMode() == RunStorageMode.REDIS_SUMMARY
                && manifest.active()
                && (manifest.attention() == null || manifest.attention().isBlank())
                && !manifest.updatedAt().isAfter(cutoff);
    }

    public record Result(int scannedCount, int expiredCount, int skippedCount, int failedCount) {
    }
}
