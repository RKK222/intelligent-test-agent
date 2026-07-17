package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
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
    void classifiesNonFastForwardPushAsRemoteRejected() {
        GitCommandFailure failure = GitCommandFailureClassifier.classify(
                List.of("git", "-C", tempDir.toString(), "push", "origin", "master"),
                """
                        ! [rejected]        master -> master (fetch first)
                        error: failed to push some refs to 'gitee.com:demo/repo.git'
                        hint: Updates were rejected because the remote contains work that you do not have locally.
                        """);

        assertThat(failure.type()).isEqualTo("REMOTE_REJECTED");
        assertThat(failure.message()).isEqualTo("Git 远端拒绝推送");
        assertThat(failure.hint()).contains("先拉取远端最新提交");
    }

    @Test
    void pushRefPublishesPersonalBranchToPublicTargetWithoutForce() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.pushRef(tempDir, "public-usr_admin", "main", "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(new Call(
                List.of(
                        "git",
                        "-C",
                        tempDir.toString(),
                        "push",
                        "origin",
                        "public-usr_admin:main"),
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
                new Call(List.of(
                        "git",
                        "-c",
                        "core.quotepath=false",
                        "-C",
                        tempDir.toString(),
                        "status",
                        "--porcelain",
                        "--untracked-files=all"), null),
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "diff", "--", "F-GCMS/workspace/设计.md"), null));
    }

    @Test
    void statusWithPathspecExpandsUntrackedDirectoriesToIndividualFiles() {
        RecordingExecutor executor = new RecordingExecutor("?? F-COSS/workspace/测试设计/案例一.md\n"
                + "?? F-COSS/workspace/测试设计/案例二.md\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        assertThat(service.statusPorcelain(tempDir, "F-COSS/workspace")).hasLineCount(2);

        assertThat(executor.calls).containsExactly(new Call(List.of(
                "git",
                "-c",
                "core.quotepath=false",
                "-C",
                tempDir.toString(),
                "status",
                "--porcelain",
                "--untracked-files=all",
                "--",
                "F-COSS/workspace"), null));
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
    void parseStatusPorcelainDecodesQuotedRenameAndUtf8EscapedPath() {
        GitWorkspaceService service = new GitWorkspaceService(new RecordingExecutor(""));
        String porcelain = "R  \"old agent.md\" -> \"F-COSS/workspace/.opencode/agents/review rule.md\"\n"
                + " M \"F-COSS/workspace/02-\\350\\256\\276\\350\\256\\241/Test Material.md\"\n";

        List<GitWorkspaceService.GitStatusEntry> entries = service.parseStatusPorcelain(porcelain);

        assertThat(entries).extracting(GitWorkspaceService.GitStatusEntry::path)
                .containsExactly(
                        "F-COSS/workspace/.opencode/agents/review rule.md",
                        "F-COSS/workspace/02-设计/Test Material.md");
        assertThat(entries.get(0).rawStatus()).isEqualTo("R ");
        assertThat(entries.get(0).status()).isEqualTo("renamed");
        assertThat(entries.get(0).staged()).isTrue();
    }

    @Test
    void parseStatusPorcelainMarksUnmergedEntriesAsConflict() {
        GitWorkspaceService service = new GitWorkspaceService(new RecordingExecutor(""));

        List<GitWorkspaceService.GitStatusEntry> entries = service.parseStatusPorcelain("AU workspace/docs/conflict.md\n");

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.path()).isEqualTo("workspace/docs/conflict.md");
            assertThat(entry.rawStatus()).isEqualTo("AU");
            assertThat(entry.status()).isEqualTo("conflict");
            assertThat(entry.unmerged()).isTrue();
            assertThat(entry.staged()).isTrue();
        });
    }

    @Test
    void discardFilesRestoresTrackedAndCleansStagedAndUntrackedNewFiles() {
        RecordingExecutor executor = new RecordingExecutor(
                " M opencode/agents/review.md\nA  opencode/agents/new.md\n?? opencode/skills/new/SKILL.md\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.discardFiles(
                tempDir,
                List.of(
                        "opencode/agents/review.md",
                        "opencode/agents/new.md",
                        "opencode/skills/new/SKILL.md"),
                "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "status", "--porcelain", "--untracked-files=all"), null),
                new Call(List.of("git", "-C", tempDir.toString(), "restore", "--staged", "--worktree", "--", "opencode/agents/review.md"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", tempDir.toString(), "restore", "--staged", "--", "opencode/agents/new.md"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", tempDir.toString(), "clean", "-f", "--", "opencode/agents/new.md", "opencode/skills/new/SKILL.md"), "PRIVATE KEY"));
    }

    @Test
    void discardFilesRejectsConflictPathsBeforeMutatingGit() {
        RecordingExecutor executor = new RecordingExecutor("UU opencode/agents/conflict.md\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        assertThatThrownBy(() -> service.discardFiles(
                tempDir,
                List.of("opencode/agents/conflict.md"),
                "PRIVATE KEY"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("合并编辑器");

        assertThat(executor.calls).singleElement().satisfies(call ->
                assertThat(call.command()).containsExactly(
                        "git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "status", "--porcelain", "--untracked-files=all"));
    }

    @Test
    void collectDiffFilesMergesStagedAndUnstagedPatchStats() {
        RecordingExecutor executor = new RecordingExecutor("");
        executor.stdoutByCall.put(1, "diff --git a/src/App.java b/src/App.java\n--- a/src/App.java\n+++ b/src/App.java\n@@ -1 +1 @@\n-old\n+staged\n");
        executor.stdoutByCall.put(2, "diff --git a/src/App.java b/src/App.java\n--- a/src/App.java\n+++ b/src/App.java\n@@ -2 +2,2 @@\n-old2\n+unstaged\n+more\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        List<GitWorkspaceService.GitDiffFile> files = service.collectDiffFiles(tempDir, "MM src/App.java\n");

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("src/App.java");
            assertThat(file.rawStatus()).isEqualTo("MM");
            assertThat(file.status()).isEqualTo("modified");
            assertThat(file.staged()).isTrue();
            assertThat(file.patch()).contains("+staged").contains("+unstaged").contains("+more");
            assertThat(file.additions()).isEqualTo(3);
            assertThat(file.deletions()).isEqualTo(2);
        });
        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "diff", "--cached"), null),
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "diff"), null));
    }

    @Test
    void collectDiffFilesBuildsPseudoPatchForUntrackedFileWithoutRunningDiff() throws Exception {
        java.nio.file.Files.createDirectories(tempDir.resolve("notes"));
        java.nio.file.Files.writeString(tempDir.resolve("notes/new.md"), "line1\nline2\n");
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        List<GitWorkspaceService.GitDiffFile> files = service.collectDiffFiles(tempDir, "?? notes/new.md\n");

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("notes/new.md");
            assertThat(file.status()).isEqualTo("untracked");
            assertThat(file.staged()).isFalse();
            assertThat(file.patch()).contains("--- /dev/null").contains("+++ b/notes/new.md").contains("+line1").contains("+line2");
            assertThat(file.additions()).isEqualTo(2);
            assertThat(file.deletions()).isZero();
        });
        assertThat(executor.calls).isEmpty();
    }

    @Test
    void collectDiffFilesUsesExpectedDiffModeForStagedAddedAndUnstagedDeletedFiles() {
        RecordingExecutor executor = new RecordingExecutor("");
        executor.stdoutByCall.put(1, "diff --git a/src/New.java b/src/New.java\n--- /dev/null\n+++ b/src/New.java\n@@ -0,0 +1,2 @@\n+one\n+two\n");
        executor.stdoutByCall.put(2, "diff --git a/src/Old.java b/src/Old.java\n--- a/src/Old.java\n+++ /dev/null\n@@ -1 +0,0 @@\n-old\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        List<GitWorkspaceService.GitDiffFile> files = service.collectDiffFiles(tempDir, "A  src/New.java\n D src/Old.java\n");

        assertThat(files).extracting(GitWorkspaceService.GitDiffFile::status)
                .containsExactly("added", "deleted");
        assertThat(files.get(0).additions()).isEqualTo(2);
        assertThat(files.get(0).deletions()).isZero();
        assertThat(files.get(1).additions()).isZero();
        assertThat(files.get(1).deletions()).isEqualTo(1);
        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "diff", "--cached"), null),
                new Call(List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(), "diff"), null));
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
    void resolvesCommitAndChecksAncestryForUncertainPushVerification() {
        RecordingExecutor executor = new RecordingExecutor("abc123\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        assertThat(service.resolveCommit(tempDir, "origin/main")).isEqualTo("abc123");
        assertThat(service.isAncestor(tempDir, "abc123", "origin/main")).isTrue();

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-C", tempDir.toString(), "rev-parse", "origin/main"), null),
                new Call(List.of("git", "-C", tempDir.toString(), "merge-base", "--is-ancestor",
                        "abc123", "origin/main"), null));
    }

    @Test
    void ancestryOnlyMapsGitExitCodeOneToFalse() {
        GitWorkspaceService notAncestor = new GitWorkspaceService((command, privateKey, timeout) -> {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "not ancestor", Map.of("exitCode", 1));
        });
        GitWorkspaceService brokenRepository = new GitWorkspaceService((command, privateKey, timeout) -> {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "bad revision", Map.of("exitCode", 128));
        });

        assertThat(notAncestor.isAncestor(tempDir, "abc123", "origin/main")).isFalse();
        assertThatThrownBy(() -> brokenRepository.isAncestor(tempDir, "abc123", "origin/main"))
                .isInstanceOf(PlatformException.class);
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

    @Test
    void conflictPathsListsUnmergedFilesAndFiltersBlankLines() {
        RecordingExecutor executor = new RecordingExecutor("src/Main.java\nREADME.md\n\n");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        assertThat(service.conflictPaths(tempDir)).containsExactly("src/Main.java", "README.md");

        assertThat(executor.calls).containsExactly(new Call(
                List.of("git", "-c", "core.quotepath=false", "-C", tempDir.toString(),
                        "diff", "--name-only", "--diff-filter", "U"),
                null));
    }

    @Test
    void abortsInProgressMergeWithPrivateKey() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.abortMerge(tempDir, "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(new Call(
                List.of("git", "-C", tempDir.toString(), "merge", "--abort"),
                "PRIVATE KEY"));
    }

    @Test
    void abortMergeRunsGitMergeAbortInRepository() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.abortMerge(tempDir);

        assertThat(executor.calls).containsExactly(new Call(
                List.of("git", "-C", tempDir.toString(), "merge", "--abort"),
                null));
    }

    @Test
    void resetsOnlyIndexBeforeStagingPublishWhitelist() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.resetIndexToHead(tempDir, "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(new Call(
                List.of("git", "-C", tempDir.toString(), "reset", "--mixed", "HEAD"),
                "PRIVATE KEY"));
    }

    @Test
    void materializesExistingCommitFilesIntoFeatureWorktree() {
        RecordingExecutor executor = new RecordingExecutor("");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.materializeCommitFiles(tempDir, "personal_head", List.of("src/App.java", "README.md"), "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-C", tempDir.toString(), "cat-file", "-e", "personal_head:src/App.java"), null),
                new Call(List.of("git", "-C", tempDir.toString(), "cat-file", "-e", "personal_head:README.md"), null),
                new Call(List.of("git", "-C", tempDir.toString(), "checkout", "personal_head", "--", "src/App.java", "README.md"), "PRIVATE KEY"));
    }

    @Test
    void materializesMissingCommitFilesAsTargetDeletes() {
        RecordingExecutor executor = new RecordingExecutor("");
        executor.failCalls.add(1);
        GitWorkspaceService service = new GitWorkspaceService(executor);

        service.materializeCommitFiles(tempDir, "personal_head", List.of("src/Removed.java", "README.md"), "PRIVATE KEY");

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-C", tempDir.toString(), "cat-file", "-e", "personal_head:src/Removed.java"), null),
                new Call(List.of("git", "-C", tempDir.toString(), "cat-file", "-e", "personal_head:README.md"), null),
                new Call(List.of("git", "-C", tempDir.toString(), "checkout", "personal_head", "--", "README.md"), "PRIVATE KEY"),
                new Call(List.of("git", "-C", tempDir.toString(), "rm", "-f", "--", "src/Removed.java"), "PRIVATE KEY"));
    }

    @Test
    void readsAvailableConflictStagesAndSelectedBlob() {
        RecordingExecutor executor = new RecordingExecutor(
                "100644 aaa 1\tsrc/Login.java\n100644 bbb 2\tsrc/Login.java\n100644 ccc 3\tsrc/Login.java\n");
        executor.stdoutByCall.put(2, "current-content");
        GitWorkspaceService service = new GitWorkspaceService(executor);

        assertThat(service.conflictStages(tempDir, "src/Login.java")).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(service.conflictStageContent(tempDir, 2, "src/Login.java")).isEqualTo("current-content");

        assertThat(executor.calls).containsExactly(
                new Call(List.of("git", "-C", tempDir.toString(), "ls-files", "--unmerged", "--stage", "--", "src/Login.java"), null),
                new Call(List.of("git", "-C", tempDir.toString(), "show", ":2:src/Login.java"), null));
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
