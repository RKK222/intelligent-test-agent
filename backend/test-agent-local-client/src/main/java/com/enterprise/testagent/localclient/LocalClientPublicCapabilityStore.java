package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** 不可变公共能力版本目录、原子 current 软链接和崩溃恢复状态。 */
final class LocalClientPublicCapabilityStore {

    /** macOS 开发 App 与其它原生启动器可显式传入随包能力基线；生产安装仍使用 installRoot/release。 */
    static final String PACKAGED_BUNDLE_PROPERTY = "testagent.localclient.packagedPublicCapabilityBundle";
    private static final int MAX_MANIFEST_FILES = 100_000;
    private final Path root;
    private final Path revisions;
    private final Path incoming;
    private final Path quarantine;
    private final Path currentLink;
    private final Path stateFile;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LocalClientTarGzExtractor extractor = new LocalClientTarGzExtractor();
    private State state;

    LocalClientPublicCapabilityStore(Path stateDirectory) {
        this.root = stateDirectory.toAbsolutePath().normalize().resolve("public-capabilities");
        this.revisions = root.resolve("revisions");
        this.incoming = root.resolve("incoming");
        this.quarantine = root.resolve("quarantine");
        this.currentLink = root.resolve("current");
        this.stateFile = root.resolve("state.json");
        this.state = readState();
    }

