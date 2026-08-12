package com.enterprise.testagent.opencode.runtime.localclient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 生成连接级短期 grant 并计算不可逆摘要。 */
final class LocalClientSecretSupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    private LocalClientSecretSupport() {
    }

    static String generateGrant() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return "lcg_v1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not support SHA-256", exception);
        }
    }
}
