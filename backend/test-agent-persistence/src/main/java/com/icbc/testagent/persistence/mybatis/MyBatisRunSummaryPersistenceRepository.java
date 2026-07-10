package com.icbc.testagent.persistence.mybatis;

import com.icbc.testagent.domain.run.RunConversationSummary;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.run.RunDetailsLocator;
import com.icbc.testagent.domain.run.RunDiffAction;
import com.icbc.testagent.domain.run.RunPersistenceAnchor;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.run.RunSummaryStatus;
import com.icbc.testagent.domain.run.RunTerminalProjection;
import com.icbc.testagent.domain.run.RunTerminalProjectionResult;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis 摘要模式的 PostgreSQL 适配器：启动只插入无原文锚点，终态用三条 SQL 完成 CAS、双摘要和 Session 更新。
 */
@Repository
public class MyBatisRunSummaryPersistenceRepository implements RunSummaryPersistencePort {

    private static final String SUMMARY_CONTENT_KIND = "SUMMARY";

    private final RunSummaryMapper mapper;

    public MyBatisRunSummaryPersistenceRepository(RunSummaryMapper mapper) {
        this.mapper = mapper;
    }

    /** 正常启动只执行一条 INSERT；冲突后的查询由调用方显式发起，避免新 Run 多一次 SELECT。 */
    @Override
    public boolean insertAnchor(RunPersistenceAnchor anchor) {
        return mapper.insertAnchor(toRow(anchor)) == 1;
    }

