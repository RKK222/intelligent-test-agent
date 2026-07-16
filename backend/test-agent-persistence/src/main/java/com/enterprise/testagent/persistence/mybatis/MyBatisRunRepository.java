package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Run 的 MyBatis Repository 实现，使用 XML 中的条件更新避免终态竞态覆盖。
 */
@Repository
public class MyBatisRunRepository implements RunRepository {

    private final RunMapper mapper;

    /**
     * 注入 MyBatis mapper；连接、事务和 SQL 执行由 MyBatis-Spring 管理。
     */
    public MyBatisRunRepository(RunMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存运行状态快照；状态迁移合法性由领域和应用层保证。
     */
    @Override
    public Run save(Run run) {
        RunRow row = toRow(run);
        if (mapper.findById(run.runId().value()) == null) {
            mapper.insert(row);
        } else {
            mapper.update(row);
        }
        return run;
    }

    /**
     * 通过状态条件更新实现 compare-and-set，失败时返回数据库当前 Run。
     */
    @Override
    public Run saveIfStatus(Run run, RunStatus expectedStatus) {
        int updated = mapper.updateIfStatus(toRow(run), expectedStatus.name());
        if (updated == 1) {
            return run;
        }
        RunRow current = mapper.findById(run.runId().value());
        if (current != null) {
            return toDomain(current);
        }
        throw new PlatformException(
                ErrorCode.NOT_FOUND,
                "Run 不存在",
                Map.of("runId", run.runId().value()));
    }

    @Override
    public Optional<Run> findById(RunId runId) {
        return Optional.ofNullable(mapper.findById(runId.value()))
                .map(this::toDomain);
    }

    /** 批量恢复历史会话的 Run 状态，避免反馈面板逐条查询。 */
    @Override
    public List<Run> findByIds(List<RunId> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return mapper.findByIds(runIds.stream().map(RunId::value).distinct().toList()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Run> findLatestActiveBySessionId(SessionId sessionId) {
        return Optional.ofNullable(mapper.findLatestActiveBySessionId(sessionId.value()))
                .map(this::toDomain);
    }

    @Override
    public List<Run> findStaleActiveRuns(Instant updatedBefore, int limit) {
        validateStaleQuery(updatedBefore, limit);
        return mapper.findStaleActiveRuns(updatedBefore, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    /** 只查询过期且仍为 active 的 SIDE_QUESTION Run，避免孤儿任务误收敛普通对话。 */
    @Override
    public List<Run> findStaleActiveSideQuestionRuns(Instant updatedBefore, int limit) {
        validateStaleQuery(updatedBefore, limit);
        return mapper.findStaleActiveSideQuestionRuns(updatedBefore, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    private void validateStaleQuery(Instant updatedBefore, int limit) {
        if (updatedBefore == null) {
            throw new IllegalArgumentException("updatedBefore must not be null");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private Run toDomain(RunRow row) {
        return new Run(
                new RunId(row.runId()),
                new SessionId(row.sessionId()),
                new WorkspaceId(row.workspaceId()),
                RunStatus.valueOf(row.status()),
                row.createdAt(),
                row.updatedAt(),
                row.traceId(),
                new TokenUsage(
                        row.tokensInput(),
                        row.tokensOutput(),
                        row.tokensReasoning(),
                        row.tokensCacheRead(),
                        row.tokensCacheWrite()),
                row.costUsd(),
                ConversationSourceType.valueOf(row.sourceType()),
                row.sourceRefId(),
                userId(row.triggeredByUserId()),
                row.agentId(),
                row.modelId());
    }

    private RunRow toRow(Run run) {
        return new RunRow(
                run.runId().value(),
                run.sessionId().value(),
                run.workspaceId().value(),
                run.status().name(),
                run.traceId(),
                run.createdAt(),
                run.updatedAt(),
                run.tokenUsage().input(),
                run.tokenUsage().output(),
                run.tokenUsage().reasoning(),
                run.tokenUsage().cacheRead(),
                run.tokenUsage().cacheWrite(),
                run.costUsd(),
                run.sourceType().name(),
                run.sourceRefId(),
                userIdValue(run.triggeredByUserId()),
                run.agentId(),
                run.modelId());
    }

    private static UserId userId(String value) {
        return value == null ? null : new UserId(value);
    }

    private static String userIdValue(UserId userId) {
        return userId == null ? null : userId.value();
    }
}
