package com.enterprise.testagent.common.git;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.Base64;
import java.util.Map;

/**
 * SSH 私钥解密服务：混合 RSA + 临时 AES 方案。
 *
 * <p>加密由前端完成（AES-256-GCM 加密私钥 + RSA-OAEP 加密 AES 密钥），
 * 服务端只负责解密：
 * <ol>
 *   <li>RSA 私钥解密 {@code encryptedAesKey} 恢复临时 AES 密钥</li>
 *   <li>用 AES 密钥解密 {@code encryptedPrivateKey} 得到 SSH 私钥明文</li>
 * </ol>
 */
public class SshKeyEncryptionService {

    private final RsaKeyService rsaKeyService;

    public SshKeyEncryptionService(RsaKeyService rsaKeyService) {
        this.rsaKeyService = rsaKeyService;
    }

    /**
     * 混合解密：先用 RSA 私钥解出临时 AES 密钥，再用 AES-GCM 解出 SSH 私钥明文。
     *
     * @param encryptedPrivateKey AES-GCM 加密的 SSH 私钥（Base64）
     * @param encryptedAesKey     RSA-OAEP 加密的临时 AES 密钥（Base64）
     * @param encryptionNonce     AES-GCM nonce（Base64）
     * @return SSH 私钥明文
     */
    public String decrypt(String encryptedPrivateKey, String encryptedAesKey, String encryptionNonce) {
        byte[] aesKeyBytes = rsaKeyService.decrypt(Base64.getDecoder().decode(encryptedAesKey));
        String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKeyBytes);
        SshKeyCryptoService cryptoService = new SshKeyCryptoService(aesKeyBase64);
        return cryptoService.decrypt(encryptedPrivateKey, encryptionNonce);
    }

    /**
     * 对规范化后的私钥内容生成 SHA-256 指纹。
     */
    public String fingerprint(String normalizedPrivateKey) {
        SshKeyCryptoService cryptoService = new SshKeyCryptoService("placeholder");
        return cryptoService.fingerprint(normalizedPrivateKey);
    }

    /**
     * 解密并校验指纹，确保数据完整性。
     *
     * @throws PlatformException 如果指纹不匹配
     */
    public String decryptAndVerify(String encryptedPrivateKey, String encryptedAesKey,
                                    String encryptionNonce, String expectedFingerprint) {
        String plaintext = decrypt(encryptedPrivateKey, encryptedAesKey, encryptionNonce);
        if (!fingerprint(plaintext).equals(expectedFingerprint)) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR,
                    "SSH key fingerprint mismatch — data may be corrupted", Map.of());
        }
        return plaintext;
    }
}
