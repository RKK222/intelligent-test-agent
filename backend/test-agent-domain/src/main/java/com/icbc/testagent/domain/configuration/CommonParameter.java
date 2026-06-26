package com.icbc.testagent.domain.configuration;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 通用参数配置项，用于保存跨模块共享的稳定运行参数。
 */
public record CommonParameter(
        String parameterId,
        String englishName,
        String chineseName,
        String parameterValue,
        ParameterPlatform platform,
        Instant createdAt,
        Instant updatedAt) {

    public CommonParameter {
        parameterId = DomainValidation.requireText(parameterId, "parameterId").trim();
        englishName = DomainValidation.requireText(englishName, "englishName").trim();
        chineseName = DomainValidation.requireText(chineseName, "chineseName").trim();
        parameterValue = DomainValidation.requireText(parameterValue, "parameterValue").trim();
        Objects.requireNonNull(platform, "platform must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
