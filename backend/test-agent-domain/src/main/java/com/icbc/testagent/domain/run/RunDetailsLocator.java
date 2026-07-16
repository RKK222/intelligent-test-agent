package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * PostgreSQL 控制面中用于低频 Run 恢复和 Diff 定位的非原文快照。
 *
 * <p>该对象只表达 Run 存储模式、用户轮次锚点和远端 ID，不包含 prompt、Diff 内容或原始事件。
 */
public record RunDetailsLocator(
        RunId runId,
        RunStorageMode storageMode,
        String rootRemoteSessionId,
        String dispatchMessageId,
        String executionNodeId,
        String lastRemoteMessageId,
        String lastRemotePartId,
        Instant detailsExpiresAt) {

    public RunDetailsLocator {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(storageMode, "storageMode must not be null");
        rootRemoteSessionId = optionalText(rootRemoteSessionId, "rootRemoteSessionId");
        dispatchMessageId = optionalText(dispatchMessageId, "dispatchMessageId");
        executionNodeId = optionalText(executionNodeId, "executionNodeId");
        lastRemoteMessageId = optionalText(lastRemoteMessageId, "lastRemoteMessageId");
        lastRemotePartId = optionalText(lastRemotePartId, "lastRemotePartId");
        detailsExpiresAt = DomainValidation.requireInstant(detailsExpiresAt, "detailsExpiresAt");
    }

    private static String optionalText(String value, String fieldName) {
        return value == null ? null : DomainValidation.requireText(value, fieldName);
    }
}
