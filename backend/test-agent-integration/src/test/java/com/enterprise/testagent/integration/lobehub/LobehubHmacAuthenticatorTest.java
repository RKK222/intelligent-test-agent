package com.enterprise.testagent.integration.lobehub;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.lobehub.LobehubSsoStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LobehubHmacAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-07-29T02:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void acceptsValidSignatureOnceAndRejectsNonceReplay() {
        LobehubSsoStore store = new NonceStore();
        LobehubHmacAuthenticator authenticator = authenticator(store);
        byte[] body = "{\"ticket\":\"opaque\"}".getBytes(StandardCharsets.UTF_8);
        String signature = LobehubHmacAuthenticator.sign(
                SECRET,
                "POST",
                "/api/internal/platform/lobehub-sso/tickets/redeem",
                body,
                Long.toString(NOW.getEpochSecond()),
                "nonce-value-123456");

        authenticator.authenticate(
                "POST",
                "/api/internal/platform/lobehub-sso/tickets/redeem",
                body,
                Long.toString(NOW.getEpochSecond()),
                "nonce-value-123456",
                signature);

        assertThatThrownBy(() -> authenticator.authenticate(
                "POST",
                "/api/internal/platform/lobehub-sso/tickets/redeem",
                body,
                Long.toString(NOW.getEpochSecond()),
                "nonce-value-123456",
                signature))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void rejectsTamperedBodyAndTimestampOutsideClockSkew() {
        LobehubSsoStore store = new NonceStore();
        LobehubHmacAuthenticator authenticator = authenticator(store);
        byte[] original = "{\"ticket\":\"opaque\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        String signature = LobehubHmacAuthenticator.sign(
                SECRET, "POST", "/redeem", original, timestamp, "nonce-value-123456");

        assertThatThrownBy(() -> authenticator.authenticate(
                "POST",
                "/redeem",
                "{\"ticket\":\"changed\"}".getBytes(StandardCharsets.UTF_8),
                timestamp,
                "nonce-value-123456",
                signature))
                .isInstanceOf(PlatformException.class);

        assertThatThrownBy(() -> authenticator.authenticate(
                "POST",
                "/redeem",
                original,
                Long.toString(NOW.minusSeconds(61).getEpochSecond()),
                "nonce-value-123456",
                signature))
                .isInstanceOf(PlatformException.class);
    }

    @Test
    void rejectsTimestampWhoseSubtractionWouldOverflow() {
        LobehubHmacAuthenticator authenticator = authenticator(new NonceStore());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(Long.MIN_VALUE + NOW.getEpochSecond());
        String nonce = "nonce-overflow-123456";
        String signature = LobehubHmacAuthenticator.sign(
                SECRET, "POST", "/redeem", body, timestamp, nonce);

        assertThatThrownBy(() -> authenticator.authenticate(
                "POST", "/redeem", body, timestamp, nonce, signature))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private static LobehubHmacAuthenticator authenticator(LobehubSsoStore store) {
        LobehubIntegrationProperties properties = new LobehubIntegrationProperties();
        properties.setHmacSecret(SECRET);
        return new LobehubHmacAuthenticator(
                store,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class NonceStore implements LobehubSsoStore {
        private String reservedNonce;

        @Override
        public void saveTicket(
                String ticketDigest,
                com.enterprise.testagent.domain.lobehub.LobehubTicketPayload payload,
                Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.enterprise.testagent.domain.lobehub.LobehubTicketPayload> consumeTicket(
                String ticketDigest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean reserveNonce(String nonceDigest, Duration ttl) {
            if (nonceDigest.equals(reservedNonce)) {
                return false;
            }
            reservedNonce = nonceDigest;
            return true;
        }

        @Override
        public void rotateGrant(
                String userId,
                String grantDigest,
                com.enterprise.testagent.domain.lobehub.LobehubGrantPayload payload,
                Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.enterprise.testagent.domain.lobehub.LobehubGrantPayload> findGrant(String grantDigest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeGrant(String grantDigest) {
            throw new UnsupportedOperationException();
        }
    }
}
