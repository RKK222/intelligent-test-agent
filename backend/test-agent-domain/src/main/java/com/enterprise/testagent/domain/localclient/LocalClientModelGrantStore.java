package com.enterprise.testagent.domain.localclient;

import java.time.Duration;
import java.util.Optional;

/** 模型 grant 只按摘要保存，明文只在签发时返回客户端一次。 */
public interface LocalClientModelGrantStore {

    void save(LocalClientModelGrant grant, Duration ttl);

    Optional<LocalClientModelGrant> findByFingerprint(String fingerprint);

    boolean delete(String fingerprint);

    boolean deleteForConnection(LocalClientInstanceId clientInstanceId, long connectionGeneration);
}
