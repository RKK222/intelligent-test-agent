package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.team.TeamReviewModels.*;
import com.enterprise.testagent.domain.team.TeamReviewScopeStore;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 权威聚合索引：UI 和 Tool 消费同一排序、去重与来源判定，绝不创建物理合并目录。 */
@Service
public class TeamReviewApplicationService {
    private final TeamReviewScopeStore scopes;
    private final TeamReviewFileGateway files;
    private final java.time.Duration directoryBudget;

    @org.springframework.beans.factory.annotation.Autowired
    public TeamReviewApplicationService(TeamReviewScopeStore scopes, TeamReviewFileGateway files) {
        this(scopes, files, java.time.Duration.ofSeconds(45));
    }

    /** 小预算仅供取消边界测试；生产一层目录总等待最多 45 秒，不按来源数累加。 */
    TeamReviewApplicationService(TeamReviewScopeStore scopes, TeamReviewFileGateway files, java.time.Duration directoryBudget) {
        this.scopes = scopes;
        this.files = files;
        this.directoryBudget = directoryBudget;
    }

    public Scope requireScope(String id, String actor) {
        Scope scope = scopes.find(id).filter(value -> value.actorUserId().equals(actor)
                && value.expiresAt().isAfter(Instant.now())).orElseThrow(() ->
                new PlatformException(ErrorCode.FORBIDDEN, "审阅范围已失效，请刷新"));
        files.authorize(scope);
        return scope;
    }

