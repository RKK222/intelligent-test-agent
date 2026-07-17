package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 通用参数修改日志，记录每次参数值修改的审计信息。
 */
public record CommonParameterChangeLog(
        String logId,
        String parameterId,
        String oldValue,
        String newValue,
        String changedByUserId,
        String changedByUsername,
        String traceId,
        Instant createdAt) {

    public CommonParameterChangeLog {
        logId = DomainValidation.requireText(logId, "logId").trim();
        parameterId = DomainValidation.requireText(parameterId, "parameterId").trim();
        // oldValue 可为空（首次修改或历史数据迁移）
        newValue = DomainValidation.requireText(newValue, "newValue").trim();
        // changedByUserId、changedByUsername、traceId 可为空（兼容本地开发或 static token 场景）
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
    }

    /**
     * 静态工厂方法，用于创建新的修改日志实例。
     */
    public static CommonParameterChangeLog create(
            String logId,
            String parameterId,
            String oldValue,
            String newValue,
            String changedByUserId,
            String changedByUsername,
            String traceId,
            Instant createdAt) {
        return new CommonParameterChangeLog(
                logId, parameterId, oldValue, newValue, changedByUserId, changedByUsername, traceId, createdAt);
    }
}
