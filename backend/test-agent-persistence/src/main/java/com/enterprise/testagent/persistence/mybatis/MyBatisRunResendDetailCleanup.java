package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResendDetailCleanupPort;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 原生替代消息受理后的关系型明细清理；保留 runs、反馈、用量与重发审计关系。 */
@Repository
public class MyBatisRunResendDetailCleanup implements RunResendDetailCleanupPort {

    private final RunResendMapper mapper;

    public MyBatisRunResendDetailCleanup(RunResendMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional
    public void purgeSourceRun(RunId sourceRunId) {
        Objects.requireNonNull(sourceRunId, "sourceRunId must not be null");
        String runId = sourceRunId.value();
        // 历史消息级反馈先解除 message 外键，run_id 仍完整保留整轮评价与运营统计。
        mapper.detachFeedbackMessagesByRunId(runId);
        mapper.deleteScopeSessionsByRunId(runId);
        mapper.deleteScopesByRunId(runId);
        mapper.deleteSessionMessagesByRunId(runId);
        mapper.deleteRunEventsByRunId(runId);
    }
}
