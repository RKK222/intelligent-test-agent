package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.domain.configuration.ModelCapability;
import reactor.core.publisher.Mono;

/** 模型能力探测的 API 边界。 */
public interface ModelCapabilityProbe {

    Mono<ModelCapabilityProbeResult> probe(
            String providerId,
            String modelId,
            ModelCapability capability,
            String ucid,
            String traceId);
}
