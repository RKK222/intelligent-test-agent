package com.enterprise.testagent.domain.run;

/** 替代消息受理后清理源 Run 可回放正文和工具明细的 MyBatis 端口。 */
public interface RunResendDetailCleanupPort {

    void purgeSourceRun(RunId sourceRunId);
}
