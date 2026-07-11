package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeManifest;

/**
 * 已确认 OpenCode 接收 dispatch 后的恢复执行端口。
 *
 * <p>返回 true 表示已恢复事件订阅并接管租约；实现随后必须每 5 秒续租，并把 fencing token
 * 传给所有可产生副作用的恢复动作。该端口禁止重新发送 prompt。</p>
 */
@FunctionalInterface
public interface RunRecoveryTakeoverExecutor {

    boolean resumeAcceptedRun(RunRuntimeManifest manifest, RunOwnerLease lease, String traceId);

    /**
     * OpenCode 已明确未接收稳定 dispatchMessageId 时，用当前 fencing lease 收敛未派发 Run。
     * 实现不得补发 prompt；返回 true 表示已完成 Redis 终态与 PostgreSQL 投影，并负责释放租约。
     */
    default boolean failUnacceptedRun(RunRuntimeManifest manifest, RunOwnerLease lease, String traceId) {
        return false;
    }
}
