package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupStatus;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupTask;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.appsource.AppSourceStepScope;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在单个关系型事务内登记应用源码 generation。
 *
 * <p>远端只读验证必须由调用方在进入本服务前完成。事务取得代码库行锁后，第一条持久化写固定为
 * 全部目标服务器的清理任务；延迟外键保证随后才可写 snapshot/operation。这样任何后续异常都会整体
 * 回滚，调用方也只有在本方法返回后才能触碰磁盘或发送唤醒。
 */
@Service
public class AppSourceMaterializationRegistrar {

    private final AppSourceRepository repository;

    public AppSourceMaterializationRegistrar(AppSourceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /** 原子登记待物化 generation；同一代码库由事实行锁和 slot 乐观版本共同保护。 */
    @Transactional
    public RegistrationResult register(RegistrationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!repository.lockRepositoryForAppSource(request.repositoryId())) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "应用源码版本库不存在");
        }
        AppSourceOperation existing = repository.findOperation(request.operationId()).orElse(null);
        if (existing != null) {
            verifyReplayIdentity(existing, request);
            return new RegistrationResult(existing, frozenTargetServerIds(existing));
        }
        AppSourceRepositorySlot current = repository.findSlotForUpdate(request.repositoryId()).orElse(null);
        Long activeGeneration = current == null ? null : current.activeGeneration();
        if (!Objects.equals(activeGeneration, request.expectedGeneration())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "应用源码 generation 已变化",
                    Map.of("actualGeneration", activeGeneration == null ? 0L : activeGeneration));
        }
        if (current != null && current.pendingGeneration() != null) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码已有进行中的物化操作");
        }
        long generation = current == null ? 1L : current.nextGeneration();

        List<AppSourceCleanupTask> cleanupTasks = request.targetServerIds().stream()
                .map(serverId -> cleanupTask(request, generation, serverId))
                .toList();
        // 强制首写：此调用之前只有 SELECT/SELECT FOR UPDATE，之后才允许写入其它 app-source 行。
        repository.insertCleanupTasks(cleanupTasks);

        AppSourceSnapshot snapshot = new AppSourceSnapshot(
                request.repositoryId(),
                generation,
                request.repositoryEnglishName(),
                request.purpose(),
                request.actorUserId(),
                request.branch(),
                request.targetCommit(),
                request.selectedPaths(),
                null,
                request.acceptedAt(),
                request.expiresAt(),
                AppSourceSnapshotStatus.PENDING,
                request.acceptedAt(),
                request.acceptedAt());
        repository.saveSnapshot(snapshot);

        AppSourceOperation operation = new AppSourceOperation(
                request.operationId(),
                request.appId(),
                request.repositoryId(),
                activeGeneration,
                generation,
                request.actorUserId(),
                request.operationType(),
                request.requestHash(),
                AppSourceOperationStatus.PENDING,
                request.traceId(),
                request.acceptedAt(),
                null);
        repository.saveOperation(operation);

        for (LinuxServerId serverId : request.targetServerIds()) {
            boolean inserted = repository.insertReplicaIfAbsent(new AppSourceReplica(
                    request.repositoryId(), generation, serverId, null, AppSourceReplicaStatus.PENDING,
                    null, null, 0, request.acceptedAt(), null, null,
                    request.acceptedAt(), request.acceptedAt()));
            if (!inserted) {
                throw new PlatformException(ErrorCode.CONFLICT, "应用源码副本目标登记冲突");
            }
            AppSourceReplicaStepCatalog.pendingSteps(request.operationId(), serverId, request.acceptedAt())
                    .forEach(repository::upsertStep);
        }

        AppSourceRepositorySlot nextSlot = new AppSourceRepositorySlot(
                request.repositoryId(),
                activeGeneration,
                generation,
                generation + 1L,
                request.operationId(),
                current == null ? 0L : current.lockVersion() + 1L,
                current == null ? request.acceptedAt() : current.createdAt(),
                request.acceptedAt());
        boolean slotSaved = current == null
                ? repository.insertSlotIfAbsent(nextSlot)
                : repository.updateSlotIfVersion(nextSlot, current.lockVersion());
        if (!slotSaved) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码 generation 登记竞争失败");
        }
        return new RegistrationResult(operation, request.targetServerIds());
    }

    /**
     * 幂等重放必须先于可变 slot 校验，并严格绑定首请求的不可变身份。
     *
     * <p>sourceGeneration 同时参与校验，避免调用方复用 operationId 跨 active generation 重放。
     */
    private void verifyReplayIdentity(AppSourceOperation existing, RegistrationRequest request) {
        boolean sameIdentity = existing.appId().equals(request.appId())
                && existing.repositoryId().equals(request.repositoryId())
                && existing.actorUserId().equals(request.actorUserId())
                && existing.operationType() == request.operationType()
                && existing.requestHash().equals(request.requestHash())
                && Objects.equals(existing.sourceGeneration(), request.expectedGeneration());
        if (!sameIdentity) {
            throw new PlatformException(ErrorCode.CONFLICT, "operationId 已被其它应用源码请求使用");
        }
    }

    /** 从首请求已持久化的 SERVER 步骤恢复冻结目标，兼容仅保留 replica 的历史 operation。 */
    private Set<LinuxServerId> frozenTargetServerIds(AppSourceOperation operation) {
        LinkedHashSet<LinuxServerId> frozenTargets = new LinkedHashSet<>();
        repository.findSteps(operation.operationId()).stream()
                .filter(step -> step.scope() == AppSourceStepScope.SERVER)
                .map(AppSourceOperationStep::linuxServerId)
                .forEach(frozenTargets::add);
        if (frozenTargets.isEmpty()) {
            repository.findReplicas(operation.repositoryId(), operation.targetGeneration()).stream()
                    .map(AppSourceReplica::linuxServerId)
                    .forEach(frozenTargets::add);
        }
        if (frozenTargets.isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码幂等操作缺少已冻结的目标服务器");
        }
        return Set.copyOf(frozenTargets);
    }

    private AppSourceCleanupTask cleanupTask(
            RegistrationRequest request, long generation, LinuxServerId serverId) {
        return new AppSourceCleanupTask(
                cleanupId(request.operationId(), serverId.value()),
                request.operationId(),
                request.repositoryId(),
                generation,
                serverId,
                request.expiresAt(),
                AppSourceCleanupStatus.PENDING,
                null,
                null,
                0,
                request.expiresAt(),
                null,
                null,
                request.traceId(),
                request.acceptedAt(),
                request.acceptedAt());
    }

    private String cleanupId(String operationId, String serverId) {
        return stableId("asc_", operationId + "\n" + serverId);
    }

    private String stableId(String prefix, String source) {
        return prefix + UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    /** 受理前已经规范化、验证且冻结的事务输入。 */
    public record RegistrationRequest(
            String operationId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            String repositoryEnglishName,
            UserId actorUserId,
            AppSourceOperationType operationType,
            String requestHash,
            Long expectedGeneration,
            String branch,
            String targetCommit,
            List<AppSourceSelectedPath> selectedPaths,
            AppSourcePurpose purpose,
            Instant expiresAt,
            Set<LinuxServerId> targetServerIds,
            String traceId,
            Instant acceptedAt) {

        public RegistrationRequest {
            operationId = requireText(operationId, "operationId");
            Objects.requireNonNull(appId, "appId must not be null");
            Objects.requireNonNull(repositoryId, "repositoryId must not be null");
            repositoryEnglishName = requireText(repositoryEnglishName, "repositoryEnglishName");
            Objects.requireNonNull(actorUserId, "actorUserId must not be null");
            Objects.requireNonNull(operationType, "operationType must not be null");
            requestHash = requireText(requestHash, "requestHash");
            branch = requireText(branch, "branch");
            targetCommit = requireText(targetCommit, "targetCommit");
            selectedPaths = List.copyOf(Objects.requireNonNull(selectedPaths, "selectedPaths must not be null"));
            Objects.requireNonNull(purpose, "purpose must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            targetServerIds = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(
                    targetServerIds, "targetServerIds must not be null")));
            if (targetServerIds.isEmpty()) {
                throw new IllegalArgumentException("targetServerIds must not be empty");
            }
            traceId = requireText(traceId, "traceId");
            Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }

    /** 事务提交后调用方据此唤醒固定目标服务器。 */
    public record RegistrationResult(AppSourceOperation operation, Set<LinuxServerId> targetServerIds) {
        public RegistrationResult {
            Objects.requireNonNull(operation, "operation must not be null");
            targetServerIds = Set.copyOf(targetServerIds);
        }
    }
}
