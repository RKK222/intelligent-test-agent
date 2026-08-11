package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResendDetailCleanupPort;
import com.enterprise.testagent.domain.session.SessionId;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 原生替代消息受理后的关系型明细清理；保留 runs、反馈、用量与重发审计关系。 */
@Repository
public class MyBatisRunResendDetailCleanup implements RunResendDetailCleanupPort {

    private final RunResendMapper mapper;
    private final RunSummaryMapper summaryMapper;

    public MyBatisRunResendDetailCleanup(RunResendMapper mapper, RunSummaryMapper summaryMapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.summaryMapper = Objects.requireNonNull(summaryMapper);
    }

    @Override
    @Transactional
    public void purgeSourceRun(RunId sourceRunId, SessionId sessionId, Instant updatedAt) {
        Objects.requireNonNull(sourceRunId, "sourceRunId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        String runId = sourceRunId.value();
        // 历史消息级反馈先解除 message 外键，run_id 仍完整保留整轮评价与运营统计。
        mapper.detachFeedbackMessagesByRunId(runId);
        mapper.deleteScopeSessionsByRunId(runId);
        mapper.deleteScopesByRunId(runId);
        mapper.deleteSessionMessagesByRunId(runId);
        mapper.deleteRunEventsByRunId(runId);
        // 分享运行态以 sessions.updated_at 作为正文修订号；必须与源轮清理原子提交，避免参与方读到半旧快照。
        summaryMapper.touchSession(sessionId.value(), updatedAt);
    }
}
