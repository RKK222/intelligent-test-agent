package com.enterprise.testagent.event;

import com.enterprise.testagent.domain.event.RunEvent;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.run.RunId;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * RunEvent 单机实时通道。它只服务当前进程内已连接的 SSE 订阅，断线恢复仍以 durable 回放或 opencode snapshot 为准。
 *
 * <p>基于 Reactor {@link Sinks.Many}（multicast + directBestEffort）服务本机订阅。跨 Java 的单 Run SSE
 * 由 API 层按 Run 生产 Java 流式转发到本机后再订阅该通道；非本机的用户级运行态刷新依赖已有低频轮询兜底。
 *
 * <p>{@code FAIL_ZERO_SUBSCRIBER}（无在线订阅）静默丢弃，因为没有 SSE 连接需要即时送达；
 * {@code FAIL_NON_SERIALIZED} 退化为 busyLoop 重试，避免并发发布丢失事件。
 */
@Service
public class RunEventLiveBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunEventLiveBus.class);
    private static final Duration NON_SERIALIZED_RETRY_TIMEOUT = Duration.ofMillis(100);

    private final Sinks.Many<RunEventLiveEvent> sink = Sinks.many().multicast().directBestEffort();

    /**
     * 发布已落库的 durable 事件；payload 携带 seq，SSE 可用该 seq 做断线续传游标。
     */
    public RunEventLiveEvent publishDurable(RunEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        LOGGER.debug("Publishing durable event, runId={}, seq={}, type={}",
                event.runId().value(), event.seq(), event.type().wireName());
        RunEventLiveEvent liveEvent = RunEventLiveEvent.durable(RunEventSsePayload.from(event));
        emit(liveEvent);
        return liveEvent;
    }

    /**
     * 发布不落库的 transient 事件，主要用于高频 message delta；事件不可作为 Last-Event-ID 恢复点。
     */
    public RunEventLiveEvent publishTransient(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        LOGGER.debug("Publishing transient event, runId={}, type={}",
                draft.runId().value(), draft.type().wireName());
        RunEventLiveEvent liveEvent = RunEventLiveEvent.transientOnly(
                RunEventSsePayload.transientFrom(draft, transientEventId()));
        emit(liveEvent);
        return liveEvent;
    }

    /**
     * 订阅指定 Run 的实时事件流；只过滤当前进程内发布的事件，不主动回放历史事件。
     */
    public Flux<RunEventLiveEvent> stream(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        return sink.asFlux().filter(event -> runId.value().equals(event.payload().runId()));
    }

    /**
     * 订阅本机实时事件的全局流；用于用户级运行态聚合在 run/question 事件后刷新快照。
     */
    public Flux<RunEventLiveEvent> streamAll() {
        return sink.asFlux();
    }

    /**
     * 向 Reactor sink 写入事件；无订阅者时静默丢弃，并发发布时短时间 busy loop 重试。
     */
    private void emit(RunEventLiveEvent liveEvent) {
        long deadlineNanos = System.nanoTime() + NON_SERIALIZED_RETRY_TIMEOUT.toNanos();
        Sinks.EmitResult result;
        do {
            result = sink.tryEmitNext(liveEvent);
            if (result == Sinks.EmitResult.OK) {
                return;
            }
            if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED && System.nanoTime() < deadlineNanos) {
                Thread.onSpinWait();
                continue;
            }
            handleEmitFailure(liveEvent, result);
            return;
        } while (true);
    }

    /**
     * 按 best-effort 语义处理投递失败：慢客户端或断开的 SSE 连接只丢弃实时帧，不能让 sink 进入终止态。
     */
    private void handleEmitFailure(RunEventLiveEvent liveEvent, Sinks.EmitResult result) {
        if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            LOGGER.debug("No subscriber for live event, runId={}", liveEvent.payload().runId());
            return;
        }
        if (result == Sinks.EmitResult.FAIL_OVERFLOW || result == Sinks.EmitResult.FAIL_CANCELLED) {
            LOGGER.debug("Dropped live event, runId={}, result={}", liveEvent.payload().runId(), result);
            return;
        }
        if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            LOGGER.warn(
                    "Dropped live event after non-serialized retry timeout, runId={}, timeoutMs={}",
                    liveEvent.payload().runId(),
                    TimeUnit.NANOSECONDS.toMillis(NON_SERIALIZED_RETRY_TIMEOUT.toNanos()));
            return;
        }
        if (result.isFailure()) {
            LOGGER.warn("Failed to emit live event, runId={}, result={}", liveEvent.payload().runId(), result);
        }
    }

    /**
     * 生成仅当前 SSE 实时连接使用的 transient eventId，不参与 durable seq 续传。
     */
    private String transientEventId() {
        return "evt_live_" + UUID.randomUUID().toString().replace("-", "");
    }
}
