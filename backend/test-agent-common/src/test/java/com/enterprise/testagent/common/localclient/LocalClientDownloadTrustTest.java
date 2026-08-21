package com.enterprise.testagent.common.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientDownloadTrustTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldResolveOnlyCanonicalPathsBelowTrustedRootAndVerifySignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        LocalClientDownloadTrust trust = new LocalClientDownloadTrust(
                URI.create("http://downloads.example/local-client/"),
                LocalClientDownloadTrust.parsePublicKeyPem(pem));
        byte[] content = "signed manifest".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(content);
        byte[] signature = signer.sign();

        assertThat(trust.resolve("releases/20260820120000/manifest.json"))
                .isEqualTo(URI.create(
                        "http://downloads.example/local-client/releases/20260820120000/manifest.json"));
        trust.verifySignature(content, signature);
        assertThat(LocalClientDownloadTrust.sha256(content)).hasSize(64);

        assertThatThrownBy(() -> trust.resolve("../outside"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trust.resolve("releases/%2e%2e/outside"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trust.resolve("http://evil.example/release"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trust.verifySignature("tampered".getBytes(StandardCharsets.UTF_8), signature))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldVerifyLargeArtifactFromFileWithoutLoadingItIntoCallerMemory() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        LocalClientDownloadTrust trust = new LocalClientDownloadTrust(
                URI.create("http://downloads.example/local-client/"), keyPair.getPublic());
        Path artifact = temporaryDirectory.resolve("artifact.bin");
        byte[] content = "signed artifact".repeat(8192).getBytes(StandardCharsets.UTF_8);
        Files.write(artifact, content);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(content);
        byte[] signature = signer.sign();

        trust.verifySignature(artifact, signature);
        assertThat(LocalClientDownloadTrust.sha256(artifact))
                .isEqualTo(LocalClientDownloadTrust.sha256(content));
        LocalClientDownloadTrust.requireSha256(artifact, LocalClientDownloadTrust.sha256(content));
    }
}
