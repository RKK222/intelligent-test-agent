package com.icbc.testagent.domain.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Redis 中的本轮完整输入；该对象不得写入 PostgreSQL 或日志。 */
public record RunRuntimeInput(
        RunId runId,
        String prompt,
        List<Map<String, Object>> parts,
        String messageId,
        Instant createdAt) {

    public RunRuntimeInput {
        Objects.requireNonNull(runId, "runId must not be null");
        prompt = prompt == null ? "" : prompt;
        parts = parts == null ? List.of() : parts.stream().map(Map::copyOf).toList();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
