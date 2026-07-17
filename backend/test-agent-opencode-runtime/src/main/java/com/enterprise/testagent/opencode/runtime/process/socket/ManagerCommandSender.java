package com.enterprise.testagent.opencode.runtime.process.socket;

/**
 * WebSocket 连接向管理进程发送命令的最小抽象，避免 runtime 模块依赖 WebSocket 类型。
 */
@FunctionalInterface
public interface ManagerCommandSender {

    /**
     * 发送一条控制面消息；实现必须只发送 JSON 文本帧，不记录敏感内容。
     */
    void send(ManagerControlMessage message);
}
