package com.enterprise.testagent.opencode.runtime.run;

import java.util.List;

/** 同一目标服务器的一批重发恢复结果。 */
public record RunResendDispatchBatchResult(
        String linuxServerId,
        List<RunResendDispatchResult> results) {

    public RunResendDispatchBatchResult {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
