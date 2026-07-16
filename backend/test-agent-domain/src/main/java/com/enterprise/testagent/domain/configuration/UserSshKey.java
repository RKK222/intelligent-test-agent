package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户个人 SSH 私钥配置。private key 明文不进入领域对象，只保存密文、nonce 和指纹。
 */
public record UserSshKey(
        SshKeyId sshKeyId,
        UserId userId,
        String name,
        String fingerprint,
        String encryptedPrivateKey,
        String encryptedAesKey,
        String encryptionNonce,
        Instant createdAt) {

    public UserSshKey {
        Objects.requireNonNull(sshKeyId, "sshKeyId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey must not be null");
        Objects.requireNonNull(encryptionNonce, "encryptionNonce must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (name.isBlank() || fingerprint.isBlank() || encryptedPrivateKey.isBlank() || encryptionNonce.isBlank()) {
            throw new IllegalArgumentException("ssh key fields must not be blank");
        }
        // encryptedAesKey may be null for pre-migration keys; callers must handle
    }
}
