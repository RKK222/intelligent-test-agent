package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.run.RunId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * RunEvent SSE 流服务：durable 事件通过 Repository 可续传回放，transient/live 事件通过进程内总线即时下发。
 *
 * <p>{@link #streamAfter} 用 {@link Flux#merge} 合并 durable replay 与 live bus 两条来源。落库的 durable 事件既会被
 * live bus 即时下发，又可能在下一轮 replay 轮询中被查出（live 推送与轮询游标推进存在竞态），因此同一 durable 事件
 * 可能重复投递。durable 事件携带稳定的 {@code evt_} 前缀 eventId，transient 事件携带 {@code evt_live_} 前缀 eventId，
 * 前端必须按 eventId 去重；replay 侧不做额外去重，依赖前端幂等。
 */
@Service
public class RunEventSseStreamService {

    private final RunEventReplayService replayService;
    private final RunEventSseMapper sseMapper;
    private final RunEventLiveBus liveBus;

    /**
     * 构造生产用 SSE 流服务，合并 durable 回放服务、SSE mapper 和当前进程实时总线。
     */
    @Autowired
    public RunEventSseStreamService(
            RunEventReplayService replayService,
            RunEventSseMapper sseMapper,
            RunEventLiveBus liveBus) {
        this.replayService = Objects.requireNonNull(replayService, "replayService must not be null");
        this.sseMapper = Objects.requireNonNull(sseMapper, "sseMapper must not be null");
        this.liveBus = Objects.requireNonNull(liveBus, "liveBus must not be null");
    }

    /**
     * 构造测试用 SSE 流服务，自动创建本地 live bus，便于单元测试不启动 Spring 容器。
     */
    public RunEventSseStreamService(RunEventReplayService replayService, RunEventSseMapper sseMapper) {
        this(replayService, sseMapper, new RunEventLiveBus());
    }

    /**
     * 从指定 Last-Event-ID 后开始输出 RunEvent SSE；durable 事件轮询回放，live bus 事件实时合流。
     */
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
        Flux<ServerSentEvent<RunEventSsePayload>> durableReplay = Flux.interval(Duration.ZERO, pollInterval)
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
        Flux<ServerSentEvent<RunEventSsePayload>> liveEvents = liveBus.stream(runId).map(this::toLiveSse);
        return Flux.merge(durableReplay, liveEvents);
    }

    /**
     * 将 durable repository 事件映射成 SSE，保持 seq 作为客户端续传游标。
     */
    private ServerSentEvent<RunEventSsePayload> toSse(RunEvent event) {
        return sseMapper.toSse(event);
    }

    /**
     * 根据 live event 的 durable 标记选择带 id 或不带 id 的 SSE 映射。
     */
    private ServerSentEvent<RunEventSsePayload> toLiveSse(RunEventLiveEvent event) {
        return event.durable()
                ? sseMapper.toDurableSse(event.payload())
                : sseMapper.toTransientSse(event.payload());
    }
}
