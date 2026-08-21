package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 本地客户端 attempt 终态事务门面：CAS、rollout 行锁、attempt 汇总和关联投影必须原子提交。
 */
@Service
public class LocalClientUpdateTerminalService {

    private final LocalClientVersionRepository versionRepository;
    private final LocalClientInstanceRepository instanceRepository;
    private final UserNotificationApplicationService notifications;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /** 生产构造器显式要求平台事务管理器，禁止终态逻辑在无事务环境静默退化。 */
    public LocalClientUpdateTerminalService(
            LocalClientVersionRepository versionRepository,
            LocalClientInstanceRepository instanceRepository,
            UserNotificationApplicationService notifications,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository must not be null");
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.notifications = Objects.requireNonNull(notifications, "notifications must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 以 attempt 当前状态为 CAS 前提写普通终态；CAS 失败表示其它并发请求已取得终态写入权。
     */
    public TerminalResult finalizeAttempt(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus target,
            String errorCode,
            String traceId) {
        requireTerminalArguments(attempt, target);
        return inTransaction(() -> finalizeExpected(
                attempt,
                attempt.status(),
                target,
                errorCode,
                traceId));
    }

    /**
     * 纠正平台 deadline 占位失败；CAS 竞争失败时只接受数据库已经落成相同物理终态。
     */
    public TerminalResult convergeDeadlineFailure(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus target,
            String errorCode,
            String traceId) {
        requireTerminalArguments(attempt, target);
        if (attempt.status() != LocalClientVersionModels.AttemptStatus.FAILED
                || !"DELIVERY_DEADLINE_EXCEEDED".equals(attempt.errorCode())
                || (target != LocalClientVersionModels.AttemptStatus.SUCCEEDED
                && target != LocalClientVersionModels.AttemptStatus.AUTO_ROLLED_BACK)) {
            throw new PlatformException(ErrorCode.CONFLICT, "deadline 失败只接受迟到成功或自动回滚终态");
        }
        return inTransaction(() -> {
            TerminalResult result = finalizeExpected(
                    attempt,
                    LocalClientVersionModels.AttemptStatus.FAILED,
                    target,
                    errorCode,
                    traceId);
            if (result.persisted()) {
                return result;
            }
            LocalClientVersionModels.Attempt persisted = versionRepository.findAttempt(attempt.commandId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "更新命令不存在"));
            if (persisted.status() == target) {
                return new TerminalResult(false, statusAck(persisted, target));
            }
            throw new PlatformException(ErrorCode.CONFLICT, "更新终态已由其它请求写入");
        });
    }

    private TerminalResult finalizeExpected(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus expected,
            LocalClientVersionModels.AttemptStatus target,
            String errorCode,
            String traceId) {
        assertActualTransaction();
        Instant observedAt = Instant.now(clock);
        if (!versionRepository.transitionAttempt(
                attempt.commandId(),
                expected.name(),
                target.name(),
                attempt.releaseDigest(),
                errorCode,
                observedAt)) {
            return new TerminalResult(false, null);
        }
        instanceRepository.updateLastUpdateStatus(
                attempt.clientInstanceId(), target.name(), attempt.targetVersion(), observedAt);
        if (target == LocalClientVersionModels.AttemptStatus.SUCCEEDED) {
            notifications.invalidateLocalClientUpdate(
                    attempt.userId(), attempt.clientInstanceId().value(), target.name(), traceId);
        }
        finalizeRollout(attempt.rolloutId(), observedAt);
        return new TerminalResult(true, statusAck(attempt, target));
    }

    private void finalizeRollout(String rolloutId, Instant completedAt) {
        // 行锁必须和终态 CAS、全量 attempt 读取、rollout 更新共享同一物理事务。
        versionRepository.findRolloutForUpdate(rolloutId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "客户端更新 rollout 不存在"));
        List<LocalClientVersionModels.Attempt> attempts = versionRepository.findAttemptsByRollout(rolloutId);
        if (attempts.isEmpty() || attempts.stream().anyMatch(attempt -> !attempt.status().terminal())) {
            return;
        }
        boolean failed = attempts.stream().anyMatch(attempt ->
                attempt.status() != LocalClientVersionModels.AttemptStatus.SUCCEEDED);
        versionRepository.completeRollout(
                rolloutId,
                failed
                        ? LocalClientVersionModels.RolloutStatus.PARTIAL_FAILED.name()
                        : LocalClientVersionModels.RolloutStatus.COMPLETED.name(),
                completedAt);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return Objects.requireNonNull(transaction.execute(status -> action.get()));
    }

    private static void assertActualTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("本地客户端更新终态缺少活动事务");
        }
    }

    private static void requireTerminalArguments(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus target) {
        Objects.requireNonNull(attempt, "attempt must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (!target.terminal()) {
            throw new IllegalArgumentException("target must be terminal");
        }
    }

    private static LocalClientPayloads.UpdateStatusAck statusAck(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus persistedStatus) {
        return new LocalClientPayloads.UpdateStatusAck(
                attempt.commandId(),
                attempt.clientInstanceId().value(),
                attempt.connectionGeneration(),
                persistedStatus.name());
    }

    /** persisted 表示本次事务赢得 CAS；ACK 只在持久化事实已等于目标终态时返回。 */
    public record TerminalResult(boolean persisted, LocalClientPayloads.UpdateStatusAck acknowledgement) {
    }
}
