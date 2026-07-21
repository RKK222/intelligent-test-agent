package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitPublishWorkflowTest {

    private static final Path REPO_ROOT = Path.of("/tmp/repo");
    private static final String PRIVATE_KEY = "PRIVATE KEY";
    private static final GitCommitIdentity COMMIT_IDENTITY =
            GitCommitIdentity.forPlatformUser("test-user", "AUTH_TEST");

    @Test
    void directPublishPullsFastForwardBeforePushAndReturnsHeadCommit() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        GitPublishWorkflow workflow = new GitPublishWorkflow(git);

        GitPublishWorkflow.PublishResult result = workflow.publishDirectBranch(REPO_ROOT, "main", false, PRIVATE_KEY);

        assertThat(result.hasConflicts()).isFalse();
        assertThat(result.headCommit()).isEqualTo("commit_after_push");
        assertThat(git.calls).containsExactly(
                "clean:/tmp/repo",
                "fetch:/tmp/repo",
                "pull:/tmp/repo:main",
                "push:/tmp/repo:main:false",
                "head:/tmp/repo");
    }

    @Test
    void mergedPublishMergesSourceBranchBeforePushAndReturnsHeadCommit() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        GitPublishWorkflow workflow = new GitPublishWorkflow(git);

        GitPublishWorkflow.PublishResult result = workflow.publishMergedBranch(
                REPO_ROOT,
                "main",
                "feature/review",
                false,
                PRIVATE_KEY,
                COMMIT_IDENTITY);

        assertThat(result.hasConflicts()).isFalse();
        assertThat(result.headCommit()).isEqualTo("commit_after_push");
        assertThat(git.mergeIdentity).isEqualTo(COMMIT_IDENTITY);
        assertThat(git.calls).containsExactly(
                "clean:/tmp/repo",
                "fetch:/tmp/repo",
                "pull:/tmp/repo:main",
                "merge:/tmp/repo:feature/review",
                "push:/tmp/repo:main:false",
                "head:/tmp/repo");
    }

    @Test
    void mergedPublishCollectsConflictsAbortsMergeAndSkipsPush() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.failMerge = true;
        git.conflictFiles = List.of("src/Main.java", "README.md");
        GitPublishWorkflow workflow = new GitPublishWorkflow(git);

        GitPublishWorkflow.PublishResult result = workflow.publishMergedBranch(
                REPO_ROOT,
                "main",
                "feature/review",
                false,
                PRIVATE_KEY,
                COMMIT_IDENTITY);

        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.conflictFiles()).containsExactly("src/Main.java", "README.md");
        assertThat(result.headCommit()).isNull();
        assertThat(git.calls).containsExactly(
                "clean:/tmp/repo",
                "fetch:/tmp/repo",
                "pull:/tmp/repo:main",
                "merge:/tmp/repo:feature/review",
                "conflicts:/tmp/repo",
                "abort:/tmp/repo");
    }

    @Test
    void mergedPublishThrowsConflictWhenAbortMergeFails() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.failMerge = true;
        git.failAbort = true;
        git.conflictFiles = List.of("src/Main.java");
        GitPublishWorkflow workflow = new GitPublishWorkflow(git);

        assertThatThrownBy(() -> workflow.publishMergedBranch(
                REPO_ROOT,
                "main",
                "feature/review",
                false,
                PRIVATE_KEY,
                COMMIT_IDENTITY))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("abortFailed", true);
                    assertThat(exception.details()).containsEntry("conflictFiles", List.of("src/Main.java"));
                });
    }

    @Test
    void syncFilesThenPushPullsBeforeCopyingThenCommitsAndPushes() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        List<String> copied = new ArrayList<>();
        GitPublishWorkflow workflow = new GitPublishWorkflow(git);

        GitPublishWorkflow.PublishResult result = workflow.syncFilesThenPush(
                REPO_ROOT,
                "main",
                List.of("src/App.java"),
                "sync files",
                true,
                PRIVATE_KEY,
                COMMIT_IDENTITY,
                () -> copied.add("copied"));

        assertThat(result.headCommit()).isEqualTo("commit_after_push");
        assertThat(copied).containsExactly("copied");
        assertThat(git.commitIdentity).isEqualTo(COMMIT_IDENTITY);
        assertThat(git.calls).containsExactly(
                "clean:/tmp/repo",
                "fetch:/tmp/repo",
                "pull:/tmp/repo:main",
                "commit:/tmp/repo:src/App.java:sync files",
                "push:/tmp/repo:main:true",
                "head:/tmp/repo");
    }

    @Test
    void syncFilesThenPushRejectsDirtyTargetBeforeCopyingFiles() {
        RecordingGitWorkspaceService git = new RecordingGitWorkspaceService();
        git.clean = false;
        List<String> copied = new ArrayList<>();
        GitPublishWorkflow workflow = new GitPublishWorkflow(git);

        assertThatThrownBy(() -> workflow.syncFilesThenPush(
                REPO_ROOT,
                "main",
                List.of("src/App.java"),
                "sync files",
                false,
                PRIVATE_KEY,
                COMMIT_IDENTITY,
                () -> copied.add("copied")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(copied).isEmpty();
        assertThat(git.calls).containsExactly("clean:/tmp/repo");
    }

    private static final class RecordingGitWorkspaceService extends GitWorkspaceService {
        private final List<String> calls = new ArrayList<>();
        private boolean clean = true;
        private boolean failMerge;
        private boolean failAbort;
        private List<String> conflictFiles = List.of();
        private GitCommitIdentity mergeIdentity;
        private GitCommitIdentity commitIdentity;

        @Override
        public boolean isWorktreeClean(Path repoRoot) {
            calls.add("clean:" + repoRoot);
            return clean;
        }

        @Override
        public void fetch(Path repoRoot, String privateKey) {
            calls.add("fetch:" + repoRoot);
        }

        @Override
        public void pullFastForward(Path repoRoot, String branch, String privateKey) {
            calls.add("pull:" + repoRoot + ":" + branch);
        }

        @Override
        public void mergeBranch(
                Path repoRoot,
                String branch,
                String privateKey,
                GitCommitIdentity identity) {
            this.mergeIdentity = identity;
            calls.add("merge:" + repoRoot + ":" + branch);
            if (failMerge) {
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "合并冲突", Map.of());
            }
        }

        @Override
        public List<String> conflictPaths(Path repoRoot) {
            calls.add("conflicts:" + repoRoot);
            return conflictFiles;
        }

        @Override
        public void abortMerge(Path repoRoot) {
            calls.add("abort:" + repoRoot);
            if (failAbort) {
                throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "终止合并失败", Map.of());
            }
        }

        @Override
        public void commitFiles(
                Path repoRoot,
                List<String> files,
                String message,
                String privateKey,
                GitCommitIdentity identity) {
            this.commitIdentity = identity;
            calls.add("commit:" + repoRoot + ":" + String.join(",", files) + ":" + message);
        }

        @Override
        public void push(Path repoRoot, String branch, boolean force, String privateKey) {
            calls.add("push:" + repoRoot + ":" + branch + ":" + force);
        }

        @Override
        public String headCommit(Path repoRoot) {
            calls.add("head:" + repoRoot);
            return "commit_after_push";
        }
    }
}
