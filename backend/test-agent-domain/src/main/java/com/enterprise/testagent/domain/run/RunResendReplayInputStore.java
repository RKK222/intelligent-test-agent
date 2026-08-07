package com.enterprise.testagent.domain.run;

import java.util.Optional;

/** 精确重放输入的有限 TTL Redis 端口。 */
public interface RunResendReplayInputStore {

    void save(RunResendReplayInput input);

    Optional<RunResendReplayInput> find(RunId replacementRunId);

    void delete(RunId replacementRunId);
}
