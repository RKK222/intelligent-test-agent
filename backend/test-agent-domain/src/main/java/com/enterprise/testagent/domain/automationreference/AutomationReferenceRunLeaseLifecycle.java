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

    void acquire(Run run, Workspace workspace, UserId userId, String traceId);

    void release(RunId runId, String traceId);
}
