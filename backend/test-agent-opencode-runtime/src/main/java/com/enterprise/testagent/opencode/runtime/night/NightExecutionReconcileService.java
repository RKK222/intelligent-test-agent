package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.opencode.runtime.process.OpencodeScheduledTaskExecutionAffinityProvider;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.scheduler.ScheduledUserPlanService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 周期恢复失去认领的任务、顺延错过时段、执行 07:00 失败和 30 天清理。 */
@Service
public class NightExecutionReconcileService {

    private static final int BATCH_SIZE = 50;
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final NightExecutionTaskRepository repository;
    private final ScheduledUserPlanService userPlanService;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final OpencodeScheduledTaskExecutionAffinityProvider affinityProvider;
    private final NightExecutionProperties properties;
    private final Clock clock;

    public NightExecutionReconcileService(
            NightExecutionTaskRepository repository,
            ScheduledUserPlanService userPlanService,
            UserOpencodeProcessAssignmentService assignmentService,
            OpencodeScheduledTaskExecutionAffinityProvider affinityProvider,
            NightExecutionProperties properties,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.userPlanService = Objects.requireNonNull(userPlanService);
        this.assignmentService = Objects.requireNonNull(assignmentService);
        this.affinityProvider = Objects.requireNonNull(affinityProvider);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Result reconcile(String traceId, BooleanSupplier stopRequested) {
        Instant now = clock.instant();
        int retried = 0;
        int rolledOver = 0;
        int failed = 0;
        int cleaned = 0;

        for (NightExecutionTask task : repository.findDispatchingBefore(now.minus(CLAIM_TIMEOUT), BATCH_SIZE)) {
            if (stopRequested.getAsBoolean()) break;
            Outcome outcome = recover(task, NightExecutionTaskStatus.DISPATCHING, now, traceId);
            retried += outcome == Outcome.RETRIED ? 1 : 0;
            rolledOver += outcome == Outcome.ROLLED_OVER ? 1 : 0;
            failed += outcome == Outcome.FAILED ? 1 : 0;
        }
        for (NightExecutionTask task : repository.findScheduledDueBefore(now.minus(CLAIM_TIMEOUT), BATCH_SIZE)) {
            if (stopRequested.getAsBoolean()) break;
            Outcome outcome = recover(task, NightExecutionTaskStatus.SCHEDULED, now, traceId);
            retried += outcome == Outcome.RETRIED ? 1 : 0;
            rolledOver += outcome == Outcome.ROLLED_OVER ? 1 : 0;
            failed += outcome == Outcome.FAILED ? 1 : 0;
        }
        for (NightExecutionTask task : repository.findTerminalBefore(now.minus(RETENTION), BATCH_SIZE)) {
            if (stopRequested.getAsBoolean()) break;
            repository.delete(task.taskId());
            cleaned++;
        }
        if (!stopRequested.getAsBoolean()) {
            // 时段占位保留用于表达已消耗的启动额度，超过任务保留期后统一清理。
            repository.deleteReservationsBefore(now.minus(RETENTION));
        }
        return new Result(retried, rolledOver, failed, cleaned);
    }

    private Outcome recover(
            NightExecutionTask task,
            NightExecutionTaskStatus expectedStatus,
            Instant now,
            String traceId) {
        if (!now.isBefore(task.windowEnd())) {
            return fail(task, expectedStatus, now) ? Outcome.FAILED : Outcome.UNCHANGED;
        }
        String affinity = assignmentService.routingLinuxServerId(task.ownerUserId(), "opencode")
                .orElseGet(affinityProvider::currentAffinity);
        if (now.isBefore(task.slotEnd())) {
            var run = userPlanService.schedule(
                    NightExecutionTaskApplicationService.TASK_KEY,
                    task.ownerUserId(),
                    now.isAfter(task.slotStart()) ? now : task.slotStart(),
                    affinity,
                    traceId);
            NightExecutionTask retry = task.reschedule(
                    task.slotStart(), task.slotEnd(), affinity, run.taskRunId(), now);
            if (!repository.updateIfStatus(retry, expectedStatus)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "夜间任务重试状态已变化");
            }
            retirePreviousPlan(task);
            return Outcome.RETRIED;
        }

        Optional<Instant> nextSlot;
        try {
            nextSlot = reserveNextSlot(task, now);
        } catch (PlatformException unavailable) {
            // 配置暂缺时保留已有任务；环境恢复后继续扫描，07:00 仍按真实窗口最终失败。
            return Outcome.UNCHANGED;
        }
        if (nextSlot.isEmpty()) {
            return fail(task, expectedStatus, now) ? Outcome.FAILED : Outcome.UNCHANGED;
        }
        Instant slotStart = nextSlot.orElseThrow();
        var run = userPlanService.schedule(
                NightExecutionTaskApplicationService.TASK_KEY,
                task.ownerUserId(), slotStart, affinity, traceId);
        NightExecutionTask rolled = task.rollover(
                slotStart,
                slotStart.plus(NightExecutionWindowCalculator.SLOT_DURATION),
                affinity,
                run.taskRunId(),
                now);
        if (!repository.updateIfStatus(rolled, expectedStatus)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "夜间任务顺延状态已变化");
        }
        retirePreviousPlan(task);
        repository.releaseSlot(task.slotStart(), now);
        return Outcome.ROLLED_OVER;
    }

    private Optional<Instant> reserveNextSlot(NightExecutionTask task, Instant now) {
        int capacity = properties.requireSlotCapacity();
        Instant first = ceilQuarter(now);
        if (first.isBefore(task.slotEnd())) first = task.slotEnd();
        Map<Instant, Integer> counts = repository.reservationCounts(
                task.windowEnd().minus(Duration.ofHours(10)), task.windowEnd());
        List<Instant> candidates = new ArrayList<>();
        for (Instant cursor = first;
                !cursor.plus(NightExecutionWindowCalculator.SLOT_DURATION).isAfter(task.windowEnd());
                cursor = cursor.plus(NightExecutionWindowCalculator.SLOT_DURATION)) {
            candidates.add(cursor);
        }
        candidates.sort(Comparator
                .comparingInt((Instant slot) -> counts.getOrDefault(slot, 0))
                .thenComparing(slot -> slot));
        for (Instant candidate : candidates) {
            if (counts.getOrDefault(candidate, 0) >= capacity) continue;
            if (repository.reserveSlot(candidate, capacity, now)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private boolean fail(
            NightExecutionTask task,
            NightExecutionTaskStatus expectedStatus,
            Instant now) {
        NightExecutionTask failed = task.fail("WINDOW_EXPIRED", "夜间执行窗口内未能启动", now);
        if (!repository.updateIfStatus(failed, expectedStatus)) return false;
        retirePreviousPlan(task);
        repository.deleteSessionLock(task.sessionId(), task.taskId());
        repository.releaseSlot(task.slotStart(), now);
        return true;
    }

    private void retirePreviousPlan(NightExecutionTask task) {
        userPlanService.cancelPendingIfPresent(
                task.scheduledTaskRunId(),
                "夜间任务原执行计划已失效");
    }

    private Instant ceilQuarter(Instant value) {
        long epochSecond = value.getEpochSecond();
        long remainder = Math.floorMod(epochSecond, 900L);
        if (remainder == 0L && value.getNano() == 0) return value;
        return Instant.ofEpochSecond(epochSecond - remainder + 900L);
    }

    private enum Outcome { RETRIED, ROLLED_OVER, FAILED, UNCHANGED }

    public record Result(int retried, int rolledOver, int failed, int cleaned) {
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("retried", retried);
            values.put("rolledOver", rolledOver);
            values.put("failed", failed);
            values.put("cleaned", cleaned);
            return values;
        }
    }
}
