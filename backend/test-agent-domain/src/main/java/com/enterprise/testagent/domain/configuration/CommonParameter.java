package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
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
        boolean editable,
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

    /**
     * 返回替换了参数值与更新时间的新实例，复用 compact 构造器校验，
     * 保证新值非空且更新时间不早于创建时间；供管理端「仅修改 value」场景使用。
     * editable 标志不随 value 改变，原样透传。
     */
    public CommonParameter withValue(String newValue, Instant updatedAt) {
        return new CommonParameter(parameterId, englishName, chineseName, newValue, platform, editable, createdAt, updatedAt);
    }
}
