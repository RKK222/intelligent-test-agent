package com.enterprise.testagent.system.management.externalapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 生成固定版本、256 位随机强度的外部 API Key。 */
@Component
public class ExternalApiKeyGenerator {

    public static final String PREFIX = "taak_v1_";
    private final SecureRandom secureRandom;

    public ExternalApiKeyGenerator() {
        this(new SecureRandom());
    }

    ExternalApiKeyGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public ExternalApiKeyMaterial generate() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String apiKey = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return new ExternalApiKeyMaterial(apiKey, fingerprint(apiKey), keyHint(apiKey));
    }

    public static String fingerprint(String apiKey) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not support SHA-256", exception);
        }
    }

    public static String keyHint(String apiKey) {
        return apiKey.substring(0, Math.min(12, apiKey.length())) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
