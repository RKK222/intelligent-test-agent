package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import java.time.Instant;

/** 替代消息受理后清理源 Run 可回放正文和工具明细的 MyBatis 端口。 */
public interface RunResendDetailCleanupPort {

    /** 清理被替代轮次，并在同一事务内推进会话内容修订时间，通知共享参与方刷新正文。 */
    void purgeSourceRun(RunId sourceRunId, SessionId sessionId, Instant updatedAt);

    /** 取消或失败时删除尚未投递的替代 USER，并推进修订时间让共享参与方恢复源轮次。 */
    void purgePendingReplacementMessage(RunId replacementRunId, SessionId sessionId, Instant updatedAt);
}
