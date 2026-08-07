package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendRepository;
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

/** 每分钟扫描 WAITING/REVERTED/过期 REVERTING，并按固定目标服务器使用公共路由分发。 */
@Service
public class RunResendDispatchCoordinator {

    static final int SCAN_LIMIT = 500;
    private static final int SERVER_CONCURRENCY = 8;

    private final RunResendRepository repository;
    private final RunResendDispatchGateway gateway;
    private final Clock clock;

    public RunResendDispatchCoordinator(
            RunResendRepository repository,
            RunResendDispatchGateway gateway,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.gateway = Objects.requireNonNull(gateway);
        this.clock = Objects.requireNonNull(clock);
    }

    public Result dispatchDue(String traceId, BooleanSupplier stopRequested) {
        List<RunResend> due = repository.findDue(clock.instant(), SCAN_LIMIT);
        Map<String, List<RunResendId>> grouped = new LinkedHashMap<>();
        due.forEach(resend -> grouped.computeIfAbsent(
                        resend.targetLinuxServerId(), ignored -> new ArrayList<>())
                .add(resend.resendId()));
        List<RunResendDispatchBatchResult> batches = Flux.fromIterable(grouped.entrySet())
                .filter(ignored -> !stopRequested.getAsBoolean())
                .flatMap(entry -> dispatchChunks(entry.getKey(), entry.getValue(), traceId, stopRequested),
                        SERVER_CONCURRENCY)
                .collectList()
                .blockOptional()
                .orElseGet(List::of);
        int started = batches.stream().flatMap(batch -> batch.results().stream())
                .mapToInt(result -> result.successful() ? 1 : 0).sum();
        int failed = batches.stream().mapToInt(batch -> batch.results().size()).sum() - started;
        return new Result(due.size(), grouped.size(), batches.size(), started, failed);
    }

    private Flux<RunResendDispatchBatchResult> dispatchChunks(
            String linuxServerId,
            List<RunResendId> ids,
            String traceId,
            BooleanSupplier stopRequested) {
        return Flux.fromIterable(chunks(ids))
                .takeWhile(ignored -> !stopRequested.getAsBoolean())
                .concatMap(chunk -> gateway.dispatch(linuxServerId, chunk, traceId)
                        .onErrorResume(failure -> Mono.just(new RunResendDispatchBatchResult(
                                linuxServerId,
                                chunk.stream().map(id -> new RunResendDispatchResult(
                                        id.value(), false, "RETRY_PENDING", "ROUTE_ERROR")).toList()))));
    }

    private List<List<RunResendId>> chunks(List<RunResendId> ids) {
        List<List<RunResendId>> chunks = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += RunResendDispatchService.MAX_BATCH_SIZE) {
            chunks.add(List.copyOf(ids.subList(
                    start, Math.min(start + RunResendDispatchService.MAX_BATCH_SIZE, ids.size()))));
        }
        return chunks;
    }

    public record Result(int scanned, int targetServers, int batches, int started, int failed) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "resendScanned", scanned,
                    "resendTargetServers", targetServers,
                    "resendBatches", batches,
                    "resendStarted", started,
                    "resendFailed", failed);
        }
    }
}
