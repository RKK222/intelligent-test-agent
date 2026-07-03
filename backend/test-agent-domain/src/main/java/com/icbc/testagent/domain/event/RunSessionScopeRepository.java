package com.icbc.testagent.domain.event;

import com.icbc.testagent.domain.run.RunId;
import java.util.List;
import java.util.Optional;

/**
 * Run session scope 持久化端口。DB 是恢复事实源，Redis 只作为运行中缓存和 pending buffer。
 */
public interface RunSessionScopeRepository {

    /**
     * 创建或更新 Run root scope。
     */
    void upsertScope(RunSessionScope scope);

    /**
     * 创建或更新当前 Run scope 内的 root/child session。
     */
    void upsertSession(RunSessionScopeSession session);

    /**
     * 查询 Run 当前子树内全部 session，按发现时间和 sessionId 稳定排序。
     */
    List<RunSessionScopeSession> findSessionsByRunId(RunId runId);

    /**
     * 查询同一个 root session 下跨 Run 发现过的全部 session，用于 Session 级历史树。
     */
    List<RunSessionScopeSession> findSessionsByRootSessionId(String rootSessionId);

    /**
     * 查询当前 Run scope 中的单个 session。
     */
    Optional<RunSessionScopeSession> findSession(RunId runId, String sessionId);
}
