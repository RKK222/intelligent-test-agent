package com.enterprise.testagent.domain.session;

import com.enterprise.testagent.domain.user.UserId;

/**
 * 用户级会话运行态只读仓储端口。
 */
public interface SessionRuntimeStateRepository {

    /**
     * 查询当前用户可见历史会话中的 active run 与待答 question 摘要。
     */
    SessionRuntimeStateSummary findUserRuntimeState(UserId userId);
}
