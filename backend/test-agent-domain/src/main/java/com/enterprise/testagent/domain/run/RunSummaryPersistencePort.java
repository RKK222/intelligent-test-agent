package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.session.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 普通 Run 的 PostgreSQL 无原文控制面端口；运行中原文和事件不得通过本端口写入。 */
public interface RunSummaryPersistencePort {

    /**
     * 插入无原文 Run 锚点；相同 Session/clientRequestId 重试返回已存在锚点，不创建第二个 Run。
     * Redis 摘要 Run 和带稳定请求号的 legacy Scheduled Run 共用同一唯一约束。
     */
    boolean insertAnchor(RunPersistenceAnchor anchor);

    /** 按幂等键查询已存在锚点，供客户端重试恢复稳定 runId/assistant messageId。 */
    Optional<RunPersistenceAnchor> findBySessionAndClientRequestId(SessionId sessionId, String clientRequestId);

    /**
     * 认领或续租尚未完成 handoff 的 legacy Scheduled Run；attempt 与租约共同阻止旧 JVM 恢复后重复提交。
     */
    boolean claimLegacyScheduledDispatch(
            RunId runId,
            String sourceRefId,
            String dispatchAttemptId,
            Instant leaseUntil,
            Instant now);

    /** 仅由当前 attempt 在异步 prompt 已提交后写入受理标记，旧 attempt 不得完成新认领。 */
    boolean markLegacyScheduledDispatchAccepted(
            RunId runId,
            String dispatchAttemptId,
            Instant acceptedAt);

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
