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
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
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
        UserRepository userRepository = mock(UserRepository.class);
        UserId userId = new UserId("usr_owner");
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user(
                userId, "UC-001", UserStatus.ACTIVE)));
        LocalClientCredentialApplicationService service = new LocalClientCredentialApplicationService(
                repository,
                userRepository,
                cipher,
                new ExternalApiKeyGenerator(),
                revoker,
                auditLogger,
                Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC));
        LocalClientCredentialResponses.CredentialView created = service.create(userId, "trace-create");
        LocalClientCredentialResponses.CredentialView existing = service.create(userId, "trace-create-existing");
        String oldKey = service.copy(userId, "trace-copy").clientKey();

        assertThatThrownBy(() -> service.copy(userId, "trace-copy-again"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("Client key 已显示，需轮换后才能再次查看");
                });
        verify(cipher, times(1)).decrypt(anyString());

        assertThat(existing).isEqualTo(created);
        assertThat(created.revealAvailable()).isTrue();
        assertThat(created.maskedKey()).startsWith("tack_v1_").contains("...");
        assertThat(created.toString()).doesNotContain(oldKey);
        assertThat(service.get(userId).revealAvailable()).isFalse();
        assertThat(service.create(userId, "trace-create-consumed").revealAvailable()).isFalse();
        assertThat(service.authenticate(oldKey, "UC-001", false).userId()).isEqualTo(userId.value());
        assertThat(service.authenticate(oldKey, null, true).userId()).isEqualTo(userId.value());

        assertThatThrownBy(() -> service.authenticate(oldKey, null, false))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(exception.getMessage()).isEqualTo("本地客户端认证失败");
                });
        assertThatThrownBy(() -> service.authenticate(oldKey, "   ", false))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(exception.getMessage()).doesNotContain("UC-001", oldKey);
                });

        assertThatThrownBy(() -> service.authenticate(oldKey, "UC-002", false))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(exception.getMessage()).doesNotContain(oldKey, "UC-001", "UC-002");
                });

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user(
                userId, "UC-001", UserStatus.INACTIVE)));
        assertThatThrownBy(() -> service.authenticate(oldKey, "UC-001", false))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user(
                userId, "UC-001", UserStatus.ACTIVE)));

        LocalClientCredentialResponses.CredentialView rotated = service.rotate(userId, "trace-rotate");
        String newKey = service.copy(userId, "trace-copy-new").clientKey();

        assertThatThrownBy(() -> service.copy(userId, "trace-copy-new-again"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(cipher, times(2)).decrypt(anyString());

        assertThat(rotated.version()).isEqualTo(2);
        assertThat(rotated.revealAvailable()).isTrue();
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(service.get(userId).revealAvailable()).isFalse();
        assertThatThrownBy(() -> service.authenticate(oldKey, "UC-001", false))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verify(revoker).revokeAll(userId, "CLIENT_KEY_ROTATED", "trace-rotate");

        service.revoke(userId, "trace-revoke");

        assertThatThrownBy(() -> service.copy(userId, "trace-copy-revoked"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.authenticate(newKey, "UC-001", false))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verify(revoker).revokeAll(userId, "CLIENT_KEY_REVOKED", "trace-revoke");
        verify(auditLogger).success(userId, "CREATE", "trace-create");
        verify(auditLogger, times(1)).success(userId, "CREATE", "trace-create-existing");
        verify(auditLogger).success(userId, "CREATE", "trace-create-consumed");
        verify(auditLogger).success(userId, "COPY", "trace-copy");
        verify(auditLogger).failure(userId, "COPY", "trace-copy-again", ErrorCode.CONFLICT.name());
        verify(auditLogger).success(userId, "ROTATE", "trace-rotate");
        verify(auditLogger).failure(userId, "COPY", "trace-copy-new-again", ErrorCode.CONFLICT.name());
        verify(auditLogger).success(userId, "REVOKE", "trace-revoke");
        verify(auditLogger).failure(userId, "COPY", "trace-copy-revoked", ErrorCode.NOT_FOUND.name());
    }

    private static User user(UserId userId, String unifiedAuthId, UserStatus status) {
        Instant now = Instant.parse("2026-08-11T12:00:00Z");
        return new User(
                userId,
                unifiedAuthId,
                "测试用户",
                "password-hash",
                null,
                null,
                null,
                status,
                now,
                now);
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
        public Optional<LocalClientCredential> findByUserIdForUpdate(UserId userId) {
            return findByUserId(userId);
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
