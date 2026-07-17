package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunStorageMode;
import java.time.Instant;
import java.util.Objects;

/** Run API 可选存储元数据；旧 Run 返回 null 而不是伪造 LEGACY_FULL。 */
public record RunStorageMetadata(
        RunStorageMode storageMode,
        String clientRequestId,
        Instant detailsAvailableUntil) {

    public RunStorageMetadata {
        Objects.requireNonNull(storageMode, "storageMode must not be null");
    }
}
