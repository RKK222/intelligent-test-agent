package com.enterprise.testagent.integration.lobehub;

import java.security.SecureRandom;
import java.util.Base64;

/** 基于 SecureRandom 生成 32 字节的一次性凭据。 */
final class SecureLobehubTokenFactory implements LobehubTokenFactory {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
