package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_instances 的 MyBatis 行模型。 */
public record LocalClientInstanceRow(
        String clientInstanceId,
        String userId,
        String clientName,
        String platform,
        String architecture,
        String clientVersion,
        String opencodeVersion,
        Instant createdAt,
        Instant updatedAt,
        Instant lastConnectedAt,
        Instant lastDisconnectedAt) {
}
