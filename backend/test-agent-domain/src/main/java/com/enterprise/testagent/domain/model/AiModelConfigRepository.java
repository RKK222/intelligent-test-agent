package com.enterprise.testagent.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * 大模型配置持久化端口，内网模型列表和启动 seed 均通过该端口访问数据库。
 */
public interface AiModelConfigRepository {

    /**
     * 保存或更新一个模型配置。
     */
    AiModelConfig save(AiModelConfig modelConfig);

    /**
     * 判断指定 provider/model 是否已有配置。
     */
    boolean existsByProviderAndModel(String providerId, String modelId);

    /**
     * 查询指定 provider 下启用的模型，按默认模型和排序字段返回。
     */
    List<AiModelConfig> findEnabledByProvider(String providerId);

    /**
     * 查询指定 provider 下的默认模型。
     */
    Optional<AiModelConfig> findDefaultByProvider(String providerId);
}