    synchronized void initializeBaseline(LocalClientConfiguration configuration, LocalClientBuildInfo buildInfo) {
        if (state.activeDigest() != null) {
            return;
        }
        Path archive = baselineArchive(configuration, buildInfo);
        if (archive == null) {
            return;
        }
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
            if (System.getProperty(PACKAGED_BUNDLE_PROPERTY) != null) {
                throw new IllegalStateException("原生客户端内置公共能力基线不存在");
            }
            return;
        }
        try {
            Candidate candidate = installArchive(archive, null, null);
            switchCurrent(candidate.bundleDigest());
            // 首次基线也必须经过真实 OpenCode 重载和目录接口校验；进程崩溃后由启动恢复程序继续收敛。
            state = new State(
                    candidate.sourceCommit(), candidate.bundleDigest(), null, "APPLYING", null,
                    null, null, null, null, Instant.now());
            writeState(state);
        } catch (Exception exception) {
            state = new State(null, null, null, "FAILED", "BASELINE_INITIALIZATION_FAILED",
                    null, null, null, null, Instant.now());
            writeState(state);
            throw new IllegalStateException("内置公共能力基线初始化失败", exception);
        }
    }

    private static Path baselineArchive(
            LocalClientConfiguration configuration,
            LocalClientBuildInfo buildInfo) {
        String packaged = System.getProperty(PACKAGED_BUNDLE_PROPERTY);
        if (packaged != null && !packaged.isBlank()) {
            return Path.of(packaged.trim()).toAbsolutePath().normalize();
        }
        if (configuration.installRoot() == null) {
            return null;
        }
        return configuration.installRoot()
                .resolve("releases")
                .resolve(buildInfo.clientVersion())
                .resolve("public-capabilities.tar.gz")
                .toAbsolutePath().normalize();
    }

    synchronized Path activeConfigDirectory(Path legacyDirectory) {
        if (state.activeDigest() == null || !Files.isSymbolicLink(currentLink)) {
            return legacyDirectory;
        }
        Path candidate = currentLink.toAbsolutePath().normalize();
        if (!Files.isDirectory(candidate) || !Files.isSymbolicLink(currentLink)) {
            throw new IllegalStateException("公共能力 current 链接不可用");
        }
        return candidate;
    }

    synchronized Candidate installArchive(Path archive, String expectedCommit, String expectedDigest) throws Exception {
        Files.createDirectories(revisions);
        Files.createDirectories(incoming);
        Path staging = Files.createTempDirectory(incoming, "candidate-");
        try {
            Path approvedArchive = staging.resolve("public-capabilities.tar.gz");
            Files.copy(archive, approvedArchive, StandardCopyOption.REPLACE_EXISTING);
            extractor.extract(approvedArchive, staging);
            Files.delete(approvedArchive);
            Path config = staging.resolve("public-capabilities");
            Candidate candidate = validate(config, expectedCommit, expectedDigest);
            Path destination = revisions.resolve(candidate.bundleDigest());
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(destination) || !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                    throw new SecurityException("公共能力版本目录类型无效");
                }
                try {
                    Candidate installed = validate(
                            destination.resolve("public-capabilities"), expectedCommit, expectedDigest);
                    deleteTree(staging);
                    return installed;
                } catch (IOException | SecurityException corrupted) {
                    // 旧版客户端可能被 OpenCode 的依赖自检写入额外文件；保留隔离副本后用签名制品重建。
                    quarantineCorruptedRevision(destination, candidate.bundleDigest());
                }
            }
            atomicMove(staging, destination);
            return new Candidate(
                    candidate.sourceCommit(), candidate.bundleDigest(), candidate.requiresRestart(),
                    destination.resolve("public-capabilities"));
        } catch (Exception exception) {
            deleteTree(staging);
            throw exception;
        }
    }

    private void quarantineCorruptedRevision(Path destination, String digest) throws IOException {
        Files.createDirectories(quarantine);
        String base = digest + "-" + Instant.now().toEpochMilli();
        Path target = quarantine.resolve(base);
        int suffix = 0;
        while (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            target = quarantine.resolve(base + "-" + (++suffix));
        }
        atomicMove(destination, target);
    }

    synchronized String activate(Candidate candidate) throws IOException {
        String previous = state.activeDigest();
        switchCurrent(candidate.bundleDigest());
        state = new State(
                candidate.sourceCommit(), candidate.bundleDigest(), previous, "APPLYING", null,
                state.pendingAvailable(), state.pendingCommandId(), state.pendingCommit(), state.pendingDigest(),
                Instant.now());
        writeState(state);
        return previous;
    }

    synchronized void completeActivation() {
        state = new State(
                state.activeCommit(), state.activeDigest(), null, "SUCCEEDED", null,
                null, null, null, null, Instant.now());
        writeState(state);
    }

    synchronized void rollback(String previousDigest, String errorCode) throws IOException {
        if (previousDigest == null) {
            Files.deleteIfExists(currentLink);
            state = new State(
                    null, null, null, "ROLLED_BACK", errorCode, state.pendingAvailable(), null,
                    state.pendingCommit(), state.pendingDigest(), Instant.now());
        } else {
            Candidate previous = validate(
                    revisions.resolve(previousDigest).resolve("public-capabilities"), null, previousDigest);
            switchCurrent(previous.bundleDigest());
            state = new State(
                    previous.sourceCommit(), previous.bundleDigest(), null, "ROLLED_BACK", errorCode,
                    state.pendingAvailable(), null, state.pendingCommit(), state.pendingDigest(), Instant.now());
        }
        writeState(state);
    }

    synchronized void recordAvailable(LocalClientPayloads.PublicCapabilityAvailable available) {
        state = new State(
                state.activeCommit(), state.activeDigest(), state.previousDigest(), state.status(), state.errorCode(),
                available, state.pendingCommandId(), available.sourceCommit(), available.bundleDigest(), Instant.now());
        writeState(state);
    }

    synchronized void recordPendingCommand(String commandId, String sourceCommit, String bundleDigest) {
        state = new State(
                state.activeCommit(), state.activeDigest(), state.previousDigest(), "PENDING", null,
                state.pendingAvailable(), commandId, sourceCommit, bundleDigest, Instant.now());
        writeState(state);
    }

    synchronized void recordStatus(String status, String errorCode) {
        state = new State(
                state.activeCommit(), state.activeDigest(), state.previousDigest(), status, errorCode,
                state.pendingAvailable(), state.pendingCommandId(), state.pendingCommit(), state.pendingDigest(),
                Instant.now());
        writeState(state);
    }

    synchronized State snapshot() {
        return state;
    }

    synchronized Path incomingArchive(String commandId) throws IOException {
        if (commandId == null || !commandId.matches("lcpc_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("公共能力 commandId 无效");
        }
        Files.createDirectories(incoming);
        return incoming.resolve(commandId + ".public-capabilities.tar.gz");
    }

    private Candidate validate(Path config, String expectedCommit, String expectedDigest) throws IOException {
        if (!Files.isDirectory(config, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(config)) {
            throw new SecurityException("能力包缺少安全根目录");
        }
        Path manifestPath = config.resolve("manifest.json");
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(manifestPath)) {
            throw new SecurityException("能力包 manifest 不存在");
        }
        JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
        if (manifest.path("schemaVersion").asInt() != 1) {
            throw new SecurityException("能力包 manifest 版本不受支持");
        }
        String sourceCommit = manifest.path("sourceCommit").asText();
        String bundleDigest = manifest.path("bundleDigest").asText();
        if (!sourceCommit.matches("[0-9a-f]{40,64}") || !bundleDigest.matches("[0-9a-f]{64}")) {
            throw new SecurityException("能力包身份摘要无效");
        }
        if (expectedCommit != null && !expectedCommit.equals(sourceCommit)) {
            throw new SecurityException("能力包 commit 与命令不一致");
        }
        if (expectedDigest != null && !expectedDigest.equals(bundleDigest)) {
            throw new SecurityException("能力包摘要与命令不一致");
        }
        Map<String, String> fileDigests = new TreeMap<>();
        int count = 0;
        for (JsonNode file : manifest.path("files")) {
            if (++count > MAX_MANIFEST_FILES) {
                throw new SecurityException("能力包 manifest 文件数量过多");
            }
            String relative = file.path("path").asText();
            String expectedSha = file.path("sha256").asText();
            if (!relative.matches("(?:agents|skills|tools|node_modules)/[^\\r\\n]+|(?:package\\.json|package-lock\\.json|\\.gitignore)")
                    || relative.contains("..") || relative.startsWith("/")
                    || !expectedSha.matches("[0-9a-f]{64}")) {
                throw new SecurityException("能力包 manifest 文件坐标无效");
            }
            Path target = config.resolve(relative).normalize();
            if (!target.startsWith(config) || Files.isSymbolicLink(target)
                    || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new SecurityException("能力包 manifest 文件不存在");
            }
            String actualSha = sha256(Files.readAllBytes(target));
            if (!expectedSha.equals(actualSha)) {
                throw new SecurityException("能力包文件摘要不匹配");
            }
            fileDigests.put(relative, actualSha);
        }
        Map<String, String> actualFiles = new TreeMap<>();
        try (var paths = Files.walk(config)) {
            for (Path target : paths.toList()) {
                if (target.equals(config) || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new SecurityException("能力包包含不受支持的文件类型");
                }
                String relative = config.relativize(target).toString().replace('\\', '/');
                if (!relative.equals("manifest.json")) {
                    actualFiles.put(relative, sha256(Files.readAllBytes(target)));
                }
            }
        }
        if (!actualFiles.equals(fileDigests)) {
            throw new SecurityException("能力包存在 manifest 未声明或缺失的文件");
        }
        String contentDigest = contentDigest(fileDigests);
        String declaredContentDigest = manifest.path("contentDigest").asText(null);
        // 兼容首版仅以文件摘要作为 bundleDigest 的已安装基线；新版把 sourceCommit 纳入版本身份，
        // 从而允许相同能力文件在新公共 commit 下生成独立、可追溯的完整包。
        String expectedBundleDigest;
        if (declaredContentDigest == null || declaredContentDigest.isBlank()) {
            expectedBundleDigest = contentDigest;
        } else {
            if (!declaredContentDigest.matches("[0-9a-f]{64}")
                    || !declaredContentDigest.equals(contentDigest)) {
                throw new SecurityException("能力包文件内容摘要不匹配");
            }
            expectedBundleDigest = bundleIdentityDigest(sourceCommit, contentDigest);
        }
        if (!bundleDigest.equals(expectedBundleDigest)) {
            throw new SecurityException("能力包内容摘要不匹配");
        }
        requireCatalog(config, manifest);
        return new Candidate(sourceCommit, bundleDigest, manifest.path("requiresRestart").asBoolean(), config);
    }

    private static void requireCatalog(Path config, JsonNode manifest) throws IOException {
        JsonNode counts = manifest.path("counts");
        if (counts.path("agents").asInt() > 0 && !Files.isDirectory(config.resolve("agents"))) {
            throw new SecurityException("能力包 Agent 目录缺失");
        }
        if (counts.path("skills").asInt() > 0 && !Files.isDirectory(config.resolve("skills"))) {
            throw new SecurityException("能力包 Skill 目录缺失");
        }
        if (counts.path("tools").asInt() > 0 && !Files.isDirectory(config.resolve("tools"))) {
            throw new SecurityException("能力包 Tool 目录缺失");
        }
    }

    private void switchCurrent(String digest) throws IOException {
        Path destination = revisions.resolve(digest).resolve("public-capabilities");
        if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
            throw new SecurityException("待激活能力目录不存在");
        }
        Files.createDirectories(root);
        Path next = root.resolve("current.next");
        Files.deleteIfExists(next);
        Files.createSymbolicLink(next, destination);
        atomicMove(next, currentLink);
    }

    private State readState() {
        try {
            if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS)) {
                return State.empty();
            }
            return objectMapper.readValue(stateFile.toFile(), State.class);
        } catch (Exception exception) {
            throw new IllegalStateException("公共能力状态文件无法读取", exception);
        }
    }

    private void writeState(State value) {
        try {
            Files.createDirectories(root);
            Path temporary = root.resolve("state.json.tmp");
            Files.write(temporary, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            atomicMove(temporary, stateFile);
        } catch (IOException exception) {
            throw new IllegalStateException("公共能力状态文件无法写入", exception);
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
        }
    }

    private static String contentDigest(Map<String, String> files) {
        MessageDigest digest = messageDigest();
        files.forEach((path, sha) -> {
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(sha.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(messageDigest().digest(bytes));
    }

    private static String bundleIdentityDigest(String sourceCommit, String contentDigest) {
        return sha256((sourceCommit + "\n" + contentDigest).getBytes(StandardCharsets.US_ASCII));
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    record Candidate(String sourceCommit, String bundleDigest, boolean requiresRestart, Path configDirectory) {
    }

    record State(
            String activeCommit,
            String activeDigest,
            String previousDigest,
            String status,
            String errorCode,
            LocalClientPayloads.PublicCapabilityAvailable pendingAvailable,
            String pendingCommandId,
            String pendingCommit,
            String pendingDigest,
            Instant updatedAt) {
        static State empty() {
            return new State(null, null, null, "NOT_INITIALIZED", null,
                    null, null, null, null, Instant.EPOCH);
        }
    }
}
