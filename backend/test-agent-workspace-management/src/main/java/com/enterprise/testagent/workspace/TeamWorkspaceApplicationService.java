package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.GitCommitIdentity;
import com.enterprise.testagent.common.git.GitWorkspaceService.GitCommitSummary;
import com.enterprise.testagent.common.git.GitWorkspaceService.GitDiffFile;
import com.enterprise.testagent.common.git.GitWorkspaceService.GitNameStatusEntry;
import com.enterprise.testagent.common.git.GitWorkspaceService.GitStatusEntry;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.team.TeamContributionType;
import com.enterprise.testagent.domain.team.TeamWorkspaceQueryRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.MemberContributionView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.PersonalWorkspaceView;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 系统管理员团队代码视图的本地只读能力。
 *
 * <p>团队范围授权由 API 层的实时权限服务完成，本服务仍在每次调用时通过带范围的查询端口锁定
 * 个人 worktree，并要求目标 worktree 位于当前节点。所有 Git 命令均为只读命令。</p>
 */
@Service
public class TeamWorkspaceApplicationService {

    private final TeamWorkspaceQueryRepository queries;
    private final ManagedWorkspacePathResolver pathResolver;
    private final WorkspaceServerIdentity serverIdentity;
    private final GitWorkspaceService git;
    private final ScmGitIdentityResolver scmGitIdentityResolver;
    private WorkspaceFileService reviewFiles = new WorkspaceFileService();

    /** 审阅元数据继续使用公共安全文件内核，兼容原有直接构造测试。 */
    @Autowired
    void setReviewFiles(WorkspaceFileService reviewFiles) { this.reviewFiles = reviewFiles; }

    @Autowired
    public TeamWorkspaceApplicationService(
            TeamWorkspaceQueryRepository queries,
            CommonParameterValues commonParameterValues,
            WorkspaceServerIdentity serverIdentity,
            ScmGitIdentityResolver scmGitIdentityResolver) {
        this(queries, new ManagedWorkspacePathResolver(commonParameterValues), serverIdentity,
                new GitWorkspaceService(), scmGitIdentityResolver);
    }

