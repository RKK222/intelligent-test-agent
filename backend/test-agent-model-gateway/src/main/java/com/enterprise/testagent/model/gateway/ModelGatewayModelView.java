package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.domain.configuration.ModelCapability;
import java.util.Set;

/** 对 LobeHub 公开的企业模型视图，不暴露内部 provider 路由。 */
public record ModelGatewayModelView(
        String id,
        String displayName,
        Long contextLimit,
        Set<ModelCapability> capabilities,
        String providerId) {
}
