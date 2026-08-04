package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocation;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextWorkspaceMutation;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 目标 Java 的搬迁归档接收、恢复校验和数据库切换应用服务。 */
@Service
public class PersonalWorkspaceRelocationReceiveService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonalWorkspaceRelocationReceiveService.class);

    private final PersonalWorkspaceRelocationRepository repository;
    private final ManagedWorkspaceApplicationService managedWorkspaceService;
    private final PersonalWorkspaceSnapshotService snapshotService;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;
    private ConversationContextStore conversationContextStore;

    public PersonalWorkspaceRelocationReceiveService(
            PersonalWorkspaceRelocationRepository repository,
            ManagedWorkspaceApplicationService managedWorkspaceService,
            PersonalWorkspaceSnapshotService snapshotService,
            WorkspaceServerIdentity serverIdentity,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.managedWorkspaceService = Objects.requireNonNull(managedWorkspaceService);
        this.snapshotService = Objects.requireNonNull(snapshotService);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.clock = Objects.requireNonNull(clock);
    }

    @Autowired(required = false)
    void setConversationContextStore(ConversationContextStore conversationContextStore) {
        this.conversationContextStore = conversationContextStore;
    }

    /** 票据签发前只校验数据库状态和源/目标绑定，不创建目录或接受任意文件路径。 */
    public void authorize(
            String relocationId,
            String sourceLinuxServerId,
            String targetLinuxServerId,
            String snapshotSha256,
            long archiveSizeBytes) {
        PersonalWorkspaceRelocation relocation = requireRelocation(relocationId);
        if (!serverIdentity.linuxServerId().equals(targetLinuxServerId)
                || !relocation.sourceLinuxServerId().equals(sourceLinuxServerId)
                || !relocation.targetLinuxServerId().equals(targetLinuxServerId)
                || relocation.status() != PersonalWorkspaceRelocationStatus.TRANSFERRING
                || !Objects.equals(relocation.snapshotSha256(), snapshotSha256)
                || !Objects.equals(relocation.archiveSizeBytes(), archiveSizeBytes)
                || archiveSizeBytes < 0
                || archiveSizeBytes > PersonalWorkspaceSnapshotService.MAX_ARCHIVE_BYTES) {
            throw new PlatformException(ErrorCode.CONFLICT, "个人工作区搬迁票据事实已变化");
        }
    }

    /** 消费一次性 ticket 后在受控目标根目录创建临时归档，不接受客户端传入路径。 */
    public PersonalWorkspaceRelocationArchiveUpload begin(
            String relocationId,
            String sourceLinuxServerId,
            String targetLinuxServerId,
            String snapshotSha256,
            long archiveSizeBytes,
            String traceId) {
        authorize(relocationId, sourceLinuxServerId, targetLinuxServerId, snapshotSha256, archiveSizeBytes);
        if (!repository.markApplying(
                relocationId, snapshotSha256, archiveSizeBytes, clock.instant())) {
            throw new PlatformException(ErrorCode.CONFLICT, "个人工作区搬迁接收租约已失效");
        }
        PersonalWorkspaceRelocation relocation = requireRelocation(relocationId);
        PersonalWorkspaceRelocationPaths targetPaths =
                managedWorkspaceService.targetRelocationPaths(relocation, traceId);
        try {
            Path parent = targetPaths.personalRepoRoot().toAbsolutePath().normalize().getParent();
            Files.createDirectories(parent);
            Path archive = Files.createTempFile(parent, ".workspace-relocation-", ".zip");
            OutputStream output = Files.newOutputStream(archive);
            return new PersonalWorkspaceRelocationArchiveUpload(
                    this,
                    relocationId,
                    snapshotSha256,
                    archiveSizeBytes,
                    traceId,
                    archive,
                    output,
                    MessageDigest.getInstance("SHA-256"));
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "创建个人工作区搬迁接收文件失败",
                    Map.of("reason", "TARGET_ARCHIVE_OPEN_FAILED"),
                    exception);
        }
    }

    ReceiveResult apply(
            String relocationId,
            Path archive,
            String snapshotSha256,
            long archiveSizeBytes,
            String traceId) {
        PersonalWorkspaceRelocation relocation = requireRelocation(relocationId);
        if (relocation.status() != PersonalWorkspaceRelocationStatus.APPLYING
                || !serverIdentity.linuxServerId().equals(relocation.targetLinuxServerId())
                || !Objects.equals(snapshotSha256, relocation.snapshotSha256())
                || !Objects.equals(archiveSizeBytes, relocation.archiveSizeBytes())) {
            throw new PlatformException(ErrorCode.CONFLICT, "个人工作区搬迁恢复事实已变化");
        }
        PersonalWorkspaceRelocationPaths targetPaths =
                managedWorkspaceService.targetRelocationPaths(relocation, traceId);
        PersonalWorkspaceSnapshotService.RestoreResult restored = snapshotService.restoreSnapshot(
                archive,
                snapshotSha256,
                archiveSizeBytes,
                relocationId,
                relocation.branch(),
                targetPaths);

        ConversationContextWorkspaceMutation mutation = conversationContextStore == null
                ? null
                : conversationContextStore.beginWorkspaceMutation(relocation.runtimeWorkspaceId());
        boolean switched;
        try {
            switched = repository.completeTarget(
                    relocationId,
                    relocation.sourceLinuxServerId(),
                    relocation.targetLinuxServerId(),
                    targetPaths.personalRepoRootValue(),
                    targetPaths.workspaceRootValue(),
                    restored.headCommit(),
                    traceId,
                    clock.instant());
        } catch (RuntimeException exception) {
            abortMutation(mutation, exception);
            throw exception;
        }
        if (!switched) {
            abortMutation(mutation, null);
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "个人工作区搬迁切换条件已变化",
                    Map.of("reason", "TARGET_DATABASE_CAS_LOST"));
        }
        completeMutation(mutation, relocationId);
        try {
            Files.deleteIfExists(archive);
        } catch (Exception ignored) {
            // 已切库后的临时归档清理失败不反转搬迁；文件不在工作区可见路径内。
        }
        return new ReceiveResult(relocationId, restored.headCommit());
    }

    private PersonalWorkspaceRelocation requireRelocation(String relocationId) {
        return repository.findByRelocationId(relocationId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "个人工作区搬迁记录不存在"));
    }

    private void completeMutation(ConversationContextWorkspaceMutation mutation, String relocationId) {
        if (mutation == null) {
            return;
        }
        try {
            conversationContextStore.completeWorkspaceMutation(mutation);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "event=personal_workspace_relocation_context_completion_failed relocationId={} errorType={}",
                    relocationId,
                    exception.getClass().getSimpleName());
            try {
                conversationContextStore.abortWorkspaceMutation(mutation);
            } catch (RuntimeException ignored) {
                // 数据库已完成可信绑定切换；旧上下文已在 begin 阶段失效，后续签发仍会读取新事实。
            }
        }
    }

    private void abortMutation(
            ConversationContextWorkspaceMutation mutation, RuntimeException original) {
        if (mutation == null) {
            return;
        }
        try {
            conversationContextStore.abortWorkspaceMutation(mutation);
        } catch (RuntimeException abortFailure) {
            if (original != null) {
                original.addSuppressed(abortFailure);
            }
        }
    }

    public record ReceiveResult(String relocationId, String headCommit) {
    }
}
