package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.event.RunEventDraft;
import java.util.Objects;

/** Redis runtime Stream 中按全事件版本排序的一帧；durable seq 与 transient runtimeVersion 相互独立。 */
public record RunRuntimeStreamEvent(
        long runtimeVersion,
        boolean durable,
        long durableSeq,
        RunEventDraft draft) {

    public RunRuntimeStreamEvent {
        if (runtimeVersion <= 0) {
            throw new IllegalArgumentException("runtimeVersion must be greater than 0");
        }
        if (durable && durableSeq <= 0) {
            throw new IllegalArgumentException("durable event seq must be greater than 0");
        }
        if (!durable && durableSeq != 0) {
            throw new IllegalArgumentException("transient event seq must be 0");
        }
        Objects.requireNonNull(draft, "draft must not be null");
    }
}
