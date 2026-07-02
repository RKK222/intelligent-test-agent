package com.icbc.testagent.common.git;

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
