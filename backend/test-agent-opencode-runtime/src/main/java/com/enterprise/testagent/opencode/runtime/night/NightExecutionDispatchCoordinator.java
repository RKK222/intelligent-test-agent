package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private LocalClientConnectionStore localClientConnectionStore;

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
        Map<DispatchTarget, List<NightExecutionTaskId>> grouped = new LinkedHashMap<>();
        for (NightExecutionTask task : due) {
            DispatchTarget target = dispatchTarget(task);
            if (target != null) {
                grouped.computeIfAbsent(target, ignored -> new ArrayList<>()).add(task.taskId());
            }
        }
        List<BatchOutcome> outcomes = Flux.fromIterable(grouped.entrySet())
                .filter(ignored -> !stopRequested.getAsBoolean())
                .flatMap(entry -> dispatchTarget(entry.getKey(), entry.getValue(), traceId, stopRequested),
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

    private Flux<BatchOutcome> dispatchTarget(
            DispatchTarget target,
            List<NightExecutionTaskId> taskIds,
            String traceId,
            BooleanSupplier stopRequested) {
        return Flux.fromIterable(chunks(taskIds))
                .takeWhile(ignored -> !stopRequested.getAsBoolean())
                .concatMap(chunk -> (target.runtimeKind() == RuntimeKind.LOCAL_CLIENT
                                ? gateway.dispatchLocal(
                                        target.clientInstanceId(), target.backendProcessId(), chunk, traceId)
                                : gateway.dispatch(target.linuxServerId(), chunk, traceId))
                        .map(this::outcome)
                        .onErrorResume(failure -> Mono.just(new BatchOutcome(0, chunk.size()))));
    }

    private DispatchTarget dispatchTarget(NightExecutionTask task) {
        if (task.targetRuntimeKind() == RuntimeKind.SERVER_PROCESS) {
            return DispatchTarget.server(task.targetLinuxServerId());
        }
        if (localClientConnectionStore == null) {
            return null;
        }
        LocalClientConnectionRoute route = localClientConnectionStore
                .find(task.targetLocalClientInstanceId())
                .filter(candidate -> candidate.userId().equals(task.ownerUserId()))
                .orElse(null);
        // 离线任务保持 SCHEDULED；窗口过期扫描统一落 WINDOW_EXPIRED。
        return route == null
                ? null
                : DispatchTarget.local(task.targetLocalClientInstanceId(), route.backendProcessId());
    }

    @Autowired(required = false)
    void configureLocalClientConnections(LocalClientConnectionStore localClientConnectionStore) {
        this.localClientConnectionStore = Objects.requireNonNull(localClientConnectionStore);
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

    private record DispatchTarget(
            RuntimeKind runtimeKind,
            String linuxServerId,
            com.enterprise.testagent.domain.localclient.LocalClientInstanceId clientInstanceId,
            BackendProcessId backendProcessId) {

        static DispatchTarget server(String linuxServerId) {
            return new DispatchTarget(RuntimeKind.SERVER_PROCESS, linuxServerId, null, null);
        }

        static DispatchTarget local(
                com.enterprise.testagent.domain.localclient.LocalClientInstanceId clientInstanceId,
                BackendProcessId backendProcessId) {
            return new DispatchTarget(RuntimeKind.LOCAL_CLIENT, null, clientInstanceId, backendProcessId);
        }
    }

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
