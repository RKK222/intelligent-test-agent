package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 单服务器应用源码副本 worker。
 *
 * <p>数据库租约是跨进程执行事实，目标同根文件锁再保护本机原子替换；只有 Git 和索引均成功后才注册
 * generation 专属 Workspace，并由事务 recorder 收敛全局状态。</p>
 */
@Component
public class AppSourceReplicaWorker {

    private final AppSourceRepository appSources;
    private final ConfigurationManagementRepository configuration;
    private final AppSourceGitAccessResolver gitAccess;
    private final AppSourceGitMaterializer materializer;
    private final AppSourceReplicaResultRecorder results;
    private final AppSourceReplicaProgressRecorder progress;
    private final ManagedWorkspacePathResolver paths;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;
    private final Duration leaseDuration;

    /** 生产租约默认十分钟，覆盖有界浅克隆并允许进程退出后自动接管。 */
    @Autowired
    public AppSourceReplicaWorker(
            AppSourceRepository appSources,
            ConfigurationManagementRepository configuration,
            AppSourceGitAccessResolver gitAccess,
            AppSourceGitMaterializer materializer,
            AppSourceReplicaResultRecorder results,
            AppSourceReplicaProgressRecorder progress,
            ManagedWorkspacePathResolver paths,
            WorkspaceServerIdentity serverIdentity,
            @Value("${test-agent.app-source.replica-lease-seconds:600}") long leaseSeconds) {
        this(appSources, configuration, gitAccess, materializer, results, progress, paths, serverIdentity,
                Clock.systemUTC(), Duration.ofSeconds(leaseSeconds));
    }

    AppSourceReplicaWorker(
            AppSourceRepository appSources,
            ConfigurationManagementRepository configuration,
            AppSourceGitAccessResolver gitAccess,
            AppSourceGitMaterializer materializer,
            AppSourceReplicaResultRecorder results,
            ManagedWorkspacePathResolver paths,
            WorkspaceServerIdentity serverIdentity,
            Clock clock,
            Duration leaseDuration) {
        this(appSources, configuration, gitAccess, materializer, results,
                new AppSourceReplicaProgressRecorder(appSources), paths, serverIdentity, clock, leaseDuration);
    }

