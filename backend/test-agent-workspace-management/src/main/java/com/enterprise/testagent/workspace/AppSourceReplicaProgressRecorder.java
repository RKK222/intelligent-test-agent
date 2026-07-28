package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceStepScope;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通过副本活租约推进服务器步骤，并为 lease-CAS 后的同事务终态提供内部收敛入口。 */
@Service
public class AppSourceReplicaProgressRecorder {

    private final AppSourceRepository appSources;

    public AppSourceReplicaProgressRecorder(AppSourceRepository appSources) {
        this.appSources = Objects.requireNonNull(appSources, "appSources must not be null");
    }

    /** 新 lease attempt 开始前重置整条稳定时间线，清除上一 attempt 的时间和终态。 */
    @Transactional
    public void resetForAttempt(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            Instant now) {
        requireLeaseLock(operation, claimed, leaseOwner, now);
        for (AppSourceOperationStep legacy : serverSteps(operation, claimed)) {
            if (!AppSourceReplicaStepCatalog.codes().contains(legacy.stepCode())
                    && !terminal(legacy.status())) {
                AppSourceOperationStep skipped = new AppSourceOperationStep(
                        legacy.stepId(), legacy.operationId(), legacy.scope(), legacy.linuxServerId(),
                        legacy.stepCode(), legacy.sequence(), AppSourceStepStatus.SKIPPED,
                        "已跳过：兼容旧版服务器步骤", legacy.startedAt(), now, now);
                requireLeaseTransition(operation, claimed, leaseOwner, skipped, now);
            }
        }
        for (AppSourceOperationStep pending : AppSourceReplicaStepCatalog.pendingSteps(
                operation.operationId(), claimed.linuxServerId(), now)) {
            if (!appSources.resetStepIfReplicaLease(
                    pending, operation.repositoryId(), operation.targetGeneration(), claimed.linuxServerId(),
                    leaseOwner, now)) {
                throw leaseLost("应用源码步骤 attempt 重置租约已失效");
            }
        }
    }

    /** 动作开始前把步骤从 PENDING 推进为 RUNNING。 */
    @Transactional
    public void start(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            String stepCode,
            Instant now) {
        requireLeaseLock(operation, claimed, leaseOwner, now);
        AppSourceOperationStep current = requireStep(operation, claimed, stepCode);
        requireLeaseTransition(operation, claimed, leaseOwner, transition(current, AppSourceStepStatus.RUNNING, now), now);
    }

    /** 动作完成后把当前 RUNNING 步骤推进为 SUCCEEDED。 */
    @Transactional
    public void succeed(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            String stepCode,
            Instant now) {
        requireLeaseLock(operation, claimed, leaseOwner, now);
        AppSourceOperationStep current = requireStep(operation, claimed, stepCode);
        requireLeaseTransition(operation, claimed, leaseOwner, transition(current, AppSourceStepStatus.SUCCEEDED, now), now);
    }

    /** 当前动作失败时写 FAILED，并把同服务器全部后续非终态步骤写成 SKIPPED。 */
    @Transactional
    public void failCurrentAndSkipFollowing(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            String stepCode,
            Instant now) {
        requireLeaseLock(operation, claimed, leaseOwner, now);
        List<AppSourceOperationStep> serverSteps = serverSteps(operation, claimed);
        AppSourceOperationStep current = serverSteps.stream()
                .filter(step -> step.stepCode().equals(stepCode))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码服务器步骤不存在"));
        for (AppSourceOperationStep step : serverSteps) {
            if (step.sequence() < current.sequence() || terminal(step.status())) {
                continue;
            }
            AppSourceStepStatus terminal = step.sequence() == current.sequence()
                    ? AppSourceStepStatus.FAILED
                    : AppSourceStepStatus.SKIPPED;
            requireLeaseTransition(operation, claimed, leaseOwner, transition(step, terminal, now), now);
        }
    }

    /**
     * 只允许 result recorder 在副本 lease-CAS 已成功后、同一事务内调用。
     * 此时副本租约已被清空，不能再执行 lease-fenced SQL；外层事务回滚会同时撤销 CAS 与步骤更新。
     */
    void completeAfterLeaseCas(AppSourceOperation operation, AppSourceReplica claimed, Instant now) {
        for (AppSourceOperationStep step : serverSteps(operation, claimed)) {
            if (!terminal(step.status())) {
                completeWithoutLease(step, AppSourceStepStatus.SUCCEEDED, now);
            }
        }
    }

