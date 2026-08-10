package com.enterprise.testagent.integration.externalapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 固定 salt/nonce 的 TAEK1 协议向量与篡改保护测试。 */
class ExternalSshKeyEnvelopeServiceTest {

    private static final String API_KEY = "taak_v1_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    @Test
    void productionSpringBeanUsesObjectMapperConstructor() {
        new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ExternalSshKeyEnvelopeService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExternalSshKeyEnvelopeService.class);
                });
    }

    @Test
    void createsDeterministicTaek1EnvelopeThatDecryptsToCompletePrivateKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ExternalSshKeyEnvelopeService service = new ExternalSshKeyEnvelopeService(
                mapper,
                new SequenceSecureRandom(),
                Clock.fixed(Instant.parse("2026-08-09T04:00:00Z"), ZoneOffset.UTC));

        ExternalSshKeyEnvelope envelope = service.encrypt(
                "deploy.bot", API_KEY, "u001", "ssh_001", "default", "SHA256:test",
                "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n",
                TRACE_ID);

        assertThat(envelope.version()).isEqualTo("TAEK1");
        assertThat(envelope.keyDerivation()).isEqualTo("HKDF-SHA256");
        assertThat(envelope.cipher()).isEqualTo("AES-256-GCM");
        assertThat(envelope.salt()).isEqualTo("AAECAwQFBgcICQoLDA0ODw");
        assertThat(envelope.nonce()).isEqualTo("EBESExQVFhcYGRob");
        assertThat(envelope.ciphertext()).isEqualTo(
                "dUdTPzuIoWHpIN7ApzlqY5i_Y4NHiJZql04gGa2Px3EETbr2l71ssX8fZmB-1xQ59_xiUgtwHgzfCTi8b33QuXr41X5rNYHIFWaIbacsTWZrdTSE-vqS2JrJlUnKwLTpKPYnJEkUiVLDRnvZUa5PZhJrhFD6Y5ctLYN1PrQ-zbPS79E4oxAWYN8ud4QBfeAYS1AaA6G0YO2SWRH7csx-arELS5Xgco44-92zCH87eX5weNWfOsFHY5q6s0C_jjFav7kScNVDvF9OcH0GX4dxPc6Fg9PQrNoCAzFx9TB5srLpWplANLVpem4WGOqksVi71Or2Kk6t99ySmQov2Gk");
        JsonNode plaintext = decrypt(mapper, envelope, "deploy.bot", "u001", TRACE_ID, API_KEY);
        assertThat(plaintext.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(plaintext.path("unifiedAuthId").asText()).isEqualTo("u001");
        assertThat(plaintext.path("sshKeyId").asText()).isEqualTo("ssh_001");
        assertThat(plaintext.path("privateKey").asText()).contains("OPENSSH PRIVATE KEY").contains("secret");
        assertThat(plaintext.path("issuedAt").asText()).isEqualTo("2026-08-09T04:00:00Z");
    }

    @Test
    void rejectsWrongKeyAadAndCiphertextTampering() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ExternalSshKeyEnvelopeService service = new ExternalSshKeyEnvelopeService(
                mapper, new SequenceSecureRandom(), Clock.systemUTC());
        ExternalSshKeyEnvelope envelope = service.encrypt(
                "deploy.bot", API_KEY, "u001", "ssh_001", "default", "SHA256:test", "private", TRACE_ID);

        assertThatThrownBy(() -> decrypt(mapper, envelope, "other.bot", "u001", TRACE_ID, API_KEY))
                .isInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> decrypt(mapper, envelope, "deploy.bot", "u001", TRACE_ID, API_KEY + "x"))
                .isInstanceOf(AEADBadTagException.class);
        byte[] changed = Base64.getUrlDecoder().decode(envelope.ciphertext());
        changed[0] ^= 1;
        ExternalSshKeyEnvelope tampered = new ExternalSshKeyEnvelope(
                envelope.version(), envelope.keyDerivation(), envelope.cipher(), envelope.salt(), envelope.nonce(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(changed));
        assertThatThrownBy(() -> decrypt(mapper, tampered, "deploy.bot", "u001", TRACE_ID, API_KEY))
                .isInstanceOf(AEADBadTagException.class);
    }

    private static JsonNode decrypt(
            ObjectMapper mapper,
            ExternalSshKeyEnvelope envelope,
            String toolCode,
            String unifiedAuthId,
            String traceId,
            String apiKey) throws Exception {
        byte[] salt = Base64.getUrlDecoder().decode(envelope.salt());
        byte[] key = hkdf(apiKey.getBytes(StandardCharsets.UTF_8), salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Base64.getUrlDecoder().decode(envelope.nonce())));
        cipher.updateAAD(("TAEK1\n" + toolCode + "\n" + unifiedAuthId + "\n" + traceId)
                .getBytes(StandardCharsets.UTF_8));
        return mapper.readTree(cipher.doFinal(Base64.getUrlDecoder().decode(envelope.ciphertext())));
    }

    private static byte[] hkdf(byte[] inputKey, byte[] salt) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(inputKey);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update("test-agent/external-api/ssh-key/v1".getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 1);
        return mac.doFinal();
    }

    /** 第一次返回 0..15 作为 salt，第二次返回 16..27 作为 nonce。 */
    private static final class SequenceSecureRandom extends SecureRandom {
        private int next;

        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) next++;
            }
        }
    }
}