    TeamWorkspaceApplicationService(
            TeamWorkspaceQueryRepository queries,
            ManagedWorkspacePathResolver pathResolver,
            WorkspaceServerIdentity serverIdentity,
            GitWorkspaceService git,
            ScmGitIdentityResolver scmGitIdentityResolver) {
        this.queries = Objects.requireNonNull(queries, "queries must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.git = Objects.requireNonNull(git, "git must not be null");
        this.scmGitIdentityResolver = Objects.requireNonNull(
                scmGitIdentityResolver, "scmGitIdentityResolver must not be null");
    }

    /**
     * target 为空时返回团队级列表；传入后只返回该成员当前或历史可审阅的应用。
     * 成员状态随列表返回，供菜单标记历史范围。
     */
    public List<TeamWorkspaceResponses.ApplicationResponse> applications(
            boolean global, UserId owner, UserId target) {
        return queries.findApplications(global, owner, target).stream()
                .map(value -> new TeamWorkspaceResponses.ApplicationResponse(
                        value.appId(), value.appName(), value.enabled(),
                        value.currentMemberCount(), value.historicalMemberCount(),
                        value.membershipState().name()))
                .toList();
    }

    public List<TeamWorkspaceResponses.WorkspaceTemplateResponse> workspaceTemplates(
            boolean global, UserId owner, String appId, UserId target) {
        return queries.findWorkspaceTemplates(global, owner, appId, target).stream()
                .map(value -> new TeamWorkspaceResponses.WorkspaceTemplateResponse(
                        value.workspaceId(), value.appId(), value.workspaceName(), value.branch(),
                        value.directoryPath(), value.enabled(), value.membershipState().name()))
                .toList();
    }

    public List<TeamWorkspaceResponses.WorkspaceVersionResponse> versions(
            boolean global, UserId owner, String workspaceId, UserId target) {
        return queries.findWorkspaceVersions(global, owner, workspaceId, target).stream()
                .map(value -> {
                    var version = value.version();
                    return new TeamWorkspaceResponses.WorkspaceVersionResponse(
                            version.versionId().value(), version.applicationWorkspaceId().value(),
                            version.appId().value(), version.version(), version.branch(), version.status().name(),
                            version.targetCommitHash(), version.updatedAt(), value.membershipState().name());
                })
                .toList();
    }

    public List<TeamWorkspaceResponses.ContributionResponse> contributions(
            boolean global, UserId owner, String versionId) {
        return queries.findContributions(global, owner, versionId).stream().map(this::contribution).toList();
    }

    public PersonalWorkspaceView personalWorkspace(boolean global, UserId owner, String personalWorkspaceId) {
        return queries.findPersonalWorkspace(global, owner, new PersonalWorkspaceId(personalWorkspaceId))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND, "团队范围内的个人工作区不存在",
                        Map.of("personalWorkspaceId", personalWorkspaceId)));
    }

    /** 一层来源目录；Git 时间与未提交文件时间明确分离，不读取符号链接或敏感凭据。 */
    public List<com.enterprise.testagent.domain.team.TeamReviewModels.File> reviewList(
            boolean global, UserId owner, String personalWorkspaceId, String path) {
        LocalWorkspace context = localWorkspace(global, owner, personalWorkspaceId);
        String directory = TeamReviewApplicationService.relativePath(path, true);
        List<FileTreeEntryResponse> entries;
        try {
            entries = reviewFiles.listDirectory(context.workspaceRoot().toString(), directory, 1001);
        } catch (PlatformException exception) {
            if (!directory.isBlank() && exception.errorCode() == ErrorCode.NOT_FOUND) return List.of();
            throw exception;
        }
        if (entries.size() > 1000) throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                "审阅目录超过 1000 项，请缩小目录范围；结果未截断为完整目录");
        var statuses = git.parseStatusPorcelain(git.statusPorcelainReadOnly(context.repoRoot()));
        java.util.Set<String> dirty = statuses.stream().map(item -> displayPath(context, item.path()))
                .collect(java.util.stream.Collectors.toSet());
        List<com.enterprise.testagent.domain.team.TeamReviewModels.File> result = new java.util.ArrayList<>();
        for (FileTreeEntryResponse entry : entries) {
            String file = entry.path().replace('\\', '/');
            if (reviewProtected(file)) continue;
            if (entry.directory()) {
                result.add(new com.enterprise.testagent.domain.team.TeamReviewModels.File(file, entry.name(), true,
                        0, null, "directory", null, null, "UNKNOWN", "DIRECTORY", false));
                continue;
            }
            var history = dirty.contains(file) ? List.<GitCommitSummary>of()
                    : git.listCommitHistory(context.repoRoot(), null, null, 0, 1, true, gitPath(context, file));
            GitCommitSummary change = history.isEmpty() ? null : history.getFirst();
            result.add(new com.enterprise.testagent.domain.team.TeamReviewModels.File(file, entry.name(), false,
                    entry.size(), entry.lastModifiedAt(), reviewVersion(context, file, entry.size(), entry.lastModifiedAt()),
                    change == null ? null : change.authorName(), change == null ? null : change.committedAt(),
                    change == null ? "FILE_TIME" : "GIT_COMMIT", dirty.contains(file) ? "UNCOMMITTED" : "COMMITTED", false));
        }
        // 缺少文件不是全局删除；只保留 Git 明确记录的本来源删除候选。
        for (var status : statuses) {
            String file = displayPath(context, status.path());
            int slash = file.lastIndexOf('/');
            String parent = slash < 0 ? "" : file.substring(0, slash);
            if (parent.equals(directory) && status.rawStatus().contains("D") && visibleEntry(context, status.path())
                    && !reviewProtected(file) && result.stream().noneMatch(item -> item.path().equals(file))) {
                result.add(new com.enterprise.testagent.domain.team.TeamReviewModels.File(file,
                        slash < 0 ? file : file.substring(slash + 1), false, 0, null,
                        "deleted", null, null, "UNKNOWN", "UNCOMMITTED", true));
            }
        }
        return List.copyOf(result);
    }

    /** 读取前重新计算版本；分片仍由公共文件内核执行大小/mtime/UTF-8 边界校验。 */
    public FilePreviewChunkResponse reviewRead(boolean global, UserId owner, String personalWorkspaceId,
            String path, String expectedVersion, long offset) {
        LocalWorkspace context = localWorkspace(global, owner, personalWorkspaceId);
        String file = TeamReviewApplicationService.relativePath(path, false);
        if (reviewProtected(file)) throw new PlatformException(ErrorCode.FORBIDDEN, "审阅不能读取敏感凭据");
        var first = reviewFiles.readBinaryChunk(context.workspaceRoot().toString(), file, 0, null, null);
        String current = reviewVersion(context, file, first.size(), java.time.Instant.ofEpochMilli(first.lastModifiedMillis()));
        if (!java.util.Objects.equals(current, expectedVersion))
            throw new PlatformException(ErrorCode.CONFLICT, "来源文件已变化，请刷新审阅目录");
        return reviewFiles.readContentChunk(context.workspaceRoot().toString(), file, offset,
                first.size(), first.lastModifiedMillis());
    }

    private String reviewVersion(LocalWorkspace context, String file, long size, java.time.Instant time) {
        return reviewFiles.contentVersion(context.workspaceRoot().toString(), file, size, time.toEpochMilli());
    }

    private boolean reviewProtected(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        for (String part : lower.split("/")) {
            if (part.equals(".git") || part.startsWith(".env") || part.equals(".ssh")
                    || java.util.Set.of("id_rsa", "id_dsa", "id_ecdsa", "id_ed25519").contains(part)
                    || part.matches(".*\\.(pem|key|p12|pfx|keystore)")) return true;
        }
        return false;
    }

    public TeamWorkspaceResponses.GitStatusResponse status(
            boolean global, UserId owner, String personalWorkspaceId) {
        LocalWorkspace context = localWorkspace(global, owner, personalWorkspaceId);
        try {
            String porcelain = context.pathspec().isBlank()
                    ? git.statusPorcelainReadOnly(context.repoRoot())
                    : git.statusPorcelainReadOnly(context.repoRoot(), context.pathspec());
            List<GitStatusEntry> entries = git.parseStatusPorcelain(porcelain).stream()
                    .filter(entry -> visibleEntry(context, entry.path()))
                    .toList();
            List<GitDiffFile> diff = git.collectDiffFilesReadOnly(context.repoRoot(), entries);
            int staged = (int) entries.stream().filter(entry -> !entry.untrackedFile() && entry.staged()).count();
            int untracked = (int) entries.stream().filter(GitStatusEntry::untrackedFile).count();
            int unstaged = (int) entries.stream()
                    .filter(entry -> !entry.untrackedFile()
                            && entry.worktreeStatus() != ' ' && entry.worktreeStatus() != '?')
                    .count();
            return new TeamWorkspaceResponses.GitStatusResponse(
                    diff.stream().map(file -> new TeamWorkspaceResponses.GitDiffFileResponse(
                            displayPath(context, file.path()), file.rawStatus(), file.status(), file.staged(),
                            file.patch(), file.additions(), file.deletions())).toList(),
                    staged, unstaged, untracked);
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.GIT_UNAVAILABLE, "读取团队工作区 Git 状态失败", Map.of(), exception);
        }
    }

    public TeamWorkspaceResponses.CommitPageResponse commits(
            boolean global,
            UserId owner,
            String personalWorkspaceId,
            String kind,
            int offset,
            int limit) {
        LocalWorkspace context = localWorkspace(global, owner, personalWorkspaceId);
        boolean published = "PUBLISHED".equalsIgnoreCase(kind);
        String end = published ? context.version().targetCommitHash() : git.headCommit(context.repoRoot());
        if (end == null || end.isBlank() || context.personal().baseCommit().isBlank()) {
            return new TeamWorkspaceResponses.CommitPageResponse(
                    List.of(), offset, limit, false, !published,
                    published ? "版本目标提交或个人工作区基线缺失，无法归属" : null);
        }
        if (!git.isCommitAncestor(context.repoRoot(), context.personal().baseCommit(), end)) {
            return new TeamWorkspaceResponses.CommitPageResponse(
                    List.of(), offset, limit, false, false, "个人工作区基线与版本分支祖先关系无法确认");
        }
        GitCommitIdentity identity = context.identity();
        CommitSlice slice = published
                ? publishedPage(context, end, identity, offset, limit)
                : personalPage(context, end, offset, limit);
        List<TeamWorkspaceResponses.CommitResponse> items = slice.items().stream()
                .map(value -> commit(value, value.merge()
                        ? TeamContributionType.SYNC_MERGE
                        : (published ? TeamContributionType.PUBLISHED_COMMIT : TeamContributionType.PERSONAL_COMMIT)))
                .toList();
        return new TeamWorkspaceResponses.CommitPageResponse(
                items, offset, limit, slice.hasMore(), true, null);
    }

    /** 已发布提交按成员 SCM 身份过滤后再分页，避免其它提交人穿插时出现漏页或错误 hasMore。 */
    private CommitSlice publishedPage(
            LocalWorkspace context, String end, GitCommitIdentity identity, int offset, int limit) {
        int rawOffset = 0;
        int matched = 0;
        java.util.ArrayList<GitCommitSummary> selected = new java.util.ArrayList<>();
        while (selected.size() <= limit) {
            List<GitCommitSummary> batch = git.listCommitHistory(
                    context.repoRoot(), context.personal().baseCommit(), end,
                    rawOffset, 200, true, context.pathspec());
            if (batch.isEmpty()) break;
            for (GitCommitSummary value : batch) {
                if (!matchesIdentity(value, identity)) continue;
                if (matched++ < offset) continue;
                selected.add(value);
                if (selected.size() > limit) break;
            }
            if (selected.size() > limit || batch.size() < 200) break;
            rawOffset += batch.size();
        }
        boolean hasMore = selected.size() > limit;
        return new CommitSlice(List.copyOf(selected.subList(0, Math.min(limit, selected.size()))), hasMore);
    }

    private CommitSlice personalPage(LocalWorkspace context, String end, int offset, int limit) {
        int firstLimit = Math.min(200, limit + 1);
        java.util.ArrayList<GitCommitSummary> selected = new java.util.ArrayList<>(git.listCommitHistory(
                context.repoRoot(), context.personal().baseCommit(), end,
                offset, firstLimit, true, context.pathspec()));
        if (limit == 200 && selected.size() == 200) {
            selected.addAll(git.listCommitHistory(
                    context.repoRoot(), context.personal().baseCommit(), end,
                    offset + 200, 1, true, context.pathspec()));
        }
        boolean hasMore = selected.size() > limit;
        return new CommitSlice(List.copyOf(selected.subList(0, Math.min(limit, selected.size()))), hasMore);
    }

    public TeamWorkspaceResponses.CommitDetailResponse commitDetail(
            boolean global,
            UserId owner,
            String personalWorkspaceId,
            String kind,
            String commit) {
        LocalWorkspace context = localWorkspace(global, owner, personalWorkspaceId);
        CommitAccess access = requireCommitAccess(context, kind, commit);
        GitCommitSummary summary = git.listCommitHistory(context.repoRoot(), null, commit, 0, 1, true).stream()
                .filter(value -> value.commit().equals(commit))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "提交不存在", Map.of("commit", commit)));
        List<TeamWorkspaceResponses.CommitFileResponse> files = git.commitChangedFiles(context.repoRoot(), commit).stream()
                .filter(value -> visibleEntry(context, value.path()))
                .map(value -> new TeamWorkspaceResponses.CommitFileResponse(
                        value.rawStatus(), displayPath(context, value.oldPath()), displayPath(context, value.path())))
                .toList();
        TeamContributionType type = summary.merge()
                ? TeamContributionType.SYNC_MERGE
                : (access.published() ? TeamContributionType.PUBLISHED_COMMIT : TeamContributionType.PERSONAL_COMMIT);
        return new TeamWorkspaceResponses.CommitDetailResponse(commit(summary, type), files);
    }

    public TeamWorkspaceResponses.CommitDiffResponse commitDiff(
            boolean global,
            UserId owner,
            String personalWorkspaceId,
            String kind,
            String commit,
            String path) {
        LocalWorkspace context = localWorkspace(global, owner, personalWorkspaceId);
        requireCommitAccess(context, kind, commit);
        String gitPath = gitPath(context, path);
        return new TeamWorkspaceResponses.CommitDiffResponse(
                commit, path, git.commitFileDiff(context.repoRoot(), commit, gitPath));
    }

    private CommitAccess requireCommitAccess(LocalWorkspace context, String kind, String commit) {
        boolean published = "PUBLISHED".equalsIgnoreCase(kind);
        String end = published ? context.version().targetCommitHash() : git.headCommit(context.repoRoot());
        if (end == null || end.isBlank() || context.personal().baseCommit().isBlank()
                || !git.isCommitAncestor(context.repoRoot(), context.personal().baseCommit(), commit)
                || !git.isCommitAncestor(context.repoRoot(), commit, end)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "提交不在该成员的授权范围内", Map.of("commit", commit));
        }
        if (published) {
            GitCommitSummary summary = git.listCommitHistory(context.repoRoot(), null, commit, 0, 1, true).stream()
                    .filter(value -> value.commit().equals(commit)).findFirst()
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "提交不存在"));
            if (!matchesIdentity(summary, context.identity())) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "已发布提交不属于该成员", Map.of("commit", commit));
            }
        }
        return new CommitAccess(published);
    }

    private LocalWorkspace localWorkspace(boolean global, UserId owner, String personalWorkspaceId) {
        PersonalWorkspaceView view = personalWorkspace(global, owner, personalWorkspaceId);
        if (!serverIdentity.linuxServerId().equals(view.linuxServerId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT, "个人工作区不在当前节点",
                    Map.of("targetLinuxServerId", view.linuxServerId(), "currentLinuxServerId", serverIdentity.linuxServerId()));
        }
        PersonalWorkspace personal = view.workspace();
        ApplicationWorkspaceVersion version = queries.findWorkspaceVersions(
                        global, owner, personal.applicationWorkspaceId().value(), null).stream()
                .map(value -> value.version())
                .filter(value -> value.versionId().equals(personal.versionId()))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "团队范围内的工作空间版本不存在"));
        Path repoRoot = pathResolver.resolve(personal.repoRootPath()).toAbsolutePath().normalize();
        Path workspaceRoot = pathResolver.resolve(personal.workspaceRootPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(repoRoot) || !Files.isDirectory(workspaceRoot)
                || !workspaceRoot.startsWith(repoRoot) || containsSymbolicLink(repoRoot, workspaceRoot)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "个人工作区目录不可用");
        }
        String prefix = repoRoot.relativize(workspaceRoot).toString().replace('\\', '/');
        String pathspec = prefix.isBlank() ? "" : prefix;
        String displayPrefix = prefix.isBlank() ? "" : prefix + "/";
        GitCommitIdentity identity = scmGitIdentityResolver.resolve(personal.userId());
        return new LocalWorkspace(personal, version, repoRoot, workspaceRoot, pathspec, displayPrefix, identity);
    }

    private TeamWorkspaceResponses.ContributionResponse contribution(MemberContributionView value) {
        return new TeamWorkspaceResponses.ContributionResponse(
                value.user().userId().value(), value.user().unifiedAuthId(), value.user().username(),
                value.user().organization(), value.user().department(), value.membershipState().name(),
                value.personalWorkspaces().stream().map(this::personal).toList());
    }

    private TeamWorkspaceResponses.PersonalWorkspaceResponse personal(PersonalWorkspaceView value) {
        PersonalWorkspace workspace = value.workspace();
        return new TeamWorkspaceResponses.PersonalWorkspaceResponse(
                workspace.personalWorkspaceId().value(), workspace.runtimeWorkspaceId().value(),
                workspace.workspaceName(), workspace.branch(), value.linuxServerId(), workspace.baseCommit(),
                workspace.status().name(), workspace.updatedAt());
    }

    private TeamWorkspaceResponses.CommitResponse commit(GitCommitSummary value, TeamContributionType type) {
        return new TeamWorkspaceResponses.CommitResponse(
                value.commit(), value.parents(), value.authorName(), value.authorEmail(),
                value.committerName(), value.committerEmail(), value.committedAt(), value.subject(), type.name());
    }

    /** 已发布提交必须同时匹配企业 SCM 校准姓名和统一认证号邮箱，避免同邮箱/同名误归属。 */
    private boolean matchesIdentity(GitCommitSummary value, GitCommitIdentity identity) {
        return identity.email().equalsIgnoreCase(value.committerEmail())
                && identity.name().equals(value.committerName());
    }

    private boolean visibleEntry(LocalWorkspace context, String gitPath) {
        if (gitPath == null || gitPath.isBlank()) {
            return false;
        }
        String normalized = gitPath.replace('\\', '/');
        if (!context.pathspec().isBlank()
                && !(normalized.equals(context.pathspec()) || normalized.startsWith(context.displayPrefix()))) {
            return false;
        }
        Path target = context.repoRoot().resolve(normalized).normalize();
        return target.startsWith(context.workspaceRoot()) && !containsSymbolicLink(context.repoRoot(), target);
    }

    private String displayPath(LocalWorkspace context, String gitPath) {
        if (gitPath == null) {
            return null;
        }
        return context.displayPrefix().isBlank() || !gitPath.startsWith(context.displayPrefix())
                ? gitPath
                : gitPath.substring(context.displayPrefix().length());
    }

    private String gitPath(LocalWorkspace context, String displayPath) {
        try {
            String normalized = Objects.requireNonNull(displayPath, "path must not be null").trim().replace('\\', '/');
            Path relative = Path.of(normalized).normalize();
            if (normalized.isBlank() || relative.isAbsolute() || relative.startsWith("..")
                    || normalized.equals(".git") || normalized.startsWith(".git/")) {
                throw new IllegalArgumentException("invalid path");
            }
            String joined = context.displayPrefix() + relative.toString().replace('\\', '/');
            if (!visibleEntry(context, joined)) {
                throw new IllegalArgumentException("path is outside workspace");
            }
            return joined;
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "文件路径无效");
        }
    }

    private boolean containsSymbolicLink(Path root, Path target) {
        try {
            Path current = root;
            for (Path segment : root.relativize(target)) {
                current = current.resolve(segment);
                if (Files.isSymbolicLink(current)) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            return true;
        }
    }

    private record LocalWorkspace(
            PersonalWorkspace personal,
            ApplicationWorkspaceVersion version,
            Path repoRoot,
            Path workspaceRoot,
            String pathspec,
            String displayPrefix,
            GitCommitIdentity identity) {
    }

    private record CommitAccess(boolean published) {
    }

    private record CommitSlice(List<GitCommitSummary> items, boolean hasMore) {
    }
}
