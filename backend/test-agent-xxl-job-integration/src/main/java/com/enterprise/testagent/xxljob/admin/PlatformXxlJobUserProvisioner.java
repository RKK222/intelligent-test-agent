package com.enterprise.testagent.xxljob.admin;

import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.xxljob.XxlJobSsoIdentity;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** 按 platformUserId 幂等创建或更新 XXL 管理员，并稳定处理显示名冲突。 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformXxlJobUserProvisioner {

    private static final int USERNAME_MAX_LENGTH = 128;

    private final PlatformXxlJobUserMapper mapper;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public PlatformXxlJobUserProvisioner(PlatformXxlJobUserMapper mapper) {
        this(mapper, Clock.systemUTC(), new SecureRandom());
    }

    PlatformXxlJobUserProvisioner(PlatformXxlJobUserMapper mapper, Clock clock, SecureRandom secureRandom) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public PlatformXxlJobUser provision(XxlJobSsoIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (!identity.sessionExpiresAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("platform session expired");
        }
        PlatformXxlJobUser existing = mapper.findByPlatformUserId(identity.platformUserId());
        String username = normalizedDisplayName(identity.displayName());
        PlatformXxlJobUser collision = mapper.findByUsername(username);
        if (collision != null && !identity.platformUserId().equals(collision.platformUserId())) {
            username = collisionName(username, identity.platformUserId());
        }
        mapper.upsert(new PlatformXxlJobUserUpsert(
                identity.platformUserId(),
                username,
                existing == null ? disabledPassword() : "",
                1,
                "",
                identity.sessionDigest(),
                identity.sessionExpiresAt()));
        PlatformXxlJobUser provisioned = mapper.findByPlatformUserId(identity.platformUserId());
        if (provisioned == null) {
            throw new IllegalStateException("XXL-JOB user upsert failed");
        }
        return provisioned;
    }

    private String normalizedDisplayName(String displayName) {
        String value = displayName.trim();
        return value.length() <= USERNAME_MAX_LENGTH ? value : value.substring(0, USERNAME_MAX_LENGTH);
    }

    private String collisionName(String base, String platformUserId) {
        String suffix = "-" + TokenSessionMarkerStore.sha256(platformUserId).substring(0, 8);
        int baseLength = Math.min(base.length(), USERNAME_MAX_LENGTH - suffix.length());
        return base.substring(0, baseLength) + suffix;
    }

    private String disabledPassword() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
