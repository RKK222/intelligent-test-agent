package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.git.SshKeyCryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 个人 SSH 私钥加解密服务；密钥只来自部署配置，不允许硬编码默认值。
 */
@Service
public class SshKeyEncryptionService {

    private final SshKeyCryptoService cryptoService;

    /**
     * 注入加密密钥；兼容平台配置项和环境变量名。
     */
    public SshKeyEncryptionService(
            @Value("${test-agent.security.ssh-key-encryption-key:${TEST_AGENT_SSH_KEY_ENCRYPTION_KEY:}}")
                    String encryptionKey) {
        this.cryptoService = new SshKeyCryptoService(encryptionKey);
    }

    /**
     * 对规范化后的私钥内容生成 SHA-256 指纹。
     */
    public String fingerprint(String normalizedPrivateKey) {
        return cryptoService.fingerprint(normalizedPrivateKey);
    }

    /**
     * 使用 AES-GCM 加密私钥，返回密文和 nonce，调用方负责落库。
     */
    public EncryptedPrivateKey encrypt(String normalizedPrivateKey) {
        SshKeyCryptoService.EncryptedPrivateKey encrypted = cryptoService.encrypt(normalizedPrivateKey);
        return new EncryptedPrivateKey(encrypted.encryptedPrivateKey(), encrypted.encryptionNonce());
    }

    /**
     * 解密私钥明文，仅供当前用户的 Git SSH 远端只读命令使用。
     */
    public String decrypt(String encryptedPrivateKey, String encryptionNonce) {
        return cryptoService.decrypt(encryptedPrivateKey, encryptionNonce);
    }

    public record EncryptedPrivateKey(String encryptedPrivateKey, String encryptionNonce) {
    }
}
