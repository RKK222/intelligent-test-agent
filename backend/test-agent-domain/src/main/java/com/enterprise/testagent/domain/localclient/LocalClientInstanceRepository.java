package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 本地客户端实例仓储端口。 */
public interface LocalClientInstanceRepository {

    Optional<LocalClientInstance> findById(LocalClientInstanceId clientInstanceId);

    List<LocalClientInstance> findByUserId(UserId userId);

    List<LocalClientInstance> findAll();

    void save(LocalClientInstance instance);

    void markDisconnected(LocalClientInstanceId clientInstanceId, Instant disconnectedAt);

    void updateLastUpdateStatus(
            LocalClientInstanceId clientInstanceId,
            String status,
            String targetVersion,
            Instant observedAt);
}
