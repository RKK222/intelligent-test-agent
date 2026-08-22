package com.enterprise.testagent.domain.automationreference;

import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;

/**
 * 根 Run 的自动化引用代次租约端口。
 *
 * <p>实现只能记录代次和释放租约，禁止向 OpenCode 上下文、用户消息或系统提示拼接引用信息。
 */
public interface AutomationReferenceRunLeaseLifecycle {

    /**
     * 在 Run 对外可见、占用活动会话之前，对账工作树 JSONC 并固定本次可用代次。
     * 不可用的单个自动化副本必须降级成 warning，不能阻断主工作区 Run。
     */
    AutomationReferenceRunPreparation prepare(Workspace workspace, UserId userId, String traceId);

    /** Run durable 锚点建立后写入 prepare 阶段固定的精确代次租约。 */
    void acquire(Run run, AutomationReferenceRunPreparation preparation, String traceId);

    void release(RunId runId, String traceId);
}
