package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/** 显式 JVM 内存通用参数的稳定键；同一英文名可按平台分别注册。 */
public record CommonParameterMemoryKey(String englishName, ParameterPlatform platform) {

    public CommonParameterMemoryKey {
        englishName = DomainValidation.requireText(englishName, "englishName").trim();
        platform = Objects.requireNonNull(platform, "platform must not be null");
    }
}
