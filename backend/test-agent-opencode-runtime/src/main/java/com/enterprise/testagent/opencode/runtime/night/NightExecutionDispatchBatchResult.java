package com.enterprise.testagent.opencode.runtime.night;

import java.util.List;

/** 目标 Java 的批量分发结果，顺序与请求 taskIds 一致。 */
public record NightExecutionDispatchBatchResult(
        String linuxServerId,
        List<NightExecutionDispatchResult> results) {

    public NightExecutionDispatchBatchResult {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            throw new IllegalArgumentException("linuxServerId must not be blank");
        }
        linuxServerId = linuxServerId.trim();
        results = results == null ? List.of() : List.copyOf(results);
    }
}
