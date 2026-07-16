package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
