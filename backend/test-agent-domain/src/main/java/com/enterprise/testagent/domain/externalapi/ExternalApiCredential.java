package com.enterprise.testagent.domain.externalapi;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 外部工具凭据聚合；只保存 API Key 的 RSA 密文和不可逆摘要。 */
public record ExternalApiCredential(
        ExternalApiCredentialId credentialId,
        String toolCode,
        String toolName,
        String encryptedApiKey,
        String apiKeyFingerprint,
        String keyHint,
        boolean enabled,
        Set<ExternalApiScope> scopes,
        Instant createdAt,
        Instant updatedAt) {

    public static final Pattern TOOL_CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public ExternalApiCredential {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        toolCode = requireToolCode(toolCode);
        toolName = requireToolName(toolName);
        encryptedApiKey = requireText(encryptedApiKey, "encryptedApiKey");
        apiKeyFingerprint = requireText(apiKeyFingerprint, "apiKeyFingerprint");
        if (!apiKeyFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("apiKeyFingerprint must be a SHA-256 hex digest");
        }
        keyHint = requireText(keyHint, "keyHint");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes must not be null"));
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("scopes must not be empty");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static String requireToolCode(String value) {
        String normalized = requireText(value, "toolCode");
        if (!TOOL_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("toolCode format is invalid");
        }
        return normalized;
    }

    public static String requireToolName(String value) {
        String normalized = requireText(value, "toolName");
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("toolName must not exceed 128 characters");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
