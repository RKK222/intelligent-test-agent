package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 用真实 Git 验证搬迁保留 HEAD、staged、unstaged 和全部未跟踪文件。 */
class PersonalWorkspaceSnapshotServiceRealGitTest {

    private Path root;

    @AfterEach
    void cleanup() throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // 测试临时目录由系统兜底。
                }
            });
        }
    }

    @Test
    void exportsAndRestoresIndexWorktreeAndUntrackedFilesWithoutChangingSource() throws Exception {
        root = Files.createTempDirectory("personal-workspace-relocation-test-");
        Path sourceApplication = root.resolve("source-application");
        Path sourcePersonal = root.resolve("source-personal");
        Path targetApplication = root.resolve("target-application");
        Path targetPersonal = root.resolve("target-personal");
        Path archive = root.resolve("snapshot.zip");

        git(root, "init", "--initial-branch=main", sourceApplication.toString());
        git(sourceApplication, "config", "user.name", "Test User");
        git(sourceApplication, "config", "user.email", "test@example.com");
        Files.createDirectories(sourceApplication.resolve("app"));
        Files.writeString(sourceApplication.resolve("app/tracked.txt"), "base\n", StandardCharsets.UTF_8);
        Files.writeString(sourceApplication.resolve(".gitignore"), "app/*.env\n", StandardCharsets.UTF_8);
        git(sourceApplication, "add", ".");
        git(sourceApplication, "commit", "-m", "base");
        // 目标公共副本先固定在 base，确保后续个人本地提交只能通过搬迁 bundle 到达目标。
        git(root, "clone", sourceApplication.toString(), targetApplication.toString());
        git(sourceApplication, "worktree", "add", "-b", "personal-user", sourcePersonal.toString());

        Files.writeString(sourcePersonal.resolve("app/local-commit.txt"), "local only\n", StandardCharsets.UTF_8);
        git(sourcePersonal, "add", "app/local-commit.txt");
        git(sourcePersonal, "commit", "-m", "local personal commit");

        Files.writeString(sourcePersonal.resolve("app/tracked.txt"), "base\nstaged\n", StandardCharsets.UTF_8);
        git(sourcePersonal, "add", "app/tracked.txt");
        Files.writeString(sourcePersonal.resolve("app/tracked.txt"), "base\nstaged\nunstaged\n", StandardCharsets.UTF_8);
        Files.write(sourcePersonal.resolve("app/untracked.bin"), new byte[]{0, 1, 2, 3, 4});
        Files.writeString(sourcePersonal.resolve("app/local.env"), "ignored but irreplaceable\n", StandardCharsets.UTF_8);

        String sourceStatus = git(sourcePersonal, "status", "--porcelain=v1", "--untracked-files=all");
        String sourceCachedDiff = git(sourcePersonal, "diff", "--cached", "--binary");
        String sourceWorktreeDiff = git(sourcePersonal, "diff", "--binary");

        PersonalWorkspaceSnapshotService service = new PersonalWorkspaceSnapshotService(
                new GitWorkspaceService(), new ObjectMapper());
        PersonalWorkspaceSnapshotService.Snapshot snapshot = service.exportSnapshot(
                sourcePersonal, archive, "pwr_1234567890abcdef");

        assertThat(git(sourcePersonal, "status", "--porcelain=v1", "--untracked-files=all"))
                .isEqualTo(sourceStatus);
        assertThat(git(sourcePersonal, "diff", "--cached", "--binary")).isEqualTo(sourceCachedDiff);
        assertThat(git(sourcePersonal, "diff", "--binary")).isEqualTo(sourceWorktreeDiff);

        PersonalWorkspaceRelocationPaths targetPaths = new PersonalWorkspaceRelocationPaths(
                targetApplication,
                targetPersonal,
                targetPersonal.resolve("app"),
                "target-personal",
                "target-personal/app");
        service.restoreSnapshot(
                archive,
                snapshot.sha256(),
                snapshot.size(),
                "pwr_1234567890abcdef",
                "personal-user",
                targetPaths);

        assertThat(git(targetPersonal, "status", "--porcelain=v1", "--untracked-files=all"))
                .isEqualTo(sourceStatus);
        assertThat(git(targetPersonal, "diff", "--cached", "--binary")).isEqualTo(sourceCachedDiff);
        assertThat(git(targetPersonal, "diff", "--binary")).isEqualTo(sourceWorktreeDiff);
        assertThat(Files.readAllBytes(targetPersonal.resolve("app/untracked.bin")))
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(Files.readString(targetPersonal.resolve("app/local.env"), StandardCharsets.UTF_8))
                .isEqualTo("ignored but irreplaceable\n");
        assertThat(git(targetPersonal, "rev-parse", "HEAD").trim()).isEqualTo(snapshot.headCommit());
    }

    @Test
    void rejectsRepositoryContainingSubmoduleBeforeCreatingArchive() throws Exception {
        root = Files.createTempDirectory("personal-workspace-relocation-submodule-test-");
        Path child = root.resolve("child");
        Path sourceApplication = root.resolve("source-application");
        Path sourcePersonal = root.resolve("source-personal");
        Path archive = root.resolve("snapshot.zip");

        git(root, "init", "--initial-branch=main", child.toString());
        git(child, "config", "user.name", "Test User");
        git(child, "config", "user.email", "test@example.com");
        Files.writeString(child.resolve("README.md"), "child\n", StandardCharsets.UTF_8);
        git(child, "add", ".");
        git(child, "commit", "-m", "child base");

        git(root, "init", "--initial-branch=main", sourceApplication.toString());
        git(sourceApplication, "config", "user.name", "Test User");
        git(sourceApplication, "config", "user.email", "test@example.com");
        Files.writeString(sourceApplication.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        git(sourceApplication, "add", ".");
        git(sourceApplication, "commit", "-m", "base");
        git(sourceApplication, "-c", "protocol.file.allow=always", "submodule", "add",
                child.toString(), "modules/child");
        git(sourceApplication, "commit", "-am", "add submodule");
        git(sourceApplication, "worktree", "add", "-b", "personal-user", sourcePersonal.toString());

        PersonalWorkspaceSnapshotService service = new PersonalWorkspaceSnapshotService(
                new GitWorkspaceService(), new ObjectMapper());

        assertThatThrownBy(() -> service.exportSnapshot(
                        sourcePersonal, archive, "pwr_1234567890abcdef"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("子模块");
        assertThat(archive).doesNotExist();
        assertThat(sourcePersonal).exists();
    }

    @Test
    void sourceCleanupCanRetryAfterWorktreeWasAlreadyRemoved() throws Exception {
        root = Files.createTempDirectory("personal-workspace-relocation-cleanup-test-");
        Path application = root.resolve("application");
        Path personal = root.resolve("personal");
        git(root, "init", "--initial-branch=main", application.toString());
        git(application, "config", "user.name", "Test User");
        git(application, "config", "user.email", "test@example.com");
        Files.writeString(application.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        git(application, "add", ".");
        git(application, "commit", "-m", "base");
        git(application, "worktree", "add", "-b", "personal-user", personal.toString());

        PersonalWorkspaceSnapshotService service = new PersonalWorkspaceSnapshotService(
                new GitWorkspaceService(), new ObjectMapper());
        PersonalWorkspaceRelocationPaths paths = new PersonalWorkspaceRelocationPaths(
                application,
                personal,
                personal,
                "personal",
                "personal");

        service.removeSourceWorktree(paths);
        service.removeSourceWorktree(paths);

        assertThat(Files.exists(personal)).isFalse();
        assertThat(git(application, "worktree", "list", "--porcelain"))
                .doesNotContain(personal.toString());
    }

    @Test
    void rejectsUntrackedFileBelowTrackedSymlink() throws Exception {
        root = Files.createTempDirectory("personal-workspace-relocation-symlink-test-");
        Path sourceApplication = root.resolve("source-application");
        Path sourcePersonal = root.resolve("source-personal");
        Path targetApplication = root.resolve("target-application");
        Path targetPersonal = root.resolve("target-personal");
        Path outsideTarget = root.resolve("outside-target");
        Path archive = root.resolve("snapshot.zip");

        git(root, "init", "--initial-branch=main", sourceApplication.toString());
        git(sourceApplication, "config", "user.name", "Test User");
        git(sourceApplication, "config", "user.email", "test@example.com");
        Files.createDirectories(sourceApplication.resolve("app"));
        Files.writeString(sourceApplication.resolve("app/tracked.txt"), "base\n", StandardCharsets.UTF_8);
        Files.createSymbolicLink(sourceApplication.resolve("app/link"), Path.of("../../outside-target"));
        git(sourceApplication, "add", ".");
        git(sourceApplication, "commit", "-m", "base with symlink");
        git(root, "clone", sourceApplication.toString(), targetApplication.toString());
        git(sourceApplication, "worktree", "add", "-b", "personal-user", sourcePersonal.toString());
        Files.writeString(sourcePersonal.resolve("payload.txt"), "must stay inside\n", StandardCharsets.UTF_8);

        ObjectMapper objectMapper = new ObjectMapper();
        PersonalWorkspaceSnapshotService service = new PersonalWorkspaceSnapshotService(
                new GitWorkspaceService(), objectMapper);
        service.exportSnapshot(sourcePersonal, archive, "pwr_1234567890abcdef");
        rewriteUntrackedPath(archive, objectMapper, "app/link/payload.txt");

        PersonalWorkspaceRelocationPaths targetPaths = new PersonalWorkspaceRelocationPaths(
                targetApplication,
                targetPersonal,
                targetPersonal.resolve("app"),
                "target-personal",
                "target-personal/app");
        assertThatThrownBy(() -> service.restoreSnapshot(
                        archive,
                        sha256(archive),
                        Files.size(archive),
                        "pwr_1234567890abcdef",
                        "personal-user",
                        targetPaths))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("快照无效");
        assertThat(outsideTarget.resolve("payload.txt")).doesNotExist();
    }

    private void rewriteUntrackedPath(Path archive, ObjectMapper objectMapper, String path) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        ((ObjectNode) manifest.path("untrackedFiles").get(0)).put("path", path);
        entries.put("manifest.json", objectMapper.writeValueAsBytes(manifest));

        Path replacement = archive.resolveSibling(archive.getFileName() + ".replacement");
        try (OutputStream output = Files.newOutputStream(replacement);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        Files.move(replacement, archive, StandardCopyOption.REPLACE_EXISTING);
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String git(Path directory, String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new AssertionError("git command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }
}
