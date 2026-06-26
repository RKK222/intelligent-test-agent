package com.icbc.testagent.domain.configuration;

import java.util.Optional;

/**
 * 通用参数持久化端口；业务模块只按英文名和平台读取，不直接感知参数表结构。
 */
public interface CommonParameterRepository {

    Optional<CommonParameter> findByEnglishNameAndPlatform(String englishName, ParameterPlatform platform);
}
