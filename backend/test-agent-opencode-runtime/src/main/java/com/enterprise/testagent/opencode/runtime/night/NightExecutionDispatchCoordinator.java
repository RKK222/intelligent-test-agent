package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** XXL 每轮扫描和分组编排；目标服务器失败不会中止其他服务器。 */
@Service
public class NightExecutionDispatchCoordinator {

    static final int SCAN_LIMIT = 500;
    static final int SERVER_CONCURRENCY = 8;

    private final NightExecutionTaskRepository repository;
    private final NightExecutionDispatchGateway gateway;
    private final Clock clock;

    public NightExecutionDispatchCoordinator(
            NightExecutionTaskRepository repository,
            NightExecutionDispatchGateway gateway,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.gateway = Objects.requireNonNull(gateway);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 数据库扫描失败向 XXL 抛出；单服务器 HTTP 或逐任务失败只进入统计并留待下一轮。 */
    public Result dispatchDue(String traceId, BooleanSupplier stopRequested) {
        List<NightExecutionTask> due = repository.findScheduledDue(clock.instant(), SCAN_LIMIT);
        Map<String, List<NightExecutionTaskId>> grouped = new LinkedHashMap<>();
        for (NightExecutionTask task : due) {
            grouped.computeIfAbsent(task.targetLinuxServerId(), ignored -> new ArrayList<>())
                    .add(task.taskId());
        }
        List<BatchOutcome> outcomes = Flux.fromIterable(grouped.entrySet())
                .filter(ignored -> !stopRequested.getAsBoolean())
                .flatMap(entry -> dispatchServer(entry.getKey(), entry.getValue(), traceId, stopRequested),
                        SERVER_CONCURRENCY)
                .collectList()
                .blockOptional()
                .orElseGet(List::of);
        int started = outcomes.stream().mapToInt(BatchOutcome::started).sum();
        int failed = outcomes.stream().mapToInt(BatchOutcome::failed).sum();
        return new Result(due.size(), grouped.size(), outcomes.size(), started, failed);
    }

    private Flux<BatchOutcome> dispatchServer(
            String linuxServerId,
            List<NightExecutionTaskId> taskIds,
            String traceId,
            BooleanSupplier stopRequested) {
        return Flux.fromIterable(chunks(taskIds))
                .takeWhile(ignored -> !stopRequested.getAsBoolean())
                .concatMap(chunk -> gateway.dispatch(linuxServerId, chunk, traceId)
                        .map(this::outcome)
                        .onErrorResume(failure -> Mono.just(new BatchOutcome(0, chunk.size()))));
    }

    private BatchOutcome outcome(NightExecutionDispatchBatchResult batch) {
        int started = (int) batch.results().stream()
                .filter(NightExecutionDispatchResult::successful)
                .count();
        return new BatchOutcome(started, batch.results().size() - started);
    }

    private List<List<NightExecutionTaskId>> chunks(List<NightExecutionTaskId> taskIds) {
        List<List<NightExecutionTaskId>> result = new ArrayList<>();
        for (int start = 0; start < taskIds.size(); start += NightExecutionDispatchService.MAX_BATCH_SIZE) {
            result.add(List.copyOf(taskIds.subList(
                    start,
                    Math.min(start + NightExecutionDispatchService.MAX_BATCH_SIZE, taskIds.size()))));
        }
        return result;
    }

    private record BatchOutcome(int started, int failed) { }

    public record Result(int scanned, int targetServers, int batches, int started, int failed) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "scanned", scanned,
                    "targetServers", targetServers,
                    "batches", batches,
                    "started", started,
                    "failed", failed);
        }
    }
}
