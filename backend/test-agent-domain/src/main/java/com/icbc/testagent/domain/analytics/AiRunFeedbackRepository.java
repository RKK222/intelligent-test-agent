package com.icbc.testagent.domain.analytics;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.user.UserId;
import java.util.List;
import java.util.Optional;

/** Run 整体评价持久化端口，业务唯一键为用户与 Run。 */
public interface AiRunFeedbackRepository {

    AiRunFeedback save(AiRunFeedback feedback);

    Optional<AiRunFeedback> findByUserIdAndRunId(UserId userId, RunId runId);

    List<AiRunFeedback> findByUserIdAndRunIds(UserId userId, List<RunId> runIds);
}
