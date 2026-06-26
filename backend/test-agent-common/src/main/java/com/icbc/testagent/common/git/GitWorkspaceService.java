package com.icbc.testagent.common.git;

import com.icbc.testagent.common.error.PlatformException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Git 工作区命令服务，集中封装 clone、worktree、diff 和 push 等本地仓库操作。
 */
public class GitWorkspaceService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration PUSH_TIMEOUT = Duration.ofSeconds(120);

    private final GitCommandExecutor executor;

    /**
     * 使用本机 git 命令执行器，生产路径统一从这里进入 Git 命令。
     */
    public GitWorkspaceService() {
        this(new ProcessGitCommandExecutor());
    }

    /**
     * 测试可注入 fake executor，避免依赖真实 Git 仓库。
     */
    public GitWorkspaceService(GitCommandExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * clone 指定分支到目标目录；调用方负责选择目录和处理已有目录接管。
     */
    public void cloneBranch(String gitUrl, String branch, Path repoRoot, String privateKey) {
        executor.execute(
                List.of(
                        "git",
                        "clone",
                        "--branch",
                        branch,
                        "--single-branch",
                        gitUrl,
                        repoRoot.toString()),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 基于应用版本仓库创建个人 worktree，并创建新的个人分支。
     */
    public void createWorktree(Path repoRoot, Path worktreeRoot, String branch, String privateKey) {
        executor.execute(
                List.of(
                        "git",
                        "-C",
                        repoRoot.toString(),
                        "worktree",
                        "add",
                        "-b",
                        branch,
                        worktreeRoot.toString()),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 判断目录是否是 Git 仓库；该方法用于接管公共配置目录前做冲突校验。
     */
    public boolean isGitRepository(Path repoRoot) {
        try {
            GitCommandResult result = executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "rev-parse", "--is-inside-work-tree"),
                    null,
                    DEFAULT_TIMEOUT);
            return "true".equalsIgnoreCase(result.stdoutText().trim());
        } catch (PlatformException exception) {
            return false;
        }
    }

    /**
     * 读取本地仓库当前分支，用于接管已有磁盘目录时校验分支是否符合记录。
     */
    public String currentBranch(Path repoRoot) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "rev-parse", "--abbrev-ref", "HEAD"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim();
    }

    /**
     * 读取本地仓库 remote.origin.url，用于接管已有磁盘目录时校验仓库来源。
     */
    public String originUrl(Path repoRoot) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "config", "--get", "remote.origin.url"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim();
    }

    /**
     * 读取 HEAD commit，个人空间记录 base commit 时使用。
     */
    public String headCommit(Path repoRoot) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "rev-parse", "HEAD"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim();
    }

    /**
     * stage 指定文件并提交；没有变更时 Git 会返回冲突错误，由业务层决定如何提示。
     */
    public void commitFiles(Path repoRoot, List<String> files, String message, String privateKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        executor.execute(addCommand(repoRoot, files), privateKey, DEFAULT_TIMEOUT);
        executor.execute(List.of("git", "-C", repoRoot.toString(), "commit", "-m", message), privateKey, DEFAULT_TIMEOUT);
    }

    /**
     * 将当前分支推送到 origin；force=true 时使用 --force-with-lease，避免无保护地覆盖远端未知提交。
     */
    public void push(Path repoRoot, String branch, boolean force, String privateKey) {
        List<String> command = force
                ? List.of("git", "-C", repoRoot.toString(), "push", "--force-with-lease", "origin", branch)
                : List.of("git", "-C", repoRoot.toString(), "push", "origin", branch);
        executor.execute(command, privateKey, PUSH_TIMEOUT);
    }

    /**
     * 切换到本地分支；本地不存在时基于 origin/branch 创建 tracking 分支。
     */
    public void checkoutTrackingBranch(Path repoRoot, String branch, String privateKey) {
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "checkout", branch),
                    privateKey,
                    DEFAULT_TIMEOUT);
        } catch (PlatformException exception) {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "checkout", "-B", branch, "origin/" + branch),
                    privateKey,
                    DEFAULT_TIMEOUT);
        }
    }

    /**
     * 将 worktree 对应分支合并回主配置仓库当前分支，调用方负责提前 pull 和 clean 校验。
     */
    public void mergeBranch(Path repoRoot, String branch, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "merge", "--no-ff", branch),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 以 fast-forward only 模式拉取指定远端分支，避免自动 merge 产生不可预期的工作区差异。
     */
    public void pullFastForward(Path repoRoot, String branch, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "pull", "--ff-only", "origin", branch),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 获取 origin 最新引用；副本同步先 fetch，再 reset 到明确 commit。
     */
    public void fetch(Path repoRoot, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "fetch", "origin"),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 暂存指定文件；空列表时不执行 Git 命令。
     */
    public void stageFiles(Path repoRoot, List<String> files, String privateKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        executor.execute(addCommand(repoRoot, files), privateKey, DEFAULT_TIMEOUT);
    }

    /**
     * 从暂存区移除指定文件；空列表时不执行 Git 命令。
     */
    public void unstageFiles(Path repoRoot, List<String> files, String privateKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoRoot.toString());
        command.add("restore");
        command.add("--staged");
        command.add("--");
        command.addAll(files);
        executor.execute(List.copyOf(command), privateKey, DEFAULT_TIMEOUT);
    }

    /**
     * 返回工作树 porcelain 状态，由业务层转换成前端 diff 文件列表。
     */
    public String statusPorcelain(Path repoRoot) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "status", "--porcelain"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText();
    }

    /**
     * 返回单个文件工作树 diff；staged=true 时返回暂存区 diff。
     */
    public String diff(Path repoRoot, String file, boolean staged) {
        List<String> command = staged
                ? List.of("git", "-C", repoRoot.toString(), "diff", "--cached", "--", file)
                : List.of("git", "-C", repoRoot.toString(), "diff", "--", file);
        return executor.execute(command, null, DEFAULT_TIMEOUT).stdoutText();
    }

    /**
     * 提交当前暂存区；调用方负责先 stage 和校验 message。
     */
    public void commitStaged(Path repoRoot, String message, String privateKey) {
        executor.execute(List.of("git", "-C", repoRoot.toString(), "commit", "-m", message), privateKey, DEFAULT_TIMEOUT);
    }

    /**
     * 删除 worktree 目录并清理 Git worktree 元数据。
     */
    public void removeWorktree(Path repoRoot, Path worktreeRoot, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "worktree", "remove", "--force", worktreeRoot.toString()),
                privateKey,
                DEFAULT_TIMEOUT);
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "worktree", "prune"),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 将托管副本硬重置到目标 commit；调用方必须先确认这是受控副本且工作树干净。
     */
    public void resetHardToCommit(Path repoRoot, String commitHash) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "reset", "--hard", commitHash),
                null,
                DEFAULT_TIMEOUT);
    }

    /**
     * 判断工作树是否无未提交变更；用于远端副本同步前避免静默覆盖本地修改。
     */
    public boolean isWorktreeClean(Path repoRoot) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "status", "--porcelain"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim().isEmpty();
    }

    private List<String> addCommand(Path repoRoot, List<String> files) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoRoot.toString());
        command.add("add");
        command.add("--");
        command.addAll(files);
        return List.copyOf(command);
    }
}
