package com.enterprise.testagent.common.git;

import com.enterprise.testagent.common.error.PlatformException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Git 工作区命令服务，集中封装 clone、worktree、diff 和 push 等本地仓库操作。
 */
public class GitWorkspaceService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration PUSH_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration BUNDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final int READ_ONLY_UNTRACKED_PATCH_MAX_BYTES = 256 * 1024;
    private static final int READ_ONLY_UNTRACKED_PATCH_MAX_LINES = 2_000;
    private static final int READ_ONLY_PATCH_MAX_FILE_CHARS = 256 * 1024;
    private static final int READ_ONLY_PATCH_MAX_TOTAL_CHARS = 1024 * 1024;
    private static final int READ_ONLY_COMMIT_DIFF_MAX_CHARS = 1024 * 1024;
    private static final Pattern COMMIT_OBJECT_ID = Pattern.compile("^[0-9a-fA-F]{7,64}$");
    private static final Pattern RELOCATION_REF = Pattern.compile(
            "^refs/test-agent/relocations/[A-Za-z0-9_-]{8,128}/(head|stash)$");

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
     * 清理已失效的 worktree 元数据。Git 只会删除磁盘目录已不存在的登记，不会移除仍存活的其它 worktree；
     * 公开给删除成功但状态回写失败后的幂等补偿复用。
     */
    public void pruneWorktrees(Path repoRoot, String privateKey) {
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
     * 判断目录自身是否为 Git 工作树根；父仓库中的普通子目录不能作为独立共享工作区接管。
     */
    public boolean isGitWorkTreeRoot(Path repoRoot) {
        try {
            Path expected = repoRoot.toRealPath();
            GitCommandResult result = executor.execute(
                    List.of("git", "-C", expected.toString(), "rev-parse", "--path-format=absolute", "--show-toplevel"),
                    null,
                    DEFAULT_TIMEOUT);
            String topLevel = result.stdoutText().trim();
            return !topLevel.isBlank() && Path.of(topLevel).toRealPath().equals(expected);
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 幂等初始化一个只在本地使用的 Git 仓库。
     *
     * <p>已有仓库和已有提交均不会被重置；仅当仓库还没有 HEAD 时补齐初始文件并建立基线提交。
     * 初始化过程不创建 remote，因此不会引入任何远程推送能力。
     */
    public void initializeLocalRepository(
            Path repoRoot,
            String initialFile,
            String initialContent,
            GitCommitIdentity identity) {
        Objects.requireNonNull(repoRoot, "repoRoot must not be null");
        Objects.requireNonNull(initialFile, "initialFile must not be null");
        Objects.requireNonNull(initialContent, "initialContent must not be null");
        Objects.requireNonNull(identity, "identity must not be null");
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        Path gitMetadata = normalizedRoot.resolve(".git");
        try {
            Files.createDirectories(normalizedRoot);
            if (Files.exists(gitMetadata, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isDirectory(gitMetadata, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(gitMetadata))) {
                throw new PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.GIT_UNAVAILABLE,
                        "本地 Git 元数据路径非法");
            }
            if (!Files.exists(gitMetadata, LinkOption.NOFOLLOW_LINKS)) {
                executor.execute(
                        List.of("git", "-C", normalizedRoot.toString(), "init", "-b", "main"),
                        null,
                        DEFAULT_TIMEOUT);
            }
            if (!isGitWorkTreeRoot(normalizedRoot)) {
                throw new PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.GIT_UNAVAILABLE,
                        "本地 Git 仓库初始化失败");
            }
            if (hasHeadCommit(normalizedRoot)) {
                return;
            }
            Path seed = normalizedRoot.resolve(initialFile).normalize();
            if (!seed.startsWith(normalizedRoot) || seed.equals(normalizedRoot)) {
                throw new IllegalArgumentException("initialFile must stay inside repoRoot");
            }
            if (!Files.exists(seed, LinkOption.NOFOLLOW_LINKS)) {
                Path parent = seed.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        seed,
                        initialContent,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            }
            String relativeSeed = normalizedRoot.relativize(seed).toString().replace('\\', '/');
            stageFiles(normalizedRoot, List.of(relativeSeed), null);
            commitStaged(normalizedRoot, "初始化体验工作区", null, identity);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.GIT_UNAVAILABLE,
                    "本地 Git 仓库初始化失败",
                    Map.of(),
                    exception);
        }
    }

    /** 仓库是否已经建立 HEAD；使用 status 的初始分支标记，避免把空仓库记成 Git 失败告警。 */
    public boolean hasHeadCommit(Path repoRoot) {
        GitCommandResult status = executor.execute(
                List.of(
                        "git", "--no-optional-locks", "-C", repoRoot.toString(),
                        "status", "--porcelain=v2", "--branch", "--untracked-files=no"),
                null,
                DEFAULT_TIMEOUT);
        return status.stdoutText().lines()
                .filter(line -> line.startsWith("# branch.oid "))
                .map(line -> line.substring("# branch.oid ".length()).trim())
                .anyMatch(oid -> !"(initial)".equals(oid));
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

    /** 读取当前 HEAD 的完整提交说明，供已提交未推送记录恢复原发布说明。 */
    public String headCommitMessage(Path repoRoot) {
        GitCommandResult result = executor.execute(
                List.of("git", "-c", "log.showSignature=false", "-C", repoRoot.toString(),
                        "log", "-1", "--format=%B", "HEAD"),
                null,
                DEFAULT_TIMEOUT);
        return result.stdoutText().trim();
    }

    /**
     * 分页读取两个固定提交之间的只读提交历史；不会 fetch、刷新 index 或修改工作树。
     * baseExclusive 为空时从 endInclusive 可达的全部历史读取，调用方不得用该模式猜测业务归属。
     */
    public List<GitCommitSummary> listCommitHistory(
            Path repoRoot,
            String baseExclusive,
            String endInclusive,
            int offset,
            int limit,
            boolean includeMerges) {
        return listCommitHistory(repoRoot, baseExclusive, endInclusive, offset, limit, includeMerges, null);
    }

    /** 与无 pathspec 版本相同，但只保留触及指定仓库相对目录的提交。 */
    public List<GitCommitSummary> listCommitHistory(
            Path repoRoot,
            String baseExclusive,
            String endInclusive,
            int offset,
            int limit,
            boolean includeMerges,
            String pathPrefix) {
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("commit page must use offset >= 0 and limit between 1 and 200");
        }
        String end = requireCommitObjectId(endInclusive == null || endInclusive.isBlank() ? headCommit(repoRoot) : endInclusive);
        String revision = end;
        if (baseExclusive != null && !baseExclusive.isBlank()) {
            revision = requireCommitObjectId(baseExclusive) + ".." + end;
        }
        ArrayList<String> command = new ArrayList<>(List.of(
                "git", "--no-optional-locks", "-c", "log.showSignature=false",
                "-C", repoRoot.toString(), "log", "--date-order", "--skip=" + offset,
                "--max-count=" + limit,
                "--format=%H%x1f%P%x1f%an%x1f%ae%x1f%cn%x1f%ce%x1f%ct%x1f%s%x1e"));
        if (!includeMerges) {
            command.add("--no-merges");
        }
        command.add(revision);
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            command.add("--");
            command.add(requireReadOnlyRelativePath(pathPrefix));
        }
        String output = executor.execute(List.copyOf(command), null, DEFAULT_TIMEOUT).stdoutText();
        return parseCommitSummaries(output);
    }

    /** 读取一个固定提交修改的路径和状态，不返回文件正文。 */
    public List<GitNameStatusEntry> commitChangedFiles(Path repoRoot, String commit) {
        String output = executor.execute(
                List.of("git", "--no-optional-locks", "-c", "core.quotepath=false",
                        "-C", repoRoot.toString(), "show", "--format=", "--name-status",
                        "--no-renames", requireCommitObjectId(commit)),
                null,
                DEFAULT_TIMEOUT).stdoutText();
        return parseNameStatus(output);
    }

    /** 返回指定提交中单个安全相对路径的受限 patch；超限时明确标记截断。 */
    public String commitFileDiff(Path repoRoot, String commit, String file) {
        String normalizedFile = requireReadOnlyRelativePath(file);
        String output = executor.execute(
                List.of("git", "--no-optional-locks", "-c", "core.quotepath=false",
                        "-C", repoRoot.toString(), "show", "--format=", "--no-ext-diff",
                        "--no-renames", "--unified=3", requireCommitObjectId(commit), "--", normalizedFile),
                null,
                DEFAULT_TIMEOUT).stdoutText();
        if (output.length() <= READ_ONLY_COMMIT_DIFF_MAX_CHARS) {
            return output;
        }
        return output.substring(0, READ_ONLY_COMMIT_DIFF_MAX_CHARS)
                + "\n\n[diff truncated at " + READ_ONLY_COMMIT_DIFF_MAX_CHARS + " characters]\n";
    }

    private List<GitCommitSummary> parseCommitSummaries(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        ArrayList<GitCommitSummary> commits = new ArrayList<>();
        for (String record : output.split("\\u001e")) {
            String normalized = record.strip();
            if (normalized.isEmpty()) {
                continue;
            }
            String[] fields = normalized.split("\\u001f", -1);
            if (fields.length != 8) {
                continue;
            }
            List<String> parents = fields[1].isBlank()
                    ? List.of()
                    : List.of(fields[1].trim().split("\\s+"));
            commits.add(new GitCommitSummary(
                    fields[0], parents, fields[2], fields[3], fields[4], fields[5],
                    Instant.ofEpochSecond(Long.parseLong(fields[6])), fields[7], parents.size() > 1));
        }
        return List.copyOf(commits);
    }

    private String requireCommitObjectId(String value) {
        String normalized = Objects.requireNonNull(value, "commit must not be null").trim();
        if (!COMMIT_OBJECT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("commit object id is invalid");
        }
        return normalized;
    }

    private String requireReadOnlyRelativePath(String value) {
        String normalized = Objects.requireNonNull(value, "file must not be null").trim().replace('\\', '/');
        Path path = Path.of(normalized).normalize();
        if (normalized.isBlank() || path.isAbsolute() || path.startsWith("..")
                || normalized.equals(".git") || normalized.startsWith(".git/")) {
            throw new IllegalArgumentException("file path is invalid");
        }
        return path.toString().replace('\\', '/');
    }

    /**
     * 读取指定应用分支在本地仓库中的 origin 跟踪提交；不执行 fetch，也不访问网络。
     *
     * <p>该引用是本地 Git 对远程状态的事实快照，适合只读 Diff 判断本地 HEAD 是否包含
     * 已知远程提交。引用不存在时返回空，调用方不能用数据库 target commit 冒充远程事实。</p>
     */
    public Optional<String> remoteTrackingBranchCommit(Path repoRoot, String branch) {
        String normalizedBranch = Objects.requireNonNull(branch, "branch must not be null").trim();
        if (normalizedBranch.isEmpty()
                || normalizedBranch.indexOf('\r') >= 0
                || normalizedBranch.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("branch is invalid");
        }
        String ref = "refs/remotes/origin/" + normalizedBranch;
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet", ref),
                    null,
                    DEFAULT_TIMEOUT);
            return Optional.of(resolveCommit(repoRoot, ref));
        } catch (PlatformException exception) {
            Object exitCode = exception.details().get("exitCode");
            if (exitCode instanceof Number number && number.intValue() == 1) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /**
     * 顺序读取本地 origin 跟踪引用中的最近提交者证据；不执行 fetch，也不访问网络。
     * 调用方必须设置有界 maxCount，并在多个仓库间串行调度，避免夜间任务放大磁盘压力。
     */
    public List<GitCommitterIdentityEvidence> acceptedCommitterIdentities(Path repoRoot, int maxCount) {
        if (maxCount < 1 || maxCount > 50_000) {
            throw new IllegalArgumentException("maxCount must be between 1 and 50000");
        }
        String output = executor.execute(
                List.of(
                        "git", "-c", "log.showSignature=false", "-C", repoRoot.toString(),
                        "log", "--remotes=origin", "--date-order", "--regexp-ignore-case",
                        "--committer=@mails\\.icbc", "--max-count=" + maxCount,
                        "--format=%H%x1f%ct%x1f%cn%x1f%ce%x1e"),
                null,
                DEFAULT_TIMEOUT).stdoutText();
        return parseCommitterEvidence(output);
    }

    /** 只查一个统一认证邮箱的最近已接受提交，用于 SSH Key 新增后的低频即时校准。 */
    public Optional<GitCommitterIdentityEvidence> latestAcceptedCommitterIdentity(
            Path repoRoot,
            String email) {
        String normalizedEmail = Objects.requireNonNull(email, "email must not be null").trim();
        if (normalizedEmail.isEmpty() || normalizedEmail.indexOf('\r') >= 0 || normalizedEmail.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("email is invalid");
        }
        String output = executor.execute(
                List.of(
                        "git", "-c", "log.showSignature=false", "-C", repoRoot.toString(),
                        "log", "--remotes=origin", "--fixed-strings", "--committer=" + normalizedEmail,
                        "--max-count=1", "--format=%H%x1f%ct%x1f%cn%x1f%ce%x1e"),
                null,
                DEFAULT_TIMEOUT).stdoutText();
        return parseCommitterEvidence(output).stream()
                .filter(evidence -> normalizedEmail.equalsIgnoreCase(evidence.email()))
                .findFirst();
    }

    /** 仅改写当前 HEAD 的作者/提交者身份，供右控拒绝后的单次受控重试使用。 */
    public String amendHeadCommitIdentity(Path repoRoot, String privateKey, GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        executor.execute(
                withCommitIdentity(
                        List.of("git", "-C", repoRoot.toString(), "commit", "--amend", "--no-edit", "--reset-author"),
                        identity),
                privateKey,
                DEFAULT_TIMEOUT);
        return headCommit(repoRoot);
    }

    private List<GitCommitterIdentityEvidence> parseCommitterEvidence(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        List<GitCommitterIdentityEvidence> evidence = new ArrayList<>();
        for (String record : output.split("\\u001e")) {
            String normalized = record.strip();
            if (normalized.isEmpty()) {
                continue;
            }
            String[] fields = normalized.split("\\u001f", -1);
            if (fields.length != 4) {
                continue;
            }
            try {
                evidence.add(new GitCommitterIdentityEvidence(
                        fields[2], fields[3], Instant.ofEpochSecond(Long.parseLong(fields[1])), fields[0]));
            } catch (IllegalArgumentException ignored) {
                // 单条历史提交元数据异常时跳过，不让补偿任务中断其它用户和仓库。
            }
        }
        return List.copyOf(evidence);
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

    /** 团队只读视图仅接受固定对象 ID，避免把数据库文本解释为 Git option 或可移动 ref。 */
    public boolean isCommitAncestor(Path repoRoot, String ancestor, String descendant) {
        return isAncestor(repoRoot, requireCommitObjectId(ancestor), requireCommitObjectId(descendant));
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
     * 只提交指定路径的当前工作树内容，不把共享 index 中其他人已暂存的文件带入本次提交。
     */
    public void commitFilesOnly(
            Path repoRoot,
            List<String> files,
            String message,
            String privateKey,
            GitCommitIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (files == null || files.isEmpty()) {
            return;
        }
        ArrayList<String> command = new ArrayList<>(List.of(
                "git", "-C", repoRoot.toString(), "commit", "--only", "-m", message, "--"));
        command.addAll(files);
        executor.execute(withCommitIdentity(List.copyOf(command), identity), privateKey, DEFAULT_TIMEOUT);
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
        if (localRefExists(repoRoot, localRef)) {
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

    private boolean localRefExists(Path repoRoot, String localRef) {
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
     * 直接比较两个提交树的文件变化，不使用 merge-base；公共运行副本切换必须识别旧树被删除的 Tool 文件。
     */
    public String diffNameStatusBetweenTrees(Path repoRoot, String from, String to) {
        return executor.execute(
                gitNoQuotedPath(repoRoot, "diff", "--name-status", "-M", from, to),
                null,
                DEFAULT_TIMEOUT).stdoutText();
    }

    /**
     * 解析 name-status；rename/copy 同时保留旧路径和新路径，调用方可据此正确投影删除与新增。
     */
    public List<GitNameStatusEntry> parseNameStatus(String nameStatus) {
        if (nameStatus == null || nameStatus.isBlank()) {
            return List.of();
        }
        List<GitNameStatusEntry> entries = new ArrayList<>();
        for (String line : nameStatus.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length < 2 || fields[0].isBlank()) {
                continue;
            }
            char status = fields[0].charAt(0);
            String oldPath = null;
            String path;
            if ((status == 'R' || status == 'C') && fields.length >= 3) {
                oldPath = unquotePorcelainPath(fields[1]);
                path = unquotePorcelainPath(fields[2]);
            } else {
                path = unquotePorcelainPath(fields[1]);
            }
            if (path.isBlank()) {
                continue;
            }
            entries.add(new GitNameStatusEntry(status, fields[0], oldPath, path));
        }
        return List.copyOf(entries);
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

    /** 返回不会获取可选锁、刷新 index stat/fsmonitor 或写入 untracked cache 的 porcelain 状态。 */
    public String statusPorcelainReadOnly(Path repoRoot) {
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
        return result.stdoutText();
    }

    /** 返回指定安全 pathspec 下不会修改 index 的 porcelain 状态。 */
    public String statusPorcelainReadOnly(Path repoRoot, String pathspec) {
        String normalized = requireReadOnlyRelativePath(pathspec);
        GitCommandResult result = executor.execute(
                List.of(
                        "git", "--no-optional-locks", "-c", "core.quotepath=false",
                        "-c", "core.untrackedCache=false", "-c", "core.fsmonitor=false",
                        "-C", repoRoot.toString(), "status", "--porcelain",
                        "--untracked-files=all", "--", normalized),
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
     * 为平台只读 Git 视图收集指定条目的 Diff。
     *
     * <p>命令只携带调用方已授权的精确 pathspec，并禁用 external diff/textconv，避免仓库本地配置
     * 把受控目录内容拼进其它文件的 patch；同时不获取可选锁或启用会写缓存的 Git 能力。</p>
     */
    public List<GitDiffFile> collectDiffFilesReadOnly(Path repoRoot, List<GitStatusEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<String> stagedFiles = entries.stream()
                .filter(entry -> !entry.untrackedFile() && entry.staged())
                .map(GitStatusEntry::path)
                .toList();
        List<String> unstagedFiles = entries.stream()
                .filter(entry -> !entry.untrackedFile() && entry.needsUnstagedDiff())
                .map(GitStatusEntry::path)
                .toList();
        Map<String, String> stagedDiffs = readOnlyDiffMap(repoRoot, stagedFiles, true);
        Map<String, String> unstagedDiffs = readOnlyDiffMap(repoRoot, unstagedFiles, false);

        List<GitDiffFile> files = new ArrayList<>();
        ReadOnlyPatchBudget patchBudget = new ReadOnlyPatchBudget();
        for (GitStatusEntry entry : entries) {
            DiffAccumulator accumulator = new DiffAccumulator();
            if (entry.untrackedFile()) {
                appendNewFilePatchReadOnly(entry.path(), repoRoot.resolve(entry.path()), accumulator, patchBudget);
            } else {
                if (entry.staged()) {
                    appendReadOnlyDiff(
                            repoRoot,
                            entry.path(),
                            true,
                            stagedDiffs.get(entry.path()),
                            accumulator,
                            patchBudget);
                }
                if (entry.needsUnstagedDiff()) {
                    appendReadOnlyDiff(
                            repoRoot,
                            entry.path(),
                            false,
                            unstagedDiffs.get(entry.path()),
                            accumulator,
                            patchBudget);
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

    /** 执行禁用可选锁、external diff 与 textconv 的精确 pathspec Diff。 */
    public String diffReadOnly(Path repoRoot, List<String> files, boolean staged) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        ArrayList<String> command = new ArrayList<>(List.of(
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
                "diff",
                "--no-ext-diff",
                "--no-textconv"));
        if (staged) {
            command.add("--cached");
        }
        command.add("--");
        command.addAll(files);
        return executor.execute(command, null, DEFAULT_TIMEOUT).stdoutText();
    }

    private Map<String, String> readOnlyDiffMap(Path repoRoot, List<String> files, boolean staged) {
        if (files.isEmpty()) {
            return Map.of();
        }
        try {
            return parseFullDiff(diffReadOnly(repoRoot, files, staged));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void appendReadOnlyDiff(
            Path repoRoot,
            String file,
            boolean staged,
            String batchDiff,
            DiffAccumulator accumulator,
            ReadOnlyPatchBudget patchBudget) {
        if (batchDiff != null && !batchDiff.isBlank()) {
            patchBudget.append(accumulator, batchDiff);
            return;
        }
        if (patchBudget.exhausted()) {
            return;
        }
        try {
            String output = diffReadOnly(repoRoot, List.of(file), staged);
            if (output != null && !output.isBlank()) {
                patchBudget.append(accumulator, output);
            }
        } catch (Exception ignored) {
            // 单文件只读 diff 失败不影响其它文件状态展示。
        }
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

    /**
     * 体验区未跟踪文件只生成有界文本 patch。超大、多行、二进制或并发变化文件仍保留状态，
     * 但不把正文复制进 JVM 堆与 HTTP 响应。
     */
    private void appendNewFilePatchReadOnly(
            String gitPath,
            Path filePath,
            DiffAccumulator accumulator,
            ReadOnlyPatchBudget patchBudget) {
        if (patchBudget.exhausted() || !Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            if (Files.size(filePath) > READ_ONLY_UNTRACKED_PATCH_MAX_BYTES) {
                return;
            }
            byte[] bytes;
            try (var input = Files.newInputStream(filePath)) {
                bytes = input.readNBytes(READ_ONLY_UNTRACKED_PATCH_MAX_BYTES + 1);
            }
            if (bytes.length > READ_ONLY_UNTRACKED_PATCH_MAX_BYTES || containsNul(bytes)) {
                return;
            }
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            List<String> lines = content.lines()
                    .limit(READ_ONLY_UNTRACKED_PATCH_MAX_LINES + 1L)
                    .toList();
            if (lines.size() > READ_ONLY_UNTRACKED_PATCH_MAX_LINES) {
                return;
            }
            StringBuilder diff = new StringBuilder(Math.min(
                    READ_ONLY_PATCH_MAX_FILE_CHARS,
                    content.length() + gitPath.length() + 64));
            diff.append("--- /dev/null\n");
            diff.append("+++ b/").append(gitPath).append('\n');
            diff.append("@@ -0,0 +1,").append(lines.size()).append(" @@\n");
            for (String line : lines) {
                diff.append('+').append(line).append('\n');
                if (diff.length() > READ_ONLY_PATCH_MAX_FILE_CHARS) {
                    return;
                }
            }
            patchBudget.append(accumulator, diff.toString());
        } catch (Exception ignored) {
            // 文件并发变化、解码或读取失败时只省略 patch，状态列表仍可返回。
        }
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
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
     * 判断 index 相对 HEAD 是否存在真实变更。发布重试必须先调用该方法，不能依赖
     * `git commit` 的空提交 stderr 文案，因为不同 Git/语言环境下文案并不稳定。
     */
    public boolean hasStagedChanges(Path repoRoot, String privateKey) {
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "diff", "--cached", "--quiet", "--exit-code"),
                    privateKey,
                    DEFAULT_TIMEOUT);
            return false;
        } catch (PlatformException exception) {
            Object exitCode = exception.details().get("exitCode");
            if (exitCode instanceof Number number && number.intValue() == 1) {
                return true;
            }
            throw exception;
        }
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
     * 列出指定提交和目录前缀下的普通 Git blob 路径。Hub 只从已 push 的不可变提交取材，
     * 不读取可能继续变化的工作树。使用 NUL 分隔读取原始 UTF-8 路径，避免 Git 默认
     * quotepath 把中文文件名转换成带引号的 C 风格展示文本，导致后续按提交读取 blob 失败。
     */
    public List<String> listFilesAtCommit(Path repoRoot, String commit, String pathPrefix) {
        String prefix = pathPrefix == null ? "" : pathPrefix.replace('\\', '/');
        byte[] output = executor.execute(
                        List.of("git", "-C", repoRoot.toString(), "ls-tree", "-r", "--name-only", "-z", commit, "--", prefix),
                        null,
                        DEFAULT_TIMEOUT)
                .stdoutBytes();
        return java.util.Arrays.stream(new String(output, StandardCharsets.UTF_8).split("\\u0000", -1))
                .filter(path -> !path.isEmpty())
                .toList();
    }

    /** 读取指定提交中的原始 blob 字节，供 Hub 构造不可变内容制品。 */
    public byte[] readFileAtCommit(Path repoRoot, String commit, String file) {
        return executor.execute(
                List.of("git", "-C", repoRoot.toString(), "show", commit + ":" + file),
                null,
                DEFAULT_TIMEOUT).stdoutBytes();
    }

    /**
     * 使用 Git 自带的 diff3 算法做纯文本三方合并。返回值携带冲突标记但不接触真实工作树，
     * Hub 可在所有冲突确认完成前保持目标个人 worktree 不变。
     */
    public MergeTextResult mergeText(String base, String current, String incoming) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("test-agent-hub-merge-");
            Path currentFile = directory.resolve("current.txt");
            Path baseFile = directory.resolve("base.txt");
            Path incomingFile = directory.resolve("incoming.txt");
            Files.writeString(currentFile, current == null ? "" : current, StandardCharsets.UTF_8);
            Files.writeString(baseFile, base == null ? "" : base, StandardCharsets.UTF_8);
            Files.writeString(incomingFile, incoming == null ? "" : incoming, StandardCharsets.UTF_8);
            try {
                String merged = executor.execute(
                        List.of("git", "merge-file", "-p", "-L", "当前引用", "-L", "引用基线", "-L", "Hub 最新版",
                                currentFile.toString(), baseFile.toString(), incomingFile.toString()),
                        null,
                        DEFAULT_TIMEOUT).stdoutText();
                return new MergeTextResult(merged, false);
            } catch (PlatformException exception) {
                Object exitCode = exception.details().get("exitCode");
                if (exitCode instanceof Number number && number.intValue() == 1) {
                    // ProcessGitCommandExecutor 的失败摘要不保留 stdout，因此用原生命令仅处理本机临时文件，
                    // 不携带密钥和用户路径；退出码 1 是 git merge-file 的正常冲突语义。
                    Process process = new ProcessBuilder(
                            "git", "merge-file", "-p", "-L", "当前引用", "-L", "引用基线", "-L", "Hub 最新版",
                            currentFile.toString(), baseFile.toString(), incomingFile.toString()).start();
                    byte[] output = process.getInputStream().readAllBytes();
                    int status = process.waitFor();
                    if (status == 1) {
                        return new MergeTextResult(new String(output, StandardCharsets.UTF_8), true);
                    }
                }
                throw exception;
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.GIT_UNAVAILABLE,
                    "执行 Hub 三方合并失败",
                    Map.of(),
                    exception);
        } finally {
            if (directory != null) {
                try (var files = Files.walk(directory)) {
                    files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // 临时合并目录清理失败不改变已计算结果。
                        }
                    });
                } catch (Exception ignored) {
                    // 临时目录清理由操作系统兜底。
                }
            }
        }
    }

    public record MergeTextResult(String content, boolean conflicted) {
    }

    /**
     * 捕获不修改工作树的 Git 状态指纹。stash create 只写入临时 Git 对象，不更新 stash ref、index 或文件。
     */
    public PortableTrackedState capturePortableTrackedState(Path worktreeRoot) {
        if (isMergeInProgress(worktreeRoot) || !conflictPaths(worktreeRoot).isEmpty()) {
            throw new PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.CONFLICT,
                    "个人工作区存在未完成合并，暂不能自动搬迁",
                    Map.of("gitFailureType", "UNMERGED_WORKTREE"));
        }
        if (hasIndexedSubmodule(worktreeRoot)) {
            throw new PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.CONFLICT,
                    "个人工作区包含 Git 子模块，暂不能自动搬迁",
                    Map.of("gitFailureType", "UNSUPPORTED_GIT_SUBMODULE"));
        }
        String headCommit = headCommit(worktreeRoot);
        String indexTree = executor.execute(
                List.of("git", "-C", worktreeRoot.toString(), "write-tree"),
                null,
                DEFAULT_TIMEOUT).stdoutText().trim();
        String stashCommit = executor.execute(
                List.of("git", "-C", worktreeRoot.toString(), "stash", "create"),
                null,
                DEFAULT_TIMEOUT).stdoutText().trim();
        String worktreeTree = stashCommit.isBlank()
                ? resolveCommit(worktreeRoot, headCommit + "^{tree}")
                : resolveCommit(worktreeRoot, stashCommit + "^{tree}");
        return new PortableTrackedState(
                headCommit,
                indexTree,
                worktreeTree,
                stashCommit.isBlank() ? null : stashCommit);
    }

    /** 以 NUL 分隔读取全部未跟踪文件路径（包括被 ignore 的文件），避免空格和换行造成路径拆分。 */
    public List<String> untrackedPaths(Path worktreeRoot) {
        byte[] output = executor.execute(
                List.of(
                        "git", "-c", "core.quotepath=false", "-C", worktreeRoot.toString(),
                        "ls-files", "--others", "-z"),
                null,
                DEFAULT_TIMEOUT).stdoutBytes();
        List<String> paths = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < output.length; index++) {
            if (output[index] == 0) {
                if (index > start) {
                    paths.add(new String(output, start, index - start, StandardCharsets.UTF_8));
                }
                start = index + 1;
            }
        }
        if (start < output.length) {
            paths.add(new String(output, start, output.length - start, StandardCharsets.UTF_8));
        }
        return List.copyOf(paths);
    }

    /**
     * Git bundle 只能携带超级仓库中的 gitlink，不包含子模块内部的本地工作态。
     * 源端删除 worktree 前必须失败关闭，避免已初始化或有未提交内容的子模块被误删。
     */
    private boolean hasIndexedSubmodule(Path worktreeRoot) {
        byte[] output = executor.execute(
                List.of(
                        "git", "-c", "core.quotepath=false", "-C", worktreeRoot.toString(),
                        "ls-files", "--stage", "-z"),
                null,
                DEFAULT_TIMEOUT).stdoutBytes();
        int recordStart = 0;
        for (int index = 0; index <= output.length; index++) {
            if (index < output.length && output[index] != 0) {
                continue;
            }
            if (index - recordStart >= 7
                    && output[recordStart] == '1'
                    && output[recordStart + 1] == '6'
                    && output[recordStart + 2] == '0'
                    && output[recordStart + 3] == '0'
                    && output[recordStart + 4] == '0'
                    && output[recordStart + 5] == '0'
                    && output[recordStart + 6] == ' ') {
                return true;
            }
            recordStart = index + 1;
        }
        return false;
    }

    /**
     * 把 HEAD 与可选 stash commit 写入只包含两条临时引用的 bundle，finally 中删除源仓库临时 ref。
     */
    public void createPortableBundle(
            Path worktreeRoot,
            Path bundlePath,
            String headRef,
            String stashRef,
            PortableTrackedState state) {
        requireRelocationRef(headRef, "head");
        requireRelocationRef(stashRef, "stash");
        Objects.requireNonNull(state, "state must not be null");
        try {
            updateRef(worktreeRoot, headRef, state.headCommit());
            if (state.stashCommit() != null) {
                updateRef(worktreeRoot, stashRef, state.stashCommit());
            }
            ArrayList<String> command = new ArrayList<>(List.of(
                    "git", "-C", worktreeRoot.toString(), "bundle", "create", bundlePath.toString(), headRef));
            if (state.stashCommit() != null) {
                command.add(stashRef);
            }
            executor.execute(List.copyOf(command), null, BUNDLE_TIMEOUT);
        } finally {
            deleteRefQuietly(worktreeRoot, headRef);
            deleteRefQuietly(worktreeRoot, stashRef);
        }
    }

    /** 从搬迁 bundle 获取指定引用到目标公共仓库的隔离临时引用。 */
    public void fetchPortableBundleRef(
            Path repoRoot, Path bundlePath, String sourceRef, String targetRef) {
        requireAnyRelocationRef(sourceRef);
        requireAnyRelocationRef(targetRef);
        executor.execute(
                List.of(
                        "git", "-C", repoRoot.toString(), "fetch", bundlePath.toString(),
                        sourceRef + ":" + targetRef),
                null,
                BUNDLE_TIMEOUT);
    }

    /** 在隔离分支上从指定提交创建目标端暂存 worktree。 */
    public void createWorktreeAtCommit(Path repoRoot, Path worktreeRoot, String branch, String commitRef) {
        executor.execute(
                List.of(
                        "git", "-C", repoRoot.toString(), "worktree", "add", "-b", branch,
                        worktreeRoot.toString(), commitRef),
                null,
                DEFAULT_TIMEOUT);
    }

    /** 使用 stash 原生三父提交恢复工作树和 index，保留 staged/unstaged 的区别。 */
    public void applyPortableStash(Path worktreeRoot, String stashRef) {
        requireAnyRelocationRef(stashRef);
        executor.execute(
                List.of("git", "-C", worktreeRoot.toString(), "stash", "apply", "--index", stashRef),
                null,
                BUNDLE_TIMEOUT);
    }

    /** 把目标端暂存 worktree 原子登记移动到最终目录。 */
    public void moveWorktree(Path repoRoot, Path sourceWorktreeRoot, Path targetWorktreeRoot) {
        executor.execute(
                List.of(
                        "git", "-C", repoRoot.toString(), "worktree", "move",
                        sourceWorktreeRoot.toString(), targetWorktreeRoot.toString()),
                null,
                DEFAULT_TIMEOUT);
    }

    /** 返回分支当前登记的 worktree；空表示分支不存在或未被任何 worktree 使用。 */
    public Optional<Path> worktreePathForBranch(Path repoRoot, String branch) {
        return Optional.ofNullable(registeredWorktreePathForBranch(repoRoot, branch));
    }

    /** 判断本地分支是否存在，不把 show-ref 的标准“未命中”退出码误报成仓库故障。 */
    public boolean localBranchExists(Path repoRoot, String branch) {
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet", "refs/heads/" + branch),
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

    /** 删除未被 worktree 使用的旧本地分支；调用方必须先完成登记路径校验。 */
    public void deleteLocalBranch(Path repoRoot, String branch) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "branch", "-D", branch),
                null,
                DEFAULT_TIMEOUT);
    }

    /** 将当前暂存分支改回个人工作区原始分支名。 */
    public void renameCurrentBranch(Path worktreeRoot, String branch) {
        executor.execute(
                List.of("git", "-C", worktreeRoot.toString(), "branch", "-m", branch),
                null,
                DEFAULT_TIMEOUT);
    }

    /** 删除目标仓库搬迁临时引用。 */
    public void deletePortableRef(Path repoRoot, String ref) {
        requireAnyRelocationRef(ref);
        deleteRefQuietly(repoRoot, ref);
    }

    private void updateRef(Path repoRoot, String ref, String commit) {
        executor.execute(
                List.of("git", "-C", repoRoot.toString(), "update-ref", ref, commit),
                null,
                DEFAULT_TIMEOUT);
    }

    private void deleteRefQuietly(Path repoRoot, String ref) {
        try {
            executor.execute(
                    List.of("git", "-C", repoRoot.toString(), "update-ref", "-d", ref),
                    null,
                    DEFAULT_TIMEOUT);
        } catch (RuntimeException ignored) {
            // 临时 ref 清理由下一次同 ID 操作覆盖；不得掩盖主搬迁结果。
        }
    }

    private void requireRelocationRef(String ref, String suffix) {
        requireAnyRelocationRef(ref);
        if (!ref.endsWith("/" + suffix)) {
            throw new IllegalArgumentException("relocation ref suffix mismatch");
        }
    }

    private void requireAnyRelocationRef(String ref) {
        if (ref == null || !RELOCATION_REF.matcher(ref).matches()) {
            throw new IllegalArgumentException("invalid relocation ref");
        }
    }

    public record PortableTrackedState(
            String headCommit,
            String indexTree,
            String worktreeTree,
            String stashCommit) {

        public PortableTrackedState {
            headCommit = requireObjectId(headCommit, "headCommit");
            indexTree = requireObjectId(indexTree, "indexTree");
            worktreeTree = requireObjectId(worktreeTree, "worktreeTree");
            stashCommit = stashCommit == null ? null : requireObjectId(stashCommit, "stashCommit");
        }

        private static String requireObjectId(String value, String name) {
            if (value == null || !value.matches("^[0-9a-fA-F]{40,64}$")) {
                throw new IllegalArgumentException(name + " must be a Git object id");
            }
            return value.toLowerCase(java.util.Locale.ROOT);
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
        pruneWorktrees(repoRoot, privateKey);
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

    /** 团队只读代码视图使用的固定提交摘要。 */
    public record GitCommitSummary(
            String commit,
            List<String> parents,
            String authorName,
            String authorEmail,
            String committerName,
            String committerEmail,
            Instant committedAt,
            String subject,
            boolean merge) {
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

    /** 两提交树之间的一条 name-status 记录；非 rename/copy 时 oldPath 为空。 */
    public record GitNameStatusEntry(
            char status,
            String rawStatus,
            String oldPath,
            String path) {
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

        private int patchLength() {
            return patch.length();
        }
    }

    /** 单文件和整次只读 Diff 共用预算，防止多文件分别命中上限后聚合放大。 */
    private static final class ReadOnlyPatchBudget {
        private int remainingChars = READ_ONLY_PATCH_MAX_TOTAL_CHARS;

        private boolean append(DiffAccumulator accumulator, String diff) {
            int separatorChars = accumulator.patchLength() == 0 ? 0 : 1;
            int appendedChars = separatorChars + diff.length();
            if (appendedChars > remainingChars
                    || accumulator.patchLength() + appendedChars > READ_ONLY_PATCH_MAX_FILE_CHARS) {
                return false;
            }
            accumulator.append(diff);
            remainingChars -= appendedChars;
            return true;
        }

        private boolean exhausted() {
            return remainingChars <= 0;
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
