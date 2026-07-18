package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 使用临时真实 Git 仓库验证 index 白名单和三方冲突 stage，避免 fake executor 掩盖 Git 语义差异。
 */
class GitWorkspaceServiceRealGitTest {

    @TempDir
    Path tempDir;

    @Test
    void whitelistCommitLeavesResidualStagedFileOutOfCommit() throws Exception {
        Path repo = initializeRepository();
        write(repo, "selected.txt", "base selected\n");
        write(repo, "other.txt", "base other\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        write(repo, "selected.txt", "selected change\n");
        write(repo, "other.txt", "other change\n");
        git(repo, "add", "--", "other.txt");

        GitWorkspaceService service = new GitWorkspaceService();
        service.resetIndexToHead(repo, null);
        service.stageFiles(repo, List.of("selected.txt"), null);
        service.commitStaged(repo, "selected only", null);

        assertThat(git(repo, "show", "--name-only", "--pretty=format:", "HEAD").stdoutText().trim())
                .isEqualTo("selected.txt");
        assertThat(service.statusPorcelain(repo)).contains(" M other.txt");
    }

    @Test
    void applicationAgentProjectionCommitsOnlyWhitelistAndPreservesOtherStagedFiles() throws Exception {
        Path repo = initializeRepository();
        Files.createDirectories(repo.resolve("workspace/.opencode/agents"));
        Files.createDirectories(repo.resolve("workspace/docs"));
        write(repo, "workspace/.opencode/agents/reviewer.md", "version 1\n");
        write(repo, "workspace/docs/design.md", "base docs\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String base = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        git(repo, "checkout", "-b", "feature");
        write(repo, "workspace/.opencode/agents/reviewer.md", "version 2\n");
        write(repo, "workspace/.opencode/opencode.jsonc", "{\"agent\": {}}\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "publish application agent");
        String feature = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        git(repo, "checkout", "-b", "personal", base);
        write(repo, "workspace/docs/design.md", "personal staged docs\n");
        git(repo, "add", "--", "workspace/docs/design.md");

        GitWorkspaceService service = new GitWorkspaceService();
        assertThat(service.listTrackedFiles(repo, "workspace/.opencode"))
                .containsExactly("workspace/.opencode/agents/reviewer.md");
        assertThat(service.listFilesAtCommit(repo, feature, "workspace/.opencode"))
                .containsExactlyInAnyOrder(
                        "workspace/.opencode/agents/reviewer.md",
                        "workspace/.opencode/opencode.jsonc");

        List<String> files = List.of(
                "workspace/.opencode/agents/reviewer.md",
                "workspace/.opencode/opencode.jsonc");
        service.materializeCommitFiles(repo, feature, files, null);
        service.commitFilesOnly(
                repo,
                "同步应用 Agent 配置",
                files,
                null,
                GitCommitIdentity.forPlatformUser("publisher", "AUTH_PUBLISHER"));

        assertThat(git(repo, "show", "--name-only", "--pretty=format:", "HEAD").stdoutText())
                .contains("workspace/.opencode/agents/reviewer.md")
                .contains("workspace/.opencode/opencode.jsonc")
                .doesNotContain("workspace/docs/design.md");
        assertThat(service.statusPorcelain(repo)).contains("M  workspace/docs/design.md");
    }

    @Test
    void commitStagedUsesExplicitIdentityWhenRepositoryHasNoConfiguredIdentity() throws Exception {
        Path repo = initializeRepository();
        git(repo, "config", "--unset-all", "user.name");
        git(repo, "config", "--unset-all", "user.email");
        write(repo, "identity.txt", "committed by current user\n");
        git(repo, "add", "--all");

        GitWorkspaceService service = new GitWorkspaceService();
        service.commitStaged(
                repo,
                "identity commit",
                null,
                GitCommitIdentity.forPlatformUser("alice", "AUTH_ALICE"));

        assertThat(git(repo, "show", "-s", "--format=%an <%ae>", "HEAD").stdoutText().trim())
                .isEqualTo("alice <AUTH_ALICE@testagent.local>");
    }

    @Test
    void exposesBaseCurrentAndIncomingForRealMergeConflict() throws Exception {
        Path repo = initializeRepository();
        write(repo, "conflict.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        git(repo, "checkout", "-b", "application");
        write(repo, "conflict.txt", "incoming\n");
        git(repo, "commit", "-am", "application change");
        git(repo, "checkout", "main");
        write(repo, "conflict.txt", "current\n");
        git(repo, "commit", "-am", "personal change");
        try {
            git(repo, "merge", "--no-ff", "application");
        } catch (RuntimeException ignored) {
            // 预期由真实 Git 生成冲突 index。
        }

        GitWorkspaceService service = new GitWorkspaceService();
        assertThat(service.conflictStages(repo, "conflict.txt")).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(service.conflictStageContent(repo, 1, "conflict.txt")).isEqualTo("base\n");
        assertThat(service.conflictStageContent(repo, 2, "conflict.txt")).isEqualTo("current\n");
        assertThat(service.conflictStageContent(repo, 3, "conflict.txt")).isEqualTo("incoming\n");
        assertThat(service.isMergeInProgress(repo)).isTrue();
    }

    @Test
    void resolvesChineseAddDeleteConflictsWithNativeCurrentAndIncomingStages() throws Exception {
        Path currentRepo = createAddDeleteConflict("current");
        GitWorkspaceService service = new GitWorkspaceService();

        assertThat(service.conflictPaths(currentRepo))
                .containsExactlyInAnyOrder("中文/个人新增.txt", "中文/远程新增.txt");
        service.resolveAllConflicts(
                currentRepo,
                GitWorkspaceService.ConflictResolutionSide.CURRENT,
                null);

        assertThat(service.conflictPaths(currentRepo)).isEmpty();
        assertThat(Files.readString(currentRepo.resolve("中文/个人新增.txt"))).isEqualTo("current\n");
        assertThat(currentRepo.resolve("中文/远程新增.txt")).doesNotExist();

        Path incomingRepo = createAddDeleteConflict("incoming");
        service.resolveAllConflicts(
                incomingRepo,
                GitWorkspaceService.ConflictResolutionSide.INCOMING,
                null);

        assertThat(service.conflictPaths(incomingRepo)).isEmpty();
        assertThat(incomingRepo.resolve("中文/个人新增.txt")).doesNotExist();
        assertThat(Files.readString(incomingRepo.resolve("中文/远程新增.txt"))).isEqualTo("incoming\n");
    }

    @Test
    void fetchesRemoteAgentCommitIntoPersonalAndSharedRepositories() throws Exception {
        Path remote = tempDir.resolve("public-agent.git");
        Files.createDirectories(remote);
        git(remote, "init", "--bare");

        Path writer = tempDir.resolve("remote-writer");
        git(tempDir, "clone", remote.toString(), writer.toString());
        git(writer, "config", "user.name", "Remote Admin");
        git(writer, "config", "user.email", "remote-admin@example.invalid");
        git(writer, "checkout", "-b", "main");
        Files.createDirectories(writer.resolve("opencode/agents"));
        write(writer, "opencode/agents/remote-review.md", "version 1\n");
        git(writer, "add", "--all");
        git(writer, "commit", "-m", "initial public agent");
        git(writer, "push", "-u", "origin", "main");

        Path personal = tempDir.resolve("public-admin-worktree");
        Path shared = tempDir.resolve("public-runtime");
        git(tempDir, "clone", "--branch", "main", remote.toString(), personal.toString());
        git(tempDir, "clone", "--branch", "main", remote.toString(), shared.toString());

        write(writer, "opencode/agents/remote-review.md", "version 2 from remote\n");
        git(writer, "commit", "-am", "update public agent remotely");
        git(writer, "push", "origin", "main");

        GitWorkspaceService service = new GitWorkspaceService();
        service.fetch(personal, null);
        service.mergeBranch(
                personal,
                "origin/main",
                null,
                GitCommitIdentity.forPlatformUser("admin", "AUTH_ADMIN"));
        service.fetch(shared, null);
        String remoteCommit = git(shared, "rev-parse", "origin/main").stdoutText().trim();
        service.resetHardToCommit(shared, remoteCommit);

        assertThat(Files.readString(personal.resolve("opencode/agents/remote-review.md")))
                .isEqualTo("version 2 from remote\n");
        assertThat(Files.readString(shared.resolve("opencode/agents/remote-review.md")))
                .isEqualTo("version 2 from remote\n");
        assertThat(git(shared, "rev-parse", "HEAD").stdoutText().trim()).isEqualTo(remoteCommit);
    }

    @Test
    void resolvesLatestCommitOfExactRemoteBranch() throws Exception {
        Path remote = tempDir.resolve("reference-assets.git");
        Files.createDirectories(remote);
        git(remote, "init", "--bare");

        Path writer = tempDir.resolve("reference-writer");
        git(tempDir, "clone", remote.toString(), writer.toString());
        git(writer, "config", "user.name", "Reference Admin");
        git(writer, "config", "user.email", "reference-admin@example.invalid");
        git(writer, "checkout", "-b", "main");
        write(writer, "README.md", "version 1\n");
        git(writer, "add", "--all");
        git(writer, "commit", "-m", "initial reference asset");
        git(writer, "push", "-u", "origin", "main");
        write(writer, "README.md", "version 2\n");
        git(writer, "commit", "-am", "update reference asset");
        git(writer, "push", "origin", "main");
        String expectedCommit = git(writer, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();

        assertThat(service.resolveRemoteBranchCommit(remote.toString(), "main", null))
                .isEqualTo(expectedCommit);
    }

    @Test
    void safelyChecksOutExistingTargetBranchOnlyWhenItCanFastForward() throws Exception {
        Path repo = initializeRepository();
        write(repo, "base.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        String releaseBeforeReset = git(repo, "rev-parse", "HEAD").stdoutText().trim();
        git(repo, "branch", "release");
        write(repo, "release.txt", "release\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "release target");
        String target = git(repo, "rev-parse", "HEAD").stdoutText().trim();

        GitWorkspaceService service = new GitWorkspaceService();
        service.checkoutBranchForFixedCommit(repo, "release", target, null);

        assertThat(service.currentBranch(repo)).isEqualTo("release");
        assertThat(service.headCommit(repo)).isEqualTo(releaseBeforeReset);
        service.resetHardToCommit(repo, target);
        assertThat(service.headCommit(repo)).isEqualTo(target);

        git(repo, "checkout", "main");
        git(repo, "branch", "-D", "release");
        git(repo, "checkout", "--orphan", "release");
        git(repo, "rm", "-rf", ".");
        write(repo, "diverged.txt", "diverged\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "diverged release");
        git(repo, "checkout", "main");

        assertThatThrownBy(() -> service.checkoutBranchForFixedCommit(repo, "release", target, null))
                .isInstanceOf(PlatformException.class);
        assertThat(service.currentBranch(repo)).isEqualTo("main");
    }

    @Test
    void readOnlyCleanCheckDoesNotRefreshOrRewriteIndex() throws Exception {
        Path repo = initializeRepository();
        write(repo, "tracked.txt", "base\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "base");
        write(repo, "tracked.txt", "dirty\n");
        write(repo, "untracked.txt", "untracked\n");
        Path index = repo.resolve(".git/index");
        FileTime preservedTime = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"));
        Files.setLastModifiedTime(index, preservedTime);
        byte[] before = Files.readAllBytes(index);

        GitCommandExecutor.startRecording();
        boolean clean;
        List<String> commands;
        try {
            clean = new GitWorkspaceService().isWorktreeCleanReadOnly(repo);
        } finally {
            commands = GitCommandExecutor.stopRecording();
        }

        assertThat(clean).isFalse();
        assertThat(Files.readAllBytes(index)).containsExactly(before);
        assertThat(Files.getLastModifiedTime(index)).isEqualTo(preservedTime);
        assertThat(commands).singleElement().satisfies(command -> {
            assertThat(command).contains("git --no-optional-locks");
            assertThat(command).contains("status --porcelain --untracked-files=all");
            assertThat(command).contains("core.untrackedCache=false");
            assertThat(command).contains("core.fsmonitor=false");
        });
    }

    private Path createAddDeleteConflict(String suffix) throws Exception {
        Path repo = tempDir.resolve("repo-" + suffix);
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "Test Agent");
        git(repo, "config", "user.email", "test-agent@example.invalid");
        Files.createDirectories(repo.resolve("中文"));
        write(repo, "中文/个人新增.txt", "base-current\n");
        write(repo, "中文/远程新增.txt", "base-incoming\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "conflict base");
        git(repo, "checkout", "-b", "application");
        Files.delete(repo.resolve("中文/个人新增.txt"));
        write(repo, "中文/远程新增.txt", "incoming\n");
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "application delete and modify");
        git(repo, "checkout", "main");
        write(repo, "中文/个人新增.txt", "current\n");
        Files.delete(repo.resolve("中文/远程新增.txt"));
        git(repo, "add", "--all");
        git(repo, "commit", "-m", "personal modify and delete");
        try {
            git(repo, "merge", "--no-ff", "application");
        } catch (RuntimeException ignored) {
            // 预期生成 UD/DU 冲突。
        }
        return repo;
    }

    private Path initializeRepository() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "Test Agent");
        git(repo, "config", "user.email", "test-agent@example.invalid");
        return repo;
    }

    private void write(Path repo, String file, String content) throws Exception {
        Files.writeString(repo.resolve(file), content, StandardCharsets.UTF_8);
    }

    private GitCommandResult git(Path repo, String... args) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repo.toString());
        command.addAll(List.of(args));
        return new ProcessGitCommandExecutor().execute(List.copyOf(command), null, Duration.ofSeconds(30));
    }
}
