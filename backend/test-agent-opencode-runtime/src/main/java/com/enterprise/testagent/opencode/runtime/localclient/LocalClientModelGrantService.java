package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrant;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrantStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 后台模型中继入口校验短期 grant，并再次核对当前连接 generation。 */
@Service
public class LocalClientModelGrantService {

    private final LocalClientModelGrantStore grantStore;
    private final LocalClientConnectionStore connectionStore;
    private final Clock clock;

    @Autowired
    public LocalClientModelGrantService(
            LocalClientModelGrantStore grantStore,
            LocalClientConnectionStore connectionStore) {
        this(grantStore, connectionStore, Clock.systemUTC());
    }

    LocalClientModelGrantService(
            LocalClientModelGrantStore grantStore,
            LocalClientConnectionStore connectionStore,
            Clock clock) {
        this.grantStore = Objects.requireNonNull(grantStore, "grantStore must not be null");
        this.connectionStore = Objects.requireNonNull(connectionStore, "connectionStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public LocalClientModelGrant authenticate(String rawGrant) {
        if (rawGrant == null || !rawGrant.startsWith("lcg_v1_") || rawGrant.length() > 128) {
            throw unauthenticated();
        }
        String fingerprint = LocalClientSecretSupport.fingerprint(rawGrant);
        LocalClientModelGrant grant = grantStore.findByFingerprint(fingerprint)
                .orElseThrow(LocalClientModelGrantService::unauthenticated);
        if (!grant.expiresAt().isAfter(Instant.now(clock))) {
            grantStore.delete(fingerprint);
            throw unauthenticated();
        }
        LocalClientConnectionRoute route = connectionStore.find(grant.clientInstanceId())
                .orElseThrow(LocalClientModelGrantService::unauthenticated);
        if (route.connectionGeneration() != grant.connectionGeneration()
                || !route.userId().equals(grant.userId())) {
            grantStore.delete(fingerprint);
            throw unauthenticated();
        }
        return grant;
    }

    private static PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "本地客户端模型授权无效或已过期");
    }
}
