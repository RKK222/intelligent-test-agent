package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupTask;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 按当前服务器扫描、认领并执行到期源码清理；离线服务器任务保留在数据库等待恢复。 */
@Component
public class AppSourceCleanupWorker {

    private final AppSourceRepository appSources;
    private final ManagedWorkspacePathResolver paths;
    private final AppSourceIndexManager indexes;
    private final AppSourceCleanupResultRecorder results;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;
    private final Duration leaseDuration;
    private final int batchSize;

    public AppSourceCleanupWorker(
            AppSourceRepository appSources,
            ManagedWorkspacePathResolver paths,
            AppSourceIndexManager indexes,
            AppSourceCleanupResultRecorder results,
            WorkspaceServerIdentity serverIdentity,
            Clock clock,
            @Value("${test-agent.app-source.cleanup-lease-seconds:300}") long leaseSeconds,
            @Value("${test-agent.app-source.cleanup-batch-size:32}") int batchSize) {
        this.appSources = Objects.requireNonNull(appSources);
        this.paths = Objects.requireNonNull(paths);
        this.indexes = Objects.requireNonNull(indexes);
        this.results = Objects.requireNonNull(results);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.clock = Objects.requireNonNull(clock);
        this.leaseDuration = Duration.ofSeconds(leaseSeconds);
        this.batchSize = batchSize;
        if (leaseSeconds < 1 || batchSize < 1) {
            throw new IllegalArgumentException("cleanup lease and batch size must be positive");
        }
    }

    /** 返回本轮已成功或 fencing 收敛的任务数。 */
    public int runDue() {
        Instant scanTime = clock.instant();
        LinuxServerId localServer = new LinuxServerId(serverIdentity.linuxServerId());
        int completed = 0;
        for (AppSourceCleanupTask due : appSources.findDueCleanupTasks(localServer, scanTime, batchSize)) {
            // 同一批前序文件删除可能耗时，逐任务重新取时，避免签发已经过期的绝对租约。
            Instant claimTime = clock.instant();
            String leaseOwner = "app-source-cleanup-" + UUID.randomUUID();
            AppSourceCleanupTask task = appSources.claimCleanupTask(
                            due.cleanupTaskId(), leaseOwner, claimTime.plus(leaseDuration), claimTime)
                    .orElse(null);
            if (task == null) {
                continue;
            }
            try {
                cleanWithGenerationFence(task, leaseOwner);
                completed++;
            } catch (RuntimeException exception) {
                Instant failedAt = clock.instant();
                appSources.rescheduleCleanupTask(
                        task.cleanupTaskId(), leaseOwner, task.attemptCount(),
                        failedAt.plus(retryDelay(task.attemptCount())), safeErrorCode(exception),
                        "应用源码清理失败", failedAt);
            }
        }
        return completed;
    }

    private void cleanWithGenerationFence(AppSourceCleanupTask task, String leaseOwner) {
        AppSourceSnapshot snapshot = appSources.findSnapshot(task.repositoryId(), task.generation())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码清理快照不存在"));
        String logicalRoot = paths.appSourceValue(snapshot.repositoryEnglishName());
        Path target = paths.resolve(logicalRoot).toAbsolutePath().normalize();
        withFileLock(target, () -> {
            Instant fenceTime = clock.instant();
            appSources.findReplica(task.repositoryId(), task.generation(), task.linuxServerId())
                    .filter(replica -> replica.status()
                            == com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus.RUNNING)
                    .filter(replica -> replica.leaseUntil() != null && replica.leaseUntil().isAfter(fenceTime))
                    .ifPresent(replica -> {
                        throw new PlatformException(ErrorCode.CONFLICT, "源码副本物化租约仍有效，延后清理");
                    });
            AppSourceRepositorySlot slot = appSources.findSlot(task.repositoryId()).orElse(null);
            if (slot != null && Objects.equals(slot.pendingGeneration(), task.generation())) {
                throw new PlatformException(ErrorCode.CONFLICT, "待物化 generation 暂不清理");
            }
            boolean newerGenerationOwnsSharedRoot = slot != null
                    && (readyLocalReplica(task, slot.activeGeneration())
                            || readyLocalReplica(task, slot.pendingGeneration()));
            cleanupGenerationStaging(target, task.generation());
            if (!newerGenerationOwnsSharedRoot) {
                deleteSourceContentPreservingIndex(target);
                if (snapshot.indexSha256() != null) {
                    indexes.ensureAuthoritativeIndex(target, snapshot);
                }
                cleanupBackups(target);
            } else if (slot.activeGeneration() != null) {
                appSources.findSnapshot(task.repositoryId(), slot.activeGeneration())
                        .filter(active -> active.indexSha256() != null)
                        .ifPresent(active -> indexes.ensureAuthoritativeIndex(target, active));
            }
            // 文件删除完成后重新取绝对时间，过期 lease 必须由数据库拒绝旧清理者写回。
            results.complete(task, leaseOwner, clock.instant());
        });
    }

    private boolean different(Long generation, long expected) {
        return generation != null && generation != expected;
    }

    /** 全局 slot 已前进但本服务器没有新代 READY 副本时，磁盘仍属于旧代，不能因此跳过清理。 */
    private boolean readyLocalReplica(AppSourceCleanupTask task, Long generation) {
        return different(generation, task.generation())
                && appSources.findReplica(task.repositoryId(), generation, task.linuxServerId())
                        .filter(replica -> replica.status()
                                == com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus.READY)
                        .isPresent();
    }

    private void deleteSourceContentPreservingIndex(Path target) {
        if (!Files.isDirectory(target)) {
            return;
        }
        try (var children = Files.list(target)) {
            for (Path child : children.toList()) {
                if (!AppSourceApplicationService.INDEX_FILE_NAME.equals(child.getFileName().toString())) {
                    deleteTree(child);
                }
            }
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "删除应用源码内容失败", Map.of(), exception);
        }
    }

    private void cleanupGenerationStaging(Path target, long generation) {
        cleanupSiblingMatches(target, "." + target.getFileName() + ".g" + generation + ".", ".staging");
    }

    private void cleanupBackups(Path target) {
        cleanupSiblingMatches(target, "." + target.getFileName() + ".", ".backup");
    }

    private void cleanupSiblingMatches(Path target, String prefix, String suffix) {
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return;
        }
        try (var siblings = Files.list(parent)) {
            for (Path sibling : siblings.toList()) {
                String name = sibling.getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(suffix)) {
                    deleteTree(sibling);
                }
            }
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "删除应用源码临时目录失败", Map.of(), exception);
        }
    }

    private void withFileLock(Path target, Runnable operation) {
        Path parent = Objects.requireNonNull(target.getParent());
        Path lockPath = parent.resolve("." + target.getFileName() + ".app-source.lock");
        try {
            Files.createDirectories(parent);
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock acquired;
                try {
                    acquired = channel.tryLock();
                } catch (OverlappingFileLockException exception) {
                    acquired = null;
                }
                if (acquired == null) {
                    throw new PlatformException(ErrorCode.CONFLICT, "应用源码目录正在更新");
                }
                try (FileLock ignored = acquired) {
                    operation.run();
                }
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "应用源码清理文件锁失败", Map.of(), exception);
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Duration retryDelay(int attemptCount) {
        return Duration.ofSeconds(Math.min(300L, 5L << Math.min(6, Math.max(0, attemptCount - 1))));
    }

    private String safeErrorCode(RuntimeException exception) {
        return exception instanceof PlatformException platform ? platform.errorCode().name() : ErrorCode.INTERNAL_ERROR.name();
    }
}
