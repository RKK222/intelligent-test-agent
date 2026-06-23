package com.icbc.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void cloneBranchUsesCurrentUserPrivateKeyAndTargetDirectory() {
        RecordingExecutor executor = new RecordingExecutor("feature_testagent_20260707\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);
        Path repoRoot = tempDir.resolve("appworkspace/20260707/repo_1");

        service.cloneBranch("git@example.com:gcms/gcms.git", "feature_testagent_20260707", repoRoot, "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(new Call(
                List.of(
                        "git",
                        "clone",
                        "--branch",
                        "feature_testagent_20260707",
                        "--single-branch",
                        "git@example.com:gcms/gcms.git",
                        repoRoot.toString()),
                "PRIVATE KEY"));
    }

    @Test
    void createWorktreeCreatesNewBranchFromApplicationBranch() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);
        Path repoRoot = tempDir.resolve("appworkspace/20260707/repo_1");
        Path worktreeRoot = tempDir.resolve("personalworktree/20260707/000857009/repo_1/psn_1");

        service.createWorktree(
                repoRoot,
                worktreeRoot,
                "feature_testagent_20260707_000857009_psn_1",
                "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(new Call(
                List.of(
                        "git",
                        "-C",
                        repoRoot.toString(),
                        "worktree",
                        "add",
                        "-b",
                        "feature_testagent_20260707_000857009_psn_1",
                        worktreeRoot.toString()),
                "PRIVATE KEY"));
    }

    @Test
    void readsCurrentBranchFromLocalRepository() {
        RecordingExecutor executor = new RecordingExecutor("feature_testagent_20260707\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        String branch = service.currentBranch(tempDir);

        assertThat(branch).isEqualTo("feature_testagent_20260707");
        assertThat(executor.calls).containsExactly(new Call(
                List.of("git", "-C", tempDir.toString(), "rev-parse", "--abbrev-ref", "HEAD"),
                null));
    }

    private static final class RecordingExecutor implements GitCommandExecutor {
        private final String stdout;
        private final List<Call> calls = new ArrayList<>();

        private RecordingExecutor(String stdout) {
            this.stdout = stdout;
        }

        @Override
        public GitCommandResult execute(List<String> command, String privateKey, Duration timeout) {
            calls.add(new Call(command, privateKey));
            return new GitCommandResult(0, stdout, stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private record Call(List<String> command, String privateKey) {
    }
}
