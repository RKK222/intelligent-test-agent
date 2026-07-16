package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunTerminalProjectionPending;
import com.enterprise.testagent.domain.run.RunTerminalProjectionResult;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 恢复“Redis 已终态、PostgreSQL 尚未投影”的 crash window。
 *
 * <p>候选复用终态前已登记的服务器恢复索引；只有公共路由器选中的同服务器 Java 才执行幂等 CAS。
 */
@Service
public class RunTerminalProjectionRecoveryCoordinator {

    static final int BATCH_LIMIT = 200;

    private static final Logger LOGGER = LoggerFactory.getLogger(RunTerminalProjectionRecoveryCoordinator.class);

    private final RunRuntimeStore runtimeStore;
    private final BackendJavaRouteResolver routeResolver;
    private final RunTerminalProjectionService projectionService;

    public RunTerminalProjectionRecoveryCoordinator(
            RunRuntimeStore runtimeStore,
            BackendJavaRouteResolver routeResolver,
            RunTerminalProjectionService projectionService) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService must not be null");
    }

    /** 扫描当前服务器最多 200 条终态 outbox；单条失败保留 pending 并等待下一轮。 */
    public Result recoverCurrentServer(String traceId, BooleanSupplier stopRequested) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        BooleanSupplier shouldStop = stopRequested == null ? () -> false : stopRequested;
        String serverId = routeResolver.currentLinuxServerIdValue();
        Counter counter = new Counter();
        for (RunTerminalProjectionPending pending
                : runtimeStore.findTerminalProjectionPendingByServer(serverId, BATCH_LIMIT)) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            counter.scannedCount++;
            try {
                if (routeResolver.remoteTarget(pending.producerLinuxServerId()).isPresent()) {
                    counter.routedAwayCount++;
                    continue;
                }
                RunTerminalProjectionResult result = projectionService.project(pending);
                if (result == RunTerminalProjectionResult.APPLIED) {
                    counter.appliedCount++;
                } else if (result == RunTerminalProjectionResult.VERSION_CONFLICT) {
                    counter.versionConflictCount++;
                } else {
                    counter.pendingDatabaseCount++;
                }
            } catch (RuntimeException exception) {
                counter.failedCount++;
                LOGGER.warn(
                        "Run 终态 outbox 恢复失败，等待下轮重试，runId={}, version={}, traceId={}, exceptionType={}",
                        pending.runId().value(),
                        pending.version(),
                        traceId,
                        exception.getClass().getSimpleName());
            }
        }
        return counter.result();
    }

    /** 单轮恢复统计不包含 prompt、回答或错误正文。 */
    public record Result(
            int scannedCount,
            int routedAwayCount,
            int appliedCount,
            int versionConflictCount,
            int pendingDatabaseCount,
            int failedCount) {

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("scannedCount", scannedCount);
            result.put("routedAwayCount", routedAwayCount);
            result.put("appliedCount", appliedCount);
            result.put("versionConflictCount", versionConflictCount);
            result.put("pendingDatabaseCount", pendingDatabaseCount);
            result.put("failedCount", failedCount);
            return Map.copyOf(result);
        }
    }

    private static final class Counter {
        private int scannedCount;
        private int routedAwayCount;
        private int appliedCount;
        private int versionConflictCount;
        private int pendingDatabaseCount;
        private int failedCount;

        private Result result() {
            return new Result(
                    scannedCount,
                    routedAwayCount,
                    appliedCount,
                    versionConflictCount,
                    pendingDatabaseCount,
                    failedCount);
        }
    }
}
