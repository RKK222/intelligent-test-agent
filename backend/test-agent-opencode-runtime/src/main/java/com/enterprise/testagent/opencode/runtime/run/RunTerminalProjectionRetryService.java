package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.run.RunTerminalProjectionResult;
import com.enterprise.testagent.domain.run.RunTerminalRetry;
import com.enterprise.testagent.domain.run.RunTerminalRetryStore;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 从 Redis 读取到期的安全终态投影并重试 PostgreSQL 三语句事务。
 *
 * <p>事务已提交但响应丢失时会返回版本冲突，该结果与成功一样删除重试；数据库继续不可用时只更新 Redis
 * 失败次数和 nextAttemptAt，不把原始详情降级到数据库。
 */
@Service
public class RunTerminalProjectionRetryService {

    static final int BATCH_LIMIT = 200;

    private static final Logger LOGGER = LoggerFactory.getLogger(RunTerminalProjectionRetryService.class);

    private final RunTerminalRetryStore retryStore;
    private final RunSummaryPersistencePort persistencePort;
    private final RunRuntimeStore runtimeStore;
    private final Clock clock;

    /** 保留给既有单元测试和不关联 Run outbox 的兼容构造。 */
    public RunTerminalProjectionRetryService(
            RunTerminalRetryStore retryStore,
            RunSummaryPersistencePort persistencePort,
            Clock clock) {
        this(retryStore, persistencePort, null, clock);
    }

    /** 生产装配同时注入 Run 运行态，成功或版本冲突后确认同一版终态 outbox。 */
    @Autowired
    public RunTerminalProjectionRetryService(
            RunTerminalRetryStore retryStore,
            RunSummaryPersistencePort persistencePort,
            RunRuntimeStore runtimeStore,
            Clock clock) {
        this.retryStore = Objects.requireNonNull(retryStore, "retryStore must not be null");
        this.persistencePort = Objects.requireNonNull(persistencePort, "persistencePort must not be null");
        this.runtimeStore = runtimeStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 每轮最多处理 200 条 due 记录，并在每次数据库调用前响应 scheduler 协作式停止。 */
    public Result retryDue(BooleanSupplier stopRequested) {
        BooleanSupplier safeStopRequested = stopRequested == null ? () -> false : stopRequested;
        Instant scanAt = clock.instant();
        var due = retryStore.findDue(scanAt, BATCH_LIMIT);
        int scanned = 0;
        int applied = 0;
        int versionConflict = 0;
        int rescheduled = 0;
        int expired = 0;
        for (RunTerminalRetry retry : due) {
            if (safeStopRequested.getAsBoolean()) {
                break;
            }
            scanned++;
            Instant attemptAt = clock.instant();
            if (retry.isExpired(attemptAt)) {
                retryStore.delete(retry);
                expired++;
                continue;
            }
            try {
                RunTerminalProjectionResult result = Objects.requireNonNull(
                        persistencePort.persistTerminal(retry.projection()),
                        "persistTerminal result must not be null");
                if (result == RunTerminalProjectionResult.APPLIED) {
                    acknowledgeOutbox(retry);
                    retryStore.delete(retry);
                    applied++;
                } else if (result == RunTerminalProjectionResult.VERSION_CONFLICT) {
                    // 可能是其它执行者已经成功，也可能是事务提交后响应丢失；两者都已无需重复写摘要。
                    acknowledgeOutbox(retry);
                    retryStore.delete(retry);
                    versionConflict++;
                } else {
                    int scheduled = rescheduleOrExpire(retry, attemptAt);
                    rescheduled += scheduled;
                    if (scheduled == 0) {
                        expired++;
                    }
                }
            } catch (RuntimeException databaseFailure) {
                int before = rescheduled;
                rescheduled += rescheduleOrExpire(retry, clock.instant());
                if (rescheduled == before) {
                    expired++;
                }
                LOGGER.warn(
                        "Run terminal projection retry deferred, runId={}, errorType={}",
                        retry.projection().runId().value(),
                        databaseFailure.getClass().getSimpleName());
            }
        }
        return new Result(scanned, applied, versionConflict, rescheduled, expired);
    }

    private void acknowledgeOutbox(RunTerminalRetry retry) {
        if (runtimeStore != null && retry.terminalProjectionVersion() > 0) {
            runtimeStore.ackTerminalProjection(
                    retry.projection().runId(), retry.terminalProjectionVersion());
        }
    }

    /** 返回 1 表示完成重排，返回 0 表示剩余窗口不足并已删除。 */
    private int rescheduleOrExpire(RunTerminalRetry retry, Instant failedAt) {
        Optional<RunTerminalRetry> next = retry.rescheduleAfterFailure(failedAt);
        if (next.isPresent()) {
            retryStore.save(next.orElseThrow());
            return 1;
        }
        retryStore.delete(retry);
        return 0;
    }

    /** scheduler 运行记录只保存低敏计数，不保存投影、错误正文或 Redis 内容。 */
    public record Result(
            int scannedCount,
            int appliedCount,
            int versionConflictCount,
            int rescheduledCount,
            int expiredCount) {

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("scannedCount", scannedCount);
            result.put("appliedCount", appliedCount);
            result.put("versionConflictCount", versionConflictCount);
            result.put("rescheduledCount", rescheduledCount);
            result.put("expiredCount", expiredCount);
            return Map.copyOf(result);
        }
    }
}
