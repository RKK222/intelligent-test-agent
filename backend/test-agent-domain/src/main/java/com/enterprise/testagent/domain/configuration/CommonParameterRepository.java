package com.enterprise.testagent.domain.configuration;

import java.util.List;
import java.util.Optional;

/**
 * 通用参数持久化端口；业务模块只按英文名和平台读取，不直接感知参数表结构。
 *
 * <p>管理端通过 {@link #findAll()} 列出全部参数，通过 {@link #findByParameterId(String)}
 * 定位单条参数，通过 {@link #updateValue(String, String, java.time.Instant)} 仅更新参数值。
 * 端口不提供新增/删除能力，从领域层约束通用参数不可新增或删除。
 */
public interface CommonParameterRepository {

    Optional<CommonParameter> findByEnglishNameAndPlatform(String englishName, ParameterPlatform platform);

    /**
     * 列出全部通用参数，供管理端展示；结果按英文名、平台稳定排序。
     * 默认返回空列表，便于只关心按名读取的消费方用 lambda 实现本端口。
     */
    default List<CommonParameter> findAll() {
        return List.of();
    }

    /**
     * 按参数业务 ID 定位单条通用参数，供管理端更新前校验存在性。
     * 默认返回空，便于只关心按名读取的消费方用 lambda 实现本端口。
     */
    default Optional<CommonParameter> findByParameterId(String parameterId) {
        return Optional.empty();
    }

    /**
     * 仅更新参数值与更新时间，返回受影响行数；0 表示指定参数不存在。
     * 该方法只做 update，不会 insert，确保不会凭空新增参数。
     * 默认返回 0，便于只读消费方用 lambda 实现本端口。
     */
    default int updateValue(String parameterId, String newValue, java.time.Instant updatedAt) {
        return 0;
    }
}
