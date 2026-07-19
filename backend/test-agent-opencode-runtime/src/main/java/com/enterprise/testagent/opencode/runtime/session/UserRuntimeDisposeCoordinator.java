package com.enterprise.testagent.opencode.runtime.session;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 用户级 OpenCode 运行态释放闸门。
 *
 * <p>申请闸门时由 Redis 原子确认 active Run 索引为空，随后再用用户级会话摘要兜底检查兼容链路；
 * Run 启动入口同时检查同一闸门，避免前端只检查当前标签页造成跨会话 dispose 竞态。
 */
@Service
public class UserRuntimeDisposeCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRuntimeDisposeCoordinator.class);
    // OpenCode client 最长 30 秒且允许一次重试；两分钟初始 TTL 覆盖完整调用上限，期间每 30 秒续租。
    private static final Duration DISPOSE_LEASE_TTL = Duration.ofMinutes(2);
    private static final Duration DISPOSE_LEASE_RENEW_INTERVAL = Duration.ofSeconds(30);

    private final RunRuntimeStore runRuntimeStore;
    private final SessionRuntimeStateApplicationService sessionRuntimeStateService;
    private final Duration leaseTtl;
    private final Duration renewInterval;
    private final Scheduler renewScheduler;

    @Autowired
    public UserRuntimeDisposeCoordinator(
            RunRuntimeStore runRuntimeStore,
            SessionRuntimeStateApplicationService sessionRuntimeStateService) {
        this(
                runRuntimeStore,
                sessionRuntimeStateService,
                DISPOSE_LEASE_TTL,
                DISPOSE_LEASE_RENEW_INTERVAL,
                Schedulers.parallel());
    }

    /** 测试可注入虚拟时钟，验证续租和租约丢失语义而无需真实等待。 */
    UserRuntimeDisposeCoordinator(
            RunRuntimeStore runRuntimeStore,
            SessionRuntimeStateApplicationService sessionRuntimeStateService,
            Duration leaseTtl,
            Duration renewInterval,
            Scheduler renewScheduler) {
        this.runRuntimeStore = Objects.requireNonNull(runRuntimeStore, "runRuntimeStore must not be null");
        this.sessionRuntimeStateService = Objects.requireNonNull(
                sessionRuntimeStateService, "sessionRuntimeStateService must not be null");
        this.leaseTtl = requirePositive(leaseTtl, "leaseTtl");
        this.renewInterval = requirePositive(renewInterval, "renewInterval");
        this.renewScheduler = Objects.requireNonNull(renewScheduler, "renewScheduler must not be null");
    }

    /**
     * 在当前用户全部 Session 明确空闲时执行一次 dispose；任何运行态、闸门冲突或快照异常都会拒绝执行。
     */
    public <T> T withUserIdle(UserId userId, String traceId, Supplier<T> action) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        String token = "dispose_" + UUID.randomUUID();
        if (!runRuntimeStore.tryAcquireUserRuntimeDispose(userId, token, leaseTtl)) {
            throw busy(userId, traceId, null);
        }
        AtomicBoolean leaseLost = new AtomicBoolean();
        Disposable renewal = renewWhileRunning(userId, token, traceId, leaseLost);
        try {
            SessionRuntimeStateSummary summary = sessionRuntimeStateService.snapshot(userId);
            if (summary.runningCount() > 0) {
                throw busy(userId, traceId, summary);
            }
            requireLease(leaseLost, traceId);
            T result = action.get();
            requireLease(leaseLost, traceId);
            return result;
        } finally {
            renewal.dispose();
            runRuntimeStore.releaseUserRuntimeDispose(userId, token);
        }
    }

    /** Run 启动前检查同一用户是否正在 dispose，供 Redis 初始化之外的兼容链路快速拒绝。 */
    public void requireNotDisposing(UserId userId, String traceId) {
        if (userId != null && runRuntimeStore.isUserRuntimeDisposeActive(userId)) {
            throw busy(userId, traceId, null);
        }
    }

    private PlatformException busy(UserId userId, String traceId, SessionRuntimeStateSummary summary) {
        int runningCount = summary == null ? -1 : summary.runningCount();
        return new PlatformException(
                ErrorCode.CONFLICT,
                "当前用户仍有运行中的 Session，不能释放 OpenCode 运行态",
                java.util.Map.of(
                        "runningCount", runningCount,
                        "traceId", traceId == null ? "" : traceId));
    }

    private Disposable renewWhileRunning(
            UserId userId,
            String token,
            String traceId,
            AtomicBoolean leaseLost) {
        return Flux.interval(renewInterval, renewScheduler).subscribe(
                ignored -> {
                    try {
                        if (!runRuntimeStore.renewUserRuntimeDispose(userId, token, leaseTtl)) {
                            leaseLost.set(true);
                        }
                    } catch (RuntimeException exception) {
                        leaseLost.set(true);
                        LOGGER.warn(
                                "用户 OpenCode dispose 闸门续租失败，userId={}, traceId={}, exceptionType={}",
                                userId.value(), traceId, exception.getClass().getSimpleName());
                    }
                },
                error -> {
                    leaseLost.set(true);
                    LOGGER.warn(
                            "用户 OpenCode dispose 闸门续租调度失败，userId={}, traceId={}, exceptionType={}",
                            userId.value(), traceId, error.getClass().getSimpleName());
                });
    }

    private void requireLease(AtomicBoolean leaseLost, String traceId) {
        if (leaseLost.get()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "OpenCode 运行态释放闸门已失效，请重试",
                    java.util.Map.of("traceId", traceId == null ? "" : traceId));
        }
    }

    private Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
