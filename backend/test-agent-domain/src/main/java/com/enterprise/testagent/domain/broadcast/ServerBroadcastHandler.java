package com.enterprise.testagent.domain.broadcast;

/**
 * 服务器广播消费端口，业务模块通过 Bean 注册自己能处理的事件类型。
 */
public interface ServerBroadcastHandler {

    /**
     * 当前 handler 是否处理该事件类型。
     */
    boolean supports(String type);

    /**
     * 处理其他实例发布的广播事件；实现内部负责业务幂等和错误转换。
     */
    void handle(ServerBroadcastEvent event);
}
