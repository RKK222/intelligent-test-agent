package com.enterprise.testagent.integration.lobehub;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** LobeHub 服务身份、票据和委托的安全时限配置。 */
@Component
@ConfigurationProperties(prefix = "test-agent.lobehub")
public class LobehubIntegrationProperties {

    private static final Duration DEFAULT_TICKET_TTL = Duration.ofSeconds(60);
    private static final Duration DEFAULT_GRANT_TTL = Duration.ofDays(30);
    private static final Duration DEFAULT_HMAC_CLOCK_SKEW = Duration.ofSeconds(60);
    private static final Duration DEFAULT_NONCE_TTL = Duration.ofSeconds(120);

    private String hmacSecret;
    private String clientId = "lobehub";
    private Duration ticketTtl = DEFAULT_TICKET_TTL;
    private Duration grantTtl = DEFAULT_GRANT_TTL;
    private Duration hmacClockSkew = DEFAULT_HMAC_CLOCK_SKEW;
    private Duration nonceTtl = DEFAULT_NONCE_TTL;

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Duration getTicketTtl() {
        return ticketTtl;
    }

    public void setTicketTtl(Duration ticketTtl) {
        this.ticketTtl = bounded(ticketTtl, DEFAULT_TICKET_TTL, DEFAULT_TICKET_TTL, "ticketTtl");
    }

    public Duration getGrantTtl() {
        return grantTtl;
    }

    public void setGrantTtl(Duration grantTtl) {
        this.grantTtl = bounded(grantTtl, DEFAULT_GRANT_TTL, DEFAULT_GRANT_TTL, "grantTtl");
    }

    public Duration getHmacClockSkew() {
        return hmacClockSkew;
    }

    public void setHmacClockSkew(Duration hmacClockSkew) {
        this.hmacClockSkew = bounded(
                hmacClockSkew,
                DEFAULT_HMAC_CLOCK_SKEW,
                DEFAULT_HMAC_CLOCK_SKEW,
                "hmacClockSkew");
    }

    public Duration getNonceTtl() {
        return nonceTtl;
    }

    public void setNonceTtl(Duration nonceTtl) {
        Duration value = positive(nonceTtl, DEFAULT_NONCE_TTL, "nonceTtl");
        // nonce 必须覆盖允许的时间戳前后偏差，避免签名仍有效时重放记录已经过期。
        if (value.compareTo(DEFAULT_NONCE_TTL) < 0) {
            throw new IllegalArgumentException("nonceTtl is shorter than the replay window");
        }
        this.nonceTtl = value;
    }

    private static Duration bounded(
            Duration candidate,
            Duration fallback,
            Duration maximum,
            String name) {
        Duration value = positive(candidate, fallback, name);
        if (value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " exceeds the secure maximum");
        }
        return value;
    }

    private static Duration positive(Duration candidate, Duration fallback, String name) {
        if (candidate == null) {
            return fallback;
        }
        if (candidate.isZero() || candidate.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return candidate;
    }
}
