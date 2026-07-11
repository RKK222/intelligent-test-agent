package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunStatus;
import java.util.Objects;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 把普通 2 小时无活动 Run 委托给共享 owner/fencing/远端取消程序，并保留 legacy 的 FAILED 语义。 */
@Component
@Order(0)
public class RedisSummaryInactiveRunExpiryExecutor implements RunInactiveExpiryExecutor {

    private final RedisSummaryPendingAskExpiryExecutor termination;

    public RedisSummaryInactiveRunExpiryExecutor(RedisSummaryPendingAskExpiryExecutor termination) {
        this.termination = Objects.requireNonNull(termination, "termination must not be null");
    }

    @Override
    public boolean expireInactiveRun(
            RunRuntimeManifest manifest,
            RunOwnerLease lease,
            String traceId) {
        return termination.terminate(
                manifest,
                lease,
                traceId,
                RunStatus.FAILED,
                "STALE_ACTIVE_RUN_TIMEOUT",
                "STALE_ACTIVE_RUN_TIMEOUT",
                "运行超过 2 小时无活动，当前对话已安全终止");
    }
}
