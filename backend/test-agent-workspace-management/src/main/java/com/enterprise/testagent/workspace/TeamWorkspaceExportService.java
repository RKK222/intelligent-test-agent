package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportItem;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportJob;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportStatus;
import com.enterprise.testagent.domain.team.TeamWorkspaceQueryRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.MemberContributionView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.PersonalWorkspaceView;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 团队版本整组 ZIP 导出协调器。
 *
 * <p>每个 worktree 先生成稳定过滤 shard，再由协调节点校验摘要并合并；全局最多四个并发任务、
 * 同一源节点最多两个。文件遍历拒绝 .git、符号链接和特殊设备文件，敏感文件只进入清单。</p>
 */
@Service
public class TeamWorkspaceExportService {

    static final long MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;
    static final int MAX_FILE_COUNT = 50_000;
    private static final Duration RETENTION = Duration.ofHours(2);
    private static final Duration ITEM_LEASE = Duration.ofMinutes(10);
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final Set<String> SENSITIVE_EXACT = Set.of(
            ".npmrc", ".pypirc", ".netrc", "credentials", "credentials.json",
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519");

    private final TeamWorkspaceQueryRepository queries;
    private final TeamWorkspaceExportRepository exports;
    private final ManagedWorkspacePathResolver paths;
    private final WorkspaceServerIdentity serverIdentity;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TeamWorkspaceExportShardGateway shardGateway;
    private final ExecutorService coordinator = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "team-workspace-export-coordinator");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService workers = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "team-workspace-export");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Semaphore> sourceLimits = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Future<?>> running = new java.util.concurrent.ConcurrentHashMap<>();
    private final Path artifactRoot;

    public TeamWorkspaceExportService(
            TeamWorkspaceQueryRepository queries,
            TeamWorkspaceExportRepository exports,
            CommonParameterValues commonParameterValues,
            WorkspaceServerIdentity serverIdentity,
            ObjectMapper objectMapper,
            Clock clock,
            TeamWorkspaceExportShardGateway shardGateway) {
        this.queries = Objects.requireNonNull(queries);
        this.exports = Objects.requireNonNull(exports);
        this.paths = new ManagedWorkspacePathResolver(commonParameterValues);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.shardGateway = Objects.requireNonNull(shardGateway);
        this.artifactRoot = Path.of(System.getProperty("java.io.tmpdir"), "testagent-team-exports")
                .toAbsolutePath().normalize();
    }

    public TeamWorkspaceResponses.ExportResponse create(
            TeamScopeMode mode,
            UserId actor,
            UserId owner,
            boolean global,
            String versionId) {
        List<MemberContributionView> contributions = queries.findContributions(global, owner, versionId);
        if (contributions.isEmpty()) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "团队范围内没有可导出的版本人员");
        }
        Instant now = clock.instant();
        String exportId = RuntimeIdGenerator.teamWorkspaceExportId();
        List<PlannedItem> planned = plan(exportId, contributions, now);
        Preflight total = preflight(planned);
        if (total.fileCount() > MAX_FILE_COUNT || total.bytes() > MAX_UNCOMPRESSED_BYTES) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "整组导出超过 2 GiB 或 5 万文件上限",
                    Map.of("fileCount", total.fileCount(), "uncompressedBytes", total.bytes()));
        }
        TeamWorkspaceExportJob job = new TeamWorkspaceExportJob(
                exportId, actor.value(), owner == null ? null : owner.value(), mode, versionId,
                serverIdentity.linuxServerId(), TeamWorkspaceExportStatus.QUEUED,
                planned.size(), 0, 0, 0, 0, 0L, null, null, null,
                null, null, now, null, null, now.plus(RETENTION), null);
        exports.insertJob(job);
        planned.forEach(item -> exports.insertItem(item.item()));
        running.put(exportId, coordinator.submit(() -> run(job, planned)));
        return response(job, planned.stream().map(PlannedItem::item).toList());
    }

    public TeamWorkspaceResponses.ExportResponse get(String exportId, String actorUserId) {
        TeamWorkspaceExportJob job = requireOwned(exportId, actorUserId);
        return response(job, exports.findItems(exportId));
    }

    public ExportAccess access(String exportId, String actorUserId) {
        TeamWorkspaceExportJob job = requireOwned(exportId, actorUserId);
        return new ExportAccess(
                job.scopeMode(), job.ownerUserId(), job.coordinatorLinuxServerId(),
                exports.findItems(exportId).stream().map(TeamWorkspaceExportItem::userId).distinct().toList());
    }

    public boolean cancel(String exportId, String actorUserId) {
        requireOwned(exportId, actorUserId);
        boolean changed = exports.cancel(exportId, actorUserId, clock.instant());
        Future<?> future = running.remove(exportId);
        if (future != null) future.cancel(true);
        deleteArtifacts(exportId);
        return changed;
    }

    /** 实时角色或团队关系失效时立即作废任务，并删除当前协调节点的未下载产物。 */
    public void invalidateAuthorization(String exportId) {
        TeamWorkspaceExportJob job = exports.findJob(exportId).orElse(null);
        if (job == null || job.status() == TeamWorkspaceExportStatus.EXPIRED) return;
        Instant now = clock.instant();
        Future<?> future = running.remove(exportId);
        if (future != null) future.cancel(true);
        exports.updateJob(copyJob(
                job, TeamWorkspaceExportStatus.CANCELLED, job.completedItems(), job.succeededItems(),
                job.failedItems(), job.fileCount(), job.uncompressedBytes(), null, null, null,
                "ACCESS_REVOKED", "系统管理员角色或团队关系已失效", job.startedAt(), now, now));
        deleteArtifacts(exportId);
    }

    /** 成员被移出团队后立即将相关任务置为不可下载；协调节点最迟在下一次清理轮询删除远端产物。 */
    public void invalidateTeamMember(String ownerUserId, String memberUserId) {
        exports.findActiveByTeamMember(ownerUserId, memberUserId).forEach(job -> {
            invalidateAuthorization(job.exportId());
            try {
                shardGateway.deleteRevokedArtifact(
                        job.coordinatorLinuxServerId(), job.exportId(),
                        "trace_team_export_revoke_" + job.exportId());
            } catch (RuntimeException ignored) {
                // 数据库状态已先置为不可下载；协调节点每分钟扫描 CANCELLED 任务完成离线兜底清理。
            }
        });
    }

    /** 内部撤权控制面只能删除当前协调节点上已取消的产物。 */
    public void deleteRevokedArtifact(String exportId) {
        TeamWorkspaceExportJob job = exports.findJob(exportId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "团队导出任务不存在"));
        if (job.status() != TeamWorkspaceExportStatus.CANCELLED
                || !serverIdentity.linuxServerId().equals(job.coordinatorLinuxServerId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "团队导出撤权清理节点或状态不匹配");
        }
        deleteArtifacts(exportId);
    }

    /** 仅协调节点返回 READY/PARTIAL_READY 产物；Controller 负责再次实时授权。 */
    public Path artifact(String exportId, String actorUserId) {
        TeamWorkspaceExportJob job = requireOwned(exportId, actorUserId);
        if (job.status() != TeamWorkspaceExportStatus.READY
                && job.status() != TeamWorkspaceExportStatus.PARTIAL_READY) {
            throw new PlatformException(ErrorCode.CONFLICT, "团队导出产物尚不可下载");
        }
        if (!serverIdentity.linuxServerId().equals(job.coordinatorLinuxServerId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "团队导出产物不在当前节点");
        }
        Path artifact = artifactPath(exportId);
        try {
            if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
                    || !sha256(artifact).equals(job.archiveSha256())) {
                throw new PlatformException(ErrorCode.NOT_FOUND, "团队导出产物不存在或校验失败");
            }
            return artifact;
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "团队导出产物不存在", Map.of(), exception);
        }
    }

    private void run(TeamWorkspaceExportJob queued, List<PlannedItem> planned) {
        Instant started = clock.instant();
        TeamWorkspaceExportJob runningJob = copyJob(
                queued, TeamWorkspaceExportStatus.RUNNING, 0, 0, 0, 0, 0L,
                null, null, null, null, null, started, null, null);
        exports.updateJob(runningJob);
        List<Future<ShardResult>> futures = planned.stream()
                .map(item -> workers.submit(() -> buildClaimedShard(item)))
                .toList();
        ArrayList<ShardResult> results = new ArrayList<>();
        try {
            for (Future<ShardResult> future : futures) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("cancelled");
                ShardResult result = future.get();
                results.add(result);
                exports.updateItem(result.item());
            }
            TeamWorkspaceExportJob current = exports.findJob(queued.exportId()).orElse(queued);
            if (current.status() == TeamWorkspaceExportStatus.CANCELLED) {
                deleteArtifacts(queued.exportId());
                return;
            }
            finishArchive(runningJob, results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            deleteArtifacts(queued.exportId());
        } catch (Exception exception) {
            failJob(runningJob, "EXPORT_FAILED", stableMessage(exception));
            deleteArtifacts(queued.exportId());
        } finally {
            futures.forEach(future -> future.cancel(true));
            running.remove(queued.exportId());
            results.forEach(result -> deleteQuietly(result.shard()));
        }
    }

    private ShardResult buildShard(PlannedItem planned) {
        TeamWorkspaceExportItem item = planned.item();
        Instant now = clock.instant();
        if ("NO_WORKTREE".equals(item.status())) {
            return new ShardResult(item, null, List.of(), planned.username(), planned.workspaceName());
        }
        if (!serverIdentity.linuxServerId().equals(item.sourceLinuxServerId())) {
            return fetchRemoteShard(planned, now);
        }
        Semaphore semaphore = sourceLimits.computeIfAbsent(item.sourceLinuxServerId(), ignored -> new Semaphore(2));
        boolean acquired = false;
        Path shard = shardPath(item.exportId(), item.exportItemId());
        try {
            semaphore.acquire();
            acquired = true;
            List<ExcludedFile> excluded = new ArrayList<>();
            Files.createDirectories(shard.getParent());
            int count = 0;
            long bytes = 0L;
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(shard));
                 ZipOutputStream zip = new ZipOutputStream(output)) {
                for (FileSnapshot file : planned.files()) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("cancelled");
                    if (file.excludedReason() != null) {
                        excluded.add(new ExcludedFile(file.relativePath(), file.excludedReason()));
                        continue;
                    }
                    BasicFileAttributes before = Files.readAttributes(
                            file.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!before.isRegularFile() || before.size() != file.size()
                            || before.lastModifiedTime().toMillis() != file.modifiedMillis()) {
                        throw new WorkspaceChangedException(file.relativePath());
                    }
                    zip.putNextEntry(new ZipEntry(file.relativePath()));
                    try (InputStream input = new BufferedInputStream(Files.newInputStream(file.path()))) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                    BasicFileAttributes after = Files.readAttributes(
                            file.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (after.size() != before.size()
                            || after.lastModifiedTime().toMillis() != before.lastModifiedTime().toMillis()) {
                        throw new WorkspaceChangedException(file.relativePath());
                    }
                    count++;
                    bytes += before.size();
                }
            }
            long shardBytes = Files.size(shard);
            TeamWorkspaceExportItem succeeded = new TeamWorkspaceExportItem(
                    item.exportItemId(), item.exportId(), item.userId(), item.personalWorkspaceId(),
                    item.sourceLinuxServerId(), "SUCCEEDED", count, bytes, shardBytes, sha256(shard),
                    null, null, item.createdAt(), clock.instant());
            return new ShardResult(succeeded, shard, List.copyOf(excluded),
                    planned.username(), planned.workspaceName());
        } catch (WorkspaceChangedException exception) {
            deleteQuietly(shard);
            return failed(item, "WORKTREE_CHANGED", exception.getMessage(), clock.instant());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            deleteQuietly(shard);
            return failed(item, "CANCELLED", "导出已取消", clock.instant());
        } catch (Exception exception) {
            deleteQuietly(shard);
            return failed(item, "SHARD_FAILED", stableMessage(exception), clock.instant());
        } finally {
            if (acquired) semaphore.release();
        }
    }

    /** 每个 worktree 在执行前先原子领取十分钟租约，完成写回时由 Mapper 清除租约。 */
    private ShardResult buildClaimedShard(PlannedItem planned) {
        TeamWorkspaceExportItem item = planned.item();
        if ("NO_WORKTREE".equals(item.status())) {
            return buildShard(planned);
        }
        Instant now = clock.instant();
        boolean claimed = exports.claimItem(
                item.exportItemId(), serverIdentity.linuxServerId(),
                RuntimeIdGenerator.teamWorkspaceExportItemId(), now, now.plus(ITEM_LEASE));
        if (!claimed) {
            return failed(item, "LEASE_UNAVAILABLE", "个人 worktree 已被其它导出 worker 领取", now);
        }
        return buildShard(planned);
    }

    private ShardResult failed(TeamWorkspaceExportItem item, String code, String message, Instant now) {
        return new ShardResult(new TeamWorkspaceExportItem(
                item.exportItemId(), item.exportId(), item.userId(), item.personalWorkspaceId(),
                item.sourceLinuxServerId(), "FAILED", 0, 0L, null, null,
                code, message, item.createdAt(), now), null, List.of(), "unknown", "unknown");
    }

    /**
     * 协调节点通过 API 层网关从权威源节点取得已过滤 shard。网关负责公共路由、
     * 控制面 HTTP 和文件 WebSocket；本服务只接受摘要匹配的本地临时文件。
     */
    private ShardResult fetchRemoteShard(PlannedItem planned, Instant now) {
        TeamWorkspaceExportItem item = planned.item();
        Path shard = shardPath(item.exportId(), item.exportItemId());
        try {
            Files.createDirectories(shard.getParent());
            TeamWorkspaceExportShardGateway.TransferredShard transferred = shardGateway.fetch(
                    new TeamWorkspaceExportShardGateway.Request(
                            item.exportId(), item.exportItemId(), item.sourceLinuxServerId(), item.userId(),
                            item.personalWorkspaceId(), planned.versionId(), MAX_UNCOMPRESSED_BYTES, MAX_FILE_COUNT,
                            "trace_team_export_" + item.exportItemId()),
                    shard);
            if (!Files.isRegularFile(shard, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(shard) != transferred.archiveBytes()
                    || !sha256(shard).equals(transferred.sha256())) {
                throw new PlatformException(ErrorCode.CONFLICT, "跨节点 shard 摘要校验失败");
            }
            TeamWorkspaceExportItem succeeded = new TeamWorkspaceExportItem(
                    item.exportItemId(), item.exportId(), item.userId(), item.personalWorkspaceId(),
                    item.sourceLinuxServerId(), "SUCCEEDED", transferred.fileCount(),
                    transferred.uncompressedBytes(), transferred.archiveBytes(), transferred.sha256(),
                    null, null, item.createdAt(), clock.instant());
            List<ExcludedFile> excluded = transferred.excluded().stream()
                    .map(value -> new ExcludedFile(value.path(), value.reason()))
                    .toList();
            return new ShardResult(succeeded, shard, excluded, planned.username(), planned.workspaceName());
        } catch (Exception exception) {
            deleteQuietly(shard);
            ShardResult failure = failed(item, "SOURCE_NODE_UNAVAILABLE", stableMessage(exception), now);
            return new ShardResult(failure.item(), null, List.of(), planned.username(), planned.workspaceName());
        }
    }

    /** 源节点为内部 WebSocket 传输生成 shard；请求中的人员和版本必须与数据库映射一致。 */
    public LocalShard buildTransferShard(TeamWorkspaceExportShardGateway.Request request) {
        PlannedItem planned = transferPlanned(request);
        Preflight total = preflight(List.of(planned));
        if (total.fileCount() > request.maxFileCount() || total.bytes() > request.maxUncompressedBytes()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "源节点 worktree 超过导出上限");
        }
        ShardResult result = buildShard(planned);
        if (!"SUCCEEDED".equals(result.item().status()) || result.shard() == null) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    result.item().errorMessage() == null ? "源节点 shard 生成失败" : result.item().errorMessage());
        }
        return new LocalShard(
                result.shard(), result.item().fileCount(), result.item().uncompressedBytes(),
                result.item().shardBytes(), result.item().shardSha256(),
                result.excluded().stream()
                        .map(value -> new TeamWorkspaceExportShardGateway.Excluded(value.path(), value.reason()))
                        .toList());
    }

    /** 源节点只扫描普通、非敏感文件的数量和大小，供协调节点在建任务前完成全局预检。 */
    public TeamWorkspaceExportShardGateway.Preflight inspectTransferShard(
            TeamWorkspaceExportShardGateway.Request request) {
        Preflight total = preflight(List.of(transferPlanned(request)));
        return new TeamWorkspaceExportShardGateway.Preflight(total.fileCount(), total.bytes());
    }

    private PlannedItem transferPlanned(TeamWorkspaceExportShardGateway.Request request) {
        PersonalWorkspaceView personal = queries.findPersonalWorkspace(
                        true, null, new PersonalWorkspaceId(request.personalWorkspaceId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "个人 worktree 不存在"));
        if (!personal.workspace().userId().value().equals(request.userId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "团队导出人员与个人 worktree 不匹配");
        }
        if (request.versionId() != null
                && !personal.workspace().versionId().value().equals(request.versionId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "团队导出版本与个人 worktree 不匹配");
        }
        if (!serverIdentity.linuxServerId().equals(personal.linuxServerId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "个人 worktree 不属于当前源节点");
        }
        Instant now = clock.instant();
        TeamWorkspaceExportItem item = new TeamWorkspaceExportItem(
                request.exportItemId(), request.exportId(), request.userId(), request.personalWorkspaceId(),
                personal.linuxServerId(), "QUEUED", 0, 0L, null, null, null, null, now, now);
        PlannedItem planned = new PlannedItem(
                item, request.userId(), personal.workspace().workspaceName(),
                personal.workspace().versionId().value(),
                scan(paths.resolve(personal.workspace().workspaceRootPath())), 0, 0L);
        Preflight total = preflightFiles(planned.files());
        return new PlannedItem(
                planned.item(), planned.username(), planned.workspaceName(), planned.versionId(),
                planned.files(), total.fileCount(), total.bytes());
    }

    private void finishArchive(TeamWorkspaceExportJob job, List<ShardResult> results) throws Exception {
        List<ShardResult> succeeded = results.stream()
                .filter(result -> "SUCCEEDED".equals(result.item().status())).toList();
        int noWorktree = (int) results.stream().filter(result -> "NO_WORKTREE".equals(result.item().status())).count();
        int failures = results.size() - succeeded.size() - noWorktree;
        int completed = results.size();
        int files = succeeded.stream().mapToInt(result -> result.item().fileCount()).sum();
        long bytes = succeeded.stream().mapToLong(result -> result.item().uncompressedBytes()).sum();
        Instant completedAt = clock.instant();
        if (files > MAX_FILE_COUNT || bytes > MAX_UNCOMPRESSED_BYTES) {
            TeamWorkspaceExportJob failed = copyJob(job, TeamWorkspaceExportStatus.FAILED,
                    completed, succeeded.size(), failures, files, bytes, null, null, null,
                    "EXPORT_LIMIT_CHANGED", "worktree 在预检后增长并超过整组导出上限",
                    job.startedAt(), completedAt, null);
            exports.updateJob(failed);
            return;
        }
        if (succeeded.isEmpty() && noWorktree == 0) {
            TeamWorkspaceExportJob failed = copyJob(job, TeamWorkspaceExportStatus.FAILED,
                    completed, 0, failures, files, bytes, null, null, null,
                    "ALL_WORKTREES_FAILED", "所有个人 worktree 均未生成可用 shard",
                    job.startedAt(), completedAt, null);
            exports.updateJob(failed);
            return;
        }
        Path target = artifactPath(job.exportId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.createDirectories(target.getParent());
        ArrayList<Map<String, Object>> manifestItems = new ArrayList<>();
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary));
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (ShardResult result : results) {
                PlannedNames names = names(result);
                LinkedHashMap<String, Object> manifestItem = new LinkedHashMap<>();
                manifestItem.put("userId", result.item().userId());
                manifestItem.put("personalWorkspaceId", result.item().personalWorkspaceId());
                manifestItem.put("status", result.item().status());
                manifestItem.put("errorCode", result.item().errorCode());
                manifestItem.put("errorMessage", result.item().errorMessage());
                manifestItem.put("excluded", result.excluded());
                manifestItems.add(manifestItem);
                if (result.shard() != null) mergeShard(zip, result.shard(), names.prefix());
            }
            LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("exportId", job.exportId());
            manifest.put("versionId", job.versionId());
            // 清单是可移植 ZIP 元数据，不依赖运行节点的 Jackson Java Time 模块配置。
            manifest.put("createdAt", job.createdAt().toString());
            manifest.put("completedAt", completedAt.toString());
            manifest.put("items", manifestItems);
            byte[] bytesJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(bytesJson);
            zip.closeEntry();
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        TeamWorkspaceExportStatus status = failures == 0
                ? TeamWorkspaceExportStatus.READY : TeamWorkspaceExportStatus.PARTIAL_READY;
        TeamWorkspaceExportJob ready = copyJob(job, status, completed, succeeded.size(), failures,
                files, bytes, Files.size(target), sha256(target), target.getFileName().toString(),
                null, null, job.startedAt(), completedAt, null);
        exports.updateJob(ready);
    }

    private void mergeShard(ZipOutputStream target, Path shard, String prefix) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(shard)))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((entry = zip.getNextEntry()) != null) {
                target.putNextEntry(new ZipEntry(prefix + entry.getName()));
                int read;
                while ((read = zip.read(buffer)) >= 0) target.write(buffer, 0, read);
                target.closeEntry();
                zip.closeEntry();
            }
        }
    }

    private List<PlannedItem> plan(String exportId, List<MemberContributionView> contributions, Instant now) {
        ArrayList<PlannedItem> planned = new ArrayList<>();
        for (MemberContributionView member : contributions) {
            if (member.personalWorkspaces().isEmpty()) {
                TeamWorkspaceExportItem item = new TeamWorkspaceExportItem(
                        RuntimeIdGenerator.teamWorkspaceExportItemId(), exportId,
                        member.user().userId().value(), null, null, "NO_WORKTREE", 0, 0L,
                        null, null, null, null, now, now);
                planned.add(new PlannedItem(
                        item, member.user().username(), "no-worktree", null, List.of(), 0, 0L));
                continue;
            }
            for (PersonalWorkspaceView personal : member.personalWorkspaces()) {
                TeamWorkspaceExportItem item = new TeamWorkspaceExportItem(
                        RuntimeIdGenerator.teamWorkspaceExportItemId(), exportId,
                        member.user().userId().value(), personal.workspace().personalWorkspaceId().value(),
                        personal.linuxServerId(), "QUEUED", 0, 0L, null, null,
                        null, null, now, now);
                boolean local = serverIdentity.linuxServerId().equals(personal.linuxServerId());
                List<FileSnapshot> files = local
                        ? scan(paths.resolve(personal.workspace().workspaceRootPath())) : List.of();
                Preflight itemPreflight = local
                        ? preflightFiles(files)
                        : remotePreflight(item, personal.workspace().versionId().value());
                planned.add(new PlannedItem(
                        item, member.user().username(), personal.workspace().workspaceName(),
                        personal.workspace().versionId().value(), files,
                        itemPreflight.fileCount(), itemPreflight.bytes()));
            }
        }
        return List.copyOf(planned);
    }

    private List<FileSnapshot> scan(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalizedRoot)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "个人 worktree 目录不可用");
        }
        ArrayList<FileSnapshot> files = new ArrayList<>();
        try {
            Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(normalizedRoot) && Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE;
                    String relative = relative(normalizedRoot, dir);
                    return relative.equals(".git") || relative.startsWith(".git/")
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = relative(normalizedRoot, file);
                    String reason = Files.isSymbolicLink(file) ? "SYMBOLIC_LINK"
                            : (!attrs.isRegularFile() ? "SPECIAL_FILE" : sensitiveReason(relative));
                    files.add(new FileSnapshot(file, relative, attrs.size(),
                            attrs.lastModifiedTime().toMillis(), reason));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "扫描个人 worktree 失败", Map.of(), exception);
        }
        return List.copyOf(files);
    }

    private Preflight preflight(List<PlannedItem> planned) {
        int files = 0;
        long bytes = 0L;
        for (PlannedItem item : planned) {
            files += item.preflightFileCount();
            bytes += item.preflightBytes();
            if (files > MAX_FILE_COUNT || bytes > MAX_UNCOMPRESSED_BYTES) return new Preflight(files, bytes);
        }
        return new Preflight(files, bytes);
    }

    private Preflight preflightFiles(List<FileSnapshot> snapshots) {
        int files = 0;
        long bytes = 0L;
        for (FileSnapshot file : snapshots) {
            if (file.excludedReason() == null) {
                files++;
                bytes += file.size();
            }
        }
        return new Preflight(files, bytes);
    }

    private Preflight remotePreflight(TeamWorkspaceExportItem item, String versionId) {
        TeamWorkspaceExportShardGateway.Preflight remote = shardGateway.inspect(
                new TeamWorkspaceExportShardGateway.Request(
                        item.exportId(), item.exportItemId(), item.sourceLinuxServerId(), item.userId(),
                        item.personalWorkspaceId(), versionId, MAX_UNCOMPRESSED_BYTES, MAX_FILE_COUNT,
                        "trace_team_export_preflight_" + item.exportItemId()));
        return new Preflight(remote.fileCount(), remote.uncompressedBytes());
    }

    static String sensitiveReason(String relative) {
        String name = Path.of(relative).getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals(".env") || name.startsWith(".env.")) {
            if (name.endsWith(".example") || name.endsWith(".sample") || name.endsWith(".template")) return null;
            return "ENV_SECRET";
        }
        if (SENSITIVE_EXACT.contains(name) || name.matches("service-account.*\\.json")) return "CREDENTIAL_FILE";
        if (name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".p12")
                || name.endsWith(".pfx") || name.endsWith(".jks") || name.endsWith(".keystore")) {
            return "PRIVATE_KEY_OR_KEYSTORE";
        }
        return null;
    }

    private PlannedNames names(ShardResult result) {
        TeamWorkspaceExportItem item = result.item();
        return new PlannedNames("members/" + safe(result.username()) + "-" + safe(item.userId()) + "/"
                + safe(result.workspaceName()) + "-"
                + safe(item.personalWorkspaceId() == null ? "no-worktree" : item.personalWorkspaceId()) + "/");
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private Path artifactPath(String exportId) {
        return artifactRoot.resolve(safe(exportId) + ".zip").normalize();
    }

    private Path shardPath(String exportId, String itemId) {
        return artifactRoot.resolve(safe(exportId)).resolve(safe(itemId) + ".zip").normalize();
    }

    private TeamWorkspaceExportJob requireOwned(String exportId, String actorUserId) {
        return exports.findJob(exportId)
                .filter(job -> Objects.equals(job.actorUserId(), actorUserId))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "团队导出任务不存在"));
    }

    private void failJob(TeamWorkspaceExportJob job, String code, String message) {
        Instant now = clock.instant();
        exports.updateJob(copyJob(job, TeamWorkspaceExportStatus.FAILED,
                job.completedItems(), job.succeededItems(), Math.max(1, job.failedItems()),
                job.fileCount(), job.uncompressedBytes(), null, null, null,
                code, message, job.startedAt(), now, null));
    }

    private TeamWorkspaceExportJob copyJob(
            TeamWorkspaceExportJob job,
            TeamWorkspaceExportStatus status,
            int completed,
            int succeeded,
            int failed,
            int fileCount,
            long bytes,
            Long archiveBytes,
            String sha256,
            String fileName,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt) {
        return new TeamWorkspaceExportJob(
                job.exportId(), job.actorUserId(), job.ownerUserId(), job.scopeMode(), job.versionId(),
                job.coordinatorLinuxServerId(), status, job.totalItems(), completed, succeeded, failed,
                fileCount, bytes, archiveBytes, sha256, fileName, errorCode, errorMessage,
                job.createdAt(), startedAt, completedAt, job.expiresAt(), cancelledAt);
    }

    private TeamWorkspaceResponses.ExportResponse response(
            TeamWorkspaceExportJob job, List<TeamWorkspaceExportItem> items) {
        return new TeamWorkspaceResponses.ExportResponse(
                job.exportId(), job.status().name(), job.totalItems(), job.completedItems(),
                job.succeededItems(), job.failedItems(), job.fileCount(), job.uncompressedBytes(),
                job.archiveBytes(), job.archiveSha256(), job.artifactFileName(), job.errorCode(),
                job.errorMessage(), job.createdAt(), job.completedAt(), job.expiresAt(),
                items.stream().map(item -> new TeamWorkspaceResponses.ExportItemResponse(
                        item.exportItemId(), item.userId(), item.personalWorkspaceId(),
                        item.sourceLinuxServerId(), item.status(), item.fileCount(),
                        item.uncompressedBytes(), item.errorCode(), item.errorMessage())).toList());
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String stableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 900));
    }

    @Scheduled(fixedDelay = 60_000L)
    void cleanupExpired() {
        Instant now = clock.instant();
        for (TeamWorkspaceExportJob job : exports.findExpiredReady(serverIdentity.linuxServerId(), now, 100)) {
            deleteArtifacts(job.exportId());
            TeamWorkspaceExportJob expired = copyJob(job, TeamWorkspaceExportStatus.EXPIRED,
                    job.completedItems(), job.succeededItems(), job.failedItems(), job.fileCount(),
                    job.uncompressedBytes(), null, null, null, job.errorCode(), job.errorMessage(),
                    job.startedAt(), job.completedAt(), job.cancelledAt());
            exports.updateJob(expired);
        }
    }

    private void deleteArtifacts(String exportId) {
        deleteQuietly(artifactPath(exportId));
        Path shards = artifactRoot.resolve(safe(exportId));
        if (Files.isDirectory(shards)) {
            try (var paths = Files.walk(shards)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteQuietly);
            } catch (IOException ignored) {
            }
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    @PreDestroy
    void close() {
        coordinator.shutdownNow();
        workers.shutdownNow();
    }

    private record PlannedItem(
            TeamWorkspaceExportItem item,
            String username,
            String workspaceName,
            String versionId,
            List<FileSnapshot> files,
            int preflightFileCount,
            long preflightBytes) {
    }

    private record FileSnapshot(
            Path path, String relativePath, long size, long modifiedMillis, String excludedReason) {
    }

    private record ExcludedFile(String path, String reason) {
    }

    private record Preflight(int fileCount, long bytes) {
    }

    private record ShardResult(
            TeamWorkspaceExportItem item,
            Path shard,
            List<ExcludedFile> excluded,
            String username,
            String workspaceName) {
    }

    private record PlannedNames(String prefix) {
    }

    private static final class WorkspaceChangedException extends RuntimeException {
        private WorkspaceChangedException(String path) {
            super("worktree changed while exporting: " + path);
        }
    }

    public record ExportAccess(
            TeamScopeMode scopeMode,
            String ownerUserId,
            String coordinatorLinuxServerId,
            List<String> targetUserIds) {
    }

    public record LocalShard(
            Path path,
            int fileCount,
            long uncompressedBytes,
            long archiveBytes,
            String sha256,
            List<TeamWorkspaceExportShardGateway.Excluded> excluded) {
    }
}
