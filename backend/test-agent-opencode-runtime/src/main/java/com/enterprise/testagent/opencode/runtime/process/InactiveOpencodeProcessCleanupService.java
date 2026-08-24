package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.InactiveOpencodeProcessCandidate;
import com.enterprise.testagent.domain.opencodeprocess.InactiveOpencodeProcessRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionWindowCalculator;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 每台 Java 只关闭自己实际持有 manager 连接的长期闲置用户 OpenCode 进程。
 */
@Service
public class InactiveOpencodeProcessCleanupService {

    static final Duration INACTIVITY_THRESHOLD = Duration.ofDays(10);
    static final int SCAN_LIMIT = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(InactiveOpencodeProcessCleanupService.class);

    private final InactiveOpencodeProcessRepository repository;
    private final BackendJavaRouteResolver routeResolver;
    private final LiveOpencodeContainerCandidateResolver containerResolver;
    private final UserRuntimeDisposeCoordinator idleCoordinator;
    private final OpencodeProcessStopService stopService;
    private final Clock clock;

    public InactiveOpencodeProcessCleanupService(
            InactiveOpencodeProcessRepository repository,
            BackendJavaRouteResolver routeResolver,
            LiveOpencodeContainerCandidateResolver containerResolver,
            UserRuntimeDisposeCoordinator idleCoordinator,
            OpencodeProcessStopService stopService,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.containerResolver = Objects.requireNonNull(containerResolver, "containerResolver must not be null");
        this.idleCoordinator = Objects.requireNonNull(idleCoordinator, "idleCoordinator must not be null");
        this.stopService = Objects.requireNonNull(stopService, "stopService must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 扫描当前服务器候选；每个候选在用户闸门内重新核对活动时间、绑定和 manager 本机归属。
     */
    public Result cleanupCurrentServer(String traceId, BooleanSupplier stopRequested) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        BooleanSupplier shouldStop = stopRequested == null ? () -> false : stopRequested;
        Instant now = clock.instant();
        Instant activityBefore = now.minus(INACTIVITY_THRESHOLD);
        // 02:00 清理横跨前一晚窗口：窗口未结束的遗留任务和北京时间当天任务都必须保留进程。
        Instant pendingTaskSlotBefore = now.atZone(NightExecutionWindowCalculator.ZONE)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(NightExecutionWindowCalculator.ZONE)
                .toInstant();
        List<InactiveOpencodeProcessCandidate> candidates = repository.findCandidates(
                routeResolver.currentLinuxServerId(), activityBefore, now, pendingTaskSlotBefore, SCAN_LIMIT);
        Counter counter = new Counter();
        for (InactiveOpencodeProcessCandidate candidate : candidates) {
            if (shouldStop.getAsBoolean()) {
                counter.stopRequested = true;
                break;
            }
            counter.scannedCount++;
            cleanupCandidate(candidate, activityBefore, now, pendingTaskSlotBefore, traceId, counter);
        }
        return counter.result(activityBefore);
    }

    private void cleanupCandidate(
            InactiveOpencodeProcessCandidate candidate,
            Instant activityBefore,
            Instant pendingTaskActiveAfter,
            Instant pendingTaskSlotBefore,
            String traceId,
            Counter counter) {
        OpencodeServerProcess scannedProcess = candidate.process();
        try {
            Outcome outcome = idleCoordinator.withUserIdle(scannedProcess.userId(), traceId, () -> {
                Optional<InactiveOpencodeProcessCandidate> current = repository.findCurrentCandidate(
                        scannedProcess.processId(),
                        activityBefore,
                        pendingTaskActiveAfter,
                        pendingTaskSlotBefore);
                if (current.isEmpty()) {
                    return Outcome.CHANGED;
                }
                OpencodeServerProcess currentProcess = current.orElseThrow().process();
                if (containerResolver.findExactBoundContainer(
                                currentProcess.linuxServerId(), currentProcess.containerId())
                        .isEmpty()) {
                    return Outcome.NOT_LOCAL;
                }
                stopService.stopAndVerify(OpencodeProcessStopRequest.tracked(currentProcess, traceId));
                return Outcome.STOPPED;
            });
            switch (outcome) {
                case STOPPED -> counter.stoppedCount++;
                case CHANGED -> counter.changedSkippedCount++;
                case NOT_LOCAL -> counter.notLocalSkippedCount++;
            }
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.CONFLICT) {
                counter.busySkippedCount++;
                LOGGER.info(
                        "跳过仍有活动运行或释放竞争的闲置 OpenCode 进程，processId={}, traceId={}",
                        scannedProcess.processId().value(), traceId);
                return;
            }
            counter.failedCount++;
            LOGGER.warn(
                    "关闭闲置 OpenCode 进程失败，processId={}, errorCode={}, traceId={}",
                    scannedProcess.processId().value(), exception.errorCode(), traceId, exception);
        } catch (RuntimeException exception) {
            counter.failedCount++;
            LOGGER.warn(
                    "关闭闲置 OpenCode 进程异常，processId={}, exceptionType={}, traceId={}",
                    scannedProcess.processId().value(), exception.getClass().getSimpleName(), traceId, exception);
        }
    }

    private enum Outcome {
        STOPPED,
        CHANGED,
        NOT_LOCAL
    }

    /** 单个 Java 实例的清理统计，写入 XXL 运行结果并用于日志诊断。 */
    public record Result(
            Instant activityBefore,
            int scannedCount,
            int stoppedCount,
            int busySkippedCount,
            int changedSkippedCount,
            int notLocalSkippedCount,
            int failedCount,
            boolean stopRequested) {

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("activityBefore", activityBefore.toString());
            result.put("scannedCount", scannedCount);
            result.put("stoppedCount", stoppedCount);
            result.put("busySkippedCount", busySkippedCount);
            result.put("changedSkippedCount", changedSkippedCount);
            result.put("notLocalSkippedCount", notLocalSkippedCount);
            result.put("failedCount", failedCount);
            result.put("stopRequested", stopRequested);
            return Map.copyOf(result);
        }
    }

    private static final class Counter {
        private int scannedCount;
        private int stoppedCount;
        private int busySkippedCount;
        private int changedSkippedCount;
        private int notLocalSkippedCount;
        private int failedCount;
        private boolean stopRequested;

        private Result result(Instant activityBefore) {
            return new Result(
                    activityBefore,
                    scannedCount,
                    stoppedCount,
                    busySkippedCount,
                    changedSkippedCount,
                    notLocalSkippedCount,
                    failedCount,
                    stopRequested);
        }
    }
}
