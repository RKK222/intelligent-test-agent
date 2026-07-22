package com.enterprise.testagent.common.git;

import com.enterprise.testagent.common.error.PlatformException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * 使用 ls-remote 精确解析远端分支当前提交。调用方据此固定一次同步 generation 的目标版本，
     * 避免 clone/fetch 期间分支继续前进导致不同服务器落到不同提交。
     */
    public String resolveRemoteBranchCommit(String gitUrl, String branch, String privateKey) {
        String normalizedBranch = Objects.requireNonNull(branch, "branch must not be null").trim();
        if (normalizedBranch.isEmpty()) {
            throw new IllegalArgumentException("branch must not be blank");
        }
        String expectedRef = "refs/heads/" + normalizedBranch;
        String output = executor.execute(
                List.of("git", "ls-remote", "--heads", gitUrl, expectedRef),
                privateKey,
                DEFAULT_TIMEOUT).stdoutText();
        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+", 2))
                .filter(fields -> fields.length == 2 && expectedRef.equals(fields[1]))
                .map(fields -> fields[0])
                .findFirst()
                .orElseThrow(() -> new PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.GIT_UNAVAILABLE,
                        "Git 远端分支不存在",
                        Map.of("branch", normalizedBranch)));
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
     * 创建个人 worktree；当同名分支已存在时，复用该分支重新挂载 worktree，避免默认私人空间重复进入时报创建冲突。
     */
    public void createWorktreeReusingBranch(Path repoRoot, Path worktreeRoot, String branch, String privateKey) {
        try {
            createWorktree(repoRoot, worktreeRoot, branch, privateKey);
        } catch (PlatformException exception) {
            if (!"WORKTREE_CONFLICT".equals(exception.details().get("gitFailureType"))) {
                throw exception;
            }
            pruneWorktrees(repoRoot, privateKey);
            try {
                executor.execute(
                        List.of(
                                "git",
                                "-C",
                                repoRoot.toString(),
                                "worktree",
                                "add",
                                worktreeRoot.toString(),
                                branch),
                        privateKey,
                        DEFAULT_TIMEOUT);
            } catch (PlatformException reuseException) {
                if ("WORKTREE_CONFLICT".equals(reuseException.details().get("gitFailureType"))) {
                    Path registeredPath = registeredWorktreePathForBranch(repoRoot, branch);
                    if (samePath(worktreeRoot, registeredPath)) {
                        return;
                    }
                    if (registeredPath != null && !Files.exists(worktreeRoot)) {
                        moveRegisteredWorktree(repoRoot, registeredPath, worktreeRoot, privateKey);
                        return;
                    }
                }
                throw reuseException;
            }
        }
    }

    /**
     * 清理已失效的 worktree 元数据。Git 只会删除磁盘目录已不存在的登记，不会移除仍存活的其它 worktree。
     */
    private void pruneWorktrees(Path repoRoot, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "worktree", "prune"),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    private Path registeredWorktreePathForBranch(Path repoRoot, String branch) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "worktree", "list", "--porcelain"),
                null,
                DEFAULT_TIMEOUT);
        String currentPath = null;
        boolean currentBranchMatches = false;
        for (String line : result.stdoutText().split("\\R")) {
            if (line.startsWith("worktree ")) {
                if (currentBranchMatches) {
                    return normalizeWorktreeListPath(currentPath);
                }
                currentPath = line.substring("worktree ".length()).trim();
                currentBranchMatches = false;
            } else if (line.startsWith("branch refs/heads/")) {
                currentBranchMatches = branch.equals(line.substring("branch refs/heads/".length()).trim());
            }
        }
        return currentBranchMatches ? normalizeWorktreeListPath(currentPath) : null;
    }

    private Path normalizeWorktreeListPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return Path.of(path).toAbsolutePath().normalize();
    }

    private boolean samePath(Path expected, Path actual) {
        return actual != null && expected.toAbsolutePath().normalize().equals(actual.toAbsolutePath().normalize());
    }

    private void moveRegisteredWorktree(Path repoRoot, Path existingWorktreeRoot, Path targetWorktreeRoot, String privateKey) {
        try {
            Files.createDirectories(targetWorktreeRoot.toAbsolutePath().normalize().getParent());
        } catch (Exception exception) {
            throw new PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.INTERNAL_ERROR,
                    "创建 worktree 目标父目录失败",
                    java.util.Map.of("path", targetWorktreeRoot.toString()),
                    exception);
        }
        executor.execute(
                List.of(
                        "git",
                        "-C",
                        repoRoot.toString(),
                        "worktree",
                        "move",
                        existingWorktreeRoot.toString(),
                        targetWorktreeRoot.toString()),
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
     * 更新本地仓库 remote.origin.url。内部部署模式会按当前操作人统一认证号动态刷新 origin。
     */
    public void setOriginUrl(Path repoRoot, String gitUrl, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "remote", "set-url", "origin", gitUrl),
                privateKey,
                DEFAULT_TIMEOUT);
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
     * 解析任意本地或远端引用的提交，用于发布状态机在修改运行副本前固定目标版本。
     */
    public String resolveCommit(Path repoRoot, String ref) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "rev-parse", ref),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim();
    }

    /**
     * 从 sourceCommit 的最终文件树创建一个仅以 parentCommit 为父节点的线性提交。
     *
     * <p>公共配置个人分支可能长期复用，并残留企业 SCM 不认可的历史提交身份。发布时不能直接
     * 推送整段个人分支历史；这里通过 commit-tree 只保留最终文件树，并由当前操作人生成一个
     * 可审计的新提交。若文件树与远端父提交完全一致，则直接复用父提交。</p>
     */
    public String createLinearCommitFromTree(
            Path repoRoot,
            String sourceCommit,
            String parentCommit,
            String message,
            GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        String normalizedMessage = Objects.requireNonNull(message, "message must not be null").trim();
        if (normalizedMessage.isEmpty()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        String sourceTree = resolveCommit(repoRoot, sourceCommit + "^{tree}");
        String parentTree = resolveCommit(repoRoot, parentCommit + "^{tree}");
        if (sourceTree.equals(parentTree)) {
            return parentCommit;
        }
        return executor.execute(
                withCommitIdentity(
                        List.of(
                                "git",
                                "-C",
                                repoRoot.toString(),
                                "commit-tree",
                                sourceTree,
                                "-p",
                                parentCommit,
                                "-m",
                                normalizedMessage),
                        identity),
                null,
                DEFAULT_TIMEOUT).stdoutText().trim();
    }

    /**
     * 判断 ancestor 是否已包含在 descendant 中；远端 push 回包不确定时用于确认实际结果。
     */
    public boolean isAncestor(Path repoRoot, String ancestor, String descendant) {
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "merge-base", "--is-ancestor", ancestor, descendant),
                    null,
                    DEFAULT_TIMEOUT);
            return true;
        } catch (PlatformException exception) {
            Object exitCode = exception.details().get("exitCode");
            if (exitCode instanceof Number number && number.intValue() == 1) {
                return false;
            }
            // merge-base 只有退出码 1 表示“不是祖先”；超时、仓库损坏或引用错误必须继续失败关闭。
            throw exception;
        }
    }

    /**
     * 暂存指定文件并使用当前操作人的身份提交；身份必填且仅对本次 commit 命令生效。
     */
    public void commitFiles(
            Path repoRoot,
            List<String> files,
            String message,
            String privateKey,
            GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (files == null || files.isEmpty()) {
            return;
        }
        executor.execute(addCommand(repoRoot, files), privateKey, DEFAULT_TIMEOUT);
        executor.execute(
                withCommitIdentity(List.of("git", "-C", repoRoot.toString(), "commit", "-m", message), identity),
                privateKey,
                DEFAULT_TIMEOUT);
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
     * 将个人本地分支推送到指定远端公共分支；不使用 force，远端前进时由 Git 拒绝并交给调用方处理。
     */
    public void pushRef(Path repoRoot, String sourceBranch, String targetBranch, String privateKey) {
        executor.execute(
                List.of(
                        "git",
                        "-C",
                        repoRoot.toString(),
                        "push",
                        "origin",
                        sourceBranch + ":" + targetBranch),
                privateKey,
                PUSH_TIMEOUT);
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
     * 将受管共享副本安全切换到固定提交。已有目标本地分支必须可快进到目标提交；
     * 不存在时从本次已固定并验证可解析的目标提交创建，绝不使用 -B 覆盖未知本地分支。
     * single-branch clone 的 fetchspec 不识别后来显式抓取的 remote ref，不能依赖 --track 建分支。
     */
    public void checkoutBranchForFixedCommit(
            Path repoRoot,
            String branch,
            String targetCommit,
            String privateKey) {
        String localRef = "refs/heads/" + branch;
        if (localBranchExists(repoRoot, localRef)) {
            String localCommit = resolveCommit(repoRoot, localRef);
            if (!isAncestor(repoRoot, localCommit, targetCommit)) {
                throw new PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.CONFLICT,
                        "目标本地分支与远端目标提交发生分叉",
                        Map.of("branch", branch));
            }
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "checkout", branch),
                    privateKey,
                    DEFAULT_TIMEOUT);
        } else {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "checkout", "-b", branch, targetCommit),
                    privateKey,
                    DEFAULT_TIMEOUT);
        }
    }

    private boolean localBranchExists(Path repoRoot, String localRef) {
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet", localRef),
                    null,
                    DEFAULT_TIMEOUT);
            return true;
        } catch (PlatformException exception) {
            Object exitCode = exception.details().get("exitCode");
            if (exitCode instanceof Number number && number.intValue() == 1) {
                return false;
            }
            throw exception;
        }
    }

    /**
     * 合并分支并为可能产生的 merge commit 注入必填的当前操作人身份。
     */
    public void mergeBranch(Path repoRoot, String branch, String privateKey, GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        executor.execute(
                withCommitIdentity(List.of("git", "-C", repoRoot.toString(), "merge", "--no-ff", branch), identity),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 把已经解析并固定的提交合并到当前分支。
     *
     * <p>托管应用同步不能在执行时重新解析可移动的分支名，否则广播记录的目标 commit 与实际
     * 合并内容可能不一致。这里显式使用 {@code --no-edit}，无分叉时允许 Git fast-forward，
     * 有个人提交时生成正常 merge commit，冲突时则保留 Git 原生 MERGE_HEAD 和三方 index。</p>
     */
    public void mergeCommit(
            Path repoRoot,
            String targetCommit,
            String privateKey,
            GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        List<String> command = List.of(
                "git", "-C", repoRoot.toString(), "merge", "--no-edit", targetCommit);
        executor.execute(
                withCommitIdentity(command, identity),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 返回当前仓库未解决的合并冲突文件列表，用于"个人 worktree 合并回应用版本分支"失败时提示前端。
     */
    public List<String> conflictPaths(Path repoRoot) {
        GitCommandResult result = executor.execute(
                gitNoQuotedPath(repoRoot, "diff", "--name-only", "--diff-filter", "U"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText()
                .lines()
                .filter(line -> !line.isBlank())
                .toList();
    }

    /**
     * 使用 Git index 原生 stage 批量解决冲突。CURRENT 对应 stage 2/ours，
     * INCOMING 对应 stage 3/theirs；目标 stage 不存在表示该侧删除文件。
     */
    public void resolveAllConflicts(
            Path repoRoot,
            ConflictResolutionSide side,
            String privateKey) {
        Objects.requireNonNull(side, "side must not be null");
        List<String> conflicts = conflictPaths(repoRoot);
        if (conflicts.isEmpty()) {
            return;
        }
        List<String> checkoutFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();
        for (String file : conflicts) {
            if (conflictStages(repoRoot, file).contains(side.stage())) {
                checkoutFiles.add(file);
            } else {
                deletedFiles.add(file);
            }
        }
        if (!checkoutFiles.isEmpty()) {
            ArrayList<String> command = new ArrayList<>();
            command.add("git");
            command.add("-C");
            command.add(repoRoot.toString());
            command.add("checkout");
            command.add(side == ConflictResolutionSide.CURRENT ? "--ours" : "--theirs");
            command.add("--");
            command.addAll(checkoutFiles);
            executor.execute(List.copyOf(command), privateKey, DEFAULT_TIMEOUT);
            stageFiles(repoRoot, checkoutFiles, privateKey);
        }
        if (!deletedFiles.isEmpty()) {
            ArrayList<String> command = new ArrayList<>();
            command.add("git");
            command.add("-C");
            command.add(repoRoot.toString());
            command.add("rm");
            command.add("--");
            command.addAll(deletedFiles);
            executor.execute(List.copyOf(command), privateKey, DEFAULT_TIMEOUT);
        }
    }

    public enum ConflictResolutionSide {
        CURRENT(2),
        INCOMING(3);

        private final int stage;

        ConflictResolutionSide(int stage) {
            this.stage = stage;
        }

        int stage() {
            return stage;
        }
    }

    /**
     * 统计 from 不包含、to 包含的提交数，用于发布前远程变化预览。
     */
    public int countCommits(Path repoRoot, String from, String to) {
        String output = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "rev-list", "--count", from + ".." + to),
                null,
                DEFAULT_TIMEOUT).stdoutText().trim();
        try {
            return Integer.parseInt(output);
        } catch (NumberFormatException exception) {
            throw new PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.GIT_UNAVAILABLE,
                    "解析 Git 提交数量失败",
                    java.util.Map.of("output", output),
                    exception);
        }
    }

    /**
     * 返回两提交之间的 name-status，路径关闭 quote，供业务层汇总 A/M/D/R。
     */
    public String diffNameStatus(Path repoRoot, String from, String to) {
        return executor.execute(
                gitNoQuotedPath(repoRoot, "diff", "--name-status", "-M", from + "..." + to),
                null,
                DEFAULT_TIMEOUT).stdoutText();
    }

    /**
     * 返回冲突文件在 Git index 中实际存在的 stage（1=base、2=current、3=incoming）。
     */
    public Set<Integer> conflictStages(Path repoRoot, String file) {
        GitCommandResult result = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "ls-files", "--unmerged", "--stage", "--", file),
                null,
                DEFAULT_TIMEOUT);
        Set<Integer> stages = new LinkedHashSet<>();
        for (String line : result.stdoutText().lines().toList()) {
            int tab = line.indexOf('\t');
            String metadata = tab >= 0 ? line.substring(0, tab) : line;
            String[] fields = metadata.trim().split("\\s+");
            if (fields.length < 3) {
                continue;
            }
            try {
                stages.add(Integer.parseInt(fields[2]));
            } catch (NumberFormatException ignored) {
                // 非标准输出不进入 stage 集合，由业务层按“不是冲突文件”处理。
            }
        }
        return Set.copyOf(stages);
    }

    /**
     * 读取冲突 index 的指定 stage 文本。调用方必须先确认该 stage 存在。
     */
    public String conflictStageContent(Path repoRoot, int stage, String file) {
        if (stage < 1 || stage > 3) {
            throw new IllegalArgumentException("conflict stage must be 1, 2 or 3");
        }
        return executor.execute(
                List.of("git", "-C", repoRoot.toString(), "show", ":" + stage + ":" + file),
                null,
                DEFAULT_TIMEOUT).stdoutText();
    }

    /**
     * 终止当前仓库中的未完成 merge，用于业务层在收集冲突文件后恢复受控副本到可继续操作状态。
     */
    public void abortMerge(Path repoRoot, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "merge", "--abort"),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    public void abortMerge(Path repoRoot) {
        abortMerge(repoRoot, null);
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
     * 显式抓取目标远端分支并刷新同名 tracking ref。引用资产初始化使用 single-branch clone，
     * 普通 fetch 会继续受初始 fetchspec 限制，因此受控切换分支必须使用明确 refspec。
     */
    public void fetchBranch(Path repoRoot, String branch, String privateKey) {
        String normalizedBranch = Objects.requireNonNull(branch, "branch must not be null").trim();
        if (normalizedBranch.isEmpty()) {
            throw new IllegalArgumentException("branch must not be blank");
        }
        String remoteRef = "refs/heads/" + normalizedBranch;
        String trackingRef = "refs/remotes/origin/" + normalizedBranch;
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "fetch", "origin", "+" + remoteRef + ":" + trackingRef),
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
     * 仅把索引恢复到 HEAD，不改动工作树。个人工作区按文件白名单发布前调用，
     * 防止历史暂存项被无 pathspec 的 commit 一并提交。
     */
    public void resetIndexToHead(Path repoRoot, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "reset", "--mixed", "HEAD"),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 判断仓库是否存在未完成 merge。worktree 的 MERGE_HEAD 可能位于独立 gitdir，
     * 因此先由 Git 返回实际路径再检查文件。
     */
    public boolean isMergeInProgress(Path repoRoot) {
        String value = executor.execute(
                List.of("git", "-C", repoRoot.toString(), "rev-parse", "--git-path", "MERGE_HEAD"),
                null,
                DEFAULT_TIMEOUT).stdoutText().trim();
        if (value.isBlank()) {
            return false;
        }
        Path mergeHead = Path.of(value);
        return Files.isRegularFile(mergeHead.isAbsolute() ? mergeHead : repoRoot.resolve(mergeHead).normalize());
    }

    /**
     * 暂存工作区全部变更（含未跟踪文件），用于"更新+提交并推送"场景；不包含被 .gitignore 忽略的文件。
     */
    public void stageAll(Path repoRoot, String privateKey) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "add", "--all"),
                privateKey,
                DEFAULT_TIMEOUT);
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
     * 放弃指定文件的暂存区和工作区改动；未跟踪文件由调用方过滤后再清理。
     */
    public void restoreFiles(Path repoRoot, List<String> files, String privateKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoRoot.toString());
        command.add("restore");
        command.add("--staged");
        command.add("--worktree");
        command.add("--");
        command.addAll(files);
        executor.execute(List.copyOf(command), privateKey, DEFAULT_TIMEOUT);
    }

    /**
     * 清理指定未跟踪文件，调用方必须传入明确文件列表，避免扩大删除范围。
     */
    public void cleanUntrackedFiles(Path repoRoot, List<String> files, String privateKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoRoot.toString());
        command.add("clean");
        command.add("-f");
        command.add("--");
        command.addAll(files);
        executor.execute(List.copyOf(command), privateKey, DEFAULT_TIMEOUT);
    }

    /**
     * 定点丢弃文件改动：已跟踪文件恢复索引和工作树，新增文件取消暂存后定点清理。
     * 冲突文件必须由上层合并编辑器处理，不能通过普通回退绕过 Git stage 1/2/3。
     */
    public void discardFiles(Path repoRoot, List<String> files, String privateKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        Map<String, GitStatusEntry> statuses = new LinkedHashMap<>();
        for (GitStatusEntry status : parseStatusPorcelain(statusPorcelain(repoRoot))) {
            statuses.put(status.path(), status);
        }
        List<String> conflictFiles = files.stream()
                .filter(file -> {
                    GitStatusEntry status = statuses.get(file);
                    return status != null && status.unmerged();
                })
                .toList();
        if (!conflictFiles.isEmpty()) {
            throw new PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.CONFLICT,
                    "冲突文件必须通过合并编辑器解决",
                    Map.of("files", conflictFiles));
        }

        List<String> trackedFiles = new ArrayList<>();
        List<String> stagedNewFiles = new ArrayList<>();
        List<String> untrackedFiles = new ArrayList<>();
        for (String file : files) {
            GitStatusEntry status = statuses.get(file);
            if (status != null && status.stagedNewFile()) {
                stagedNewFiles.add(file);
            } else if (status != null && status.untrackedFile()) {
                untrackedFiles.add(file);
            } else {
                trackedFiles.add(file);
            }
        }
        restoreFiles(repoRoot, trackedFiles, privateKey);
        unstageFiles(repoRoot, stagedNewFiles, privateKey);
        List<String> filesToClean = new ArrayList<>(stagedNewFiles);
        filesToClean.addAll(untrackedFiles);
        cleanUntrackedFiles(repoRoot, filesToClean, privateKey);
    }

    /**
     * 返回工作树 porcelain 状态，由业务层转换成前端 diff 文件列表。
     */
    public String statusPorcelain(Path repoRoot) {
        GitCommandResult result = executor.execute(
                gitNoQuotedPath(repoRoot, "status", "--porcelain", "--untracked-files=all"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText();
    }

    /**
     * 返回指定 pathspec 下的 porcelain 状态，并展开未跟踪目录中的每个文件。
     * 工作区 Diff 依赖文件级结果计算数量和执行定点 stage/discard，不能把目录压缩成一条状态。
     */
    public String statusPorcelain(Path repoRoot, String pathspec) {
        GitCommandResult result = executor.execute(
                gitNoQuotedPath(repoRoot, "status", "--porcelain", "--untracked-files=all", "--", pathspec),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText();
    }

    /**
     * 返回单个文件工作树 diff；staged=true 时返回暂存区 diff。
     */
    public String diff(Path repoRoot, String file, boolean staged) {
        List<String> command = staged
                ? gitNoQuotedPath(repoRoot, "diff", "--cached", "--", file)
                : gitNoQuotedPath(repoRoot, "diff", "--", file);
        return executor.execute(command, null, DEFAULT_TIMEOUT).stdoutText();
    }

    /**
     * 解析 {@code git status --porcelain} 输出，统一完成路径反转义和 rename 新路径选择。
     * 业务层只处理权限、路径展示和响应 DTO，不再重复理解 porcelain 字段。
     */
    public List<GitStatusEntry> parseStatusPorcelain(String porcelain) {
        if (porcelain == null || porcelain.isBlank()) {
            return List.of();
        }
        List<GitStatusEntry> entries = new ArrayList<>();
        for (String line : porcelain.split("\\R")) {
            String trimmed = line.stripTrailing();
            if (trimmed.length() < 4) {
                continue;
            }
            String rawStatus = trimmed.substring(0, 2);
            String rawPath = trimmed.substring(3);
            int rename = rawPath.indexOf(" -> ");
            if (rename >= 0) {
                rawPath = rawPath.substring(rename + 4);
            }
            String path = unquotePorcelainPath(rawPath);
            if (path.isBlank()) {
                continue;
            }
            entries.add(new GitStatusEntry(rawStatus.charAt(0), rawStatus.charAt(1), rawStatus, path));
        }
        return List.copyOf(entries);
    }

    /**
     * 基于 porcelain 输出收集前端 diff 文件模型，合并同一文件的暂存区和工作区 patch。
     * 单个文件 diff 失败时只降级该文件的 patch/行数，避免一次 Git 异常打断整批变更列表。
     */
    public List<GitDiffFile> collectDiffFiles(Path repoRoot, String porcelain) {
        return collectDiffFiles(repoRoot, parseStatusPorcelain(porcelain));
    }

    /**
     * 基于已解析状态收集 diff；调用方可先改写条目的 Git 路径或过滤业务目录后再调用。
     */
    public List<GitDiffFile> collectDiffFiles(Path repoRoot, List<GitStatusEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        boolean hasStaged = false;
        boolean hasUnstaged = false;
        for (GitStatusEntry entry : entries) {
            if (!entry.untrackedFile()) {
                if (entry.staged()) {
                    hasStaged = true;
                }
                if (entry.needsUnstagedDiff()) {
                    hasUnstaged = true;
                }
            }
        }

        Map<String, String> stagedDiffs = Map.of();
        Map<String, String> unstagedDiffs = Map.of();

        if (hasStaged) {
            try {
                List<String> cmd = gitNoQuotedPath(repoRoot, "diff", "--cached");
                String out = executor.execute(cmd, null, DEFAULT_TIMEOUT).stdoutText();
                stagedDiffs = parseFullDiff(out);
            } catch (Exception ignored) {
            }
        }

        if (hasUnstaged) {
            try {
                List<String> cmd = gitNoQuotedPath(repoRoot, "diff");
                String out = executor.execute(cmd, null, DEFAULT_TIMEOUT).stdoutText();
                unstagedDiffs = parseFullDiff(out);
            } catch (Exception ignored) {
            }
        }

        List<GitDiffFile> files = new ArrayList<>();
        for (GitStatusEntry entry : entries) {
            DiffAccumulator accumulator = new DiffAccumulator();
            if (entry.untrackedFile()) {
                appendNewFilePatch(entry.path(), repoRoot.resolve(entry.path()), accumulator);
            } else {
                if (entry.staged()) {
                    String diff = stagedDiffs.get(entry.path());
                    if (diff != null && !diff.isBlank()) {
                        accumulator.append(diff);
                    } else {
                        appendDiff(repoRoot, entry.path(), true, accumulator);
                    }
                }
                if (entry.needsUnstagedDiff()) {
                    String diff = unstagedDiffs.get(entry.path());
                    if (diff != null && !diff.isBlank()) {
                        accumulator.append(diff);
                    } else {
                        appendDiff(repoRoot, entry.path(), false, accumulator);
                    }
                }
            }
            files.add(new GitDiffFile(
                    entry.path(),
                    entry.rawStatus(),
                    entry.status(),
                    entry.staged(),
                    accumulator.patch(),
                    accumulator.additions,
                    accumulator.deletions));
        }
        return List.copyOf(files);
    }

    /**
     * 解析 Git 完整 Diff 输出以提取每个文件的 Diff 片段。
     */
    public Map<String, String> parseFullDiff(String diffOutput) {
        Map<String, String> diffMap = new java.util.HashMap<>();
        if (diffOutput == null || diffOutput.isEmpty()) {
            return diffMap;
        }
        String[] lines = diffOutput.split("\\R");
        StringBuilder currentBlock = new StringBuilder();
        String currentPath = null;
        for (String line : lines) {
            if (line.startsWith("diff --git ")) {
                if (currentPath != null && currentBlock.length() > 0) {
                    diffMap.put(currentPath, currentBlock.toString());
                }
                currentBlock = new StringBuilder();
                currentPath = null;
                currentBlock.append(line).append('\n');
                continue;
            }
            if (currentBlock.length() > 0) {
                currentBlock.append(line).append('\n');
            }
            if (line.startsWith("--- a/")) {
                String path = line.substring(6);
                if (currentPath == null && !path.equals("/dev/null")) {
                    currentPath = path;
                }
            } else if (line.startsWith("+++ b/")) {
                String path = line.substring(6);
                if (!path.equals("/dev/null")) {
                    currentPath = path;
                }
            }
        }
        if (currentPath != null && currentBlock.length() > 0) {
            diffMap.put(currentPath, currentBlock.toString());
        }
        return diffMap;
    }

    /**
     * 还原 Git porcelain 中带双引号的 C-style 路径，避免含空格或转义字符的路径展示乱码且 diff 查不到文件。
     */
    public String unquotePorcelainPath(String path) {
        if (path == null) {
            return "";
        }
        String value = path.trim();
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }
        String body = value.substring(1, value.length() - 1);
        StringBuilder result = new StringBuilder();
        ByteArrayOutputStream escapedBytes = new ByteArrayOutputStream();
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch != '\\' || i + 1 >= body.length()) {
                flushEscapedBytes(result, escapedBytes);
                result.append(ch);
                continue;
            }
            char next = body.charAt(++i);
            if (next >= '0' && next <= '7') {
                int octal = next - '0';
                int count = 1;
                while (count < 3 && i + 1 < body.length()) {
                    char digit = body.charAt(i + 1);
                    if (digit < '0' || digit > '7') {
                        break;
                    }
                    i++;
                    count++;
                    octal = octal * 8 + (digit - '0');
                }
                escapedBytes.write(octal);
                continue;
            }
            flushEscapedBytes(result, escapedBytes);
            result.append(switch (next) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'b' -> '\b';
                case '"' -> '"';
                case '\\' -> '\\';
                default -> next;
            });
        }
        flushEscapedBytes(result, escapedBytes);
        return result.toString();
    }

    private void flushEscapedBytes(StringBuilder result, ByteArrayOutputStream escapedBytes) {
        if (escapedBytes.size() == 0) {
            return;
        }
        result.append(escapedBytes.toString(StandardCharsets.UTF_8));
        escapedBytes.reset();
    }

    private void appendDiff(Path repoRoot, String file, boolean staged, DiffAccumulator accumulator) {
        try {
            String output = diff(repoRoot, file, staged);
            if (output == null || output.isBlank()) {
                return;
            }
            accumulator.append(output);
        } catch (Exception ignored) {
            // 单文件 diff 失败不影响其它文件展示；调用方仍能看到状态和路径。
        }
    }

    private void appendNewFilePatch(String gitPath, Path filePath, DiffAccumulator accumulator) {
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            StringBuilder diff = new StringBuilder();
            diff.append("--- /dev/null\n");
            diff.append("+++ b/").append(gitPath).append('\n');
            diff.append("@@ -0,0 +1,").append(lines.size()).append(" @@\n");
            for (String line : lines) {
                diff.append('+').append(line).append('\n');
            }
            accumulator.append(diff.toString());
        } catch (Exception exception) {
            // 单个未跟踪文件读取失败只降级该文件 patch，不影响整批 diff 展示。
        }
    }

    private static int countDiffAdditions(String diff) {
        int count = 0;
        for (String line : diff.split("\\R")) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                count++;
            }
        }
        return count;
    }

    private static int countDiffDeletions(String diff) {
        int count = 0;
        for (String line : diff.split("\\R")) {
            if (line.startsWith("-") && !line.startsWith("---")) {
                count++;
            }
        }
        return count;
    }

    /**
     * 使用当前操作人的必填身份提交暂存区；身份仅对本次命令生效，不污染共享仓库配置。
     */
    public void commitStaged(Path repoRoot, String message, String privateKey, GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        executor.execute(
                withCommitIdentity(List.of("git", "-C", repoRoot.toString(), "commit", "-m", message), identity),
                privateKey,
                DEFAULT_TIMEOUT);
    }

    /**
     * 从指定提交把白名单文件投影到目标 worktree 的工作树和索引。
     *
     * <p>发布流程使用个人 worktree 的不可变 HEAD 作为 sourceCommit，目标只能是应用
     * feature worktree；这里不合并个人分支，也不读取个人工作树上的未提交内容。</p>
     */
    public void materializeCommitFiles(
            Path targetRepoRoot,
            String sourceCommit,
            List<String> files,
            String privateKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<String> existing = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        for (String file : files) {
            if (pathExistsAtCommit(targetRepoRoot, sourceCommit, file)) {
                existing.add(file);
            } else {
                deleted.add(file);
            }
        }
        if (!existing.isEmpty()) {
            ArrayList<String> command = new ArrayList<>();
            command.add("git");
            command.add("-C");
            command.add(targetRepoRoot.toString());
            command.add("checkout");
            command.add(sourceCommit);
            command.add("--");
            command.addAll(existing);
            executor.execute(List.copyOf(command), privateKey, DEFAULT_TIMEOUT);
        }
        if (!deleted.isEmpty()) {
            ArrayList<String> command = new ArrayList<>();
            command.add("git");
            command.add("-C");
            command.add(targetRepoRoot.toString());
            command.add("rm");
            command.add("-f");
            command.add("--");
            command.addAll(deleted);
            executor.execute(List.copyOf(command), privateKey, DEFAULT_TIMEOUT);
        }
    }

    /** 判断提交中是否包含指定路径，供投影流程区分更新和删除。 */
    public boolean pathExistsAtCommit(Path repoRoot, String commit, String file) {
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "cat-file", "-e", commit + ":" + file),
                    null,
                    DEFAULT_TIMEOUT);
            return true;
        } catch (PlatformException exception) {
            return false;
        }
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
                gitNoQuotedPath(repoRoot, "status", "--porcelain"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim().isEmpty();
    }

    /**
     * 只读核验工作树状态，不刷新 index stat、fsmonitor 或 untracked cache。
     * 引用资产指针核验不得调用可能获取可选锁并回写 index 的通用 status 路径。
     */
    public boolean isWorktreeCleanReadOnly(Path repoRoot) {
        GitCommandResult result = executor.execute(
                List.of(
                        "git",
                        "--no-optional-locks",
                        "-c",
                        "core.quotepath=false",
                        "-c",
                        "core.untrackedCache=false",
                        "-c",
                        "core.fsmonitor=false",
                        "-C",
                        repoRoot.toString(),
                        "status",
                        "--porcelain",
                        "--untracked-files=all"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim().isEmpty();
    }

    public record GitStatusEntry(char indexStatus, char worktreeStatus, String rawStatus, String path) {

        public GitStatusEntry {
            rawStatus = rawStatus == null || rawStatus.length() != 2
                    ? "" + indexStatus + worktreeStatus
                    : rawStatus;
            path = path == null ? "" : path;
        }

        public boolean staged() {
            return indexStatus != ' ' && indexStatus != '?';
        }

        public boolean stagedNewFile() {
            return indexStatus == 'A';
        }

        public boolean untrackedFile() {
            return indexStatus == '?' && worktreeStatus == '?';
        }

        public boolean unmerged() {
            return rawStatus.equals("DD")
                    || rawStatus.equals("AU")
                    || rawStatus.equals("UD")
                    || rawStatus.equals("UA")
                    || rawStatus.equals("DU")
                    || rawStatus.equals("AA")
                    || rawStatus.equals("UU");
        }

        public String status() {
            if (unmerged()) {
                return "conflict";
            }
            if (untrackedFile()) {
                return "untracked";
            }
            if (indexStatus == 'A') {
                return "added";
            }
            if (indexStatus == 'D' || worktreeStatus == 'D') {
                return "deleted";
            }
            if (indexStatus == 'R') {
                return "renamed";
            }
            if (indexStatus == 'M' || worktreeStatus == 'M') {
                return "modified";
            }
            return "modified";
        }

        public GitStatusEntry withPath(String path) {
            return new GitStatusEntry(indexStatus, worktreeStatus, rawStatus, path);
        }

        private boolean needsUnstagedDiff() {
            return !untrackedFile() && worktreeStatus != ' ' && worktreeStatus != '?';
        }
    }

    public record GitDiffFile(
            String path,
            String rawStatus,
            String status,
            boolean staged,
            String patch,
            int additions,
            int deletions) {
    }

    private static final class DiffAccumulator {
        private final StringBuilder patch = new StringBuilder();
        private int additions;
        private int deletions;

        private void append(String diff) {
            if (!patch.isEmpty()) {
                patch.append('\n');
            }
            patch.append(diff);
            additions += countDiffAdditions(diff);
            deletions += countDiffDeletions(diff);
        }

        private String patch() {
            return patch.toString();
        }
    }

    private List<String> gitNoQuotedPath(Path repoRoot, String... args) {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("core.quotepath=false");
        command.add("-C");
        command.add(repoRoot.toString());
        command.addAll(List.of(args));
        return List.copyOf(command);
    }

    /**
     * 将提交身份转换为 Git 命令级配置，避免修改共享仓库的 config 文件或后端进程全局环境。
     */
    private List<String> withCommitIdentity(List<String> command, GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (command.isEmpty() || !"git".equals(command.get(0))) {
            throw new IllegalArgumentException("Git command must start with git");
        }
        ArrayList<String> configured = new ArrayList<>(command.size() + 4);
        configured.add("git");
        configured.add("-c");
        configured.add("user.name=" + identity.name());
        configured.add("-c");
        configured.add("user.email=" + identity.email());
        configured.addAll(command.subList(1, command.size()));
        return List.copyOf(configured);
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
