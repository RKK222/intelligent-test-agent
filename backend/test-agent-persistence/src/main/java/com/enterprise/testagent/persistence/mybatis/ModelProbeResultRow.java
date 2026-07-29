package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** internal_model_provider_model_probes 的 MyBatis 行映射。 */
public record ModelProbeResultRow(
        String providerId,
        String modelId,
        String capability,
        boolean succeeded,
        Instant probedAt) {
}
