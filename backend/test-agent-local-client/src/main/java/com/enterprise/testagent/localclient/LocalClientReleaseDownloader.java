package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientDownloadTrust;
import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 下载、验签、解包并自检完整 JDK/JAR/OpenCode 发布单元；成功前不触碰 current。 */
final class LocalClientReleaseDownloader {

    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_SIGNATURE_BYTES = 16 * 1024;
    private static final long MAX_ARTIFACT_BYTES = 4L * 1024 * 1024 * 1024;
    private static final Set<String> REQUIRED_KINDS = Set.of("CLIENT_JAR", "JDK", "OPENCODE");
    private static final String PUBLIC_CAPABILITIES_KIND = "PUBLIC_CAPABILITIES";
    private static final String SYSTEM_JDK_PROVENANCE = "source=system-jdk21\n";
    private static final String ARTIFACT_CACHE_DIRECTORY = "artifact-cache";
    private static final String CACHED_ARTIFACT_FILE = "artifact";
    private static final String CACHED_SIGNATURE_FILE = "artifact.sig";

    private final LocalClientDownloadTrust trust;
    private final Path installRoot;
    private final ObjectMapper objectMapper;
    private final Fetcher fetcher;
    private final ArchiveExtractor extractor;
    private final CandidateChecker candidateChecker;
    private final LocalClientPlatform platform;

    LocalClientReleaseDownloader(
            LocalClientDownloadTrust trust,
            Path installRoot,
            ObjectMapper objectMapper,
            Fetcher fetcher,
            ArchiveExtractor extractor,
            CandidateChecker candidateChecker) {
        this(trust, installRoot, objectMapper, fetcher, extractor, candidateChecker, LocalClientPlatform.current());
    }

    LocalClientReleaseDownloader(
            LocalClientDownloadTrust trust,
            Path installRoot,
            ObjectMapper objectMapper,
            Fetcher fetcher,
            ArchiveExtractor extractor,
            CandidateChecker candidateChecker,
            LocalClientPlatform platform) {
        this.trust = Objects.requireNonNull(trust);
        this.installRoot = Objects.requireNonNull(installRoot).toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.fetcher = Objects.requireNonNull(fetcher);
        this.extractor = Objects.requireNonNull(extractor);
        this.candidateChecker = Objects.requireNonNull(candidateChecker);
        this.platform = Objects.requireNonNull(platform);
    }

    PreparedRelease prepare(LocalClientPayloads.UpdateCommand command, String currentVersion) throws Exception {
        return prepare(command, currentVersion, phase -> {
            // 兼容不需要展示下载阶段的调用方。
        });
    }

