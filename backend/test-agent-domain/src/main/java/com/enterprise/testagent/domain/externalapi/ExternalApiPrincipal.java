package com.enterprise.testagent.domain.externalapi;

import java.util.Objects;
import java.util.Set;

/** 外部认证成功后的调用主体；API Key 仅用于当前请求的响应加密。 */
public record ExternalApiPrincipal(
        ExternalApiCredentialId credentialId,
        String toolCode,
        Set<ExternalApiScope> scopes,
        String apiKey) {

    public ExternalApiPrincipal {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        toolCode = ExternalApiCredential.requireToolCode(toolCode);
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes must not be null"));
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
    }

    public boolean hasScope(ExternalApiScope scope) {
        return scopes.contains(scope);
    }
}