    /** 范围在后端从授权版本解析，不接受浏览器提供的成员路径或服务器坐标。 */
    public Scope create(String actor, String sessionDigest, TeamScopeMode mode, String owner,
                        String versionId, String selectedUserId, TeamWorkspaceApplicationService workspaces) {
        var contributions = workspaces.contributions(mode == TeamScopeMode.GLOBAL,
                mode == TeamScopeMode.GLOBAL ? null : new UserId(owner), versionId);
        List<Source> sources = contributions.stream()
                .filter(item -> selectedUserId == null || selectedUserId.isBlank() || selectedUserId.equals(item.userId()))
                .flatMap(item -> item.personalWorkspaces().stream()
                        .filter(workspace -> "default".equals(workspace.workspaceName()))
                        .map(workspace -> new Source(item.userId(), item.username(), workspace.personalWorkspaceId(),
                                workspace.workspaceId(), workspace.linuxServerId())))
                .sorted(Comparator.comparing(Source::personalWorkspaceId)).toList();
        if (sources.isEmpty()) throw new PlatformException(ErrorCode.NOT_FOUND, "当前版本没有可审阅的 default 工作区");
        if (sources.size() > 200) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "审阅范围超过 200 个工作区，请选择具体成员");
        Scope scope = new Scope("trv_" + java.util.UUID.randomUUID().toString().replace("-", ""), actor,
                sessionDigest, mode, owner, versionId, selectedUserId, sources, Instant.now().plusSeconds(7200));
        files.authorize(scope);
        scopes.save(scope);
        return scope;
    }

    public Listing list(Scope scope, String path, String traceId) {
        return list(scope, path, traceId, System.nanoTime() + directoryBudget.toNanos());
    }

    private Listing list(Scope scope, String path, String traceId, long deadline) {
        String directory = relativePath(path, true);
        Map<String, List<Candidate>> grouped = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();
        // 目录按需加载，并发固定为 4；每个来源独立失败，不把缺失成员索引冒充完整结果。
        var executor = java.util.concurrent.Executors.newFixedThreadPool(Math.min(4, scope.sources().size()));
        try {
            var pending = scope.sources().stream().map(source -> executor.submit(() -> files.list(scope, source, directory, traceId))).toList();
            for (int index = 0; index < pending.size(); index++) {
                Source source = scope.sources().get(index);
                try {
                    var future = pending.get(index);
                    long remaining = deadline - System.nanoTime();
                    // 已完成结果仍可收集；超时来源中断，不能等待自动 close 把全部排队任务跑完。
                    if (!future.isDone() && remaining <= 0) throw new java.util.concurrent.TimeoutException();
                    for (File file : future.get(Math.max(1, remaining), java.util.concurrent.TimeUnit.NANOSECONDS)) {
                        grouped.computeIfAbsent(file.path(), ignored -> new ArrayList<>()).add(new Candidate(source, file));
                    }
                } catch (Exception exception) {
                    Throwable cause = exception instanceof java.util.concurrent.ExecutionException ? exception.getCause() : exception;
                    if (cause instanceof PlatformException failure && (failure.errorCode() == ErrorCode.FORBIDDEN || failure.errorCode() == ErrorCode.UNAUTHENTICATED))
                        throw failure;
                    if (exception instanceof InterruptedException) { Thread.currentThread().interrupt(); throw new PlatformException(ErrorCode.CONFLICT, "审阅索引已取消"); }
                    pending.get(index).cancel(true);
                    unavailable.add(source.username());
                }
            }
        } finally { executor.shutdownNow(); }
        List<Entry> entries = grouped.values().stream().map(TeamReviewApplicationService::aggregate)
                .sorted(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::path)).toList();
        if (entries.size() > 1000) throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                "聚合目录超过 1000 项，请选择具体成员或更窄目录；未把截断结果标为完整");
        return new Listing(entries, List.copyOf(unavailable), unavailable.isEmpty());
    }

    public Object read(Scope scope, String path, String version, long offset, String traceId) {
        String file = relativePath(path, false);
        if (offset < 0 || version == null || version.isBlank())
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "读取必须携带目录返回的文件版本和有效偏移");
        int slash = file.lastIndexOf('/');
        Listing listing = list(scope, slash < 0 ? "" : file.substring(0, slash), traceId);
        if (!listing.complete()) throw new PlatformException(ErrorCode.CONFLICT, "部分成员不可用，不能确认最新来源");
        Entry entry = listing.entries().stream().filter(item -> item.path().equals(file) && !item.directory())
                .findFirst().orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "审阅文件不存在"));
        if (entry.latestUncertain()) throw new PlatformException(ErrorCode.CONFLICT,
                "最新版本待核验，请选择具体成员后读取", Map.of("alternatives", entry.alternatives()));
        Candidate candidate = entry.selected();
        if (candidate.file().deleted()) throw new PlatformException(ErrorCode.NOT_FOUND, "该来源记录了文件删除");
        if (!Objects.equals(version, candidate.file().contentVersion()))
            throw new PlatformException(ErrorCode.CONFLICT, "文件内容或最新来源已经变化，请刷新后重读");
        return Map.of("source", candidate.source(), "file", candidate.file(), "chunk",
                files.read(scope, candidate.source(), file, version, offset, traceId));
    }

    /** 有界递归搜索；未扫描目录作为显式游标返回，不能把预算耗尽当作“搜索无结果”。 */
    public Search search(Scope scope, String path, String query, List<String> remaining, String traceId) {
        var queue = new java.util.ArrayDeque<String>();
        if (remaining == null || remaining.isEmpty()) queue.add(relativePath(path, true));
        else {
            if (remaining.size() > 1000) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "搜索游标超过预算");
            remaining.forEach(item -> queue.add(relativePath(item, true)));
        }
        List<Entry> matches = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        String keyword = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        int scanned = 0;
        boolean depthLimited = false;
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(90).toNanos();
        while (!queue.isEmpty() && scanned++ < 32 && matches.size() < 1000 && System.nanoTime() < deadline) {
            String directory = queue.removeFirst();
            if (!visited.add(directory)) continue;
            Listing listing = list(scope, directory, traceId, Math.min(deadline, System.nanoTime() + directoryBudget.toNanos()));
            unavailable.addAll(listing.unavailableMembers());
            for (Entry entry : listing.entries()) {
                if (entry.path().toLowerCase(java.util.Locale.ROOT).contains(keyword)) matches.add(entry);
                if (entry.directory()) {
                    if (entry.path().split("/").length < 20) queue.addLast(entry.path());
                    else depthLimited = true;
                }
            }
        }
        return new Search(List.copyOf(matches), List.copyOf(queue), unavailable.stream().distinct().toList(),
                queue.isEmpty() && unavailable.isEmpty() && !depthLimited);
    }

    public record Search(List<Entry> entries, List<String> remainingDirectories,
                         List<String> unavailableMembers, boolean complete, String excludedPolicy) {
        Search(List<Entry> entries, List<String> remainingDirectories, List<String> unavailableMembers, boolean complete) {
            this(entries, remainingDirectories, unavailableMembers, complete, com.enterprise.testagent.domain.team.TeamReviewModels.EXCLUDED_POLICY);
        }
    }

    /** 同内容去重；只有不同内容且全部具有可靠 Git 时间时，才按时间选择最新。 */
    static Entry aggregate(List<Candidate> candidates) {
        List<Candidate> ordered = candidates.stream().sorted(Comparator
                .comparing((Candidate item) -> item.source().personalWorkspaceId())).toList();
        Candidate first = ordered.getFirst();
        if (ordered.stream().anyMatch(item -> item.file().directory() != first.file().directory())) {
            Candidate selectedFile = ordered.stream().filter(item -> !item.file().directory()).findFirst().orElseThrow();
            return new Entry(first.file().path(), first.file().name(), false, selectedFile.file().size(), selectedFile, true, ordered);
        }
        if (first.file().directory())
            return new Entry(first.file().path(), first.file().name(), true, 0, first, false, List.of());
        boolean same = ordered.stream().allMatch(item -> Objects.equals(item.file().contentVersion(), first.file().contentVersion()));
        boolean reliable = ordered.stream().allMatch(item -> "GIT_COMMIT".equals(item.file().timeType())
                && item.file().changedAt() != null);
        Candidate selected = reliable ? ordered.stream().max(Comparator
                .comparing((Candidate item) -> item.file().changedAt())).orElse(first) : first;
        boolean tie = reliable && ordered.stream().anyMatch(item ->
                item.file().changedAt().equals(selected.file().changedAt())
                        && !Objects.equals(item.file().contentVersion(), selected.file().contentVersion()));
        boolean uncertain = ordered.size() > 1 && !same && (!reliable || tie);
        return new Entry(selected.file().path(), selected.file().name(), false, selected.file().size(),
                selected, uncertain, uncertain ? ordered : List.of());
    }

    public static String relativePath(String value, boolean rootAllowed) {
        String path = value == null ? "" : value.replace('\\', '/');
        if ((path.isBlank() && !rootAllowed) || path.startsWith("/") || path.contains(":"))
            throw new PlatformException(ErrorCode.FORBIDDEN, "审阅路径无效");
        for (String segment : path.split("/")) {
            if (segment.equals("..") || segment.equals(".") || segment.equals(".git"))
                throw new PlatformException(ErrorCode.FORBIDDEN, "审阅路径越界");
        }
        return path;
    }
}
