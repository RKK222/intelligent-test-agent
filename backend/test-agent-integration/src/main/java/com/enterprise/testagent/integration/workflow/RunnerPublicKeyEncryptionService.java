package com.enterprise.testagent.integration.workflow;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.stereotype.Component;

/** 使用Runner固定RSA公钥和OAEP-SHA256重新封装SSH私钥。 */
@Component
public class RunnerPublicKeyEncryptionService {

    public String encrypt(String plaintext, String publicKeyPem) {
        try {
            PublicKey publicKey = parse(publicKeyPem);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    publicKey,
                    new OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT));
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Runner SSH凭据封装失败");
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
