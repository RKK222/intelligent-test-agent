package com.icbc.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    void createWorktreeReusesExistingBranchWhenNewBranchAlreadyExists() {
        RecordingExecutor executor = new RecordingExecutor("");
        executor.failFirst = true;
        GitWorkspaceService service = new GitWorkspaceService(executor);
        Path repoRoot = tempDir.resolve("appworkspace/20260707/repo_1");
        Path worktreeRoot = tempDir.resolve("personalworktree/20260707/000857009/repo_1/default");

        service.createWorktreeReusingBranch(
                repoRoot,
                worktreeRoot,
                "feature_testagent_20260707_000857009_default",
                "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(
                new Call(
                        List.of(
                                "git",
                                "-C",
                                repoRoot.toString(),
                                "worktree",
                                "add",
                                "-b",
                                "feature_testagent_20260707_000857009_default",
                                worktreeRoot.toString()),
                        "PRIVATE KEY"),
                new Call(
                        List.of(
                        "git",
                        "-C",
                        repoRoot.toString(),
                        "worktree",
                        "prune"),
                "PRIVATE KEY"),
                new Call(
                        List.of(
                                "git",
                                "-C",
                                repoRoot.toString(),
                        "worktree",
                        "add",
                        worktreeRoot.toString(),
                        "feature_testagent_20260707_000857009_default"),
                        "PRIVATE KEY"));
    }

    @Test
    void createWorktreeReusesValidTargetWorktreeWhenGitReportsBranchConflict() throws Exception {
        RecordingExecutor executor = new RecordingExecutor("");
        executor.failCalls.add(1);
        executor.failCalls.add(3);
        executor.stdoutByCall.put(4, "worktree " + tempDir.resolve("personalworktree/default") + "\nbranch refs/heads/feature_default\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);
        Path repoRoot = tempDir.resolve("appworkspace/repo_1");
        Path worktreeRoot = tempDir.resolve("personalworktree/default");
        java.nio.file.Files.createDirectories(worktreeRoot);

        service.createWorktreeReusingBranch(repoRoot, worktreeRoot, "feature_default", "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "add", "-b", "feature_default", worktreeRoot.toString()), "PRIVATE KEY"),
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "prune"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "add", worktreeRoot.toString(), "feature_default"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "list", "--porcelain"), null));
    }

    @Test
    void createWorktreeMovesSameBranchFromLegacyPathToTargetWhenBranchAlreadyCheckedOutElsewhere() {
        RecordingExecutor executor = new RecordingExecutor("");
        executor.failCalls.add(1);
        executor.failCalls.add(3);
        Path repoRoot = tempDir.resolve("appworkspace/repo_1");
        Path legacyRoot = tempDir.resolve("personalworktree/20260707/usr_1/gcms/default");
        Path targetRoot = tempDir.resolve("personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default");
        executor.stdoutByCall.put(4, "worktree " + legacyRoot + "\nbranch refs/heads/feature_testagent_20260707_usr_1_default\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.createWorktreeReusingBranch(repoRoot, targetRoot, "feature_testagent_20260707_usr_1_default", "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "add", "-b", "feature_testagent_20260707_usr_1_default", targetRoot.toString()), "PRIVATE KEY"),
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "prune"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "add", targetRoot.toString(), "feature_testagent_20260707_usr_1_default"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "list", "--porcelain"), null),
                new Call(List.of("git", "-C", repoRoot.toString(), "worktree", "move", legacyRoot.toAbsolutePath().normalize().toString(), targetRoot.toString()), "PRIVATE KEY"));
    }

    @Test
    void statusAndDiffDisableGitPathQuotingForChinesePaths() {
        RecordingExecutor executor = new RecordingExecutor(" M F-GCMS/workspace/设计.md\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        assertThat(service.statusPorcelain(tempDir)).contains("设计.md");
        service.diff(tempDir, "F-GCMS/workspace/设计.md", false);

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "status", "--porcelain"), null),
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "diff", "--", "F-GCMS/workspace/设计.md"), null));
    }

    @Test
    void unquotesPorcelainPathsWithSpacesAndUtf8OctalEscapes() {
        GitWorkspaceService service = new GitWorkspaceService(new RecordingExecutor(""));

        assertThat(service.unquotePorcelainPath("\"F-COSS/workspace/02-设计/Test Material.md\""))
                .isEqualTo("F-COSS/workspace/02-设计/Test Material.md");
        assertThat(service.unquotePorcelainPath("\"F-COSS/workspace/02-\\350\\256\\276\\350\\256\\241/Test.md\""))
                .isEqualTo("F-COSS/workspace/02-设计/Test.md");
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

    @Test
    void pullsCurrentBranchWithFastForwardOnly() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.pullFastForward(tempDir, "feature_testagent_20260707", "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(new Call(
                List.of("git", "-C", tempDir.toString(), "pull", "--ff-only", "origin", "feature_testagent_20260707"),
                "PRIVATE KEY"));
    }

    @Test
    void fetchesAndResetsToExactCommitForReplicaSync() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.fetch(tempDir, "PRIVATE KEY");
        service.resetHardToCommit(tempDir, "abc123def456");

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-C", tempDir.toString(), "fetch", "origin"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", tempDir.toString(), "reset", "--hard", "abc123def456"), null));
    }

    @Test
    void reportsCleanWorktreeFromPorcelainStatus() {
        RecordingExecutor executor = new RecordingExecutor("\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        assertThat(service.isWorktreeClean(tempDir)).isTrue();

        assertThat(executor.calls).containsExactly(new Call(
                List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "status", "--porcelain"),
                null));
    }

    private static final class RecordingExecutor implements GitCommandExecutor {
        private final String stdout;
        private final List<Call> calls = new ArrayList<>();
        private final List<Integer> failCalls = new ArrayList<>();
        private final Map<Integer, String> stdoutByCall = new java.util.HashMap<>();
        private boolean failFirst;

        private RecordingExecutor(String stdout) {
            this.stdout = stdout;
        }

        @Override
        public GitCommandResult execute(List<String> command, String privateKey, Duration timeout) {
            calls.add(new Call(command, privateKey));
            if ((failFirst && calls.size() == 1) || failCalls.contains(calls.size())) {
                throw new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        "Git worktree 创建冲突",
                        Map.of("gitFailureType", "WORKTREE_CONFLICT"));
            }
            String currentStdout = stdoutByCall.getOrDefault(calls.size(), stdout);
            return new GitCommandResult(0, currentStdout, currentStdout.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private record Call(List<String> command, String privateKey) {
    }
}
