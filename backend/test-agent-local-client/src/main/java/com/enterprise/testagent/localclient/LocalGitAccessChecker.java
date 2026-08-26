package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitCommandExecutor;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.ProcessGitCommandExecutor;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** 在用户本地 Git/SSH 环境中只读检查工作区 origin 的读取权限。 */
final class LocalGitAccessChecker {

    private final GitWorkspaceService workspaceGit;
    private final GitRemoteService remoteGit;

    LocalGitAccessChecker() {
        this(new ProcessGitCommandExecutor());
    }

    LocalGitAccessChecker(GitCommandExecutor executor) {
        Objects.requireNonNull(executor);
        this.workspaceGit = new GitWorkspaceService(executor);
        this.remoteGit = new GitRemoteService(executor);
    }

    /** 只返回固定脱敏状态，不把本地路径、远端 URL、命令或 stderr 送回平台。 */
    GitAccessResult check(String rootPath) {
        Path root = Path.of(rootPath).toAbsolutePath().normalize();
        try (GitCommandExecutor.LogRedaction ignored =
                GitCommandExecutor.redactSensitiveArguments(List.of(root.toString()))) {
            if (!workspaceGit.isGitRepository(root)) {
                return GitAccessResult.unknown(
                        "NOT_GIT_REPOSITORY", "目录不是 Git 仓库，仍可作为普通本地工作区使用");
            }
            String origin;
            try {
                origin = workspaceGit.originUrl(root);
            } catch (PlatformException exception) {
                return GitAccessResult.inaccessible("GIT_REMOTE_MISSING", "Git 工作区未配置 origin 远端");
            }
            if (origin == null || origin.isBlank()) {
                return GitAccessResult.inaccessible("GIT_REMOTE_MISSING", "Git 工作区未配置 origin 远端");
            }
            try {
                remoteGit.listBranches(origin, null);
                return GitAccessResult.accessible();
            } catch (PlatformException exception) {
                String failureType = Objects.toString(
                        exception.details().get("gitFailureType"), "INSPECTION_FAILED");
                if (exception.errorCode() == ErrorCode.GIT_UNAVAILABLE
                        && ("AUTHENTICATION_FAILED".equals(failureType)
                            || "REPOSITORY_UNAVAILABLE".equals(failureType))) {
                    return GitAccessResult.inaccessible(
                            "REPOSITORY_PERMISSION_REQUIRED", "Git 仓库读取权限已失效");
                }
                if (exception.errorCode() == ErrorCode.GIT_TIMEOUT || "TIMEOUT".equals(failureType)) {
                    return GitAccessResult.inaccessible("TIMEOUT", "Git 远端响应超时，当前不可访问");
                }
                if ("NETWORK_UNAVAILABLE".equals(failureType)) {
                    return GitAccessResult.inaccessible(
                            failureType, "Git 远端网络或 SSL/TLS 连接失败，当前不可访问");
                }
                return GitAccessResult.inaccessible("INSPECTION_FAILED", "Git 仓库当前不可访问");
            }
        }
    }

    record GitAccessResult(String status, String reason, String message) {

        static GitAccessResult accessible() {
            return new GitAccessResult("ACCESSIBLE", null, null);
        }

        static GitAccessResult inaccessible(String reason, String message) {
            return new GitAccessResult("INACCESSIBLE", reason, message);
        }

        static GitAccessResult unknown(String reason, String message) {
            return new GitAccessResult("UNKNOWN", reason, message);
        }
    }
}
