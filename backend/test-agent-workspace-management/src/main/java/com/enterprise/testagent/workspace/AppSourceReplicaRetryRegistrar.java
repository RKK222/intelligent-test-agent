package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为既有 active generation 登记失败副本重试，不修改快照冻结事实或到期时间。 */
@Service
public class AppSourceReplicaRetryRegistrar {

    private final AppSourceRepository appSources;

    public AppSourceReplicaRetryRegistrar(AppSourceRepository appSources) {
        this.appSources = Objects.requireNonNull(appSources);
    }

    @Transactional
    public AppSourceOperation register(RetryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!appSources.lockRepositoryForAppSource(request.repositoryId())) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "应用源码版本库不存在");
        }
        AppSourceOperation existing = appSources.findOperation(request.operationId()).orElse(null);
        if (existing != null) {
            if (!matchesImmutableIdentity(existing, request)) {
                throw new PlatformException(ErrorCode.CONFLICT, "operationId 已被其它应用源码请求使用");
            }
            return existing;
        }
        AppSourceRepositorySlot slot = appSources.findSlotForUpdate(request.repositoryId())
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码 generation 槽位不存在"));
        if (!Objects.equals(slot.activeGeneration(), request.generation()) || slot.pendingGeneration() != null) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码 generation 已变化或正在更新");
        }
        for (LinuxServerId serverId : request.targetServerIds()) {
            if (appSources.findInFlightOperationForReplica(
                            request.repositoryId(), request.generation(), serverId)
                    .isPresent()) {
                throw new PlatformException(ErrorCode.CONFLICT, "该服务器已有进行中的源码副本重试");
            }
            boolean failed = appSources.findReplica(request.repositoryId(), request.generation(), serverId)
                    .filter(replica -> replica.status() == AppSourceReplicaStatus.FAILED
                            || replica.status() == AppSourceReplicaStatus.STALE)
                    .isPresent();
            if (!failed) {
                throw new PlatformException(ErrorCode.CONFLICT, "仅允许重试失败的源码副本");
            }
        }
        AppSourceOperation operation = new AppSourceOperation(
                request.operationId(), request.appId(), request.repositoryId(), request.generation(),
                request.generation(), request.actorUserId(), AppSourceOperationType.RETRY_REPLICAS,
                request.requestHash(), AppSourceOperationStatus.PENDING, request.traceId(), request.acceptedAt(), null);
        appSources.saveOperation(operation);
        for (LinuxServerId serverId : request.targetServerIds()) {
            AppSourceReplicaStepCatalog.pendingSteps(request.operationId(), serverId, request.acceptedAt())
                    .forEach(appSources::upsertStep);
        }
        return operation;
    }

    private boolean matchesImmutableIdentity(AppSourceOperation operation, RetryRequest request) {
        return operation.appId().equals(request.appId())
                && operation.repositoryId().equals(request.repositoryId())
                && operation.actorUserId().equals(request.actorUserId())
                && operation.operationType() == AppSourceOperationType.RETRY_REPLICAS
                && operation.targetGeneration() == request.generation();
    }

    public record RetryRequest(
            String operationId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            UserId actorUserId,
            String requestHash,
            Set<LinuxServerId> targetServerIds,
            String traceId,
            Instant acceptedAt) {
        public RetryRequest {
            if (operationId == null || operationId.isBlank() || requestHash == null || requestHash.isBlank()
                    || traceId == null || traceId.isBlank() || generation < 1L) {
                throw new IllegalArgumentException("retry request fields are invalid");
            }
            Objects.requireNonNull(appId);
            Objects.requireNonNull(repositoryId);
            Objects.requireNonNull(actorUserId);
            Objects.requireNonNull(acceptedAt);
            targetServerIds = Set.copyOf(Objects.requireNonNull(targetServerIds));
            if (targetServerIds.isEmpty()) {
                throw new IllegalArgumentException("targetServerIds must not be empty");
            }
        }
    }
}