    AppSourceReplicaWorker(
            AppSourceRepository appSources,
            ConfigurationManagementRepository configuration,
            AppSourceGitAccessResolver gitAccess,
            AppSourceGitMaterializer materializer,
            AppSourceReplicaResultRecorder results,
            AppSourceReplicaProgressRecorder progress,
            ManagedWorkspacePathResolver paths,
            WorkspaceServerIdentity serverIdentity,
            Clock clock,
            Duration leaseDuration) {
        this.appSources = Objects.requireNonNull(appSources);
        this.configuration = Objects.requireNonNull(configuration);
        this.gitAccess = Objects.requireNonNull(gitAccess);
        this.materializer = Objects.requireNonNull(materializer);
        this.results = Objects.requireNonNull(results);
        this.progress = Objects.requireNonNull(progress);
        this.paths = Objects.requireNonNull(paths);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.clock = Objects.requireNonNull(clock);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    /** 尝试执行固定 generation 的本机副本；重复唤醒通过 lease 幂等合并。 */
    public Outcome run(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String traceId) {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        if (!serverIdentity.linuxServerId().equals(linuxServerId.value())) {
            return Outcome.IGNORED_WRONG_SERVER;
        }
        Instant claimTime = clock.instant();
        AppSourceSnapshot snapshot = appSources.findSnapshot(repositoryId, generation)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码快照不存在"));
        if ((snapshot.status() != AppSourceSnapshotStatus.PENDING
                        && snapshot.status() != AppSourceSnapshotStatus.ACTIVE)
                || !snapshot.expiresAt().isAfter(claimTime)) {
            return Outcome.SKIPPED_STALE;
        }
        AppSourceOperation operation = appSources.findInFlightOperationForReplica(
                        repositoryId, generation, linuxServerId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码操作不存在"));
        CodeRepository repository = configuration.findRepository(repositoryId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "应用源码版本库不存在"));
        String leaseOwner = "app-source-" + UUID.randomUUID();
        AppSourceReplica claimed = appSources.claimReplica(
                        repositoryId, generation, linuxServerId, leaseOwner,
                        claimTime.plus(leaseDuration), claimTime)
                .orElse(null);
        if (claimed == null) {
            return Outcome.NOT_CLAIMED;
        }
        String[] currentStep = {AppSourceReplicaStepCatalog.LEASE_CLAIM};
        try {
            progress.resetForAttempt(operation, claimed, leaseOwner, claimTime);
            progress.start(operation, claimed, leaseOwner, AppSourceReplicaStepCatalog.QUEUED, clock.instant());
            progress.succeed(operation, claimed, leaseOwner, AppSourceReplicaStepCatalog.QUEUED, clock.instant());
            progress.start(operation, claimed, leaseOwner, AppSourceReplicaStepCatalog.LEASE_CLAIM, claimTime);
            progress.succeed(operation, claimed, leaseOwner, AppSourceReplicaStepCatalog.LEASE_CLAIM, clock.instant());
            currentStep[0] = AppSourceReplicaStepCatalog.LOCAL_LOCK;
            AppSourceGitAccessResolver.GitAccess access = gitAccess.resolve(repository, operation.actorUserId());
            String logicalRoot = paths.appSourceValue(snapshot.repositoryEnglishName());
            Path targetRoot = paths.resolve(logicalRoot).toAbsolutePath().normalize();
            progress.start(operation, claimed, leaseOwner, AppSourceReplicaStepCatalog.LOCAL_LOCK, clock.instant());
            withFileLock(targetRoot, () -> {
                progress.succeed(
                        operation, claimed, leaseOwner, AppSourceReplicaStepCatalog.LOCAL_LOCK, clock.instant());
                return materializer.materialize(
                        new AppSourceGitMaterializer.Request(
                                targetRoot, access.gitUrl(), snapshot.branch(), snapshot.targetCommit(),
                                snapshot.selectedPaths(), access.privateKey(), snapshot.generation(), snapshot.expiresAt()),
                        materialized -> {
                            currentStep[0] = AppSourceReplicaStepCatalog.REGISTER_WORKSPACE;
                            progress.start(
                                    operation, claimed, leaseOwner,
                                    AppSourceReplicaStepCatalog.REGISTER_WORKSPACE, clock.instant());
                            // 租约按绝对时间 fencing，Git 完成后必须重新取时，不能复用认领时刻。
                            Instant completedAt = clock.instant();
                            Workspace workspace = new Workspace(
                                    new WorkspaceId(RuntimeIdGenerator.workspaceId()),
                                    snapshot.repositoryEnglishName(), logicalRoot, WorkspaceStatus.ACTIVE,
                                    completedAt, completedAt, linuxServerId.value(), operation.traceId());
                            results.recordSuccess(
                                    operation, claimed, leaseOwner, workspace,
                                    materialized.indexSha256(), completedAt);
                        },
                        new AppSourceGitMaterializer.Progress() {
                            @Override
                            public void started(String stepCode) {
                                currentStep[0] = stepCode;
                                progress.start(operation, claimed, leaseOwner, stepCode, clock.instant());
                            }

                            @Override
                            public void succeeded(String stepCode) {
                                progress.succeed(operation, claimed, leaseOwner, stepCode, clock.instant());
                            }
                        });
            });
            return Outcome.SUCCEEDED;
        } catch (RuntimeException exception) {
            if (leaseLost(exception)) {
                return Outcome.NOT_CLAIMED;
            }
            Instant failedAt = clock.instant();
            try {
                progress.failCurrentAndSkipFollowing(
                        operation, claimed, leaseOwner, currentStep[0], failedAt);
                results.recordFailure(
                        operation, claimed, leaseOwner, safeErrorCode(exception), "源码副本物化失败", failedAt);
            } catch (RuntimeException terminalFailure) {
                if (leaseLost(terminalFailure)) {
                    return Outcome.NOT_CLAIMED;
                }
                throw terminalFailure;
            }
            return Outcome.FAILED;
        }
    }

    private <T> T withFileLock(Path targetRoot, LockedOperation<T> operation) {
        AppSourcePathGuard.requireSafe(targetRoot);
        Path parent = Objects.requireNonNull(targetRoot.getParent(), "target root parent must not be null");
        Path lockPath = parent.resolve("." + targetRoot.getFileName() + ".app-source.lock");
        try {
            AppSourcePathGuard.requireSafe(lockPath);
            Files.createDirectories(parent);
            AppSourcePathGuard.requireSafe(lockPath);
            try (FileChannel channel = FileChannel.open(
                            lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock lock = tryLock(channel, targetRoot)) {
                return operation.run();
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "应用源码文件锁执行失败",
                    Map.of("failure", "LOCAL_FILE_LOCK"),
                    exception);
        }
    }

    private FileLock tryLock(FileChannel channel, Path targetRoot) throws java.io.IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                return lock;
            }
        } catch (OverlappingFileLockException ignored) {
            // 同一 JVM 已持有锁时与跨进程 tryLock 未命中使用相同安全错误。
        }
        throw new PlatformException(
                ErrorCode.CONFLICT,
                "应用源码目录正在被其它任务更新",
                Map.of("repository", targetRoot.getFileName().toString()));
    }

    private String safeErrorCode(RuntimeException exception) {
        if (exception instanceof PlatformException platformException) {
            return platformException.errorCode().name();
        }
        return ErrorCode.INTERNAL_ERROR.name();
    }

    private boolean leaseLost(RuntimeException exception) {
        return exception instanceof PlatformException platformException
                && "REPLICA_LEASE_LOST".equals(platformException.details().get("failure"));
    }

    @FunctionalInterface
    private interface LockedOperation<T> {
        T run();
    }

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        NOT_CLAIMED,
        SKIPPED_STALE,
        IGNORED_WRONG_SERVER
    }
}
