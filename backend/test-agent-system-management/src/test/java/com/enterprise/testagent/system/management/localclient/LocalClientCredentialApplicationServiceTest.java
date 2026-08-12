package com.enterprise.testagent.system.management.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRevoker;
import com.enterprise.testagent.domain.localclient.LocalClientCredential;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialCipher;
import com.enterprise.testagent.system.management.externalapi.ExternalApiKeyGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalClientCredentialApplicationServiceTest {

    @Test
    void shouldCreateCopyRotateAuthenticateAndRevokeWithoutExposingKeyInView() {
        InMemoryRepository repository = new InMemoryRepository();
        ExternalApiCredentialCipher cipher = mock(ExternalApiCredentialCipher.class);
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        when(cipher.decrypt(anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).substring("encrypted:".length()));
        LocalClientConnectionRevoker revoker = mock(LocalClientConnectionRevoker.class);
        LocalClientCredentialAuditLogger auditLogger = mock(LocalClientCredentialAuditLogger.class);
        LocalClientCredentialApplicationService service = new LocalClientCredentialApplicationService(
                repository,
                cipher,
                new ExternalApiKeyGenerator(),
                revoker,
                auditLogger,
                Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC));
        UserId userId = new UserId("usr_owner");

        LocalClientCredentialResponses.CredentialView created = service.create(userId, "trace-create");
        LocalClientCredentialResponses.CredentialView existing = service.create(userId, "trace-create-existing");
        String oldKey = service.copy(userId, "trace-copy").clientKey();

        assertThat(existing).isEqualTo(created);
        assertThat(created.maskedKey()).startsWith("tack_v1_").contains("...");
        assertThat(created.toString()).doesNotContain(oldKey);
        assertThat(service.authenticate(oldKey).userId()).isEqualTo(userId.value());

        LocalClientCredentialResponses.CredentialView rotated = service.rotate(userId, "trace-rotate");
        String newKey = service.copy(userId, "trace-copy-new").clientKey();

        assertThat(rotated.version()).isEqualTo(2);
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThatThrownBy(() -> service.authenticate(oldKey))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verify(revoker).revokeAll(userId, "CLIENT_KEY_ROTATED", "trace-rotate");

        service.revoke(userId, "trace-revoke");

        assertThatThrownBy(() -> service.copy(userId, "trace-copy-revoked"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.authenticate(newKey))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verify(revoker).revokeAll(userId, "CLIENT_KEY_REVOKED", "trace-revoke");
        verify(auditLogger).success(userId, "CREATE", "trace-create");
        verify(auditLogger, times(1)).success(userId, "CREATE", "trace-create-existing");
        verify(auditLogger).success(userId, "COPY", "trace-copy");
        verify(auditLogger).success(userId, "ROTATE", "trace-rotate");
        verify(auditLogger).success(userId, "REVOKE", "trace-revoke");
        verify(auditLogger).failure(userId, "COPY", "trace-copy-revoked", ErrorCode.NOT_FOUND.name());
    }

    private static final class InMemoryRepository implements LocalClientCredentialRepository {
        private LocalClientCredential credential;

        @Override
        public void lockUser(UserId userId) {
            // 单线程测试仓储无需锁；生产 MyBatis 实现会锁定 users 行。
        }

        @Override
        public Optional<LocalClientCredential> findByUserId(UserId userId) {
            return credential == null || !credential.userId().equals(userId)
                    ? Optional.empty()
                    : Optional.of(credential);
        }

        @Override
        public Optional<LocalClientCredential> findActiveByFingerprint(String fingerprint) {
            return credential != null
                    && credential.active()
                    && credential.clientKeyFingerprint().equals(fingerprint)
                    ? Optional.of(credential)
                    : Optional.empty();
        }

        @Override
        public void save(LocalClientCredential credential) {
            this.credential = credential;
        }
    }
}
