package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SshKeyEncryptionServiceTest {

    private static final String PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n";

    @Test
    void encryptsAndDecryptsPrivateKeyWithConfiguredAesGcmKey() {
        SshKeyEncryptionService service = new SshKeyEncryptionService(base64AesKey());

        SshKeyEncryptionService.EncryptedPrivateKey encrypted = service.encrypt(PRIVATE_KEY);

        assertThat(encrypted.encryptedPrivateKey()).doesNotContain("secret");
        assertThat(service.decrypt(encrypted.encryptedPrivateKey(), encrypted.encryptionNonce())).isEqualTo(PRIVATE_KEY);
        assertThat(service.fingerprint(PRIVATE_KEY)).startsWith("SHA256:");
    }

    @Test
    void missingEncryptionKeyFailsWithoutDefaultSecret() {
        SshKeyEncryptionService service = new SshKeyEncryptionService("");

        assertThatThrownBy(() -> service.encrypt(PRIVATE_KEY))
                .isInstanceOf(PlatformException.class)
                .satisfies(error -> assertThat(((PlatformException) error).errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    static String base64AesKey() {
        return Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
