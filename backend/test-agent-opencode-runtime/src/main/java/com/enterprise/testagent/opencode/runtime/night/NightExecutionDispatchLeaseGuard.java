package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 仅为当前正在执行的普通 Run 启动调用续租，不承担排队或 Run 执行。 */
@Service
public class NightExecutionDispatchLeaseGuard {

    static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final Logger LOGGER = LoggerFactory.getLogger(NightExecutionDispatchLeaseGuard.class);

    private final NightExecutionTaskRepository repository;
    private final Clock clock;
    private final Map<String, Lease> inFlight = new ConcurrentHashMap<>();

    public NightExecutionDispatchLeaseGuard(NightExecutionTaskRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 注册一个同步 Run 启动调用；相同 taskId 的旧 handle 无法移除新 attempt。 */
    public Handle track(NightExecutionTaskId taskId, String attemptId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        if (attemptId == null || attemptId.isBlank()) {
            throw new IllegalArgumentException("attemptId must not be blank");
        }
        Lease lease = new Lease(taskId, attemptId.trim());
        inFlight.put(taskId.value(), lease);
        return () -> inFlight.remove(taskId.value(), lease);
    }

    /** 每分钟延长当前 HTTP/Run 启动调用租约；失败留给下一分钟或补偿任务处理。 */
    @Scheduled(fixedDelay = 60_000L)
    void renewInFlightLeases() {
        Instant now = clock.instant();
        for (Lease lease : inFlight.values()) {
            try {
                repository.renewDispatchLease(
                        lease.taskId(), lease.attemptId(), now.plus(LEASE_DURATION), now);
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "夜间任务分发租约续期失败，taskId={}, exceptionType={}",
                        lease.taskId().value(), failure.getClass().getSimpleName());
            }
        }
    }

    public boolean isInFlight(NightExecutionTaskId taskId, String attemptId) {
        return new Lease(taskId, attemptId).equals(inFlight.get(taskId.value()));
    }

    public interface Handle extends AutoCloseable {
        @Override
        void close();
    }

    private record Lease(NightExecutionTaskId taskId, String attemptId) { }
}
