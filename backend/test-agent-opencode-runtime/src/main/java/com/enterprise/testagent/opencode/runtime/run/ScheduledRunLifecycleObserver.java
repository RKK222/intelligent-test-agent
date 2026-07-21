package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.Run;

/** 普通 Scheduled Run 的内部受理回调；只观察启动边界，不监听 Run 最终状态。 */
public interface ScheduledRunLifecycleObserver {

    void onAccepted(ScheduledRunMetadata metadata, Run run);

    void onRejected(ScheduledRunMetadata metadata, RuntimeException failure);
}
