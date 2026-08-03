package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 内部模型目录的持久化端口；关系 SQL 实现必须使用 MyBatis XML。 */
public interface InternalModelProviderModelRepository {

    List<InternalModelProviderModel> findByProviderId(String providerId);

    List<InternalModelProviderModel> findEnabled();

    Optional<InternalModelProviderModel> findByModelId(String modelId);

    void replaceForProvider(
            String providerId,
            List<InternalModelProviderModel> models,
            Instant updatedAt);

    void saveProbeResult(ModelProbeResult result);
}
