package com.example.testagent.event;

import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.run.RunId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * RunEvent 单机实时通道。它只服务当前进程内已连接的 SSE 订阅，断线恢复仍以 durable 回放或 opencode snapshot 为准。
 *
 * <p>基于 Reactor {@link Sinks.Many}（multicast + directBestEffort），<b>不跨进程广播</b>：单实例部署下实时性最佳；
 * 多实例部署下，落在其他实例的 durable 事件由 {@code RunEventSseStreamService} 的 replay 轮询兜底（延迟回升到轮询间隔），
 * transient 消息内容事件由建连时的 opencode session snapshot 恢复兜底，不会丢数据但实时性下降。
 * 多实例场景需引入 Redis pub/sub 等跨进程通道，新增前必须先补架构与安全文档例外。
 *
 * <p>{@code FAIL_ZERO_SUBSCRIBER}（无在线订阅）静默丢弃，因为没有 SSE 连接需要即时送达；
 * {@code FAIL_NON_SERIALIZED} 退化为 busyLoop 重试，避免并发发布丢失事件。
 */
@Service
public class RunEventLiveBus {

    private final Sinks.Many<RunEventLiveEvent> sink = Sinks.many().multicast().directBestEffort();

    /**
     * 发布已落库的 durable 事件；payload 携带 seq，SSE 可用该 seq 做断线续传游标。
     */
    public RunEventLiveEvent publishDurable(RunEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        RunEventLiveEvent liveEvent = RunEventLiveEvent.durable(RunEventSsePayload.from(event));
        emit(liveEvent);
        return liveEvent;
    }

    /**
     * 发布不落库的 transient 事件，主要用于高频 message delta；事件不可作为 Last-Event-ID 恢复点。
     */
    public RunEventLiveEvent publishTransient(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
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
     * 向 Reactor sink 写入事件；无订阅者时静默丢弃，并发发布时短时间 busy loop 重试。
     */
    private void emit(RunEventLiveEvent liveEvent) {
        Sinks.EmitResult result = sink.tryEmitNext(liveEvent);
        if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            return;
        }
        if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            sink.emitNext(liveEvent, Sinks.EmitFailureHandler.busyLooping(java.time.Duration.ofMillis(100)));
            return;
        }
        if (result.isFailure()) {
            return;
        }
    }

    /**
     * 生成仅当前 SSE 实时连接使用的 transient eventId，不参与 durable seq 续传。
     */
    private String transientEventId() {
        return "evt_live_" + UUID.randomUUID().toString().replace("-", "");
    }
}
