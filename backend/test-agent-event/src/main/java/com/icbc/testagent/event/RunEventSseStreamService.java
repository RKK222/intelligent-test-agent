package com.icbc.testagent.event;

import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.run.RunId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * RunEvent SSE 流服务：durable 事件通过 Repository 可续传回放，transient/live 事件通过生产 Java 进程内总线即时下发。
 *
 * <p>{@link #streamAfter} 用 {@link Flux#merge} 合并 durable replay 与本机 live bus。落库的 durable 事件既会被 live bus
 * 即时下发，又可能在下一轮 replay 轮询中被查出（live 推送与轮询游标推进存在竞态），因此服务端按 payload eventId
 * 做一次轻量去重；前端仍需保持 eventId 幂等，兼容断线重连和重复投递。
 */
@Service
public class RunEventSseStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunEventSseStreamService.class);

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
     * 从指定 Last-Event-ID 后开始输出 RunEvent SSE；durable 事件轮询回放，本机 live 事件实时合流。
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
        long resolvedSeq = replayService.resolveLastSeq(lastEventId);
        LOGGER.info("SSE stream started, runId={}, lastEventId={}, resolvedSeq={}",
                runId.value(), lastEventId, resolvedSeq);
        AtomicLong cursor = new AtomicLong(resolvedSeq);
        Flux<ServerSentEvent<RunEventSsePayload>> durableReplay = Flux.interval(Duration.ZERO, pollInterval)
                .onBackpressureDrop()
                .concatMap(ignored -> Mono.fromCallable(() -> replayService.replayAfter(
                                runId,
                                Long.toString(cursor.get()),
                                batchLimit))
                        // RunEvent 回放依赖阻塞式 Repository，单次 DB 抖动只跳过本轮轮询，不断开 SSE。
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(error -> {
                            LOGGER.warn("SSE replay error, runId={}, error={}", runId.value(), error.getMessage());
                            return Mono.just(List.<RunEvent>of());
                        })
                        .flatMapMany(Flux::fromIterable))
                .doOnNext(event -> cursor.set(event.seq()))
                .map(this::toSse);
        Flux<ServerSentEvent<RunEventSsePayload>> liveEvents = liveBus.stream(runId).map(this::toLiveSse);
        return Flux.merge(durableReplay, liveEvents)
                .distinct(this::eventId)
                .doOnCancel(() -> LOGGER.info("SSE stream cancelled, runId={}", runId.value()))
                .doOnError(error -> LOGGER.warn("SSE stream error, runId={}, error={}", runId.value(), error.getMessage()))
                .doOnComplete(() -> LOGGER.debug("SSE stream completed, runId={}", runId.value()));
    }

    /**
     * 初始消息快照与实时事件并发输出，避免远端快照查询期间尚未订阅 live bus 而丢失增量。
     */
    public Flux<ServerSentEvent<RunEventSsePayload>> streamAfterWithSnapshot(
            RunId runId,
            String lastEventId,
            Duration pollInterval,
            int batchLimit,
            Flux<ServerSentEvent<RunEventSsePayload>> initialSnapshot) {
        Objects.requireNonNull(initialSnapshot, "initialSnapshot must not be null");
        return Flux.merge(
                initialSnapshot,
                streamAfter(runId, lastEventId, pollInterval, batchLimit))
                .distinct(this::eventId);
    }

    /**
     * 为 HTTP 历史接口提供一次性 durable RunEvent 快照，避免边界层直接依赖 Repository。
     */
    public List<RunEventSsePayload> snapshotDurablePayloads(RunId runId, long lastSeq, int batchLimit) {
        Objects.requireNonNull(runId, "runId must not be null");
        return replayService.replayAfter(runId, Long.toString(lastSeq), batchLimit)
                .stream()
                .map(RunEventSsePayload::from)
                .toList();
    }

    /**
     * 为 Session 级历史接口按 root session 提供一次性 durable RunEvent 快照。
     */
    public List<RunEventSsePayload> snapshotDurablePayloadsByRootSessionId(
            String rootSessionId,
            long lastSeq,
            int batchLimit) {
        return replayService.replayByRootSessionIdAfter(rootSessionId, Long.toString(lastSeq), batchLimit)
                .stream()
                .map(RunEventSsePayload::from)
                .toList();
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

    private String eventId(ServerSentEvent<RunEventSsePayload> event) {
        RunEventSsePayload data = event.data();
        return data == null ? "" : data.eventId();
    }
}
