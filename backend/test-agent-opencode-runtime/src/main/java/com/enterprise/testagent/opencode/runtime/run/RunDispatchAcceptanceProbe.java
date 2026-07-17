package com.enterprise.testagent.opencode.runtime.run;

/**
 * OpenCode dispatch 接收状态探测端口。实现必须按 remoteSessionId + dispatchMessageId 查询，查询失败返回 UNKNOWN。
 */
@FunctionalInterface
public interface RunDispatchAcceptanceProbe {

    RunDispatchAcceptance probe(RunDispatchProbeRequest request);
}
