package com.enterprise.testagent.common.git;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将 git stderr 归因成稳定、可安全暴露的失败类型，避免前端只能看到笼统的远端读取失败。
 */
final class GitCommandFailureClassifier {

    private static final GitCommandFailure AUTHENTICATION_FAILED = new GitCommandFailure(
            "AUTHENTICATION_FAILED",
            "Git 远端认证失败",
            "请检查当前用户保存的 SSH key 是否有目标仓库读取权限，并确认远端已登记对应公钥。");
    private static final GitCommandFailure REPOSITORY_UNAVAILABLE = new GitCommandFailure(
            "REPOSITORY_UNAVAILABLE",
            "Git 仓库不可访问",
            "请检查仓库地址是否正确、仓库是否存在，以及当前用户是否有读取权限。");
    private static final GitCommandFailure NETWORK_UNAVAILABLE = new GitCommandFailure(
            "NETWORK_UNAVAILABLE",
            "Git 远端网络连接失败",
            "请检查后端服务器到 Git 远端的网络、DNS、代理、防火墙或 SSH 端口连通性。");
    private static final GitCommandFailure BRANCH_NOT_FOUND = new GitCommandFailure(
            "BRANCH_NOT_FOUND",
            "Git 分支不存在",
            "请检查选择的分支是否存在于远端仓库，并刷新分支列表后重试。");
    private static final GitCommandFailure WORKTREE_CONFLICT = new GitCommandFailure(
            "WORKTREE_CONFLICT",
            "Git worktree 创建冲突",
            "请更换 worktree 名称，或清理已存在的同名分支/目录后重试。");
    private static final GitCommandFailure REMOTE_REJECTED = new GitCommandFailure(
            "REMOTE_REJECTED",
            "Git 远端拒绝推送",
            "请先拉取远端最新提交并确认本地仓库可快进，再重新提交或推送。");
    private static final GitCommandFailure LOCAL_CHANGES = new GitCommandFailure(
            "LOCAL_CHANGES",
            "本地改动会被远端更新覆盖",
            "请先提交或回退阻止拉取的文件，再重试拉取。");
    private static final GitCommandFailure UNKNOWN = new GitCommandFailure(
            "UNKNOWN",
            "Git 远端读取失败",
            "请根据 traceId 查看后端日志中的 Git stderr 和命令上下文。");

    private GitCommandFailureClassifier() {
    }

    static GitCommandFailure classify(List<String> command, String stderr) {
        String text = normalize(stderr);
        String commandText = normalize(String.join(" ", command == null ? List.of() : command));
        if (containsAny(text, "permission denied (publickey)", "authentication failed", "publickey")) {
            return AUTHENTICATION_FAILED;
        }
        if (containsAny(text, "remote branch") && containsAny(text, "not found", "could not find remote branch")) {
            return BRANCH_NOT_FOUND;
        }
        if (containsAny(text, "could not resolve host", "could not resolve hostname", "connection timed out",
                "connection refused", "no route to host", "network is unreachable", "operation timed out",
                "failed to connect", "ssl_connect", "ssl_error_syscall", "tls handshake",
                "gnutls_handshake", "schannel:", "ssl certificate problem",
                "server certificate verification failed")) {
            return NETWORK_UNAVAILABLE;
        }
        if (containsAny(commandText, " worktree ", " worktree add ")
                && containsAny(text, "already exists", "is already checked out", "missing but already registered")) {
            return WORKTREE_CONFLICT;
        }
        if (containsAny(commandText, " push ")
                && containsAny(text, "non-fast-forward", "fetch first", "updates were rejected", "remote rejected",
                "failed to push some refs")) {
            return REMOTE_REJECTED;
        }
        if (containsAny(commandText, " merge ")
                && containsAny(
                        text,
                        "your local changes to the following files would be overwritten by merge",
                        "the following untracked working tree files would be overwritten by merge",
                        "not uptodate. cannot merge")) {
            return new GitCommandFailure(
                    LOCAL_CHANGES.type(),
                    LOCAL_CHANGES.message(),
                    LOCAL_CHANGES.hint(),
                    mergeBlockingFiles(stderr));
        }
        if (containsAny(text, "repository not found", "not appear to be a git repository",
                "could not read from remote repository", "repository does not exist", "access denied")) {
            return REPOSITORY_UNAVAILABLE;
        }
        if (containsAny(text, "already exists") && containsAny(commandText, " -b ", " branch ", " worktree ")) {
            return WORKTREE_CONFLICT;
        }
        return UNKNOWN;
    }

    /**
     * 只解析 Git merge 标准的“会被覆盖”文件段；业务层还会与实际 status 取交集，
     * 避免把 stderr 中的其它文字误当成可操作路径。
     */
    private static List<String> mergeBlockingFiles(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        boolean collecting = false;
        for (String rawLine : stderr.replace('\r', '\n').split("\\n")) {
            String line = rawLine.trim();
            String normalized = line.toLowerCase(Locale.ROOT);
            if (containsAny(
                    normalized,
                    "your local changes to the following files would be overwritten by merge",
                    "the following untracked working tree files would be overwritten by merge")) {
                collecting = true;
                continue;
            }
            if (!collecting || line.isBlank()) {
                continue;
            }
            if (containsAny(
                    normalized,
                    "please commit your changes",
                    "please move or remove them",
                    "aborting",
                    "merge with strategy",
                    "updating ")) {
                break;
            }
            files.add(line.replace('\\', '/'));
        }
        return List.copyOf(files);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
