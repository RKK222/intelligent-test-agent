package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import java.time.Instant;

/** 替代消息受理后清理源 Run 可回放正文和工具明细的 MyBatis 端口。 */
public interface RunResendDetailCleanupPort {

    /** 清理被替代轮次，并在同一事务内推进会话内容修订时间，通知共享参与方刷新正文。 */
    void purgeSourceRun(RunId sourceRunId, SessionId sessionId, Instant updatedAt);
}