    PreparedRelease prepare(
            LocalClientPayloads.UpdateCommand command,
            String currentVersion,
            PreparationListener listener) throws Exception {
        Objects.requireNonNull(listener, "listener must not be null");
        validateCommand(command, currentVersion);
        String version = LocalClientReleaseVersion.parse(command.targetVersion()).value();
        String releasePrefix = "releases/" + version + "/";
        byte[] manifestBytes = fetcher.fetchBytes(
                trust.resolve(releasePrefix + "manifest.json"), MAX_MANIFEST_BYTES);
        byte[] manifestSignature = fetcher.fetchBytes(
                trust.resolve(releasePrefix + "manifest.json.sig"), MAX_SIGNATURE_BYTES);
        trust.verifySignature(manifestBytes, manifestSignature);
        String releaseDigest = LocalClientDownloadTrust.sha256(manifestBytes);
        ReleaseManifest manifest = parseManifest(manifestBytes);
        Map<String, ManifestArtifact> artifacts = validateManifest(manifest, version, releasePrefix);

        Path releasesDirectory = installRoot.resolve("releases");
        Path finalDirectory = releasesDirectory.resolve(version).normalize();
        if (!finalDirectory.getParent().equals(releasesDirectory)) {
            throw new SecurityException("release directory escapes install root");
        }
        Files.createDirectories(releasesDirectory);
        Path existingManifest = finalDirectory.resolve("manifest.json");
        if (Files.isRegularFile(existingManifest)) {
            LocalClientDownloadTrust.requireSha256(existingManifest, releaseDigest);
            verifyPreparedLayout(finalDirectory, artifacts);
            listener.onPhase(PreparationPhase.SELF_CHECKING);
            candidateChecker.check(
                    platform.javaExecutable(finalDirectory),
                    finalDirectory.resolve("test-agent-local-client.jar"),
                    finalDirectory,
                    version);
            return new PreparedRelease(version, releaseDigest, finalDirectory);
        }

        Path stagingRoot = releasesDirectory.resolve(
                ".prepare-" + UUID.randomUUID().toString().replace("-", ""));
        Path staging = stagingRoot.resolve(version);
        createPrivateDirectory(stagingRoot);
        createPrivateDirectory(staging);
        try {
            writePrivate(staging.resolve("manifest.json"), manifestBytes);
            writePrivate(staging.resolve("manifest.json.sig"), manifestSignature);
            for (String kind : artifacts.keySet()) {
                ManifestArtifact artifact = artifacts.get(kind);
                obtainArtifact(releasesDirectory, staging, kind, artifact);
            }
            extractor.extract(staging.resolve("jdk.tar.gz"), staging);
            extractor.extract(staging.resolve("opencode.tar.gz"), staging);
            verifyPreparedLayout(staging, artifacts);
            listener.onPhase(PreparationPhase.SELF_CHECKING);
            candidateChecker.check(
                    platform.javaExecutable(staging),
                    staging.resolve("test-agent-local-client.jar"),
                    staging,
                    version);
            moveAtomically(staging, finalDirectory);
            return new PreparedRelease(version, releaseDigest, finalDirectory);
        } finally {
            // 候选进程必须看到 basename=版本号；无论成功与否都只清理随机暂存父目录。
            deleteTree(stagingRoot);
        }
    }

