package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.run.Run;

/** 根 session.error 已提交为 Run 终态后的扩展观察者。 */
@FunctionalInterface
public interface RunRootSessionErrorObserver {

    void onRootSessionError(Run sourceRun, RunEventDraft terminalEvent);
}
