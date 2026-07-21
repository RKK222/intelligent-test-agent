package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Run 控制面锚点与摘要的 MyBatis mapper；关系型 SQL 只允许维护在对应 XML 中。 */
@Mapper
public interface RunSummaryMapper {

    int insertAnchor(@Param("row") RunPersistenceAnchorRow row);

    RunPersistenceAnchorRow findBySessionAndClientRequestId(
            @Param("sessionId") String sessionId,
            @Param("clientRequestId") String clientRequestId);

    int claimLegacyScheduledDispatch(
            @Param("runId") String runId,
            @Param("sourceRefId") String sourceRefId,
            @Param("dispatchAttemptId") String dispatchAttemptId,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int markLegacyScheduledDispatchAccepted(
            @Param("runId") String runId,
            @Param("dispatchAttemptId") String dispatchAttemptId,
            @Param("acceptedAt") Instant acceptedAt);

    RunDetailsLocatorRow findDetailsLocator(@Param("runId") String runId);

    int recordDiffAction(@Param("runId") String runId, @Param("action") String action);

    int insertInitialAgentBinding(@Param("binding") com.enterprise.testagent.domain.agent.AgentSessionBinding binding);

    int updateLegacySessionBinding(@Param("binding") com.enterprise.testagent.domain.agent.AgentSessionBinding binding);

    int updateTerminalIfVersion(@Param("row") RunTerminalProjectionRow row);

    int upsertSummaries(@Param("rows") List<RunSummaryRow> rows);

    int touchSession(
            @Param("sessionId") String sessionId,
            @Param("updatedAt") Instant updatedAt);

    List<RunSummaryRow> findSummariesByRunId(@Param("runId") String runId);

    List<RunSummaryRow> findSummariesBySessionId(@Param("sessionId") String sessionId);
}
