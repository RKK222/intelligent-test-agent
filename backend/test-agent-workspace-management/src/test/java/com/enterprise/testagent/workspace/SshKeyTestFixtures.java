package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.git.RsaKeyService;
import com.enterprise.testagent.common.git.SshKeyCryptoService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.configuration.SshKeyId;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.user.UserId;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;

/**
 * SSH key 混合加密测试夹具：模拟前端用服务端 RSA 公钥加密临时 AES 密钥、
 * 用 AES 加密私钥的完整流程，构造可被 {@link SshKeyEncryptionService} 解密的 {@link UserSshKey}。
 */
final class SshKeyTestFixtures {

    private final RsaKeyService rsaKeyService;
    private final SshKeyEncryptionService sshKeyEncryptionService;

    SshKeyTestFixtures() {
        this.rsaKeyService = new RsaKeyService();
        this.sshKeyEncryptionService = new SshKeyEncryptionService(rsaKeyService);
    }

    SshKeyEncryptionService encryptionService() {
        return sshKeyEncryptionService;
    }

    /** 模拟前端混合加密，构造可解密的 SSH key 夹具。 */
    UserSshKey encryptedSshKey(SshKeyId sshKeyId, UserId userId, String name, String plaintext, Instant createdAt) {
        try {
            byte[] aesKeyBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(aesKeyBytes);
            String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKeyBytes);

            SshKeyCryptoService aes = new SshKeyCryptoService(aesKeyBase64);
            SshKeyCryptoService.EncryptedPrivateKey encrypted = aes.encrypt(plaintext);

            byte[] encryptedAesKeyBytes = rsaEncrypt(aesKeyBytes);
            String encryptedAesKey = Base64.getEncoder().encodeToString(encryptedAesKeyBytes);

            return new UserSshKey(
                    sshKeyId,
                    userId,
                    name,
                    aes.fingerprint(plaintext),
                    encrypted.encryptedPrivateKey(),
                    encryptedAesKey,
                    encrypted.encryptionNonce(),
                    createdAt);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] rsaEncrypt(byte[] data) throws Exception {
        PublicKey publicKey = rsaKeyService.getPublicKey();
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    /** 仅用于测试：从 SPKI Base64 重建公钥（保留以备扩展）。 */
    @SuppressWarnings("unused")
    private static PublicKey toPublicKey(String spkiBase64) throws Exception {
        byte[] encoded = Base64.getDecoder().decode(spkiBase64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
