package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeManifest;

/** 普通 Redis Run 无活动满 2 小时后的 fencing-safe 终止端口。 */
@FunctionalInterface
public interface RunInactiveExpiryExecutor {

    boolean expireInactiveRun(RunRuntimeManifest manifest, RunOwnerLease lease, String traceId);
}
