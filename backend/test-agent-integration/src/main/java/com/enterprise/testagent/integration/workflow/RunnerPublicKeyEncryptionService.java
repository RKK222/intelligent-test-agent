package com.enterprise.testagent.integration.workflow;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 使用Runner固定RSA公钥封装AES密钥，再以AES-256-GCM加密任意长度的SSH私钥。 */
@Component
public class RunnerPublicKeyEncryptionService {

    private static final String ENVELOPE_PREFIX = "TAEC1";
    private static final byte[] ENVELOPE_AAD = "test-agent-runner-credential-v1"
            .getBytes(StandardCharsets.US_ASCII);
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext, String publicKeyPem) {
        byte[] plaintextBytes = null;
        byte[] aesKey = null;
        try {
            PublicKey publicKey = parse(publicKeyPem);
            plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            aesKey = new byte[AES_KEY_BYTES];
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            secureRandom.nextBytes(aesKey);
            secureRandom.nextBytes(nonce);

            // RSA-OAEP只封装固定32字节AES密钥，避免真实OpenSSH私钥超过RSA明文上限。
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsaCipher.init(
                    Cipher.ENCRYPT_MODE,
                    publicKey,
                    new OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT));
            byte[] wrappedKey = rsaCipher.doFinal(aesKey);

            Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            aesCipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            aesCipher.updateAAD(ENVELOPE_AAD);
            byte[] ciphertext = aesCipher.doFinal(plaintextBytes);

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return String.join(
                    ".",
                    ENVELOPE_PREFIX,
                    encoder.encodeToString(wrappedKey),
                    encoder.encodeToString(nonce),
                    encoder.encodeToString(ciphertext));
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Runner SSH凭据封装失败");
        } finally {
            if (plaintextBytes != null) {
                Arrays.fill(plaintextBytes, (byte) 0);
            }
            if (aesKey != null) {
                Arrays.fill(aesKey, (byte) 0);
            }
        }
    }

    public boolean samePublicKey(String left, String right) {
        try {
            return MessageDigest.isEqual(parse(left).getEncoded(), parse(right).getEncoded());
        } catch (Exception exception) {
            return false;
        }
    }

    private PublicKey parse(String value) throws Exception {
        if (value == null) {
            throw new IllegalArgumentException("public key missing");
        }
        String normalized = value
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
