package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.GitWorkspaceService.PortableTrackedState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 创建并恢复个人 Git 工作区的可移植快照。
 *
 * <p>Git bundle 保存 HEAD 与 stash 三父提交，未纳入 Git 的文件（包括 ignored）以独立 ZIP entry 保存。源端工作树不会
 * 被 stash/pop 或 reset；目标端恢复后必须通过树对象和逐文件 SHA-256 校验。</p>
 */
@Service
class PersonalWorkspaceSnapshotService {

    static final long MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L;
    static final int MAX_UNTRACKED_FILES = 10_000;
    private static final int BUFFER_BYTES = 256 * 1024;
    private static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String BUNDLE_ENTRY = "repository.bundle";
    private static final Pattern GIT_OBJECT_ID = Pattern.compile("^[0-9a-f]{40,64}$");

    private final GitWorkspaceService git;
    private final ObjectMapper objectMapper;

    @Autowired
    PersonalWorkspaceSnapshotService(ObjectMapper objectMapper) {
        this(new GitWorkspaceService(), objectMapper);
    }

    PersonalWorkspaceSnapshotService(GitWorkspaceService git, ObjectMapper objectMapper) {
        this.git = Objects.requireNonNull(git, "git must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    Snapshot exportSnapshot(Path sourceRoot, Path archive, String relocationId) {
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        Path normalizedArchive = archive.toAbsolutePath().normalize();
        Path bundle = null;
        try {
            PortableTrackedState before = git.capturePortableTrackedState(normalizedRoot);
            List<UntrackedFile> untrackedBefore = inspectUntracked(normalizedRoot);
            String refBase = refBase(relocationId);
            String headRef = refBase + "/head";
            String stashRef = refBase + "/stash";
            bundle = Files.createTempFile(normalizedArchive.getParent(), ".workspace-relocation-", ".bundle");
            git.createPortableBundle(normalizedRoot, bundle, headRef, stashRef, before);
            SnapshotManifest manifest = new SnapshotManifest(
                    1,
                    relocationId,
                    headRef,
                    stashRef,
                    before.headCommit(),
                    before.indexTree(),
                    before.worktreeTree(),
                    before.stashCommit() != null,
                    untrackedBefore);
            writeArchive(normalizedRoot, bundle, normalizedArchive, manifest);

            PortableTrackedState after = git.capturePortableTrackedState(normalizedRoot);
            List<UntrackedFile> untrackedAfter = inspectUntracked(normalizedRoot);
            if (!sameTrackedContent(before, after) || !untrackedBefore.equals(untrackedAfter)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "个人工作区在快照期间发生变化，将稍后重试",
                        Map.of("reason", "SOURCE_CHANGED_DURING_SNAPSHOT"));
            }
            long archiveSize = Files.size(normalizedArchive);
            if (archiveSize > MAX_ARCHIVE_BYTES) {
                throw tooLarge();
            }
            return new Snapshot(normalizedArchive, sha256(normalizedArchive), archiveSize, before.headCommit());
        } catch (PlatformException exception) {
            deleteQuietly(normalizedArchive);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(normalizedArchive);
            throw new PlatformException(
                    ErrorCode.GIT_UNAVAILABLE,
                    "创建个人工作区搬迁快照失败",
                    Map.of("reason", "SNAPSHOT_EXPORT_FAILED"),
                    exception);
        } finally {
            deleteQuietly(bundle);
        }
    }

