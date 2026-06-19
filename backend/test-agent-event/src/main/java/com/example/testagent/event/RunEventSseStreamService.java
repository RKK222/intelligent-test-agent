package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.run.RunId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * RunEvent SSE 流服务，初版通过增量轮询 Repository 实现可续传事件流，避免依赖单机内存广播。
 */
@Service
public class RunEventSseStreamService {

    private final RunEventReplayService replayService;
    private final RunEventSseMapper sseMapper;

    public RunEventSseStreamService(RunEventReplayService replayService, RunEventSseMapper sseMapper) {
        this.replayService = Objects.requireNonNull(replayService, "replayService must not be null");
        this.sseMapper = Objects.requireNonNull(sseMapper, "sseMapper must not be null");
    }

    public Flux<ServerSentEvent<RunEventSsePayload>> streamAfter(
            RunId runId,
            String lastEventId,
            Duration pollInterval,
            int batchLimit) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        AtomicLong cursor = new AtomicLong(replayService.resolveLastSeq(lastEventId));
        return Flux.interval(Duration.ZERO, pollInterval)
                .onBackpressureDrop()
                .concatMap(ignored -> Mono.fromCallable(() -> replayService.replayAfter(
                                runId,
                                Long.toString(cursor.get()),
                                batchLimit))
                        // RunEvent 回放依赖阻塞式 Repository，单次 DB 抖动只跳过本轮轮询，不断开 SSE。
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(error -> Mono.just(List.<RunEvent>of()))
                        .flatMapMany(Flux::fromIterable))
                .doOnNext(event -> cursor.set(event.seq()))
                .map(this::toSse);
    }

    private ServerSentEvent<RunEventSsePayload> toSse(RunEvent event) {
        return sseMapper.toSse(event);
    }
}
