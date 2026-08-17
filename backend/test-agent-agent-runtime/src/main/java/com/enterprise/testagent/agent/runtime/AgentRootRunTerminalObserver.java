package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;

/** 根 Run 关系型终态已经落库后的观察者；实现失败不得反向改变 Run 终态。 */
public interface AgentRootRunTerminalObserver {
    void onTerminal(RunId runId, RunStatus status, String traceId);
}
