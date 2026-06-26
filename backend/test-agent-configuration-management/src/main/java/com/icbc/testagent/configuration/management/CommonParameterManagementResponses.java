package com.icbc.testagent.configuration.management;

import com.icbc.testagent.domain.configuration.CommonParameter;
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
            Instant createdAt,
            Instant updatedAt) {

        static CommonParameterResponse from(CommonParameter parameter) {
            return new CommonParameterResponse(
                    parameter.parameterId(),
                    parameter.englishName(),
                    parameter.chineseName(),
                    parameter.parameterValue(),
                    parameter.platform().value(),
                    parameter.createdAt(),
                    parameter.updatedAt());
        }
    }
}
