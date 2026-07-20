package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;

/**
 * RSA 密钥服务测试，覆盖浏览器 Web Crypto 与 Java JCE 的 OAEP 参数兼容性。
 */
class RsaKeyServiceTest {

    private static final OAEPParameterSpec WEB_CRYPTO_RSA_OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    @Test
    void decryptsWebCryptoRsaOaepSha256Payload() throws Exception {
        RsaKeyService service = new RsaKeyService();
        byte[] aesKeyBytes = new byte[32];
        for (int i = 0; i < aesKeyBytes.length; i++) {
            aesKeyBytes[i] = (byte) (i + 1);
        }

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, service.getPublicKey(), WEB_CRYPTO_RSA_OAEP_SHA256);
        byte[] encrypted = cipher.doFinal(aesKeyBytes);

        assertThat(service.decrypt(encrypted)).isEqualTo(aesKeyBytes);
    }

}
