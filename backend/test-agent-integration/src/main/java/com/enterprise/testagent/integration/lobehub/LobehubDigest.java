package com.enterprise.testagent.integration.lobehub;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 为票据、nonce、委托和外部部门键生成稳定 SHA-256 摘要。 */
public final class LobehubDigest {

    private LobehubDigest() {
    }

    /** 返回 UTF-8 文本的小写十六进制 SHA-256。 */
    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    /** 返回原始字节的小写十六进制 SHA-256。 */
    public static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }
}
