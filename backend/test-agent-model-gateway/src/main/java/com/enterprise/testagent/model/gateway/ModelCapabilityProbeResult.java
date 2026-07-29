package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.domain.configuration.ModelCapability;
import java.time.Instant;

/** 管理 API 可安全返回的模型能力探测结果。 */
public record ModelCapabilityProbeResult(
        ModelCapability capability,
        boolean succeeded,
        Instant probedAt) {
}
