package com.enterprise.testagent.system.management.externalapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 验证外部 API Key 的固定格式、随机载荷长度和安全摘要。 */
class ExternalApiKeyGeneratorTest {

    @Test
    void shouldGenerateLocalClientKeyWithDedicatedPrefix() {
        ExternalApiKeyMaterial material = new ExternalApiKeyGenerator().generate("tack_v1_");

        assertThat(material.apiKey()).startsWith("tack_v1_");
        assertThat(material.fingerprint()).hasSize(64);
        assertThat(material.keyHint()).startsWith("tack_v1_");
    }

    @Test
    void generatesVersionedThirtyTwoByteUrlSafeKey() {
        ExternalApiKeyGenerator generator = new ExternalApiKeyGenerator(new IncrementingSecureRandom());

        ExternalApiKeyMaterial material = generator.generate();

        assertThat(material.apiKey()).startsWith("taak_v1_").doesNotContain("=");
        assertThat(Base64.getUrlDecoder().decode(material.apiKey().substring("taak_v1_".length())))
                .hasSize(32);
        assertThat(material.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(material.keyHint()).startsWith("taak_v1_").contains("...");
    }

    private static final class IncrementingSecureRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (index + 1);
            }
        }
    }
}