    RestoreResult restoreSnapshot(
            Path archive,
            String expectedSha256,
            long expectedSize,
            String relocationId,
            String finalBranch,
            PersonalWorkspaceRelocationPaths targetPaths) {
        requireArchive(archive, expectedSha256, expectedSize);
        Path targetRoot = targetPaths.personalRepoRoot().toAbsolutePath().normalize();
        Path targetParent = targetRoot.getParent();
        Path unpack = null;
        Path stage = targetParent.resolve("." + targetRoot.getFileName() + ".relocation-" + shortId(relocationId));
        String temporaryBranch = "test-agent-relocation-" + shortId(relocationId);
        SnapshotManifest manifest = null;
        try {
            Files.createDirectories(targetParent);
            unpack = Files.createTempDirectory(targetParent, ".workspace-relocation-unpack-");
            ExtractedSnapshot extracted = extractArchive(archive, unpack, relocationId);
            manifest = extracted.manifest();
            if (canReuseTarget(targetRoot, finalBranch, manifest)) {
                requireWorkspaceDirectory(targetPaths.workspaceRoot());
                return new RestoreResult(manifest.headCommit());
            }
            requireAvailableFinalPath(targetRoot);
            cleanStagingWorktree(targetPaths.applicationRepoRoot(), stage, temporaryBranch, finalBranch);
            prepareFinalBranch(targetPaths.applicationRepoRoot(), targetRoot, finalBranch);

            git.fetchPortableBundleRef(
                    targetPaths.applicationRepoRoot(), extracted.bundle(), manifest.headRef(), manifest.headRef());
            if (manifest.hasStash()) {
                git.fetchPortableBundleRef(
                        targetPaths.applicationRepoRoot(), extracted.bundle(), manifest.stashRef(), manifest.stashRef());
            }
            git.createWorktreeAtCommit(
                    targetPaths.applicationRepoRoot(), stage, temporaryBranch, manifest.headRef());
            if (manifest.hasStash()) {
                git.applyPortableStash(stage, manifest.stashRef());
            }
            materializeUntracked(stage, extracted, manifest);
            verifyRestored(stage, manifest);
            Path stagedWorkspace = stage.resolve(targetRoot.relativize(targetPaths.workspaceRoot())).normalize();
            requireWorkspaceDirectory(stagedWorkspace);
            git.renameCurrentBranch(stage, finalBranch);
            git.moveWorktree(targetPaths.applicationRepoRoot(), stage, targetRoot);
            verifyRestored(targetRoot, manifest);
            requireWorkspaceDirectory(targetPaths.workspaceRoot());
            return new RestoreResult(manifest.headCommit());
        } catch (PlatformException exception) {
            cleanFailedStage(targetPaths.applicationRepoRoot(), stage, targetRoot);
            throw exception;
        } catch (Exception exception) {
            cleanFailedStage(targetPaths.applicationRepoRoot(), stage, targetRoot);
            throw new PlatformException(
                    ErrorCode.GIT_UNAVAILABLE,
                    "恢复个人工作区搬迁快照失败",
                    Map.of("reason", "SNAPSHOT_RESTORE_FAILED"),
                    exception);
        } finally {
            if (manifest != null) {
                git.deletePortableRef(targetPaths.applicationRepoRoot(), manifest.headRef());
                git.deletePortableRef(targetPaths.applicationRepoRoot(), manifest.stashRef());
            }
            deleteTreeQuietly(unpack);
        }
    }

    void removeSourceWorktree(PersonalWorkspaceRelocationPaths sourcePaths) {
        if (!Files.exists(sourcePaths.personalRepoRoot(), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(sourcePaths.applicationRepoRoot(), LinkOption.NOFOLLOW_LINKS)
                    && git.isGitRepository(sourcePaths.applicationRepoRoot())) {
                git.pruneWorktrees(sourcePaths.applicationRepoRoot(), null);
            }
            return;
        }
        git.removeWorktree(
                sourcePaths.applicationRepoRoot(), sourcePaths.personalRepoRoot(), null);
    }

