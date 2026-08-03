package com.enterprise.testagent.integration.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RunnerPublicKeyEncryptionServiceTest {

    private static final byte[] ENVELOPE_AAD = "test-agent-runner-credential-v1"
            .getBytes(StandardCharsets.US_ASCII);

    @Test
    void shouldEncryptOpenSshKeyLargerThanRsaPlaintextLimit() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair keyPair = generator.generateKeyPair();
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        String privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\n"
                + "long-private-key-material".repeat(256)
                + "\n-----END OPENSSH PRIVATE KEY-----\n";

        RunnerPublicKeyEncryptionService service = new RunnerPublicKeyEncryptionService();
        String envelope = service.encrypt(privateKey, publicKeyPem);

        assertThat(envelope).startsWith("TAEC1.");
        assertThat(decrypt(envelope, keyPair)).isEqualTo(privateKey);
        assertThat(service.samePublicKey(publicKeyPem, publicKeyPem)).isTrue();
    }

    private String decrypt(String envelope, KeyPair keyPair) throws Exception {
        String[] parts = envelope.split("\\.", -1);
        assertThat(parts).hasSize(4);
        Base64.Decoder decoder = Base64.getUrlDecoder();

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsaCipher.init(
                Cipher.DECRYPT_MODE,
                keyPair.getPrivate(),
                new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT));
        byte[] aesKey = rsaCipher.doFinal(decoder.decode(parts[1]));

        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        aesCipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, decoder.decode(parts[2])));
        aesCipher.updateAAD(ENVELOPE_AAD);
        return new String(aesCipher.doFinal(decoder.decode(parts[3])), StandardCharsets.UTF_8);
    }
}
