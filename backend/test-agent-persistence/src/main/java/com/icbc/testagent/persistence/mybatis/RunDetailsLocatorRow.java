package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/** runs 表中的低频 Diff 定位列，不读取 prompt、摘要正文或原始事件。 */
public record RunDetailsLocatorRow(
        String runId,
        String storageMode,
        String rootRemoteSessionId,
        String executionNodeId,
        String lastRemoteMessageId,
        String lastRemotePartId,
        Instant detailsExpiresAt) {
}
