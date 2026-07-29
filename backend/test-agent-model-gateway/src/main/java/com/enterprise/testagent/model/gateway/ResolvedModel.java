package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;

/** 网关内部解析结果，包含不得序列化到 API 的供应商凭据。 */
public record ResolvedModel(
        InternalModelProviderRuntimeConfig provider,
        InternalModelProviderModel model) {

    public String upstreamModelId() {
        return model.upstreamModelId();
    }
}
