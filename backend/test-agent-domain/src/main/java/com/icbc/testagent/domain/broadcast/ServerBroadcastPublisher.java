package com.icbc.testagent.domain.broadcast;

/**
 * 服务器广播发布端口，业务模块只依赖该端口，不感知 Redis 或其他传输实现。
 */
public interface ServerBroadcastPublisher {

    /**
     * 当前发布端口实例 ID；Redis 消费端用它忽略本实例发布后回收到的事件。
     */
    default String instanceId() {
        return "noop";
    }

    /**
     * 发布当前实例产生的广播事件；实现必须自行处理传输异常，不能影响本机主流程。
     */
    void publish(ServerBroadcastEvent event);
}
