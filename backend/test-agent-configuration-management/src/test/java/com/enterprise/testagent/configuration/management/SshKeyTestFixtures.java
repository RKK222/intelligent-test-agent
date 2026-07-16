package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.common.git.RsaKeyService;
import com.enterprise.testagent.common.git.SshKeyCryptoService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.configuration.SshKeyId;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.user.UserId;
import java.security.PublicKey;
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

    /**
     * 模拟前端混合加密，返回可直接传给 {@code addSshKey} 的密文 payload。
     * 返回顺序：encryptedPrivateKey, encryptedAesKey, encryptionNonce, fingerprint。
     */
    EncryptedPayload encryptPayload(String plaintext) {
        try {
            byte[] aesKeyBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(aesKeyBytes);
            String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKeyBytes);

            SshKeyCryptoService aes = new SshKeyCryptoService(aesKeyBase64);
            SshKeyCryptoService.EncryptedPrivateKey encrypted = aes.encrypt(plaintext);

            byte[] encryptedAesKeyBytes = rsaEncrypt(aesKeyBytes);
            return new EncryptedPayload(
                    encrypted.encryptedPrivateKey(),
                    Base64.getEncoder().encodeToString(encryptedAesKeyBytes),
                    encrypted.encryptionNonce(),
                    aes.fingerprint(plaintext));
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

    record EncryptedPayload(String encryptedPrivateKey, String encryptedAesKey,
                             String encryptionNonce, String fingerprint) {
    }
}
