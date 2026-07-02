package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * user_ssh_keys 表行模型，仅在 persistence MyBatis 映射内部使用。
 */
public record UserSshKeyRow(
        String sshKeyId,
        String userId,
        String name,
        String fingerprint,
        String encryptedPrivateKey,
        String encryptedAesKey,
        String encryptionNonce,
        Instant createdAt) {
}
