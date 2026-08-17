package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 本地客户端实例仓储端口。 */
public interface LocalClientInstanceRepository {

    Optional<LocalClientInstance> findById(LocalClientInstanceId clientInstanceId);

    List<LocalClientInstance> findByUserId(UserId userId);

    void save(LocalClientInstance instance);

    void markDisconnected(LocalClientInstanceId clientInstanceId, Instant disconnectedAt);
}
