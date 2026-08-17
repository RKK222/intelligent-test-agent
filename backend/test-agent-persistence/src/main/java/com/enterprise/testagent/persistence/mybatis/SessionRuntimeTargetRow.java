package com.enterprise.testagent.persistence.mybatis;

/** sessions 表运行目标投影。 */
public record SessionRuntimeTargetRow(
        String sessionId,
        String runtimeKind,
        String localClientInstanceId) {
}
