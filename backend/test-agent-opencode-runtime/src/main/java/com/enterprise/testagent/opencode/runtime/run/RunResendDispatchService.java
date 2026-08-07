package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 目标 Java 的重发批量执行入口；每条失败隔离，未知窗口由持久状态机下一轮恢复。 */
@Service
public class RunResendDispatchService {

    public static final int MAX_BATCH_SIZE = 50;
    private static final int CONCURRENCY = 4;

    private final RunResendExecutionService executionService;
    private final BackendInstanceIdentity backendIdentity;

    public RunResendDispatchService(
            RunResendExecutionService executionService,
            BackendInstanceIdentity backendIdentity) {
        this.executionService = Objects.requireNonNull(executionService);
        this.backendIdentity = Objects.requireNonNull(backendIdentity);
    }

    public Mono<RunResendDispatchBatchResult> dispatchBatch(
            String linuxServerId,
            List<RunResendId> resendIds,
            String traceId) {
        if (resendIds == null || resendIds.isEmpty() || resendIds.size() > MAX_BATCH_SIZE) {
            return Mono.error(new IllegalArgumentException("resendIds size must be between 1 and " + MAX_BATCH_SIZE));
        }
        if (!backendIdentity.linuxServerId().equals(linuxServerId)) {
            return Mono.just(new RunResendDispatchBatchResult(
                    linuxServerId,
                    resendIds.stream().map(id -> new RunResendDispatchResult(
                            id.value(), false, "TARGET_MISMATCH", "TARGET_MISMATCH")).toList()));
        }
        return Flux.fromIterable(List.copyOf(resendIds))
                .flatMapSequential(
                        id -> Mono.fromCallable(() -> executeOne(id))
                                .subscribeOn(Schedulers.boundedElastic()),
                        CONCURRENCY,
                        1)
                .collectList()
                .map(results -> new RunResendDispatchBatchResult(linuxServerId, results));
    }

    private RunResendDispatchResult executeOne(RunResendId resendId) {
        try {
            RunResend result = executionService.execute(resendId);
            boolean successful = result.status() == com.enterprise.testagent.domain.run.RunResendStatus.DISPATCHED;
            return new RunResendDispatchResult(
                    resendId.value(), successful, result.status().name(),
                    successful ? null : "RETRY_PENDING");
        } catch (RuntimeException failure) {
            return new RunResendDispatchResult(
                    resendId.value(), false, "RETRY_PENDING", "INTERNAL_ERROR");
        }
    }
}
