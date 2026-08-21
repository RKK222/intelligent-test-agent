package com.enterprise.testagent.system.management.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.localclient.LocalClientDownloadTrust;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalClientReleaseCatalogClientTest {

    private final Map<String, byte[]> responses = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/local-client/", exchange -> {
            byte[] response = responses.get(exchange.getRequestURI().getPath());
            if (response == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldDiscoverThenVerifyAndPersistImmutableReleaseManifest() throws Exception {
        byte[] manifest = validManifest();
        byte[] manifestSignature = sign(manifest);
        String manifestSha = LocalClientDownloadTrust.sha256(manifest);
        put("releases/20260820120000/manifest.json", manifest);
        put("releases/20260820120000/manifest.json.sig", manifestSignature);
        put("releases/20260820120000/client.jar.sig", new byte[] {1});
        put("releases/20260820120000/jdk.tar.gz.sig", new byte[] {2});
        put("releases/20260820120000/opencode.tar.gz.sig", new byte[] {3});
        put("catalog.json", objectMapper.writeValueAsBytes(new LocalClientReleaseCatalogClient.Catalog(
                1,
                List.of(new LocalClientReleaseCatalogClient.CatalogEntry(
                        "20260820120000",
                        "releases/20260820120000/manifest.json",
                        manifestSha,
                        "releases/20260820120000/manifest.json.sig")))));
        LocalClientVersionRepository repository = mock(LocalClientVersionRepository.class);
        when(repository.findRelease(anyString())).thenReturn(Optional.empty());

        LocalClientReleaseCatalogClient.SyncResult result = client(repository).sync();

        ArgumentCaptor<LocalClientVersionModels.Release> release =
                ArgumentCaptor.forClass(LocalClientVersionModels.Release.class);
        verify(repository).insertRelease(release.capture());
        assertThat(result.synced()).isEqualTo(1);
        assertThat(release.getValue().compatible()).isTrue();
        assertThat(release.getValue().artifacts()).extracting(LocalClientVersionModels.Artifact::kind)
                .containsExactly("CLIENT_JAR", "JDK", "OPENCODE");
    }

    @Test
    void shouldRejectTamperedManifestSignatureBeforePersistence() throws Exception {
        byte[] manifest = validManifest();
        put("releases/20260820120000/manifest.json", manifest);
        put("releases/20260820120000/manifest.json.sig", sign("other".getBytes(StandardCharsets.UTF_8)));
        put("catalog.json", objectMapper.writeValueAsBytes(new LocalClientReleaseCatalogClient.Catalog(
                1,
                List.of(new LocalClientReleaseCatalogClient.CatalogEntry(
                        "20260820120000",
                        "releases/20260820120000/manifest.json",
                        LocalClientDownloadTrust.sha256(manifest),
                        "releases/20260820120000/manifest.json.sig")))));
        LocalClientVersionRepository repository = mock(LocalClientVersionRepository.class);

        assertThatThrownBy(() -> client(repository).sync())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectSignedManifestWithWrongOpenCodeVersion() throws Exception {
        byte[] manifest = manifest("1.18.5", "releases/20260820120000/");
        publishManifest(manifest);
        LocalClientVersionRepository repository = mock(LocalClientVersionRepository.class);

        assertThatThrownBy(() -> client(repository).sync())
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("OpenCode");
    }

    @Test
    void shouldRejectSignedManifestWhoseArtifactEscapesItsReleasePrefix() throws Exception {
        byte[] manifest = manifest("1.18.4", "releases/20260820110000/");
        publishManifest(manifest);
        LocalClientVersionRepository repository = mock(LocalClientVersionRepository.class);

        assertThatThrownBy(() -> client(repository).sync())
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("发布前缀");
    }

    private LocalClientReleaseCatalogClient client(LocalClientVersionRepository repository) {
        LocalClientReleaseCatalogProperties properties = new LocalClientReleaseCatalogProperties();
        properties.setDownloadBaseUrl(URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/local-client/"));
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        properties.setSigningPublicKeyBase64(Base64.getEncoder().encodeToString(
                pem.getBytes(StandardCharsets.US_ASCII)));
        return new LocalClientReleaseCatalogClient(
                repository,
                properties,
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-20T10:30:00Z"), ZoneOffset.UTC));
    }

    private byte[] validManifest() throws Exception {
        return manifest("1.18.4", "releases/20260820120000/");
    }

    private byte[] manifest(String opencodeVersion, String prefix) throws Exception {
        return objectMapper.writeValueAsBytes(new LocalClientReleaseCatalogClient.ReleaseManifest(
                2,
                "20260820120000",
                Instant.parse("2026-08-20T04:00:00Z"),
                "linux",
                "arm64",
                1,
                1,
                "local-opencode-client.v1",
                opencodeVersion,
                List.of(
                        artifact("CLIENT_JAR", prefix + "client.jar", prefix + "client.jar.sig"),
                        artifact("JDK", prefix + "jdk.tar.gz", prefix + "jdk.tar.gz.sig"),
                        artifact("OPENCODE", prefix + "opencode.tar.gz", prefix + "opencode.tar.gz.sig"))));
    }

    private void publishManifest(byte[] manifest) throws Exception {
        put("releases/20260820120000/manifest.json", manifest);
        put("releases/20260820120000/manifest.json.sig", sign(manifest));
        put("releases/20260820120000/client.jar.sig", new byte[] {1});
        put("releases/20260820120000/jdk.tar.gz.sig", new byte[] {2});
        put("releases/20260820120000/opencode.tar.gz.sig", new byte[] {3});
        put("releases/20260820110000/client.jar.sig", new byte[] {1});
        put("releases/20260820110000/jdk.tar.gz.sig", new byte[] {2});
        put("releases/20260820110000/opencode.tar.gz.sig", new byte[] {3});
        put("catalog.json", objectMapper.writeValueAsBytes(new LocalClientReleaseCatalogClient.Catalog(
                1,
                List.of(new LocalClientReleaseCatalogClient.CatalogEntry(
                        "20260820120000",
                        "releases/20260820120000/manifest.json",
                        LocalClientDownloadTrust.sha256(manifest),
                        "releases/20260820120000/manifest.json.sig")))));
    }

    private static LocalClientReleaseCatalogClient.ManifestArtifact artifact(
            String kind,
            String path,
            String signaturePath) {
        return new LocalClientReleaseCatalogClient.ManifestArtifact(
                kind, path, 1024, "a".repeat(64), signaturePath);
    }

    private byte[] sign(byte[] content) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(content);
        return signature.sign();
    }

    private void put(String path, byte[] content) {
        responses.put("/local-client/" + path, content);
    }
}
