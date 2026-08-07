package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 为 API 和历史投影提供中立的重发元数据查询，避免 Controller 直接依赖 Repository。 */
@Service
public class RunResendQueryService {

    private final RunResendRepository repository;

    public RunResendQueryService(RunResendRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /** 源 Run 与替代 Run 都映射到同一条重发关系，旧数据不存在时返回 null。 */
    public RunResend findForRun(RunId runId) {
        if (runId == null) return null;
        return repository.findByReplacementRunId(runId)
                .or(() -> repository.findBySourceRunId(runId))
                .orElse(null);
    }

    /** WAITING 替代 Run 尚无生产路由，停止操作必须留在入口 Java 走共享状态机 CAS。 */
    public boolean isWaitingReplacement(RunId runId) {
        return runId != null && repository.findByReplacementRunId(runId)
                .filter(resend -> resend.status() == RunResendStatus.WAITING)
                .isPresent();
    }
}
