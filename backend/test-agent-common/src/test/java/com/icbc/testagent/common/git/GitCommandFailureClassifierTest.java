package com.icbc.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GitCommandFailureClassifierTest {

    @Test
    void classifiesPublicKeyPermissionDeniedAsAuthenticationFailure() {
        GitCommandFailure failure = GitCommandFailureClassifier.classify(
                List.of("git", "ls-remote", "--heads", "git@gitee.com:org/repo.git"),
                "git@gitee.com: Permission denied (publickey). fatal: Could not read from remote repository.");

        assertThat(failure.type()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(failure.message()).contains("Git 远端认证失败");
        assertThat(failure.hint()).contains("SSH key");
    }

    @Test
    void classifiesRepositoryNotFoundAsRepositoryUnavailable() {
        GitCommandFailure failure = GitCommandFailureClassifier.classify(
                List.of("git", "clone", "git@gitee.com:org/missing.git", "/tmp/repo"),
                "ERROR: Repository not found. fatal: Could not read from remote repository.");

        assertThat(failure.type()).isEqualTo("REPOSITORY_UNAVAILABLE");
        assertThat(failure.message()).contains("Git 仓库不可访问");
        assertThat(failure.hint()).contains("仓库地址");
    }

    @Test
    void classifiesHostResolutionAndTimeoutAsNetworkFailure() {
        GitCommandFailure dnsFailure = GitCommandFailureClassifier.classify(
                List.of("git", "fetch", "origin"),
                "ssh: Could not resolve hostname gitee.com: nodename nor servname provided");
        GitCommandFailure timeoutFailure = GitCommandFailureClassifier.classify(
                List.of("git", "fetch", "origin"),
                "ssh: connect to host gitee.com port 22: Connection timed out");

        assertThat(dnsFailure.type()).isEqualTo("NETWORK_UNAVAILABLE");
        assertThat(timeoutFailure.type()).isEqualTo("NETWORK_UNAVAILABLE");
        assertThat(dnsFailure.hint()).contains("网络");
    }

    @Test
    void classifiesMissingRemoteBranchAsBranchNotFound() {
        GitCommandFailure failure = GitCommandFailureClassifier.classify(
                List.of("git", "clone", "--branch", "missing", "--single-branch", "git@gitee.com:org/repo.git", "/tmp/repo"),
                "warning: Could not find remote branch missing to clone. fatal: Remote branch missing not found in upstream origin");

        assertThat(failure.type()).isEqualTo("BRANCH_NOT_FOUND");
        assertThat(failure.message()).contains("Git 分支不存在");
        assertThat(failure.hint()).contains("分支");
    }

    @Test
    void classifiesWorktreeBranchOrDirectoryExistsAsWorktreeConflict() {
        GitCommandFailure failure = GitCommandFailureClassifier.classify(
                List.of("git", "-C", "/tmp/repo", "worktree", "add", "-b", "change-agent-md", "/tmp/worktree"),
                "fatal: a branch named 'change-agent-md' already exists");

        assertThat(failure.type()).isEqualTo("WORKTREE_CONFLICT");
        assertThat(failure.message()).contains("Git worktree 创建冲突");
        assertThat(failure.hint()).contains("worktree 名称");
    }
}
