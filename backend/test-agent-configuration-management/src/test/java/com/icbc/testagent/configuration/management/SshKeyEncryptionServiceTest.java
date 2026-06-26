package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.SshKeyEncryptionService;
import org.junit.jupiter.api.Test;

/**
 * 混合解密服务测试：通过 {@link SshKeyTestFixtures} 模拟前端加密，
 * 验证服务端 RSA+AES 解密和指纹校验流程。
 */
class SshKeyEncryptionServiceTest {

    private static final String PRIVATE_KEY =
            "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n";

    private final SshKeyTestFixtures fixtures = new SshKeyTestFixtures();

    @Test
    void decryptsHybridEncryptedPrivateKey() {
        SshKeyTestFixtures.EncryptedPayload payload = fixtures.encryptPayload(PRIVATE_KEY);
        SshKeyEncryptionService service = fixtures.encryptionService();

        String plaintext = service.decrypt(
                payload.encryptedPrivateKey(),
                payload.encryptedAesKey(),
                payload.encryptionNonce());

        assertThat(plaintext).isEqualTo(PRIVATE_KEY);
    }

    @Test
    void decryptAndVerifyAcceptsMatchingFingerprint() {
        SshKeyTestFixtures.EncryptedPayload payload = fixtures.encryptPayload(PRIVATE_KEY);
        SshKeyEncryptionService service = fixtures.encryptionService();

        String plaintext = service.decryptAndVerify(
                payload.encryptedPrivateKey(),
                payload.encryptedAesKey(),
                payload.encryptionNonce(),
                payload.fingerprint());

        assertThat(plaintext).isEqualTo(PRIVATE_KEY);
    }

    @Test
    void decryptAndVerifyRejectsTamperedFingerprint() {
        SshKeyTestFixtures.EncryptedPayload payload = fixtures.encryptPayload(PRIVATE_KEY);
        SshKeyEncryptionService service = fixtures.encryptionService();

        assertThatThrownBy(() -> service.decryptAndVerify(
                payload.encryptedPrivateKey(),
                payload.encryptedAesKey(),
                payload.encryptionNonce(),
                "SHA256:tampered"))
                .isInstanceOfSatisfying(PlatformException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void fingerprintMatchesBackendFormat() {
        String fingerprint = fixtures.encryptionService().fingerprint(PRIVATE_KEY);

        assertThat(fingerprint).startsWith("SHA256:");
        // URL-safe Base64 不带 padding，不应出现 + / =
        String body = fingerprint.substring("SHA256:".length());
        assertThat(body).doesNotContain("+").doesNotContain("/").doesNotContain("=");
    }
}
