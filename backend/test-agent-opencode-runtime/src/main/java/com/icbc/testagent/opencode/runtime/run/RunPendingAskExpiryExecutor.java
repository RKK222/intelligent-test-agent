package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeManifest;

/**
 * 超期待处理 permission/question 的执行端口。
 *
 * <p>返回 {@code true} 表示执行器已经接管 Run，并负责后续 owner lease 的续租与释放；返回
 * {@code false} 表示未接管，协调器会用原 fencing token 安全释放租约。生产执行器由 Run 编排层接入，
 * 缺失时默认 no-op，避免误取消远端会话。
 */
@FunctionalInterface
public interface RunPendingAskExpiryExecutor {

    boolean expirePendingAsk(RunRuntimeManifest manifest, RunOwnerLease lease, String traceId);
}
