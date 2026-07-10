package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.session.SessionId;
import java.util.List;
import java.util.Optional;

/** Redis 摘要模式的 PostgreSQL 控制面端口；运行中原文和事件不得通过本端口写入。 */
public interface RunSummaryPersistencePort {

    /**
     * 插入无原文 Run 锚点；相同 Session/clientRequestId 重试返回已存在锚点，不创建第二个 Run。
     */
    boolean insertAnchor(RunPersistenceAnchor anchor);

    /** 按幂等键查询已存在锚点，供客户端重试恢复稳定 runId/assistant messageId。 */
    Optional<RunPersistenceAnchor> findBySessionAndClientRequestId(SessionId sessionId, String clientRequestId);

    /** 按 Run 读取不含原文的远端定位快照，用于 Diff 显式低频动作和详情过期判定。 */
    Optional<RunDetailsLocator> findDetailsLocator(RunId runId);

    /** Redis action 事件追加成功后，用单条 SQL 增加 Run 锚点计数；不写 run_events。 */
    boolean recordDiffAction(RunId runId, RunDiffAction action);

    /** 首次远端 Session 创建后用两条写语句插入 binding 并更新 legacy Session 映射，不执行前置 SELECT。 */
    void persistInitialAgentBinding(AgentSessionBinding binding);

    /** 使用 statusVersion 执行终态 CAS；只有 CAS 成功才写摘要并刷新 Session 时间。 */
    RunTerminalProjectionResult persistTerminal(RunTerminalProjection projection);

    /** Redis 详情过期且 OpenCode 不可用时，按稳定顺序返回 PostgreSQL 双摘要。 */
    List<RunConversationSummary> findSummariesByRunId(RunId runId);

    /** Session 级 OpenCode 不可用时按创建时间返回所有终态双摘要。 */
    List<RunConversationSummary> findSummariesBySessionId(SessionId sessionId);
}
