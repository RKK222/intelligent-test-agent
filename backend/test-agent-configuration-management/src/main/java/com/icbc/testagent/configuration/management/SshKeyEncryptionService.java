package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 个人 SSH 私钥加解密服务；密钥只来自部署配置，不允许硬编码默认值。
 */
@Service
public class SshKeyEncryptionService {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;

    private final String encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 注入加密密钥；兼容平台配置项和环境变量名。
     */
    public SshKeyEncryptionService(
            @Value("${test-agent.security.ssh-key-encryption-key:${TEST_AGENT_SSH_KEY_ENCRYPTION_KEY:}}")
                    String encryptionKey) {
        this.encryptionKey = encryptionKey == null ? "" : encryptionKey.trim();
    }

    /**
     * 对规范化后的私钥内容生成 SHA-256 指纹。
     */
    public String fingerprint(String normalizedPrivateKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedPrivateKey.getBytes(StandardCharsets.UTF_8));
            return "SHA256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key 指纹生成失败", Map.of(), exception);
        }
    }

    /**
     * 使用 AES-GCM 加密私钥，返回密文和 nonce，调用方负责落库。
     */
    public EncryptedPrivateKey encrypt(String normalizedPrivateKey) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(normalizedPrivateKey.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPrivateKey(
                    Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key 加密失败", Map.of(), exception);
        }
    }

    /**
     * 解密私钥明文，仅供当前用户的 Git SSH 远端只读命令使用。
     */
    public String decrypt(String encryptedPrivateKey, String encryptionNonce) {
        try {
            byte[] nonce = Base64.getDecoder().decode(encryptionNonce);
            byte[] encrypted = Base64.getDecoder().decode(encryptedPrivateKey);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key 解密失败", Map.of(), exception);
        }
    }

    private SecretKeySpec aesKey() {
        if (encryptionKey.isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key 加密密钥未配置");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encryptionKey);
            if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
                throw new IllegalArgumentException("AES key length must be 16, 24 or 32 bytes");
            }
            return new SecretKeySpec(raw, "AES");
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "SSH key 加密密钥格式无效",
                    Map.of("expected", "base64 AES key"),
                    exception);
        }
    }

    public record EncryptedPrivateKey(String encryptedPrivateKey, String encryptionNonce) {
    }
}
