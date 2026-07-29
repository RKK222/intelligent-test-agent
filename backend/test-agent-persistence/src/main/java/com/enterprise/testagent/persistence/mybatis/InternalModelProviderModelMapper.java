package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 内部模型目录与能力探测 MyBatis mapper，SQL 统一位于 XML。 */
@Mapper
public interface InternalModelProviderModelMapper {

    List<InternalModelProviderModelRow> findByProviderId(@Param("providerId") String providerId);

    List<InternalModelProviderModelRow> findEnabled();

    InternalModelProviderModelRow findByModelId(@Param("modelId") String modelId);

    List<ModelProbeResultRow> findProbesByModelIds(@Param("modelIds") List<String> modelIds);

    void deleteByProviderId(@Param("providerId") String providerId);

    void insertModel(InternalModelProviderModelRow row);

    void upsertProbe(ModelProbeResultRow row);
}
