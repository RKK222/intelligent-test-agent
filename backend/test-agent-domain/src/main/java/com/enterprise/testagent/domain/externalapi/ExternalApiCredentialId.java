package com.enterprise.testagent.domain.externalapi;

/** 外部 API 凭据稳定标识。 */
public record ExternalApiCredentialId(String value) {
    public ExternalApiCredentialId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("credentialId must not be blank");
        }
    }
}
