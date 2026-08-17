package com.enterprise.testagent.persistence.mybatis;

/** runs 目标字段只读行。 */
public record RunRuntimeTargetRow(
        String runId,
        String targetRuntimeKind,
        String targetLocalClientInstanceId) {
}
