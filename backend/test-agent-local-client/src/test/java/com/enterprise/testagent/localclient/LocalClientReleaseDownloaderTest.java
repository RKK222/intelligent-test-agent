package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.localclient.LocalClientDownloadTrust;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientReleaseDownloaderTest {

    private static final String CURRENT_VERSION = "20260820120000";
    private static final String TARGET_VERSION = "20260820153045";

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<URI, byte[]> remote = new HashMap<>();
    private KeyPair keyPair;
    private LocalClientDownloadTrust trust;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        trust = new LocalClientDownloadTrust(
                URI.create("http://downloads.example/local-opencode-client/"), keyPair.getPublic());
        publishRelease(false);
    }

    @Test
    void shouldVerifyAndPrepareWholeReleaseBeforeCandidateSelfCheck() throws Exception {
        RecordingExtractor extractor = new RecordingExtractor();
        RecordingCandidateChecker candidateChecker = new RecordingCandidateChecker();
        MapFetcher fetcher = new MapFetcher(remote);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                temporaryDirectory.resolve("runtime"),
                objectMapper,
                fetcher,
                extractor,
                candidateChecker);

        LocalClientReleaseDownloader.PreparedRelease prepared = downloader.prepare(command(), CURRENT_VERSION);

        assertThat(prepared.version()).isEqualTo(TARGET_VERSION);
        assertThat(prepared.releaseDigest()).hasSize(64);
        assertThat(prepared.releaseDirectory()).isDirectory();
        assertThat(prepared.releaseDirectory().resolve("test-agent-local-client.jar")).hasContent("client-jar");
        assertThat(prepared.releaseDirectory().resolve("public-capabilities.tar.gz"))
                .hasContent("public-capabilities");
        assertThat(extractor.kinds).containsExactlyInAnyOrder("jdk.tar.gz", "opencode.tar.gz");
        assertThat(candidateChecker.checkedJava.toString()).endsWith("jdk/bin/java");
        assertThat(candidateChecker.checkedJar.toString()).endsWith("test-agent-local-client.jar");
        assertThat(candidateChecker.checkedReleaseDirectory.getFileName().toString())
                .isEqualTo(TARGET_VERSION);
        assertThat(fetcher.requestedFiles).contains(trust.resolve(
                "releases/" + TARGET_VERSION + "/jdk.tar.gz"));
        assertThat(fetcher.requestedBytes).contains(trust.resolve(
                "releases/" + TARGET_VERSION + "/jdk.tar.gz.sig"));
    }

    @Test
    void shouldDownloadOnlyChangedClientJarWhenRuntimeArtifactsExistInSharedCache() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-shared-cache");
        cacheArtifact(runtime, "JDK", "jdk.tar.gz");
        cacheArtifact(runtime, "OPENCODE", "opencode.tar.gz");
        cacheArtifact(runtime, "PUBLIC_CAPABILITIES", "public-capabilities.tar.gz");
        MapFetcher fetcher = new MapFetcher(remote);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                fetcher,
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        downloader.prepare(command(), CURRENT_VERSION);

        assertThat(fetcher.requestedFiles).containsExactly(trust.resolve(
                "releases/" + TARGET_VERSION + "/test-agent-local-client.jar"));
        assertThat(fetcher.requestedBytes).containsExactly(
                trust.resolve("releases/" + TARGET_VERSION + "/manifest.json"),
                trust.resolve("releases/" + TARGET_VERSION + "/manifest.json.sig"),
                trust.resolve("releases/" + TARGET_VERSION + "/test-agent-local-client.jar.sig"));
        assertThat(runtime.resolve("artifact-cache/CLIENT_JAR")
                        .resolve(LocalClientDownloadTrust.sha256(remote.get(trust.resolve(
                                "releases/" + TARGET_VERSION + "/test-agent-local-client.jar"))))
                        .resolve("artifact"))
                .hasContent("client-jar");
    }

    @Test
    void shouldSeedSharedCacheFromSignedPreviousReleaseOnFirstUpgrade() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-cache-migration");
        Path previousRelease = runtime.resolve("releases").resolve(CURRENT_VERSION);
        copySignedArtifact(previousRelease, "test-agent-local-client.jar");
        copySignedArtifact(previousRelease, "jdk.tar.gz");
        copySignedArtifact(previousRelease, "opencode.tar.gz");
        copySignedArtifact(previousRelease, "public-capabilities.tar.gz");
        MapFetcher fetcher = new MapFetcher(remote);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                fetcher,
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        downloader.prepare(command(), CURRENT_VERSION);

        assertThat(fetcher.requestedFiles).isEmpty();
        assertThat(fetcher.requestedBytes).containsExactly(
                trust.resolve("releases/" + TARGET_VERSION + "/manifest.json"),
                trust.resolve("releases/" + TARGET_VERSION + "/manifest.json.sig"));
        assertThat(runtime.resolve("artifact-cache/JDK")
                        .resolve(LocalClientDownloadTrust.sha256(remote.get(trust.resolve(
                                "releases/" + TARGET_VERSION + "/jdk.tar.gz"))))
                        .resolve("artifact"))
                .hasBinaryContent(remote.get(trust.resolve(
                        "releases/" + TARGET_VERSION + "/jdk.tar.gz")));
    }

    @Test
    void shouldDownloadOnlyArtifactWhoseSharedCacheVerificationFails() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-repair-cache");
        cacheArtifact(runtime, "CLIENT_JAR", "test-agent-local-client.jar");
        cacheArtifact(runtime, "JDK", "jdk.tar.gz");
        Path damagedEntry = cacheArtifact(runtime, "OPENCODE", "opencode.tar.gz");
        cacheArtifact(runtime, "PUBLIC_CAPABILITIES", "public-capabilities.tar.gz");
        Files.writeString(damagedEntry.resolve("artifact"), "tampered-cache", StandardCharsets.UTF_8);
        MapFetcher fetcher = new MapFetcher(remote);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                fetcher,
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        downloader.prepare(command(), CURRENT_VERSION);

        URI opencodeUri = trust.resolve("releases/" + TARGET_VERSION + "/opencode.tar.gz");
        assertThat(fetcher.requestedFiles).containsExactly(opencodeUri);
        assertThat(fetcher.requestedBytes).containsExactly(
                trust.resolve("releases/" + TARGET_VERSION + "/manifest.json"),
                trust.resolve("releases/" + TARGET_VERSION + "/manifest.json.sig"),
                trust.resolve("releases/" + TARGET_VERSION + "/opencode.tar.gz.sig"));
        assertThat(damagedEntry.resolve("artifact")).hasBinaryContent(remote.get(opencodeUri));
    }

    @Test
    void shouldRejectTamperedArtifactWithoutPublishingReleaseDirectory() throws Exception {
        URI jarUri = trust.resolve("releases/" + TARGET_VERSION + "/test-agent-local-client.jar");
        remote.put(jarUri, "tampered".getBytes(StandardCharsets.UTF_8));
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                temporaryDirectory.resolve("runtime-tampered"),
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        assertThatThrownBy(() -> downloader.prepare(command(), CURRENT_VERSION))
                .isInstanceOf(SecurityException.class);
        assertThat(temporaryDirectory.resolve("runtime-tampered/releases/" + TARGET_VERSION))
                .doesNotExist();
    }

    @Test
    void shouldRejectJdkWithIncorrectSizeWithoutPublishingReleaseDirectory() throws Exception {
        URI jdkUri = trust.resolve("releases/" + TARGET_VERSION + "/jdk.tar.gz");
        remote.put(jdkUri, "x".getBytes(StandardCharsets.UTF_8));
        assertRejectsJdkArtifact("runtime-jdk-size");
    }

    @Test
    void shouldRejectJdkWithSameSizeButIncorrectDigestWithoutPublishingReleaseDirectory() throws Exception {
        URI jdkUri = trust.resolve("releases/" + TARGET_VERSION + "/jdk.tar.gz");
        byte[] tampered = remote.get(jdkUri).clone();
        tampered[tampered.length - 1] ^= 1;
        remote.put(jdkUri, tampered);
        assertRejectsJdkArtifact("runtime-jdk-digest");
    }

    @Test
    void shouldRejectJdkWithIncorrectSignatureWithoutPublishingReleaseDirectory() throws Exception {
        URI signatureUri = trust.resolve("releases/" + TARGET_VERSION + "/jdk.tar.gz.sig");
        remote.put(signatureUri, "bad-signature".getBytes(StandardCharsets.UTF_8));
        assertRejectsJdkArtifact("runtime-jdk-signature");
    }

    @Test
    void shouldPublishSelfCheckingPhaseBeforeRunningCandidate() throws Exception {
        java.util.ArrayList<String> phases = new java.util.ArrayList<>();
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                temporaryDirectory.resolve("runtime-progress"),
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                (javaExecutable, clientJar, releaseDirectory, targetVersion) -> phases.add("CHECKED"));

        downloader.prepare(
                command(),
                CURRENT_VERSION,
                phase -> phases.add(phase.name()));

        assertThat(phases).containsExactly("SELF_CHECKING", "CHECKED");
    }

    @Test
    void shouldAcceptExistingBootstrapSystemJdkReleaseWithoutSignedJdkArchive() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-system-jdk");
        createExistingRelease(runtime, true, false);
        RecordingCandidateChecker candidateChecker = new RecordingCandidateChecker();
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                candidateChecker);

        LocalClientReleaseDownloader.PreparedRelease prepared = downloader.prepare(command(), CURRENT_VERSION);

        assertThat(prepared.releaseDirectory()).isEqualTo(runtime.resolve("releases").resolve(TARGET_VERSION));
        assertThat(candidateChecker.checkedJava.toString()).endsWith("jdk/bin/java");
    }

    @Test
    void shouldRejectExistingNonMarkerReleaseWithoutSignedJdkArchive() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-missing-jdk-archive");
        createExistingRelease(runtime, false, false);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        assertThatThrownBy(() -> downloader.prepare(command(), CURRENT_VERSION))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectTamperedJarInExistingBootstrapSystemJdkRelease() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-system-jdk-tampered-jar");
        createExistingRelease(runtime, true, true);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        assertThatThrownBy(() -> downloader.prepare(command(), CURRENT_VERSION))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectJdkArchiveWhenOrdinaryReleaseHasBootstrapMarker() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-ordinary-release-with-marker");
        Path release = createExistingRelease(runtime, true, false);
        Files.writeString(release.resolve("jdk.tar.gz"), "tampered-jdk-archive");
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        assertThatThrownBy(() -> downloader.prepare(command(), CURRENT_VERSION))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectExistingBootstrapSystemJdkReleaseWithJavac17() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime-system-jdk-javac17");
        Path release = createExistingRelease(runtime, true, false);
        Files.writeString(release.resolve("jdk/bin/java"), "#!/bin/sh\nexit 0\n");
        Files.writeString(release.resolve("jdk/bin/javac"), "#!/bin/sh\necho 'javac 17.0.13'\n");
        release.resolve("jdk/bin/java").toFile().setExecutable(true);
        release.resolve("jdk/bin/javac").toFile().setExecutable(true);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                new LocalClientCandidateChecker());

        assertThatThrownBy(() -> downloader.prepare(command(), CURRENT_VERSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JDK");
    }

    private LocalClientPayloads.UpdateCommand command() {
        return new LocalClientPayloads.UpdateCommand(
                "lcuc_test",
                "lci_test",
                7,
                11,
                TARGET_VERSION,
                "UPDATE");
    }

    private void assertRejectsJdkArtifact(String runtimeName) throws Exception {
        Path runtime = temporaryDirectory.resolve(runtimeName);
        LocalClientReleaseDownloader downloader = new LocalClientReleaseDownloader(
                trust,
                runtime,
                objectMapper,
                new MapFetcher(remote),
                new RecordingExtractor(),
                new RecordingCandidateChecker());

        assertThatThrownBy(() -> downloader.prepare(command(), CURRENT_VERSION))
                .isInstanceOf(SecurityException.class);
        assertThat(runtime.resolve("releases").resolve(TARGET_VERSION)).doesNotExist();
    }

    private Path createExistingRelease(Path runtime, boolean systemJdkMarker, boolean tamperedJar) throws Exception {
        Path release = runtime.resolve("releases").resolve(TARGET_VERSION);
        Files.createDirectories(release.resolve("jdk/bin"));
        Files.createDirectories(release.resolve("opencode/bin"));
        Files.write(release.resolve("manifest.json"), remote.get(trust.resolve(
                "releases/" + TARGET_VERSION + "/manifest.json")));
        Files.write(release.resolve("manifest.json.sig"), remote.get(trust.resolve(
                "releases/" + TARGET_VERSION + "/manifest.json.sig")));
        Files.write(release.resolve("test-agent-local-client.jar"), tamperedJar
                ? "tampered-client-jar".getBytes(StandardCharsets.UTF_8)
                : remote.get(trust.resolve("releases/" + TARGET_VERSION + "/test-agent-local-client.jar")));
        Files.write(release.resolve("opencode.tar.gz"), remote.get(trust.resolve(
                "releases/" + TARGET_VERSION + "/opencode.tar.gz")));
        Files.write(release.resolve("public-capabilities.tar.gz"), remote.get(trust.resolve(
                "releases/" + TARGET_VERSION + "/public-capabilities.tar.gz")));
        Files.writeString(release.resolve("jdk/bin/java"), "java");
        Files.writeString(release.resolve("jdk/bin/javac"), "javac");
        Files.writeString(release.resolve("opencode/bin/opencode"), "opencode");
        release.resolve("jdk/bin/java").toFile().setExecutable(true);
        release.resolve("jdk/bin/javac").toFile().setExecutable(true);
        release.resolve("opencode/bin/opencode").toFile().setExecutable(true);
        if (systemJdkMarker) {
            Files.writeString(release.resolve("jdk.provenance"), "source=system-jdk21\n");
        }
        return release;
    }

    private Path cacheArtifact(Path runtime, String kind, String fileName) throws Exception {
        URI artifactUri = trust.resolve("releases/" + TARGET_VERSION + "/" + fileName);
        byte[] artifact = remote.get(artifactUri);
        Path entry = runtime.resolve("artifact-cache")
                .resolve(kind)
                .resolve(LocalClientDownloadTrust.sha256(artifact));
        Files.createDirectories(entry);
        Files.write(entry.resolve("artifact"), artifact);
        Files.write(entry.resolve("artifact.sig"), remote.get(trust.resolve(
                "releases/" + TARGET_VERSION + "/" + fileName + ".sig")));
        return entry;
    }

    private void copySignedArtifact(Path release, String fileName) throws Exception {
        Files.createDirectories(release);
        Files.write(release.resolve(fileName), remote.get(trust.resolve(
                "releases/" + TARGET_VERSION + "/" + fileName)));
        Files.write(release.resolve(fileName + ".sig"), remote.get(trust.resolve(
                "releases/" + TARGET_VERSION + "/" + fileName + ".sig")));
    }

    private void publishRelease(boolean escapedPath) throws Exception {
        List<ArtifactFixture> artifacts = List.of(
                artifact("CLIENT_JAR", "test-agent-local-client.jar", "client-jar"),
                artifact("JDK", "jdk.tar.gz", "jdk-archive"),
                artifact("OPENCODE", "opencode.tar.gz", "opencode-archive"),
                artifact("PUBLIC_CAPABILITIES", "public-capabilities.tar.gz", "public-capabilities"));
        var manifest = objectMapper.createObjectNode();
        manifest.put("schemaVersion", 2);
        manifest.put("version", TARGET_VERSION);
        manifest.put("publishedAt", Instant.parse("2026-08-20T07:30:45Z").toString());
        manifest.put("platform", LocalClientPlatform.current().platform());
        manifest.put("architecture", LocalClientPlatform.current().architecture());
        manifest.put("launcherVersionMin", 1);
        manifest.put("launcherVersionMax", 1);
        manifest.put("protocolVersion", "local-opencode-client.v1");
        manifest.put("opencodeVersion", "2.0.18");
        var artifactArray = manifest.putArray("artifacts");
        for (ArtifactFixture artifact : artifacts) {
            var node = artifactArray.addObject();
            node.put("kind", artifact.kind());
            node.put("path", escapedPath
                    ? "http://evil.example/" + artifact.fileName()
                    : "releases/" + TARGET_VERSION + "/" + artifact.fileName());
            node.put("size", artifact.bytes().length);
            node.put("sha256", LocalClientDownloadTrust.sha256(artifact.bytes()));
            node.put("signaturePath", "releases/" + TARGET_VERSION + "/" + artifact.fileName() + ".sig");
            remote.put(trust.resolve("releases/" + TARGET_VERSION + "/" + artifact.fileName()), artifact.bytes());
            remote.put(
                    trust.resolve("releases/" + TARGET_VERSION + "/" + artifact.fileName() + ".sig"),
                    sign(artifact.bytes()));
        }
        byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
        remote.put(trust.resolve("releases/" + TARGET_VERSION + "/manifest.json"), manifestBytes);
        remote.put(trust.resolve("releases/" + TARGET_VERSION + "/manifest.json.sig"), sign(manifestBytes));
    }

    private ArtifactFixture artifact(String kind, String fileName, String value) {
        return new ArtifactFixture(kind, fileName, value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sign(byte[] content) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(content);
        return signer.sign();
    }

    private record ArtifactFixture(String kind, String fileName, byte[] bytes) {
    }

    private static final class MapFetcher implements LocalClientReleaseDownloader.Fetcher {
        private final Map<URI, byte[]> content;
        private final List<URI> requestedBytes = new ArrayList<>();
        private final List<URI> requestedFiles = new ArrayList<>();

        private MapFetcher(Map<URI, byte[]> content) {
            this.content = content;
        }

        @Override
        public byte[] fetchBytes(URI uri, int maxBytes) {
            requestedBytes.add(uri);
            byte[] bytes = content.get(uri);
            if (bytes == null || bytes.length > maxBytes) {
                throw new IllegalStateException("missing or oversized test content");
            }
            return bytes.clone();
        }

        @Override
        public void fetchFile(URI uri, Path target, long expectedSize) throws Exception {
            requestedFiles.add(uri);
            byte[] bytes = content.get(uri);
            if (bytes == null) {
                throw new IllegalStateException("missing test content");
            }
            Files.write(target, bytes);
        }
    }

    private static final class RecordingExtractor implements LocalClientReleaseDownloader.ArchiveExtractor {
        private final java.util.ArrayList<String> kinds = new java.util.ArrayList<>();

        @Override
        public void extract(Path archive, Path releaseDirectory) throws Exception {
            kinds.add(archive.getFileName().toString());
            if (archive.getFileName().toString().startsWith("jdk")) {
                Path java = releaseDirectory.resolve("jdk/bin/java");
                Path javac = releaseDirectory.resolve("jdk/bin/javac");
                Files.createDirectories(java.getParent());
                Files.writeString(java, "java");
                Files.writeString(javac, "javac");
                java.toFile().setExecutable(true);
                javac.toFile().setExecutable(true);
            } else {
                Path executable = releaseDirectory.resolve("opencode/bin/opencode");
                Files.createDirectories(executable.getParent());
                Files.writeString(executable, "opencode");
                executable.toFile().setExecutable(true);
            }
        }
    }

    private static final class RecordingCandidateChecker implements LocalClientReleaseDownloader.CandidateChecker {
        private Path checkedJava;
        private Path checkedJar;
        private Path checkedReleaseDirectory;

        @Override
        public void check(Path javaExecutable, Path clientJar, Path releaseDirectory, String targetVersion) {
            checkedJava = javaExecutable;
            checkedJar = clientJar;
            checkedReleaseDirectory = releaseDirectory;
        }
    }
}
