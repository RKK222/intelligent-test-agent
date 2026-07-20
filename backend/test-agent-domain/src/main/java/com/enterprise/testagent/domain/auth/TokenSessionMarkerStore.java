package com.enterprise.testagent.domain.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 平台 Token 的不可逆会话 marker 端口。第三方子会话只保存摘要，不接触平台原始 Token。
 */
public interface TokenSessionMarkerStore {

    /** 返回 Token 对应的 SHA-256 小写十六进制摘要。 */
    String digest(String token);

    /** 校验摘要 marker 是否仍存在；登出、刷新和 Token TTL 到期都会使其失效。 */
    boolean isActive(String sessionDigest);

    /** 共享稳定摘要算法，避免签发端和校验端出现编码差异。 */
    static String sha256(String token) {
        Objects.requireNonNull(token, "token must not be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