    @Override
    public Optional<RunPersistenceAnchor> findBySessionAndClientRequestId(
            SessionId sessionId,
            String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findBySessionAndClientRequestId(sessionId.value(), clientRequestId))
                .map(this::toDomain);
    }

    @Override
    public Optional<RunDetailsLocator> findDetailsLocator(com.icbc.testagent.domain.run.RunId runId) {
        return Optional.ofNullable(mapper.findDetailsLocator(runId.value()))
                .map(row -> new RunDetailsLocator(
                        new com.icbc.testagent.domain.run.RunId(row.runId()),
                        RunStorageMode.valueOf(row.storageMode()),
                        row.rootRemoteSessionId(),
                        row.executionNodeId(),
                        row.lastRemoteMessageId(),
                        row.lastRemotePartId(),
                        row.detailsExpiresAt()));
    }

    @Override
    public boolean recordDiffAction(com.icbc.testagent.domain.run.RunId runId, RunDiffAction action) {
        return mapper.recordDiffAction(runId.value(), action.name()) == 1;
    }

    @Override
    @Transactional
    public void persistInitialAgentBinding(AgentSessionBinding binding) {
        mapper.insertInitialAgentBinding(binding);
        if ("opencode".equals(binding.agentId())) {
            mapper.updateLegacySessionBinding(binding);
        }
    }

    /**
     * CAS 失败时立即返回且只执行一条 UPDATE；成功后摘要与 Session 时间和 Run 状态处于同一事务。
     */
    @Override
    @Transactional
    public RunTerminalProjectionResult persistTerminal(RunTerminalProjection projection) {
        if (mapper.updateTerminalIfVersion(toRow(projection)) != 1) {
            return RunTerminalProjectionResult.VERSION_CONFLICT;
        }
        if (!projection.summaries().isEmpty()) {
            mapper.upsertSummaries(projection.summaries().stream()
                    .map(summary -> toRow(projection, summary))
                    .toList());
        }
        mapper.touchSession(projection.sessionId().value(), projection.updatedAt());
        return RunTerminalProjectionResult.APPLIED;
    }

    @Override
    public List<RunConversationSummary> findSummariesByRunId(com.icbc.testagent.domain.run.RunId runId) {
        return mapper.findSummariesByRunId(runId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RunConversationSummary> findSummariesBySessionId(SessionId sessionId) {
        return mapper.findSummariesBySessionId(sessionId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    private RunPersistenceAnchorRow toRow(RunPersistenceAnchor anchor) {
        return new RunPersistenceAnchorRow(
                anchor.runId().value(),
                anchor.sessionId().value(),
                anchor.workspaceId().value(),
                anchor.status().name(),
                anchor.storageMode().name(),
                anchor.statusVersion(),
                anchor.clientRequestId(),
                anchor.producerLinuxServerId(),
                anchor.executionNodeIdSnapshot(),
                anchor.opencodeProcessIdSnapshot(),
                anchor.rootRemoteSessionId(),
                anchor.dispatchMessageId(),
                anchor.assistantSummaryMessageId().value(),
                anchor.traceId(),
                anchor.createdAt(),
                anchor.updatedAt(),
                anchor.detailsExpiresAt(),
                anchor.sourceType().name(),
                anchor.sourceRefId(),
                value(anchor.triggeredByUserId()),
                anchor.agentId(),
                anchor.modelId());
    }

    private RunTerminalProjectionRow toRow(RunTerminalProjection projection) {
        return new RunTerminalProjectionRow(
                projection.runId().value(),
                projection.sessionId().value(),
                projection.status().name(),
                projection.expectedStatusVersion(),
                projection.terminalSource(),
                projection.terminalReasonCode(),
                projection.safeErrorMessage(),
                projection.remoteStopConfirmed(),
                projection.lastEventSeq(),
                projection.detailsExpiresAt(),
                projection.rootRemoteSessionId(),
                projection.diffCounts().proposed(),
                projection.diffCounts().accepted(),
                projection.diffCounts().rejected(),
                projection.lastRemoteMessageId(),
                projection.lastRemotePartId(),
                projection.tokenUsage().input(),
                projection.tokenUsage().output(),
                projection.tokenUsage().reasoning(),
                projection.tokenUsage().cacheRead(),
                projection.tokenUsage().cacheWrite(),
                projection.costUsd(),
                projection.updatedAt());
    }

    private RunSummaryRow toRow(RunTerminalProjection projection, RunConversationSummary summary) {
        String senderUserId = summary.role() == SessionMessageRole.USER ? value(projection.senderUserId()) : null;
        return new RunSummaryRow(
                summary.messageId().value(),
                projection.sessionId().value(),
                summary.role().name(),
                summary.content(),
                projection.traceId(),
                summary.createdAt(),
                projection.runId().value(),
                projection.agentId(),
                summary.remoteMessageId(),
                projection.updatedAt(),
                projection.sourceType().name(),
                projection.sourceRefId(),
                senderUserId,
                SUMMARY_CONTENT_KIND,
                summary.summaryKey(),
                summary.summaryVersion(),
                summary.summaryStatus().name());
    }

    private RunPersistenceAnchor toDomain(RunPersistenceAnchorRow row) {
        return new RunPersistenceAnchor(
                new com.icbc.testagent.domain.run.RunId(row.runId()),
                new SessionId(row.sessionId()),
                new WorkspaceId(row.workspaceId()),
                RunStatus.valueOf(row.status()),
                RunStorageMode.valueOf(row.storageMode()),
                row.statusVersion(),
                row.clientRequestId(),
                row.producerLinuxServerId(),
                row.executionNodeIdSnapshot(),
                row.opencodeProcessIdSnapshot(),
                row.rootRemoteSessionId(),
                row.dispatchMessageId(),
                new SessionMessageId(row.assistantSummaryMessageId()),
                row.traceId(),
                row.createdAt(),
                row.updatedAt(),
                row.detailsExpiresAt(),
                ConversationSourceType.valueOf(row.sourceType()),
                row.sourceRefId(),
                userId(row.triggeredByUserId()),
                row.agentId(),
                row.modelId());
    }

    private RunConversationSummary toDomain(RunSummaryRow row) {
        return new RunConversationSummary(
                new SessionMessageId(row.messageId()),
                SessionMessageRole.valueOf(row.role()),
                row.content(),
                row.summaryKey(),
                row.summaryVersion(),
                RunSummaryStatus.valueOf(row.summaryStatus()),
                row.createdAt(),
                row.remoteMessageId());
    }

    private static String value(UserId userId) {
        return userId == null ? null : userId.value();
    }

    private static UserId userId(String value) {
        return value == null ? null : new UserId(value);
    }
}
