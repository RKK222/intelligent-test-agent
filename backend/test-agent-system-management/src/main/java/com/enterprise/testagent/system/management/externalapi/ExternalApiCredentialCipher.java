package com.enterprise.testagent.system.management.externalapi;

import com.enterprise.testagent.common.git.RsaKeyService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 用交付 JAR 中统一 RSA 密钥对保护数据库中的 API Key。 */
@Service
public class ExternalApiCredentialCipher {

    private final RsaKeyService rsaKeyService;

    public ExternalApiCredentialCipher(RsaKeyService rsaKeyService) {
        this.rsaKeyService = Objects.requireNonNull(rsaKeyService, "rsaKeyService must not be null");
    }

    public String encrypt(String apiKey) {
        byte[] encrypted = rsaKeyService.encrypt(apiKey.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String encryptedApiKey) {
        byte[] encrypted = Base64.getDecoder().decode(encryptedApiKey);
        return new String(rsaKeyService.decrypt(encrypted), StandardCharsets.UTF_8);
    }
}
