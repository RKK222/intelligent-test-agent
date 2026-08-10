package com.enterprise.testagent.integration.externalapi;

/** TAEK1 加密响应，只包含算法标识和无填充 Base64URL 二进制字段。 */
public record ExternalSshKeyEnvelope(
        String version,
        String keyDerivation,
        String cipher,
        String salt,
        String nonce,
        String ciphertext) {
}
