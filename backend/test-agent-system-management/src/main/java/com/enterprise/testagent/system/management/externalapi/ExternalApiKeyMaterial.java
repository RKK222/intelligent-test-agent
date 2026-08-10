package com.enterprise.testagent.system.management.externalapi;

/** 新生成 API Key 及其可持久化的不可逆摘要与掩码提示。 */
public record ExternalApiKeyMaterial(String apiKey, String fingerprint, String keyHint) {
}