    /** lease-CAS 后兜底保证失败 operation 不残留 PENDING/RUNNING 服务器步骤。 */
    void failAfterLeaseCas(AppSourceOperation operation, AppSourceReplica claimed, Instant now) {
        List<AppSourceOperationStep> steps = serverSteps(operation, claimed);
        AppSourceOperationStep failing = steps.stream()
                .filter(step -> !terminal(step.status()))
                .filter(step -> step.status() == AppSourceStepStatus.RUNNING)
                .findFirst()
                .orElseGet(() -> steps.stream().filter(step -> !terminal(step.status())).findFirst().orElse(null));
        for (AppSourceOperationStep step : steps) {
            if (terminal(step.status())) {
                continue;
            }
            completeWithoutLease(
                    step,
                    failing != null && step.stepId().equals(failing.stepId())
                            ? AppSourceStepStatus.FAILED
                            : AppSourceStepStatus.SKIPPED,
                    now);
        }
    }

    private void completeWithoutLease(
            AppSourceOperationStep step, AppSourceStepStatus terminalStatus, Instant now) {
        AppSourceOperationStep latest = step;
        if (latest.status() == AppSourceStepStatus.PENDING && terminalStatus == AppSourceStepStatus.SUCCEEDED) {
            latest = transition(latest, AppSourceStepStatus.RUNNING, now);
            if (!appSources.upsertStep(latest)) {
                throw new PlatformException(ErrorCode.CONFLICT, "应用源码步骤终态收敛失败");
            }
        }
        if (!appSources.upsertStep(transition(latest, terminalStatus, now))) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码步骤终态收敛失败");
        }
    }

    private AppSourceOperationStep requireStep(
            AppSourceOperation operation, AppSourceReplica claimed, String stepCode) {
        return serverSteps(operation, claimed).stream()
                .filter(step -> step.stepCode().equals(stepCode))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码服务器步骤不存在"));
    }

    private List<AppSourceOperationStep> serverSteps(
            AppSourceOperation operation, AppSourceReplica claimed) {
        return appSources.findSteps(operation.operationId()).stream()
                .filter(step -> step.scope() == AppSourceStepScope.SERVER)
                .filter(step -> step.linuxServerId().equals(claimed.linuxServerId()))
                .sorted(Comparator.comparingInt(AppSourceOperationStep::sequence))
                .toList();
    }

    private void requireLeaseTransition(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            AppSourceOperationStep next,
            Instant now) {
        if (!appSources.updateStepIfReplicaLease(
                next, operation.repositoryId(), operation.targetGeneration(), claimed.linuxServerId(),
                leaseOwner, now)) {
            throw leaseLost("应用源码步骤租约已失效");
        }
    }

    private void requireLeaseLock(
            AppSourceOperation operation,
            AppSourceReplica claimed,
            String leaseOwner,
            Instant now) {
        if (!appSources.lockReplicaLeaseForUpdate(
                operation.repositoryId(), operation.targetGeneration(), claimed.linuxServerId(),
                leaseOwner, now)) {
            throw leaseLost("应用源码步骤租约已失效");
        }
    }

    private PlatformException leaseLost(String message) {
        return new PlatformException(
                ErrorCode.CONFLICT, message, Map.of("failure", "REPLICA_LEASE_LOST"));
    }

    private AppSourceOperationStep transition(
            AppSourceOperationStep current, AppSourceStepStatus status, Instant now) {
        Instant startedAt = current.startedAt();
        Instant completedAt = null;
        if (status == AppSourceStepStatus.RUNNING && startedAt == null) {
            startedAt = now;
        } else if (terminal(status)) {
            completedAt = now;
            if (status == AppSourceStepStatus.FAILED && startedAt == null) {
                startedAt = now;
            }
        }
        return new AppSourceOperationStep(
                current.stepId(), current.operationId(), current.scope(), current.linuxServerId(),
                current.stepCode(), current.sequence(), status,
                safeSummary(current.stepCode(), status),
                startedAt, completedAt, now);
    }

    /** 增量发布中的未知旧步骤只能使用固定低敏摘要，不能因目录外 code 阻断终态恢复。 */
    private String safeSummary(String stepCode, AppSourceStepStatus status) {
        if (AppSourceReplicaStepCatalog.codes().contains(stepCode)) {
            return AppSourceReplicaStepCatalog.summary(stepCode, status);
        }
        return switch (status) {
            case PENDING -> "等待：兼容旧版服务器步骤";
            case RUNNING -> "正在执行：兼容旧版服务器步骤";
            case SUCCEEDED -> "已完成：兼容旧版服务器步骤";
            case FAILED -> "执行失败：兼容旧版服务器步骤";
            case SKIPPED -> "已跳过：兼容旧版服务器步骤";
        };
    }

    private boolean terminal(AppSourceStepStatus status) {
        return status == AppSourceStepStatus.SUCCEEDED
                || status == AppSourceStepStatus.FAILED
                || status == AppSourceStepStatus.SKIPPED;
    }
}
