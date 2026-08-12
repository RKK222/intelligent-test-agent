package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;

/** 本地客户端凭据仓储端口。 */
public interface LocalClientCredentialRepository {

    void lockUser(UserId userId);

    Optional<LocalClientCredential> findByUserId(UserId userId);

    Optional<LocalClientCredential> findActiveByFingerprint(String fingerprint);

    void save(LocalClientCredential credential);
}
