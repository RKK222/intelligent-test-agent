package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocation;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationCandidate;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 每台 Java 只处理源 worktree 位于本服务器的个人工作区搬迁。 */
@Service
public class PersonalWorkspaceRelocationWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonalWorkspaceRelocationWorker.class);
    private static final int DISCOVERY_LIMIT = 200;
    private static final int CLAIM_LIMIT = 2;
    private static final Duration LEASE_TTL = Duration.ofMinutes(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(30);

    private final PersonalWorkspaceRelocationRepository repository;
    private final ManagedWorkspaceApplicationService managedWorkspaceService;
    private final PersonalWorkspaceSnapshotService snapshotService;
    private final PersonalWorkspaceRelocationTransferGateway transferGateway;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;

    public PersonalWorkspaceRelocationWorker(
            PersonalWorkspaceRelocationRepository repository,
            ManagedWorkspaceApplicationService managedWorkspaceService,
            PersonalWorkspaceSnapshotService snapshotService,
            PersonalWorkspaceRelocationTransferGateway transferGateway,
            WorkspaceServerIdentity serverIdentity,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.managedWorkspaceService = Objects.requireNonNull(managedWorkspaceService);
        this.snapshotService = Objects.requireNonNull(snapshotService);
        this.transferGateway = Objects.requireNonNull(transferGateway);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 扫描数据库错配、写入可恢复状态并认领一个有界批次。 */
    public RunResult runDue(String traceId) {
        String currentServer = serverIdentity.linuxServerId();
        List<PersonalWorkspaceRelocationCandidate> mismatches =
                repository.findMismatches(currentServer, DISCOVERY_LIMIT);
        Instant discoveryTime = clock.instant();
        for (PersonalWorkspaceRelocationCandidate candidate : mismatches) {
            repository.discover(
                    candidate,
                    RuntimeIdGenerator.personalWorkspaceRelocationId(),
                    traceId,
                    discoveryTime);
        }

        int succeeded = 0;
        int retried = 0;
        List<PersonalWorkspaceRelocation> due = repository.findClaimable(
                currentServer, clock.instant(), CLAIM_LIMIT);
        for (PersonalWorkspaceRelocation dueRelocation : due) {
            String leaseOwner = currentServer + ":" + UUID.randomUUID().toString().replace("-", "");
            Instant now = clock.instant();
            var claimed = repository.claim(
                    dueRelocation.relocationId(), leaseOwner, now.plus(LEASE_TTL), now);
            if (claimed.isEmpty()) {
                continue;
            }
            try {
                process(claimed.get(), leaseOwner, traceId);
                succeeded++;
            } catch (RuntimeException exception) {
                retry(claimed.get(), leaseOwner, exception);
                retried++;
            }
        }
        return new RunResult(mismatches.size(), due.size(), succeeded, retried);
    }

    private void process(
            PersonalWorkspaceRelocation relocation, String leaseOwner, String traceId) {
        PersonalWorkspaceRelocationPaths sourcePaths =
                managedWorkspaceService.sourceRelocationPaths(relocation);
        if (relocation.cleanupPending()) {
            cleanupSource(relocation, sourcePaths, leaseOwner);
            return;
        }
        Path archive = null;
        try {
            archive = Files.createTempFile(
                    sourcePaths.personalRepoRoot().getParent(),
                    ".workspace-relocation-",
                    ".zip");
            PersonalWorkspaceSnapshotService.Snapshot snapshot = snapshotService.exportSnapshot(
                    sourcePaths.personalRepoRoot(), archive, relocation.relocationId());
            if (!repository.markTransferring(
                    relocation.relocationId(),
                    leaseOwner,
                    snapshot.sha256(),
                    snapshot.size(),
                    clock.instant())) {
                throw new IllegalStateException("personal workspace relocation lease lost before transfer");
            }
            transferGateway.transfer(
                    relocation,
                    snapshot.archive(),
                    snapshot.sha256(),
                    snapshot.size(),
                    traceId);
            cleanupSource(relocation, sourcePaths, leaseOwner);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("personal workspace relocation source preparation failed", exception);
        } finally {
            if (archive != null) {
                try {
                    Files.deleteIfExists(archive);
                } catch (Exception ignored) {
                    // 临时归档不在用户工作区可见路径，后续由系统清理兜底。
                }
            }
        }
    }

    private void cleanupSource(
            PersonalWorkspaceRelocation relocation,
            PersonalWorkspaceRelocationPaths sourcePaths,
            String leaseOwner) {
        snapshotService.removeSourceWorktree(sourcePaths);
        if (!repository.completeCleanup(relocation.relocationId(), leaseOwner, clock.instant())) {
            throw new IllegalStateException("personal workspace relocation cleanup fence lost");
        }
        LOGGER.info(
                "event=personal_workspace_relocation_succeeded relocationId={} sourceLinuxServerId={} targetLinuxServerId={}",
                relocation.relocationId(),
                relocation.sourceLinuxServerId(),
                relocation.targetLinuxServerId());
    }

    private void retry(
            PersonalWorkspaceRelocation relocation,
            String leaseOwner,
            RuntimeException exception) {
        Instant now = clock.instant();
        long delayMinutes = Math.min(
                MAX_RETRY_DELAY.toMinutes(),
                1L << Math.min(5, Math.max(0, relocation.attemptCount() - 1)));
        String code = exception instanceof PlatformException platform
                ? "RELOCATION_" + platform.errorCode().name()
                : "RELOCATION_INTERNAL_ERROR";
        repository.reschedule(
                relocation.relocationId(),
                leaseOwner,
                relocation.attemptCount(),
                now.plus(Duration.ofMinutes(delayMinutes)),
                code,
                "个人工作区自动搬迁失败，等待下一轮安全重试",
                now);
        LOGGER.warn(
                "event=personal_workspace_relocation_retry relocationId={} sourceLinuxServerId={} targetLinuxServerId={} attempt={} errorCode={} errorType={}",
                relocation.relocationId(),
                relocation.sourceLinuxServerId(),
                relocation.targetLinuxServerId(),
                relocation.attemptCount(),
                code,
                exception.getClass().getSimpleName());
    }

    public record RunResult(int discovered, int due, int succeeded, int retried) {
        public Map<String, Object> asDetails() {
            return Map.of(
                    "discovered", discovered,
                    "due", due,
                    "succeeded", succeeded,
                    "retried", retried);
        }
    }
}
