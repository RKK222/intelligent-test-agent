package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 通用参数修改日志表行模型，对应 common_parameter_change_logs 表。
 */
public record CommonParameterChangeLogRow(
        String logId,
        String parameterId,
        String oldValue,
        String newValue,
        String changedByUserId,
        String changedByUsername,
        String traceId,
        Instant createdAt) {
}
