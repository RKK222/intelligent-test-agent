package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.domain.localclient.LocalClientRolloutEntry;
import java.time.Instant;

/** 本地客户端灰度管理的安全响应，不包含 client key 或客户端连接信息。 */
public final class LocalClientRolloutResponses {

    private LocalClientRolloutResponses() {
    }

    public record RolloutUserView(
            String userId,
            boolean enabled,
            String updatedByUserId,
            Instant createdAt,
            Instant updatedAt) {

        static RolloutUserView from(LocalClientRolloutEntry entry) {
            return new RolloutUserView(
                    entry.userId().value(),
                    entry.enabled(),
                    entry.updatedByUserId().value(),
                    entry.createdAt(),
                    entry.updatedAt());
        }
    }
}
