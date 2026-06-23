package com.icbc.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SshKeyCryptoServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

    @Test
    void encryptsAndDecryptsPrivateKeyWithConfiguredAesKey() {
        SshKeyCryptoService service = new SshKeyCryptoService(KEY);

        SshKeyCryptoService.EncryptedPrivateKey encrypted = service.encrypt("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n");

        assertThat(encrypted.encryptedPrivateKey()).isNotBlank();
        assertThat(encrypted.encryptionNonce()).isNotBlank();
        assertThat(service.decrypt(encrypted.encryptedPrivateKey(), encrypted.encryptionNonce()))
                .isEqualTo("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n");
    }

    @Test
    void reportsInternalErrorWhenEncryptionKeyMissing() {
        SshKeyCryptoService service = new SshKeyCryptoService("");

        assertThatThrownBy(() -> service.encrypt("key"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }
}
