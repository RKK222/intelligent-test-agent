package com.enterprise.testagent.agent.runtime;

/**
 * 其他 agent 的抽象占位类，后续真实接入时继承并实现 AgentRuntime 方法。
 */
public abstract class OtherAgentRuntime implements AgentRuntime {

    /**
     * 预留稳定 agent 标志；本抽象类不注册为 Spring Bean。
     */
    @Override
    public String agentId() {
        return "otheragent";
    }
}
