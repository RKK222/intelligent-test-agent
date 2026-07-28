package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.git.ProcessGitCommandExecutor;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSourceGitMaterializerTest {

    @TempDir
    Path tempDir;

    @Test
    void shallowMaterializationKeepsOnlyExactDirectoryRemovesGitAndWritesVerifiableIndex() throws Exception {
        GitFixture fixture = fixture();
        Path target = tempDir.resolve("appsource/billing-service");

        AppSourceGitMaterializer.Result result = new AppSourceGitMaterializer().materialize(
                new AppSourceGitMaterializer.Request(
                        target,
                        fixture.remoteUri(),
                        "main",
                        fixture.commit(),
                        List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                        null,
                        3L,
                        Instant.parse("2026-07-30T04:00:00Z")));

        assertThat(result.shallowClone()).isTrue();
        assertThat(Files.readString(target.resolve("src/Main.java"))).isEqualTo("class Main {}\n");
        assertThat(target.resolve("docs/guide.md")).doesNotExist();
        assertThat(target.resolve(".git")).doesNotExist();
        Path index = target.resolve(AppSourceApplicationService.INDEX_FILE_NAME);
        assertThat(index).isRegularFile();
        assertThat(result.indexSha256()).isEqualTo(sha256(Files.readAllBytes(index)));
        assertThat(Files.readString(index)).contains(fixture.commit(), "\"generation\":3", "\"path\":\"src\"");
    }

    @Test
    void noConeStdinTreatsFileAndDirectorySelectionsWithSpecialCharactersAsExactPaths() throws Exception {
        GitFixture fixture = fixture();
        Path target = tempDir.resolve("appsource/special-source");

        new AppSourceGitMaterializer().materialize(new AppSourceGitMaterializer.Request(
                target,
                fixture.remoteUri(),
                "main",
                fixture.commit(),
                List.of(
                        new AppSourceSelectedPath("literal [x]!.txt", AppSourcePathType.FILE),
                        new AppSourceSelectedPath("dir space", AppSourcePathType.DIRECTORY)),
                null,
                4L,
                Instant.parse("2026-07-30T04:00:00Z")));

        assertThat(target.resolve("literal [x]!.txt")).hasContent("chosen special\n");
        assertThat(target.resolve("literal x!.txt")).doesNotExist();
        assertThat(target.resolve("dir space/[keep].txt")).hasContent("directory child\n");
        assertThat(target.resolve("dir space-other/unselected.txt")).doesNotExist();
    }

    @Test
    void dotSelectionMaterializesWholeRepositoryWithoutGitMetadata() throws Exception {
        GitFixture fixture = fixture();
        Path target = tempDir.resolve("appsource/whole-source");
        AppSourceSelectedPath rootSelection = new AppSourceSelectedPath(".", AppSourcePathType.DIRECTORY);

        new AppSourceGitMaterializer().materialize(new AppSourceGitMaterializer.Request(
                target, fixture.remoteUri(), "main", fixture.commit(), List.of(rootSelection),
                null, 5L, Instant.parse("2026-07-30T04:00:00Z")));

        assertThat(rootSelection.path()).isEqualTo(".");
        assertThat(target.resolve("src/Main.java")).isRegularFile();
        assertThat(target.resolve("docs/guide.md")).isRegularFile();
        assertThat(target.resolve(".git")).doesNotExist();
    }

    @Test
    void materializationUsesFrozenCommitEvenWhenBranchAdvancesBeforeWorkerStarts() throws Exception {
        GitFixture fixture = fixture();
        Path source = tempDir.resolve("fixture-source");
        Files.writeString(source.resolve("src/Main.java"), "class ChangedAfterAcceptance {}\n");
        git(source, "add", "src/Main.java");
        git(source, "commit", "-m", "branch advanced");
        git(source, "push", fixture.remoteUri(), "main");
        Path target = tempDir.resolve("appsource/frozen-commit");

        new AppSourceGitMaterializer().materialize(new AppSourceGitMaterializer.Request(
                target, fixture.remoteUri(), "main", fixture.commit(),
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                null, 11L, Instant.parse("2026-07-30T04:00:00Z")));

        assertThat(target.resolve("src/Main.java")).hasContent("class Main {}\n");
    }

    @Test
    void newGenerationReplacesExistingSourceDirectoryWithoutKeepingOldContent() throws Exception {
        GitFixture fixture = fixture();
        Path target = tempDir.resolve("appsource/replaced-source");
        Files.createDirectories(target);
        Files.writeString(target.resolve("locally-modified.txt"), "discard me\n");

        new AppSourceGitMaterializer().materialize(new AppSourceGitMaterializer.Request(
                target, fixture.remoteUri(), "main", fixture.commit(),
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                null, 6L, Instant.parse("2026-07-30T04:00:00Z")));

        assertThat(target.resolve("locally-modified.txt")).doesNotExist();
        assertThat(target.resolve("src/Main.java")).isRegularFile();
    }

    @Test
    void failedStagingPublishRestoresOldDirectoryAndLeavesNoHalfPublishedTarget() throws Exception {
        GitFixture fixture = fixture();
        Path target = tempDir.resolve("appsource/rollback-source");
        Files.createDirectories(target);
        Files.writeString(target.resolve("locally-modified.txt"), "must survive\n");
        AtomicInteger moves = new AtomicInteger();
        AppSourceGitMaterializer.DirectoryMover failingSecondMove = (source, destination) -> {
            if (moves.incrementAndGet() == 2) {
                throw new IOException("injected publish failure");
            }
            Files.move(source, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        };
        AppSourceGitMaterializer materializer = new AppSourceGitMaterializer(
                new ProcessGitCommandExecutor(), new ObjectMapper(), failingSecondMove);

        assertThatThrownBy(() -> materializer.materialize(new AppSourceGitMaterializer.Request(
                        target, fixture.remoteUri(), "main", fixture.commit(),
                        List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                        null, 7L, Instant.parse("2026-07-30T04:00:00Z"))))
                .hasMessage("应用源码物化失败");

        assertThat(moves).hasValue(3);
        assertThat(target.resolve("locally-modified.txt")).hasContent("must survive\n");
        assertThat(target.resolve("src/Main.java")).doesNotExist();
    }

    @Test
    void databaseCompletionFailureRollsBackAlreadyPublishedDirectory() throws Exception {
        GitFixture fixture = fixture();
        Path target = tempDir.resolve("appsource/db-rollback-source");
        Files.createDirectories(target);
        Files.writeString(target.resolve("old.txt"), "old generation\n");
        AppSourceGitMaterializer materializer = new AppSourceGitMaterializer();

        assertThatThrownBy(() -> materializer.materialize(
                        new AppSourceGitMaterializer.Request(
                                target, fixture.remoteUri(), "main", fixture.commit(),
                                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                                null, 8L, Instant.parse("2026-07-30T04:00:00Z")),
                        result -> {
                            throw new IllegalStateException("database completion failed");
                        }))
                .hasMessageContaining("database completion failed");

        assertThat(target.resolve("old.txt")).hasContent("old generation\n");
        assertThat(target.resolve("src/Main.java")).doesNotExist();
    }

    @Test
    void materializationRejectsSymlinkThatEscapesRepositoryRoot() throws Exception {
        Path source = tempDir.resolve("symlink-source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("inside.txt"), "inside\n");
        Files.createSymbolicLink(source.resolve("escape"), Path.of("../../outside"));
        git(tempDir, "init", "--initial-branch=main", source.toString());
        git(source, "config", "user.name", "Fixture");
        git(source, "config", "user.email", "fixture@example.invalid");
        git(source, "add", "--all");
        git(source, "commit", "-m", "symlink");
        String commit = git(source, "rev-parse", "HEAD").trim();
        Path remote = tempDir.resolve("symlink.git");
        git(tempDir, "clone", "--bare", source.toString(), remote.toString());

        assertThatThrownBy(() -> new AppSourceGitMaterializer().materialize(
                        new AppSourceGitMaterializer.Request(
                                tempDir.resolve("appsource/symlink"), remote.toUri().toString(), "main", commit,
                                List.of(new AppSourceSelectedPath(".", AppSourcePathType.DIRECTORY)),
                                null, 9L, Instant.parse("2026-07-30T04:00:00Z"))))
                .hasMessage("源码快照包含越根符号链接");
    }

    @Test
    void gitlinkIsMaterializedWithoutRecursingIntoSubmoduleRepository() throws Exception {
        Path submodule = tempDir.resolve("submodule-source");
        Files.createDirectories(submodule);
        Files.writeString(submodule.resolve("nested-secret.txt"), "must not clone recursively\n");
        git(tempDir, "init", "--initial-branch=main", submodule.toString());
        git(submodule, "config", "user.name", "Fixture");
        git(submodule, "config", "user.email", "fixture@example.invalid");
        git(submodule, "add", "--all");
        git(submodule, "commit", "-m", "submodule");
        Path source = tempDir.resolve("gitlink-source");
        Files.createDirectories(source);
        git(tempDir, "init", "--initial-branch=main", source.toString());
        git(source, "config", "user.name", "Fixture");
        git(source, "config", "user.email", "fixture@example.invalid");
        git(source, "-c", "protocol.file.allow=always", "submodule", "add", submodule.toString(), "modules/demo");
        git(source, "commit", "-m", "gitlink");
        String commit = git(source, "rev-parse", "HEAD").trim();
        Path remote = tempDir.resolve("gitlink.git");
        git(tempDir, "clone", "--bare", source.toString(), remote.toString());
        Path target = tempDir.resolve("appsource/gitlink");

        new AppSourceGitMaterializer().materialize(new AppSourceGitMaterializer.Request(
                target, remote.toUri().toString(), "main", commit,
                List.of(new AppSourceSelectedPath(".", AppSourcePathType.DIRECTORY)),
                null, 10L, Instant.parse("2026-07-30T04:00:00Z")));

        assertThat(target.resolve("modules/demo")).isDirectory();
        assertThat(target.resolve("modules/demo/nested-secret.txt")).doesNotExist();
        try (var paths = Files.walk(target)) {
            assertThat(paths.map(path -> path.getFileName() == null ? "" : path.getFileName().toString()))
                    .doesNotContain(".git");
        }
    }

    private GitFixture fixture() throws Exception {
        Path source = tempDir.resolve("fixture-source");
        Files.createDirectories(source.resolve("src"));
        Files.createDirectories(source.resolve("docs"));
        Files.createDirectories(source.resolve("dir space"));
        Files.createDirectories(source.resolve("dir space-other"));
        Files.writeString(source.resolve("src/Main.java"), "class Main {}\n");
        Files.writeString(source.resolve("docs/guide.md"), "unselected\n");
        Files.writeString(source.resolve("literal [x]!.txt"), "chosen special\n");
        Files.writeString(source.resolve("literal x!.txt"), "glob trap\n");
        Files.writeString(source.resolve("dir space/[keep].txt"), "directory child\n");
        Files.writeString(source.resolve("dir space-other/unselected.txt"), "prefix trap\n");
        git(tempDir, "init", "--initial-branch=main", source.toString());
        git(source, "config", "user.name", "Fixture");
        git(source, "config", "user.email", "fixture@example.invalid");
        git(source, "add", "--all");
        git(source, "commit", "-m", "fixture");
        String commit = git(source, "rev-parse", "HEAD").trim();
        Path remote = tempDir.resolve("fixture.git");
        git(tempDir, "clone", "--bare", source.toString(), remote.toString());
        return new GitFixture(remote.toUri().toString(), commit);
    }

    private String git(Path directory, String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(stderr).isZero();
        return stdout;
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record GitFixture(String remoteUri, String commit) {
    }
}