    private void writeArchive(
            Path sourceRoot, Path bundle, Path archive, SnapshotManifest manifest) throws Exception {
        long uncompressedBytes = Files.size(bundle);
        for (UntrackedFile file : manifest.untrackedFiles()) {
            uncompressedBytes = Math.addExact(uncompressedBytes, file.size());
            if (uncompressedBytes > MAX_ARCHIVE_BYTES) {
                throw tooLarge();
            }
        }
        byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
        if (manifestBytes.length > MAX_MANIFEST_BYTES) {
            throw tooLarge();
        }
        Files.createDirectories(archive.getParent());
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            putBytes(zip, MANIFEST_ENTRY, manifestBytes);
            putFile(zip, BUNDLE_ENTRY, bundle, null);
            for (UntrackedFile file : manifest.untrackedFiles()) {
                Path source = safeResolve(sourceRoot, file.path());
                putFile(zip, file.storageEntry(), source, file.sha256());
            }
        }
    }

    private ExtractedSnapshot extractArchive(Path archive, Path unpack, String relocationId) throws Exception {
        SnapshotManifest manifest = null;
        Path bundle = unpack.resolve(BUNDLE_ENTRY);
        Map<String, Path> extractedFiles = new HashMap<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()
                        || !seen.add(entry.getName())
                        || seen.size() > MAX_UNTRACKED_FILES + 2) {
                    throw invalidArchive();
                }
                if (MANIFEST_ENTRY.equals(entry.getName())) {
                    byte[] bytes = readLimited(zip, MAX_MANIFEST_BYTES);
                    manifest = objectMapper.readValue(bytes, SnapshotManifest.class);
                    validateManifest(manifest, relocationId);
                    total = Math.addExact(total, bytes.length);
                } else if (BUNDLE_ENTRY.equals(entry.getName())) {
                    total = copyLimited(zip, bundle, total);
                } else if (entry.getName().matches("^untracked/[0-9]{8}$")) {
                    Path target = unpack.resolve(entry.getName().replace('/', '_'));
                    total = copyLimited(zip, target, total);
                    extractedFiles.put(entry.getName(), target);
                } else {
                    throw invalidArchive();
                }
                zip.closeEntry();
            }
        }
        if (manifest == null || !Files.isRegularFile(bundle)) {
            throw invalidArchive();
        }
        Set<String> expectedEntries = manifest.untrackedFiles().stream()
                .map(UntrackedFile::storageEntry)
                .collect(java.util.stream.Collectors.toSet());
        if (!expectedEntries.equals(extractedFiles.keySet())) {
            throw invalidArchive();
        }
        for (UntrackedFile file : manifest.untrackedFiles()) {
            Path extracted = extractedFiles.get(file.storageEntry());
            if (Files.size(extracted) != file.size() || !sha256(extracted).equals(file.sha256())) {
                throw invalidArchive();
            }
        }
        return new ExtractedSnapshot(manifest, bundle, Map.copyOf(extractedFiles));
    }

    private void materializeUntracked(
            Path stage, ExtractedSnapshot extracted, SnapshotManifest manifest) throws Exception {
        for (UntrackedFile file : manifest.untrackedFiles()) {
            Path target = safeResolve(stage, file.path());
            requireSafeMaterializationParent(stage, target);
            Files.createDirectories(target.getParent());
            requireSafeMaterializationParent(stage, target);
            Files.copy(extracted.untracked().get(file.storageEntry()), target, StandardCopyOption.COPY_ATTRIBUTES);
            if (file.executable()) {
                setOwnerExecutable(target);
            }
        }
    }

    private List<UntrackedFile> inspectUntracked(Path root) throws Exception {
        List<String> paths = git.untrackedPaths(root).stream().sorted().toList();
        if (paths.size() > MAX_UNTRACKED_FILES) {
            throw tooLarge();
        }
        List<UntrackedFile> files = new ArrayList<>(paths.size());
        long total = 0;
        for (int index = 0; index < paths.size(); index++) {
            String relative = paths.get(index);
            Path file = safeResolve(root, relative);
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "未跟踪文件包含暂不支持的符号链接或特殊文件",
                        Map.of("reason", "UNSUPPORTED_UNTRACKED_ENTRY"));
            }
            long size = Files.size(file);
            total = Math.addExact(total, size);
            if (total > MAX_ARCHIVE_BYTES) {
                throw tooLarge();
            }
            files.add(new UntrackedFile(
                    "untracked/" + String.format(java.util.Locale.ROOT, "%08d", index + 1),
                    relative,
                    size,
                    sha256(file),
                    Files.isExecutable(file)));
        }
        return List.copyOf(files);
    }

    private void verifyRestored(Path root, SnapshotManifest manifest) throws Exception {
        PortableTrackedState actual = git.capturePortableTrackedState(root);
        if (!manifest.headCommit().equals(actual.headCommit())
                || !manifest.indexTree().equals(actual.indexTree())
                || !manifest.worktreeTree().equals(actual.worktreeTree())
                || !manifest.untrackedFiles().equals(inspectUntracked(root))) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "目标服务器工作区恢复校验失败",
                    Map.of("reason", "RESTORED_STATE_MISMATCH"));
        }
    }

    private boolean canReuseTarget(Path targetRoot, String branch, SnapshotManifest manifest) {
        if (!Files.exists(targetRoot)) {
            return false;
        }
        try {
            if (!git.isGitRepository(targetRoot) || !branch.equals(git.currentBranch(targetRoot))) {
                return false;
            }
            verifyRestored(targetRoot, manifest);
            return true;
        } catch (RuntimeException | java.io.IOException exception) {
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    private void prepareFinalBranch(Path applicationRepoRoot, Path targetRoot, String finalBranch) {
        if (!git.localBranchExists(applicationRepoRoot, finalBranch)) {
            return;
        }
        var registered = git.worktreePathForBranch(applicationRepoRoot, finalBranch);
        if (registered.isPresent()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "目标服务器已存在同名个人工作区分支",
                    Map.of("reason", "TARGET_BRANCH_IN_USE"));
        }
        if (Files.exists(targetRoot)) {
            throw new PlatformException(ErrorCode.CONFLICT, "目标服务器个人工作区目录已存在");
        }
        git.deleteLocalBranch(applicationRepoRoot, finalBranch);
    }

    private void cleanStagingWorktree(
            Path applicationRepoRoot, Path stage, String temporaryBranch, String finalBranch) {
        if (!Files.exists(stage)) {
            if (git.localBranchExists(applicationRepoRoot, temporaryBranch)
                    && git.worktreePathForBranch(applicationRepoRoot, temporaryBranch).isEmpty()) {
                git.deleteLocalBranch(applicationRepoRoot, temporaryBranch);
            }
            return;
        }
        if (!git.isGitRepository(stage)) {
            throw new PlatformException(ErrorCode.CONFLICT, "目标服务器搬迁暂存目录冲突");
        }
        String branch = git.currentBranch(stage);
        if (!temporaryBranch.equals(branch) && !finalBranch.equals(branch)) {
            throw new PlatformException(ErrorCode.CONFLICT, "目标服务器搬迁暂存分支冲突");
        }
        git.removeWorktree(applicationRepoRoot, stage, null);
        if (git.localBranchExists(applicationRepoRoot, branch)
                && git.worktreePathForBranch(applicationRepoRoot, branch).isEmpty()) {
            git.deleteLocalBranch(applicationRepoRoot, branch);
        }
    }

    private void cleanFailedStage(Path applicationRepoRoot, Path stage, Path finalRoot) {
        if (!Files.exists(stage) || Files.exists(finalRoot)) {
            return;
        }
        try {
            if (git.isGitRepository(stage)) {
                git.removeWorktree(applicationRepoRoot, stage, null);
            }
        } catch (RuntimeException ignored) {
            // 暂存副本由下一次相同 relocation 重试按分支和路径双重校验后清理。
        }
    }

    private void validateManifest(SnapshotManifest manifest, String relocationId) {
        String expectedHeadRef = refBase(relocationId) + "/head";
        String expectedStashRef = refBase(relocationId) + "/stash";
        if (manifest == null || manifest.formatVersion() != 1
                || !relocationId.equals(manifest.relocationId())
                || !expectedHeadRef.equals(manifest.headRef())
                || !expectedStashRef.equals(manifest.stashRef())
                || manifest.headCommit() == null
                || !GIT_OBJECT_ID.matcher(manifest.headCommit()).matches()
                || manifest.indexTree() == null
                || !GIT_OBJECT_ID.matcher(manifest.indexTree()).matches()
                || manifest.worktreeTree() == null
                || !GIT_OBJECT_ID.matcher(manifest.worktreeTree()).matches()
                || manifest.untrackedFiles() == null
                || manifest.untrackedFiles().size() > MAX_UNTRACKED_FILES) {
            throw invalidArchive();
        }
        Set<String> paths = new HashSet<>();
        Set<String> entries = new HashSet<>();
        long declaredBytes = 0;
        for (UntrackedFile file : manifest.untrackedFiles()) {
            if (file == null || file.size() < 0
                    || file.sha256() == null || !file.sha256().matches("^[0-9a-f]{64}$")
                    || file.storageEntry() == null || !file.storageEntry().matches("^untracked/[0-9]{8}$")
                    || !entries.add(file.storageEntry())) {
                throw invalidArchive();
            }
            Path relative = safeRelative(file.path());
            if (!paths.add(relative.toString()) || file.size() > MAX_ARCHIVE_BYTES - declaredBytes) {
                throw invalidArchive();
            }
            declaredBytes += file.size();
        }
    }

    /** 拒绝通过已恢复的跟踪符号链接把未跟踪文件写出暂存 worktree。 */
    private void requireSafeMaterializationParent(Path root, Path target) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null || !parent.startsWith(normalizedRoot)) {
            throw invalidArchive();
        }
        Path current = normalizedRoot;
        for (Path segment : normalizedRoot.relativize(parent)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)
                    || (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
                throw invalidArchive();
            }
        }
    }

    private void requireArchive(Path archive, String expectedSha256, long expectedSize) {
        try {
            if (expectedSize < 0 || expectedSize > MAX_ARCHIVE_BYTES
                    || Files.size(archive) != expectedSize
                    || expectedSha256 == null
                    || !expectedSha256.matches("^[0-9a-f]{64}$")
                    || !expectedSha256.equals(sha256(archive))) {
                throw invalidArchive();
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidArchive();
        }
    }

    private void requireAvailableFinalPath(Path targetRoot) {
        if (Files.exists(targetRoot)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "目标服务器个人工作区目录已存在且内容与快照不一致",
                    Map.of("reason", "TARGET_PATH_CONFLICT"));
        }
    }

    private void requireWorkspaceDirectory(Path workspaceRoot) {
        if (!Files.isDirectory(workspaceRoot)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "目标服务器恢复后缺少应用工作空间目录",
                    Map.of("reason", "TARGET_WORKSPACE_DIRECTORY_MISSING"));
        }
    }

    private long copyLimited(InputStream input, Path target, long total) throws Exception {
        try (OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total = Math.addExact(total, read);
                if (total > MAX_ARCHIVE_BYTES) {
                    throw tooLarge();
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (output.size() + read > maxBytes) {
                throw tooLarge();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private void putFile(ZipOutputStream zip, String name, Path file, String expectedSha256) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
        if (expectedSha256 != null
                && !expectedSha256.equals(HexFormat.of().formatHex(digest.digest()))) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "个人工作区在归档期间发生变化，将稍后重试",
                    Map.of("reason", "SOURCE_FILE_CHANGED_DURING_ARCHIVE"));
        }
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Path safeResolve(Path root, String relative) {
        Path resolved = root.resolve(safeRelative(relative)).toAbsolutePath().normalize();
        if (!resolved.startsWith(root.toAbsolutePath().normalize())) {
            throw invalidArchive();
        }
        return resolved;
    }

    private Path safeRelative(String relative) {
        if (relative == null || relative.isBlank()) {
            throw invalidArchive();
        }
        Path path;
        try {
            path = Path.of(relative).normalize();
        } catch (Exception exception) {
            throw invalidArchive();
        }
        if (path.isAbsolute() || path.startsWith("..") || path.toString().isBlank()) {
            throw invalidArchive();
        }
        return path;
    }

    private void setOwnerExecutable(Path file) {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(file));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException ignored) {
            file.toFile().setExecutable(true, true);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.CONFLICT, "恢复未跟踪可执行文件权限失败");
        }
    }

    private boolean sameTrackedContent(PortableTrackedState left, PortableTrackedState right) {
        return left.headCommit().equals(right.headCommit())
                && left.indexTree().equals(right.indexTree())
                && left.worktreeTree().equals(right.worktreeTree());
    }

    private String refBase(String relocationId) {
        if (relocationId == null || !relocationId.matches("^[A-Za-z0-9_-]{8,128}$")) {
            throw new IllegalArgumentException("invalid relocationId");
        }
        return "refs/test-agent/relocations/" + relocationId;
    }

    private String shortId(String relocationId) {
        String value = relocationId.replaceAll("[^A-Za-z0-9]", "");
        return value.substring(Math.max(0, value.length() - 24));
    }

    private PlatformException invalidArchive() {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "个人工作区搬迁快照无效或已损坏",
                Map.of("reason", "INVALID_RELOCATION_ARCHIVE"));
    }

    private PlatformException tooLarge() {
        return new PlatformException(
                ErrorCode.PAYLOAD_TOO_LARGE,
                "个人工作区未提交文件超过自动搬迁上限",
                Map.of("maxArchiveBytes", MAX_ARCHIVE_BYTES, "maxUntrackedFiles", MAX_UNTRACKED_FILES));
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 临时文件由操作系统或下一轮受控清理兜底。
        }
    }

    private void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (Exception ignored) {
            // 临时解包目录不影响已校验的目标工作区。
        }
    }

    record Snapshot(Path archive, String sha256, long size, String headCommit) {
    }

    record RestoreResult(String headCommit) {
    }

    private record SnapshotManifest(
            int formatVersion,
            String relocationId,
            String headRef,
            String stashRef,
            String headCommit,
            String indexTree,
            String worktreeTree,
            boolean hasStash,
            List<UntrackedFile> untrackedFiles) {
    }

    private record UntrackedFile(
            String storageEntry,
            String path,
            long size,
            String sha256,
            boolean executable) {
    }

    private record ExtractedSnapshot(
            SnapshotManifest manifest,
            Path bundle,
            Map<String, Path> untracked) {
    }
}
