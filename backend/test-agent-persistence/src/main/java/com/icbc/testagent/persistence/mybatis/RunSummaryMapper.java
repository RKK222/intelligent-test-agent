package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Redis 摘要模式 MyBatis mapper；关系型 SQL 只允许维护在对应 XML 中。 */
@Mapper
public interface RunSummaryMapper {

    int insertAnchor(@Param("row") RunPersistenceAnchorRow row);

    RunPersistenceAnchorRow findBySessionAndClientRequestId(
            @Param("sessionId") String sessionId,
            @Param("clientRequestId") String clientRequestId);

    RunDetailsLocatorRow findDetailsLocator(@Param("runId") String runId);

    int recordDiffAction(@Param("runId") String runId, @Param("action") String action);

    int insertInitialAgentBinding(@Param("binding") com.icbc.testagent.domain.agent.AgentSessionBinding binding);

    int updateLegacySessionBinding(@Param("binding") com.icbc.testagent.domain.agent.AgentSessionBinding binding);

    int updateTerminalIfVersion(@Param("row") RunTerminalProjectionRow row);

    int upsertSummaries(@Param("rows") List<RunSummaryRow> rows);

    int touchSession(
            @Param("sessionId") String sessionId,
            @Param("updatedAt") Instant updatedAt);

    List<RunSummaryRow> findSummariesByRunId(@Param("runId") String runId);

    List<RunSummaryRow> findSummariesBySessionId(@Param("sessionId") String sessionId);
}