    /**
     * 所有更新入口共用 kind/SHA-256 内容寻址缓存；旧 release 只用于首次升级时迁移填充缓存。
     */
    private void obtainArtifact(
            Path releasesDirectory,
            Path staging,
            String kind,
            ManifestArtifact artifact) throws Exception {
        Path target = staging.resolve(localFileName(kind));
        if (reuseCachedArtifact(kind, artifact, target)) {
            return;
        }
        if (reuseExistingReleaseArtifact(releasesDirectory, kind, artifact, target)) {
            cacheVerifiedArtifact(kind, artifact, target, target.resolveSibling(target.getFileName() + ".sig"));
            return;
        }

        fetcher.fetchFile(trust.resolve(artifact.path()), target, artifact.size());
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target)
                || Files.size(target) != artifact.size()) {
            throw new SecurityException("release artifact size verification failed");
        }
        LocalClientDownloadTrust.requireSha256(target, artifact.sha256());
        byte[] signature = fetcher.fetchBytes(
                trust.resolve(artifact.signaturePath()), MAX_SIGNATURE_BYTES);
        trust.verifySignature(target, signature);
        Path signatureTarget = target.resolveSibling(target.getFileName() + ".sig");
        writePrivate(signatureTarget, signature);
        cacheVerifiedArtifact(kind, artifact, target, signatureTarget);
    }

    private boolean reuseCachedArtifact(
            String kind,
            ManifestArtifact artifact,
            Path target) throws IOException {
        Path cacheRoot = installRoot.resolve(ARTIFACT_CACHE_DIRECTORY);
        if (!safeExistingCacheDirectory(cacheRoot)) {
            return false;
        }
        if (!safeExistingCacheDirectory(cacheRoot.resolve(kind))) {
            return false;
        }
        Path entry = cacheEntry(kind, artifact.sha256());
        byte[] signature = verifiedSignature(entry, artifact);
        if (signature == null) {
            return false;
        }
        copyPrivate(entry.resolve(CACHED_ARTIFACT_FILE), target);
        writePrivate(target.resolveSibling(target.getFileName() + ".sig"), signature);
        return true;
    }

    private boolean reuseExistingReleaseArtifact(
            Path releasesDirectory,
            String kind,
            ManifestArtifact artifact,
            Path target) throws IOException {
        try (var releases = Files.list(releasesDirectory)) {
            List<Path> candidates = releases
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(java.util.Comparator.reverseOrder())
                    .toList();
            for (Path candidate : candidates) {
                byte[] signature = verifiedSignature(
                        candidate,
                        localFileName(kind),
                        localFileName(kind) + ".sig",
                        artifact);
                if (signature == null) {
                    continue;
                }
                copyPrivate(candidate.resolve(localFileName(kind)), target);
                writePrivate(target.resolveSibling(target.getFileName() + ".sig"), signature);
                return true;
            }
        }
        return false;
    }

    private byte[] verifiedSignature(Path cacheEntry, ManifestArtifact artifact) {
        return verifiedSignature(
                cacheEntry, CACHED_ARTIFACT_FILE, CACHED_SIGNATURE_FILE, artifact);
    }

    private byte[] verifiedSignature(
            Path directory,
            String artifactFileName,
            String signatureFileName,
            ManifestArtifact artifact) {
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                return null;
            }
            Path artifactFile = directory.resolve(artifactFileName);
            Path signatureFile = directory.resolve(signatureFileName);
            if (!Files.isRegularFile(artifactFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(artifactFile)
                    || !Files.isRegularFile(signatureFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(signatureFile)
                    || Files.size(artifactFile) != artifact.size()
                    || Files.size(signatureFile) < 1
                    || Files.size(signatureFile) > MAX_SIGNATURE_BYTES) {
                return null;
            }
            LocalClientDownloadTrust.requireSha256(artifactFile, artifact.sha256());
            byte[] signature = Files.readAllBytes(signatureFile);
            trust.verifySignature(artifactFile, signature);
            return signature;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private void cacheVerifiedArtifact(
            String kind,
            ManifestArtifact artifact,
            Path sourceArtifact,
            Path sourceSignature) throws IOException {
        Path cacheRoot = requirePrivateDirectory(installRoot.resolve(ARTIFACT_CACHE_DIRECTORY));
        Path kindDirectory = requirePrivateDirectory(cacheRoot.resolve(kind));
        Path finalEntry = cacheEntry(kind, artifact.sha256());
        if (verifiedSignature(finalEntry, artifact) != null) {
            return;
        }

        Path staging = cacheRoot.resolve(
                ".prepare-" + UUID.randomUUID().toString().replace("-", ""));
        createPrivateDirectory(staging);
        try {
            copyPrivate(sourceArtifact, staging.resolve(CACHED_ARTIFACT_FILE));
            copyPrivate(sourceSignature, staging.resolve(CACHED_SIGNATURE_FILE));
            if (verifiedSignature(staging, artifact) == null) {
                throw new SecurityException("verified artifact cache staging changed");
            }
            if (verifiedSignature(finalEntry, artifact) != null) {
                return;
            }
            if (Files.exists(finalEntry, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(finalEntry);
            }
            moveAtomically(staging, kindDirectory.resolve(artifact.sha256()));
        } finally {
            deleteTree(staging);
        }
    }

    private Path cacheEntry(String kind, String sha256) {
        Path cacheRoot = installRoot.resolve(ARTIFACT_CACHE_DIRECTORY);
        Path entry = cacheRoot.resolve(kind).resolve(sha256).normalize();
        if (!entry.getParent().equals(cacheRoot.resolve(kind))) {
            throw new SecurityException("artifact cache path escapes install root");
        }
        return entry;
    }

    private static Path requirePrivateDirectory(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                throw new SecurityException("artifact cache directory is unsafe");
            }
            setPrivateDirectoryPermissions(directory);
            return directory;
        }
        createPrivateDirectory(directory);
        return directory;
    }

    private static boolean safeExistingCacheDirectory(Path directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new SecurityException("artifact cache directory is unsafe");
        }
        return true;
    }

    private ReleaseManifest parseManifest(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, ReleaseManifest.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("release manifest is invalid", exception);
        }
    }

    private Map<String, ManifestArtifact> validateManifest(
            ReleaseManifest manifest,
            String version,
            String releasePrefix) {
        if (manifest.schemaVersion() != 2
                || !version.equals(manifest.version())
                || manifest.publishedAt() == null
                || !platform.platform().equals(manifest.platform())
                || !platform.architecture().equals(manifest.architecture())
                || manifest.launcherVersionMin() > 1
                || manifest.launcherVersionMax() < 1
                || !LocalClientProtocol.VERSION.equals(manifest.protocolVersion())
                || !"1.18.4".equals(manifest.opencodeVersion())
                || manifest.artifacts() == null) {
            throw new IllegalArgumentException("release manifest is incompatible with this launcher");
        }
        Map<String, ManifestArtifact> byKind;
        try {
            byKind = manifest.artifacts().stream().collect(Collectors.toUnmodifiableMap(
                    ManifestArtifact::kind, Function.identity()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("release manifest contains duplicate artifacts", exception);
        }
        if (!byKind.keySet().containsAll(REQUIRED_KINDS)
                || byKind.keySet().stream().anyMatch(kind ->
                        !REQUIRED_KINDS.contains(kind) && !PUBLIC_CAPABILITIES_KIND.equals(kind))) {
            throw new IllegalArgumentException("release manifest must contain JDK, JAR and OpenCode");
        }
        for (ManifestArtifact artifact : byKind.values()) {
            if (artifact.size() < 1
                    || artifact.size() > MAX_ARTIFACT_BYTES
                    || artifact.sha256() == null
                    || !artifact.sha256().matches("[0-9a-f]{64}")
                    || artifact.path() == null
                    || !artifact.path().startsWith(releasePrefix)
                    || artifact.signaturePath() == null
                    || !artifact.signaturePath().startsWith(releasePrefix)) {
                throw new IllegalArgumentException("release artifact metadata is invalid");
            }
            trust.resolve(artifact.path());
            trust.resolve(artifact.signaturePath());
        }
        return byKind;
    }

    private static void validateCommand(LocalClientPayloads.UpdateCommand command, String currentVersion) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.commandId() == null
                || !command.commandId().matches("[A-Za-z0-9_]{1,128}")
                || command.clientInstanceId() == null
                || command.connectionGeneration() < 1
                || command.policyRevision() < 1) {
            throw new IllegalArgumentException("update command coordinates are invalid");
        }
        String expectedDirection = LocalClientReleaseVersion.parse(currentVersion)
                .directionTo(LocalClientReleaseVersion.parse(command.targetVersion()))
                .name();
        if ("SAME".equals(expectedDirection) || !expectedDirection.equals(command.direction())) {
            throw new IllegalArgumentException("update command direction does not match versions");
        }
    }

    private void verifyPreparedLayout(
            Path releaseDirectory,
            Map<String, ManifestArtifact> artifacts) throws IOException {
        if (!Files.isRegularFile(releaseDirectory.resolve("test-agent-local-client.jar"))
                || !platform.isExecutable(platform.javaExecutable(releaseDirectory))
                || !platform.isExecutable(platform.javacExecutable(releaseDirectory))
                || !platform.isExecutable(platform.managedJavaExecutable(releaseDirectory))
                || !platform.isExecutable(platform.opencodeExecutable(releaseDirectory))) {
            throw new IllegalStateException("prepared release layout is incomplete");
        }
        boolean bootstrapSystemJdk = hasBootstrapSystemJdkProvenance(releaseDirectory);
        for (String kind : artifacts.keySet()) {
            if ("JDK".equals(kind) && bootstrapSystemJdk) {
                // 只有稳定 Shell 初装写入的 marker release 可省略本地 JDK 归档摘要。
                continue;
            }
            ManifestArtifact artifact = artifacts.get(kind);
            Path file = releaseDirectory.resolve(localFileName(kind));
            if (!Files.isRegularFile(file) || Files.size(file) != artifact.size()) {
                throw new SecurityException("prepared release artifact size changed");
            }
            LocalClientDownloadTrust.requireSha256(file, artifact.sha256());
        }
    }

    private static boolean hasBootstrapSystemJdkProvenance(Path releaseDirectory) throws IOException {
        Path marker = releaseDirectory.resolve("jdk.provenance");
        Path jdkArchive = releaseDirectory.resolve("jdk.tar.gz");
        Path jdkSignature = releaseDirectory.resolve("jdk.tar.gz.sig");
        return Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                && Files.size(marker) == SYSTEM_JDK_PROVENANCE.getBytes(StandardCharsets.UTF_8).length
                && SYSTEM_JDK_PROVENANCE.equals(Files.readString(marker, StandardCharsets.UTF_8))
                && !Files.exists(jdkArchive, LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(jdkSignature, LinkOption.NOFOLLOW_LINKS);
    }

    private static String localFileName(String kind) {
        return switch (kind) {
            case "CLIENT_JAR" -> "test-agent-local-client.jar";
            case "JDK" -> "jdk.tar.gz";
            case "OPENCODE" -> "opencode.tar.gz";
            case PUBLIC_CAPABILITIES_KIND -> "public-capabilities.tar.gz";
            default -> throw new IllegalArgumentException("unknown artifact kind");
        };
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        Files.createDirectory(directory);
        setPrivateDirectoryPermissions(directory);
    }

    private static void setPrivateDirectoryPermissions(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // 麒麟使用 POSIX；Windows 继承当前用户 AppData 目录 ACL。
        }
    }

    private static void writePrivate(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // 麒麟使用 POSIX；Windows 继承当前用户 AppData 目录 ACL。
        }
    }

    private static void copyPrivate(Path source, Path target) throws IOException {
        boolean copied = false;
        try {
            Files.copy(source, target);
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // 麒麟使用 POSIX；Windows 继承当前用户 AppData 目录 ACL。
            }
            copied = true;
        } finally {
            if (!copied) {
                Files.deleteIfExists(target);
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 下次启动不会选择点号 staging；残留可由安装维护流程清理。
                }
            });
        } catch (IOException ignored) {
            // 同上，绝不删除 releases 下的正式版本。
        }
    }

    record PreparedRelease(String version, String releaseDigest, Path releaseDirectory) {
    }

    record ReleaseManifest(
            int schemaVersion,
            String version,
            Instant publishedAt,
            String platform,
            String architecture,
            int launcherVersionMin,
            int launcherVersionMax,
            String protocolVersion,
            String opencodeVersion,
            List<ManifestArtifact> artifacts) {
    }

    record ManifestArtifact(String kind, String path, long size, String sha256, String signaturePath) {
    }

    interface Fetcher {
        byte[] fetchBytes(URI uri, int maxBytes) throws Exception;

        void fetchFile(URI uri, Path target, long expectedSize) throws Exception;
    }

    @FunctionalInterface
    interface ArchiveExtractor {
        void extract(Path archive, Path releaseDirectory) throws Exception;
    }

    @FunctionalInterface
    interface CandidateChecker {
        void check(Path javaExecutable, Path clientJar, Path releaseDirectory, String targetVersion) throws Exception;
    }

    enum PreparationPhase {
        SELF_CHECKING
    }

    @FunctionalInterface
    interface PreparationListener {
        void onPhase(PreparationPhase phase);
    }
}
