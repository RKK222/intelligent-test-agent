package com.icbc.testagent.event;

import com.icbc.testagent.domain.run.RunId;
import reactor.core.publisher.Flux;

/**
 * RunEvent 跨实例实时广播端口，用于在分布式部署下把 durable/transient 事件 fan-out 到其他后端实例。
 */
public interface RunEventRemotePublisher {

    /**
     * 发布当前实例产生的实时事件；实现必须自行处理外部通道异常，不能影响本机 SSE。
     */
    void publish(RunEventLiveEvent event);

    /**
     * 订阅其他实例转发到本机的实时事件流。
     */
    Flux<RunEventLiveEvent> stream(RunId runId);

    /**
     * 订阅其他实例转发到本机的全部实时事件流，用于用户级状态聚合按事件类型触发刷新。
     */
    Flux<RunEventLiveEvent> streamAll();
}
