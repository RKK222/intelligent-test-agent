package com.enterprise.testagent.domain.run;

import java.util.Optional;

/** Run 关系型锚点运行目标端口。 */
public interface RunRuntimeTargetRepository {

    Optional<RunRuntimeTarget> findByRunId(RunId runId);

    void save(RunRuntimeTarget target);
}
