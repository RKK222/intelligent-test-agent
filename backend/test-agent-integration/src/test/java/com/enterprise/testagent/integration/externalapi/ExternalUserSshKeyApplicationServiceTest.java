package com.enterprise.testagent.integration.externalapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.SshKeyId;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialId;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 验证外部 SSH Key 查询的用户状态、scope 与旧密文保护。 */
class ExternalUserSshKeyApplicationServiceTest {

    @Test
    void activeUserWithCurrentKeyReturnsTaek1Envelope() {
        Fixture fixture = fixture(activeUser());
        UserSshKey key = sshKey("encrypted-aes");
        when(fixture.configuration.findSshKeys(new UserId("user_001"))).thenReturn(List.of(key));
        when(fixture.sshEncryption.decryptAndVerify("encrypted-private", "encrypted-aes", "nonce", "SHA256:test"))
                .thenReturn("private-key");
        ExternalSshKeyEnvelope expected = new ExternalSshKeyEnvelope(
                "TAEK1", "HKDF-SHA256", "AES-256-GCM", "salt", "nonce", "ciphertext");
        when(fixture.envelopes.encrypt(
                "deploy.bot", API_KEY, "u001", "ssh_001", "default", "SHA256:test", "private-key", "trace"))
                .thenReturn(expected);

        assertThat(fixture.service.get("u001", principal(), "trace")).isEqualTo(expected);
        verify(fixture.sshEncryption).decryptAndVerify(
                "encrypted-private", "encrypted-aes", "nonce", "SHA256:test");
    }

    @Test
    void missingInactiveOrUnconfiguredUserUsesSameNotFoundContract() {
        Fixture missing = fixture(null);
        Fixture inactive = fixture(user(UserStatus.INACTIVE));
        Fixture noKey = fixture(activeUser());
        when(noKey.configuration.findSshKeys(new UserId("user_001"))).thenReturn(List.of());

        assertError(() -> missing.service.get("u001", principal(), "trace"), ErrorCode.NOT_FOUND);
        assertError(() -> inactive.service.get("u001", principal(), "trace"), ErrorCode.NOT_FOUND);
        assertError(() -> noKey.service.get("u001", principal(), "trace"), ErrorCode.NOT_FOUND);
    }

    @Test
    void rejectsMissingScopeAndLegacySshEncryption() {
        Fixture fixture = fixture(activeUser());
        when(fixture.configuration.findSshKeys(new UserId("user_001"))).thenReturn(List.of(sshKey(null)));
        ExternalApiPrincipal noScope = new ExternalApiPrincipal(
                new ExternalApiCredentialId("eac_one"), "deploy.bot", Set.of(), API_KEY);

        assertError(() -> fixture.service.get("u001", noScope, "trace"), ErrorCode.FORBIDDEN);
        assertError(() -> fixture.service.get("u001", principal(), "trace"), ErrorCode.CONFLICT);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(PlatformException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }

    private static Fixture fixture(User user) {
        UserRepository users = mock(UserRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        SshKeyEncryptionService sshEncryption = mock(SshKeyEncryptionService.class);
        ExternalSshKeyEnvelopeService envelopes = mock(ExternalSshKeyEnvelopeService.class);
        when(users.findByUnifiedAuthId("u001")).thenReturn(java.util.Optional.ofNullable(user));
        return new Fixture(
                new ExternalUserSshKeyApplicationService(users, configuration, sshEncryption, envelopes),
                configuration, sshEncryption, envelopes);
    }

    private static User activeUser() {
        return user(UserStatus.ACTIVE);
    }

    private static User user(UserStatus status) {
        return new User(new UserId("user_001"), "u001", "测试用户", "hash", null, null, null,
                status, Instant.EPOCH, Instant.EPOCH);
    }

    private static UserSshKey sshKey(String encryptedAesKey) {
        return new UserSshKey(
                new SshKeyId("ssh_001"), new UserId("user_001"), "default", "SHA256:test",
                "encrypted-private", encryptedAesKey, "nonce", Instant.EPOCH);
    }

    private static ExternalApiPrincipal principal() {
        return new ExternalApiPrincipal(
                new ExternalApiCredentialId("eac_one"), "deploy.bot",
                Set.of(ExternalApiScope.USER_SSH_KEY_READ), API_KEY);
    }

    private static final String API_KEY = "taak_v1_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";

    private record Fixture(
            ExternalUserSshKeyApplicationService service,
            ConfigurationManagementRepository configuration,
            SshKeyEncryptionService sshEncryption,
            ExternalSshKeyEnvelopeService envelopes) {
    }
}
