package com.icbc.testagent.configuration.management;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterChangeLog;
import java.time.Instant;

/**
 * 通用参数管理对 API 层暴露的响应模型；只携带展示所需字段，不暴露数据库代理主键。
 */
public final class CommonParameterManagementResponses {

    private CommonParameterManagementResponses() {
    }

    public record CommonParameterResponse(
            String parameterId,
            String englishName,
            String chineseName,
            String parameterValue,
            String platform,
            boolean editable,
            Instant createdAt,
            Instant updatedAt) {

        static CommonParameterResponse from(CommonParameter parameter) {
            return new CommonParameterResponse(
                    parameter.parameterId(),
                    parameter.englishName(),
                    parameter.chineseName(),
                    parameter.parameterValue(),
                    parameter.platform().value(),
                    parameter.editable(),
                    parameter.createdAt(),
                    parameter.updatedAt());
        }
    }

    /**
     * 通用参数修改日志响应，用于展示修改历史。
     */
    public record ChangeLogResponse(
            String logId,
            String parameterId,
            String oldValue,
            String newValue,
            String changedByUserId,
            String changedByUsername,
            String traceId,
            Instant createdAt) {

        static ChangeLogResponse from(CommonParameterChangeLog log) {
            return new ChangeLogResponse(
                    log.logId(),
                    log.parameterId(),
                    log.oldValue(),
                    log.newValue(),
                    log.changedByUserId(),
                    log.changedByUsername(),
                    log.traceId(),
                    log.createdAt());
        }
    }

}
