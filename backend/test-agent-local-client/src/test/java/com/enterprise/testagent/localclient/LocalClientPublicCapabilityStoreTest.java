package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证公共能力包不可变安装、原子切换、失败回滚和摘要拒绝。 */
class LocalClientPublicCapabilityStoreTest {

    private static final String COMMIT_ONE = "1".repeat(40);
    private static final String COMMIT_TWO = "2".repeat(40);

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void clearPackagedBundleProperty() {
        System.clearProperty(LocalClientPublicCapabilityStore.PACKAGED_BUNDLE_PROPERTY);
    }

    @Test
    void initializesPackagedMacBaselineWithoutManagedInstallRoot() throws Exception {
        PackageFixture fixture = packageFixture("agents/public.md", "baseline", COMMIT_ONE, false);
        Path archive = write("packaged-public-capabilities.tar.gz", fixture.archive());
        System.setProperty(LocalClientPublicCapabilityStore.PACKAGED_BUNDLE_PROPERTY, archive.toString());
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);

        store.initializeBaseline(developmentConfiguration(), LocalClientBuildInfo.resolve(null));

        assertThat(store.snapshot().activeCommit()).isEqualTo(COMMIT_ONE);
        assertThat(store.snapshot().activeDigest()).isEqualTo(fixture.digest());
        assertThat(store.snapshot().status()).isEqualTo("APPLYING");
        assertThat(store.activeConfigDirectory(temporaryDirectory.resolve("legacy"))
                .resolve("agents/public.md")).hasContent("baseline");
    }

    @Test
    void installsCommitBoundIdentityPackageWhileRetainingLegacyBaselineCompatibility() throws Exception {
        PackageFixture fixture = packageFixtureV2("skills/public/SKILL.md", "current commit", COMMIT_ONE, false);
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);

        LocalClientPublicCapabilityStore.Candidate candidate = store.installArchive(
                write("commit-bound.tar.gz", fixture.archive()), COMMIT_ONE, fixture.digest());

        assertThat(store.activate(candidate)).isNull();
        assertThat(store.snapshot().activeCommit()).isEqualTo(COMMIT_ONE);
        assertThat(store.snapshot().activeDigest()).isEqualTo(fixture.digest());
        assertThat(store.activeConfigDirectory(temporaryDirectory.resolve("legacy"))
                .resolve("skills/public/SKILL.md")).hasContent("current commit");
    }

    @Test
    void createsAnIsolatedPersonalCopyAndSupportsRestoreAndClear() throws Exception {
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        PackageFixture fixture = packageFixture("agents/public.md", "signed baseline", COMMIT_ONE, false);
        LocalClientPublicCapabilityStore.Candidate candidate = store.installArchive(
                write("personal-copy.tar.gz", fixture.archive()), COMMIT_ONE, fixture.digest());
        store.activate(candidate);
        store.completeActivation();

        Path personal = store.preparePersonalConfig();
        Files.writeString(personal.resolve("agents/public.md"), "personal draft");

        assertThat(store.hasPersonalChanges()).isTrue();
        assertThat(store.activeConfigDirectory()).isEqualTo(personal);
        assertThat(store.activeConfigDirectory().resolve("agents/public.md")).hasContent("personal draft");
        assertThat(temporaryDirectory.resolve("public-capabilities/current/agents/public.md"))
                .hasContent("signed baseline");

        store.restorePersonalPath("agents/public.md");
        assertThat(store.activeConfigDirectory().resolve("agents/public.md")).hasContent("signed baseline");
        assertThatThrownBy(() -> store.restorePersonalPath("../outside.md"))
                .isInstanceOf(SecurityException.class);

        store.clearPersonalConfig();
        assertThat(store.hasPersonalChanges()).isFalse();
        assertThat(store.activeConfigDirectory().resolve("agents/public.md")).hasContent("signed baseline");
    }

    @Test
    void backsUpAndRestoresPersonalCopyWithItsOriginalBaselineMarker() throws Exception {
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        PackageFixture fixture = packageFixture("agents/public.md", "signed baseline", COMMIT_ONE, false);
        LocalClientPublicCapabilityStore.Candidate candidate = store.installArchive(
                write("personal-backup.tar.gz", fixture.archive()), COMMIT_ONE, fixture.digest());
        store.activate(candidate);
        store.completeActivation();

        Path personal = store.preparePersonalConfig();
        Files.writeString(personal.resolve("agents/public.md"), "personal draft");
        LocalClientPublicCapabilityStore.PersonalBackup backup =
                store.backupPersonalConfig("lcpcd_" + "a".repeat(32));
        store.clearPersonalConfig();

        assertThat(store.hasPersonalChanges()).isFalse();
        store.restorePersonalBackup(backup);
        assertThat(store.hasPersonalChanges()).isTrue();
        assertThat(store.activeConfigDirectory().resolve("agents/public.md")).hasContent("personal draft");
        store.deletePersonalBackup(backup);
        assertThat(backup.directory()).doesNotExist();
    }

    @Test
    void installsActivatesAndRollsBackImmutableRevisions() throws Exception {
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        PackageFixture first = packageFixture("agents/public.md", "first", COMMIT_ONE, false);
        PackageFixture second = packageFixture("tools/public.ts", "second", COMMIT_TWO, true);

        Path firstArchive = write("first.tar.gz", first.archive());
        LocalClientPublicCapabilityStore.Candidate firstCandidate =
                store.installArchive(firstArchive, COMMIT_ONE, first.digest());
        assertThat(store.activate(firstCandidate)).isNull();
        assertThat(store.snapshot().status()).isEqualTo("APPLYING");
        store.completeActivation();
        assertThat(store.activeConfigDirectory(temporaryDirectory.resolve("legacy")))
                .isDirectory()
                .satisfies(path -> assertThat(path.resolve("agents/public.md")).hasContent("first"));

        Path secondArchive = write("second.tar.gz", second.archive());
        LocalClientPublicCapabilityStore.Candidate secondCandidate =
                store.installArchive(secondArchive, COMMIT_TWO, second.digest());
        assertThat(store.activate(secondCandidate)).isEqualTo(first.digest());
        assertThat(store.snapshot().activeDigest()).isEqualTo(second.digest());
        assertThat(store.activeConfigDirectory(temporaryDirectory.resolve("legacy")).resolve("tools/public.ts"))
                .hasContent("second");

        store.rollback(first.digest(), "TEST_ROLLBACK");

        assertThat(store.snapshot().status()).isEqualTo("ROLLED_BACK");
        assertThat(store.snapshot().activeDigest()).isEqualTo(first.digest());
        assertThat(store.activeConfigDirectory(temporaryDirectory.resolve("legacy")).resolve("agents/public.md"))
                .hasContent("first");
    }

    @Test
    void rejectsManifestOrCommandDigestMismatchWithoutChangingCurrent() throws Exception {
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        PackageFixture fixture = packageFixture("skills/public/SKILL.md", "safe", COMMIT_ONE, false);
        Path archive = write("capability.tar.gz", fixture.archive());

        assertThatThrownBy(() -> store.installArchive(archive, COMMIT_ONE, "f".repeat(64)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("摘要");
        assertThat(store.snapshot().activeDigest()).isNull();
        assertThat(temporaryDirectory.resolve("public-capabilities/current")).doesNotExist();
    }

    @Test
    void rejectsArchiveFileNotDeclaredByManifest() throws Exception {
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        PackageFixture fixture = packageFixture("agents/public.md", "safe", COMMIT_ONE, false, true);

        assertThatThrownBy(() -> store.installArchive(
                write("undeclared.tar.gz", fixture.archive()), COMMIT_ONE, fixture.digest()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("manifest");
    }

    @Test
    void quarantinesPollutedRevisionAndReinstallsSignedArchive() throws Exception {
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        PackageFixture fixture = packageFixture("agents/public.md", "safe", COMMIT_ONE, false);
        Path archive = write("repair.tar.gz", fixture.archive());
        LocalClientPublicCapabilityStore.Candidate installed =
                store.installArchive(archive, COMMIT_ONE, fixture.digest());
        Files.writeString(installed.configDirectory().resolve("package.json"), "polluted");

        LocalClientPublicCapabilityStore.Candidate repaired =
                store.installArchive(archive, COMMIT_ONE, fixture.digest());

        assertThat(repaired.configDirectory().resolve("package.json")).doesNotExist();
        try (var entries = Files.list(temporaryDirectory.resolve("public-capabilities/quarantine"))) {
            assertThat(entries.toList()).singleElement().satisfies(quarantined ->
                    assertThat(quarantined.resolve("public-capabilities/package.json")).hasContent("polluted"));
        }
    }

    private Path write(String name, byte[] content) throws Exception {
        Path path = temporaryDirectory.resolve(name);
        Files.write(path, content);
        return path;
    }

    private LocalClientConfiguration developmentConfiguration() {
        return new LocalClientConfiguration(
                java.net.URI.create("http://127.0.0.1:8080"),
                java.net.URI.create("http://127.0.0.1:3000"),
                "mac-test-client",
                temporaryDirectory.resolve("opencode"),
                temporaryDirectory.resolve("legacy-config"),
                temporaryDirectory.resolve("data"),
                4096,
                4195,
                true);
    }

    private static PackageFixture packageFixture(
            String relativePath,
            String content,
            String sourceCommit,
            boolean requiresRestart) throws Exception {
        return packageFixture(relativePath, content, sourceCommit, requiresRestart, false);
    }

    private static PackageFixture packageFixture(
            String relativePath,
            String content,
            String sourceCommit,
            boolean requiresRestart,
            boolean includeUndeclaredFile) throws Exception {
        return packageFixture(
                relativePath, content, sourceCommit, requiresRestart, includeUndeclaredFile, false);
    }

    private static PackageFixture packageFixtureV2(
            String relativePath,
            String content,
            String sourceCommit,
            boolean requiresRestart) throws Exception {
        return packageFixture(relativePath, content, sourceCommit, requiresRestart, false, true);
    }

    private static PackageFixture packageFixture(
            String relativePath,
            String content,
            String sourceCommit,
            boolean requiresRestart,
            boolean includeUndeclaredFile,
            boolean commitBoundIdentity) throws Exception {
        byte[] file = content.getBytes(StandardCharsets.UTF_8);
        String fileSha = sha256(file);
        String contentDigest = sha256((relativePath + "\0" + fileSha + "\n").getBytes(StandardCharsets.UTF_8));
        String digest = commitBoundIdentity
                ? sha256((sourceCommit + "\n" + contentDigest).getBytes(StandardCharsets.US_ASCII))
                : contentDigest;
        String category = relativePath.substring(0, relativePath.indexOf('/'));
        int agents = category.equals("agents") ? 1 : 0;
        int skills = category.equals("skills") ? 1 : 0;
        int tools = category.equals("tools") ? 1 : 0;
        String manifest = commitBoundIdentity ? """
                {"schemaVersion":1,"sourceCommit":"%s","bundleDigest":"%s","contentDigest":"%s","requiresRestart":%s,
                 "counts":{"agents":%d,"skills":%d,"tools":%d},
                 "files":[{"path":"%s","sha256":"%s","size":%d}]}
                """.formatted(sourceCommit, digest, contentDigest, requiresRestart, agents, skills, tools,
                relativePath, fileSha, file.length) : """
                {"schemaVersion":1,"sourceCommit":"%s","bundleDigest":"%s","requiresRestart":%s,
                 "counts":{"agents":%d,"skills":%d,"tools":%d},
                 "files":[{"path":"%s","sha256":"%s","size":%d}]}
                """.formatted(sourceCommit, digest, requiresRestart, agents, skills, tools,
                relativePath, fileSha, file.length);
        TarEntry[] entries = includeUndeclaredFile
                ? new TarEntry[] {
                    entry("public-capabilities/", new byte[0], '5', 0755),
                    entry("public-capabilities/" + category + "/", new byte[0], '5', 0755),
                    entry("public-capabilities/" + relativePath, file, '0', 0644),
                    entry("public-capabilities/agents/undeclared.md", "extra".getBytes(StandardCharsets.UTF_8), '0', 0644),
                    entry("public-capabilities/manifest.json", manifest.getBytes(StandardCharsets.UTF_8), '0', 0644)
                }
                : new TarEntry[] {
                    entry("public-capabilities/", new byte[0], '5', 0755),
                    entry("public-capabilities/" + category + "/", new byte[0], '5', 0755),
                    entry("public-capabilities/" + relativePath, file, '0', 0644),
                    entry("public-capabilities/manifest.json", manifest.getBytes(StandardCharsets.UTF_8), '0', 0644)
                };
        byte[] archive = tarGz(entries);
        return new PackageFixture(digest, archive);
    }

    private static byte[] tarGz(TarEntry... entries) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            for (TarEntry entry : entries) {
                byte[] header = new byte[512];
                writeAscii(header, 0, 100, entry.name());
                writeOctal(header, 100, 8, entry.mode());
                writeOctal(header, 108, 8, 0);
                writeOctal(header, 116, 8, 0);
                writeOctal(header, 124, 12, entry.content().length);
                writeOctal(header, 136, 12, 0);
                java.util.Arrays.fill(header, 148, 156, (byte) ' ');
                header[156] = (byte) entry.type();
                writeAscii(header, 257, 6, "ustar");
                long checksum = 0;
                for (byte value : header) {
                    checksum += Byte.toUnsignedInt(value);
                }
                writeOctal(header, 148, 8, checksum);
                gzip.write(header);
                gzip.write(entry.content());
                gzip.write(new byte[(512 - entry.content().length % 512) % 512]);
            }
            gzip.write(new byte[1024]);
        }
        return compressed.toByteArray();
    }

    private static void writeAscii(byte[] target, int offset, int length, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, Math.min(length, encoded.length));
    }

    private static void writeOctal(byte[] target, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        writeAscii(target, offset, length - 1, "0".repeat(Math.max(0, length - octal.length() - 1)) + octal);
        target[offset + length - 1] = 0;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static TarEntry entry(String name, byte[] content, char type, int mode) {
        return new TarEntry(name, content, type, mode);
    }

    private record PackageFixture(String digest, byte[] archive) {
    }

    private record TarEntry(String name, byte[] content, char type, int mode) {
    }
}
