package com.enterprise.testagent.agent.runtime;

/** 探测成功时返回存在或不存在；网络异常由 Mono error 表达为未知。 */
public record AgentMessageProbeResult(boolean present) {
}
