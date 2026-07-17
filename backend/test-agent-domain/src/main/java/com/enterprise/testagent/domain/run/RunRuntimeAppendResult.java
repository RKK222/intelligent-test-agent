package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.event.RunEvent;
import java.util.Objects;

/** Lua 原子追加结果，显式返回容量截断和 reset generation。 */
public record RunRuntimeAppendResult(
        RunEvent event,
        boolean truncatedNow,
        long resetGeneration,
        long earliestSeq,
        boolean visible) {

    /** 兼容正常追加调用；只有终态后的非法回退事件才显式标记为不可见。 */
    public RunRuntimeAppendResult(
            RunEvent event,
            boolean truncatedNow,
            long resetGeneration,
            long earliestSeq) {
        this(event, truncatedNow, resetGeneration, earliestSeq, true);
    }

    public RunRuntimeAppendResult {
        Objects.requireNonNull(event, "event must not be null");
        if (resetGeneration < 0 || earliestSeq < 0) {
            throw new IllegalArgumentException("append counters must not be negative");
        }
    }
}
