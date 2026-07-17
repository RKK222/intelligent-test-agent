package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 高风险 Git 发布业务流程，统一收口 clean、pull、merge、push、headCommit 和冲突清理语义。
 */
public class GitPublishWorkflow {

    private final GitWorkspaceService gitWorkspaceService;

    public GitPublishWorkflow(GitWorkspaceService gitWorkspaceService) {
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService, "gitWorkspaceService must not be null");
    }

    /**
     * 发布当前仓库分支：先确认工作树干净并快进拉取远端，再 push 当前分支。
     */
    public PublishResult publishDirectBranch(Path repoRoot, String branch, boolean force, String privateKey) {
        ensureClean(repoRoot);
        gitWorkspaceService.fetch(repoRoot, privateKey);
        gitWorkspaceService.pullFastForward(repoRoot, branch, privateKey);
        gitWorkspaceService.push(repoRoot, branch, force, privateKey);
        return PublishResult.succeeded(gitWorkspaceService.headCommit(repoRoot));
    }

    /**
     * 将 sourceBranch 合并进 targetBranch 并发布；合并冲突会收集文件列表并执行 merge --abort。
     */
    public PublishResult publishMergedBranch(
            Path repoRoot,
            String targetBranch,
            String sourceBranch,
            boolean force,
            String privateKey) {
        return publishMergedBranch(repoRoot, targetBranch, sourceBranch, force, privateKey, null);
    }

    /**
     * 合并并发布当前操作人的分支；merge 产生提交时使用传入的 Git 身份。
     */
    public PublishResult publishMergedBranch(
            Path repoRoot,
            String targetBranch,
            String sourceBranch,
            boolean force,
            String privateKey,
            GitCommitIdentity identity) {
        ensureClean(repoRoot);
        gitWorkspaceService.fetch(repoRoot, privateKey);
        gitWorkspaceService.pullFastForward(repoRoot, targetBranch, privateKey);
        try {
            gitWorkspaceService.mergeBranch(repoRoot, sourceBranch, privateKey, identity);
        } catch (PlatformException mergeException) {
            return handleMergeFailure(repoRoot, mergeException);
        }
        gitWorkspaceService.push(repoRoot, targetBranch, force, privateKey);
        return PublishResult.succeeded(gitWorkspaceService.headCommit(repoRoot));
    }

    /**
     * 同步文件到目标仓库后提交并发布；fileCopy 只会在目标仓库干净且完成快进拉取后执行。
     */
    public PublishResult syncFilesThenPush(
            Path repoRoot,
            String branch,
            List<String> files,
            String message,
            boolean force,
            String privateKey,
            FileCopyAction fileCopy) {
        return syncFilesThenPush(repoRoot, branch, files, message, force, privateKey, null, fileCopy);
    }

    /**
     * 同步文件、提交并发布；提交使用当前操作人的 Git 身份。
     */
    public PublishResult syncFilesThenPush(
            Path repoRoot,
            String branch,
            List<String> files,
            String message,
            boolean force,
            String privateKey,
            GitCommitIdentity identity,
            FileCopyAction fileCopy) {
        ensureClean(repoRoot);
        gitWorkspaceService.fetch(repoRoot, privateKey);
        gitWorkspaceService.pullFastForward(repoRoot, branch, privateKey);
        fileCopy.copy();
        gitWorkspaceService.commitFiles(repoRoot, files, message, privateKey, identity);
        gitWorkspaceService.push(repoRoot, branch, force, privateKey);
        return PublishResult.succeeded(gitWorkspaceService.headCommit(repoRoot));
    }

    private void ensureClean(Path repoRoot) {
        if (!gitWorkspaceService.isWorktreeClean(repoRoot)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Git 工作树存在未提交变更，无法发布",
                    Map.of("path", repoRoot.toString()));
        }
    }

    private PublishResult handleMergeFailure(Path repoRoot, PlatformException mergeException) {
        List<String> conflictFiles = gitWorkspaceService.conflictPaths(repoRoot);
        if (conflictFiles.isEmpty()) {
            throw mergeException;
        }
        try {
            gitWorkspaceService.abortMerge(repoRoot);
        } catch (RuntimeException abortException) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Git 合并冲突且自动终止合并失败",
                    Map.of(
                            "conflictFiles", conflictFiles,
                            "abortFailed", true,
                            "path", repoRoot.toString()),
                    abortException);
        }
        return PublishResult.conflicted(conflictFiles);
    }

    @FunctionalInterface
    public interface FileCopyAction {
        void copy();
    }

    public record PublishResult(String headCommit, List<String> conflictFiles) {

        public PublishResult {
            conflictFiles = conflictFiles == null ? List.of() : List.copyOf(conflictFiles);
        }

        public static PublishResult succeeded(String headCommit) {
            return new PublishResult(headCommit, List.of());
        }

        public static PublishResult conflicted(List<String> conflictFiles) {
            return new PublishResult(null, conflictFiles);
        }

        public boolean hasConflicts() {
            return !conflictFiles.isEmpty();
        }
    }
}
