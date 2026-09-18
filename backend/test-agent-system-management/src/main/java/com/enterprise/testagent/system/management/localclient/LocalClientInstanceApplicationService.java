package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialRepository;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientRolloutRepository;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前用户本地客户端实例查询与所有权校验。 */
@Service
public class LocalClientInstanceApplicationService {

    private static final Map<String, Boolean> CAPABILITIES = capabilities();

    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final LocalClientVersionRepository versionRepository;
    private final LocalClientCredentialRepository credentialRepository;
    private LocalClientRolloutRepository rolloutRepository;
    private LocalClientPublicCapabilityRepository publicCapabilityRepository;

    public LocalClientInstanceApplicationService(
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientVersionRepository versionRepository,
            LocalClientCredentialRepository credentialRepository) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
        this.versionRepository = Objects.requireNonNull(versionRepository);
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
    }

    /** 方法注入保持既有模块测试构造器兼容。 */
    @Autowired
    void setPublicCapabilityRepository(LocalClientPublicCapabilityRepository repository) {
        this.publicCapabilityRepository = Objects.requireNonNull(repository);
    }

    /** 方法注入保持既有模块测试构造器兼容；生产装配必须提供灰度可见性仓储。 */
    @Autowired
    void setRolloutRepository(LocalClientRolloutRepository repository) {
        this.rolloutRepository = Objects.requireNonNull(repository);
    }

    @Transactional(readOnly = true)
    public List<LocalClientInstanceResponses.InstanceView> list(UserId userId) {
        // 用户撤销凭据或超管关闭客户端灰度都只隐藏投影；灰度本身不撤销 Key，也不断开客户端连接。
        if (credentialRepository.findByUserId(userId).filter(credential -> credential.active()).isEmpty()
                || rolloutRepository == null
                || !rolloutRepository.isEnabled(userId)) {
            return List.of();
        }
        LocalClientVersionModels.EffectivePolicy effective = LocalClientVersionModels.resolveEffectivePolicy(
                versionRepository.findGlobalPolicy().orElse(null),
                versionRepository.findUserPolicy(userId).orElse(null));
        // PostgreSQL 保留实例历史供外键和审计使用；普通用户投影只展示仍有短 TTL 实时连接的实例。
        // OpenCode 健康与否不参与过滤，确保在线但异常的客户端仍可从页面发起重启。
        return instanceRepository.findByUserId(userId).stream()
                .flatMap(instance -> connectionStore.find(instance.clientInstanceId())
                        .filter(route -> route.userId().equals(instance.userId()))
                        .stream()
                        .map(route -> view(instance, effective, route)))
                .toList();
    }

    @Transactional(readOnly = true)
    public LocalClientInstance requireOwned(UserId userId, LocalClientInstanceId clientInstanceId) {
        LocalClientInstance instance = instanceRepository.findById(clientInstanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地客户端实例不存在"));
        if (!instance.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不属于当前用户");
        }
        return instance;
    }

    public LocalClientConnectionRoute requireOwnedOnline(UserId userId, LocalClientInstanceId clientInstanceId) {
        requireOwned(userId, clientInstanceId);
        LocalClientConnectionRoute route = connectionStore.find(clientInstanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地客户端已离线"));
        if (!route.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端连接不属于当前用户");
        }
        return route;
    }

    private LocalClientInstanceResponses.InstanceView view(
            LocalClientInstance instance,
            LocalClientVersionModels.EffectivePolicy effective,
            LocalClientConnectionRoute route) {
        String compatibleTarget = compatibleTargetVersion(instance, effective.targetVersion());
        return new LocalClientInstanceResponses.InstanceView(
                instance.clientInstanceId().value(),
                instance.clientName(),
                instance.platform(),
                instance.architecture(),
                instance.clientVersion(),
                instance.opencodeVersion(),
                true,
                route.connectionGeneration(),
                route.reportedAddresses(),
                route.observedRemoteAddress(),
                route.opencodePort(),
                route.processStatus().name(),
                route.opencodeHealthy(),
                route.processId(),
                route.processStartedAt(),
                route.lastHeartbeatAt(),
                instance.lastConnectedAt(),
                instance.lastDisconnectedAt(),
                CAPABILITIES,
                instance.selfUpdateSupported(),
                compatibleTarget,
                direction(instance, compatibleTarget),
                instance.lastUpdateStatus(),
                instance.lastUpdateAt(),
                publicCapabilities(instance));
    }

    /** 页面不能把其它平台的全局目标展示成当前实例的可执行更新。 */
    private String compatibleTargetVersion(LocalClientInstance instance, String targetVersion) {
        if (targetVersion == null) {
            return null;
        }
        return versionRepository.findRelease(targetVersion)
                .filter(LocalClientVersionModels.Release::compatible)
                .filter(release -> release.platform().equals(instance.platform()))
                .filter(release -> release.architecture().equals(instance.architecture()))
                .map(LocalClientVersionModels.Release::version)
                .orElse(null);
    }

    private LocalClientInstanceResponses.PublicCapabilitiesView publicCapabilities(LocalClientInstance instance) {
        boolean supported = instance.selfUpdateCapabilities().contains("PUBLIC_CAPABILITY_SYNC_V1");
        if (publicCapabilityRepository == null) {
            return new LocalClientInstanceResponses.PublicCapabilitiesView(
                    supported, null, null, null, null, supported ? "UNKNOWN" : "UNSUPPORTED", null,
                    null, null, null, null, null, null, null);
        }
        LocalClientPublicCapabilityModels.InstanceState state = publicCapabilityRepository
                .findInstanceState(instance.clientInstanceId()).orElse(null);
        LocalClientPublicCapabilityModels.Release pending = state == null || state.pendingDigest() == null
                ? null : publicCapabilityRepository.findReleaseByDigest(state.pendingDigest()).orElse(null);
        return new LocalClientInstanceResponses.PublicCapabilitiesView(
                supported,
                state == null ? null : state.activeCommit(),
                state == null ? null : state.activeDigest(),
                state == null ? null : state.pendingCommit(),
                state == null ? null : state.pendingDigest(),
                state == null ? supported ? "NOT_REPORTED" : "UNSUPPORTED" : state.status().name(),
                state == null ? null : state.errorCode(),
                pending == null ? null : pending.counts().agents(),
                pending == null ? null : pending.counts().skills(),
                pending == null ? null : pending.counts().tools(),
                pending == null ? null : pending.requiresRestart(),
                pending == null ? null : pending.changeSummaryJson(),
                state == null ? null : state.reportedAt(),
                state == null ? null : state.updatedAt());
    }

    private static String direction(LocalClientInstance instance, String targetVersion) {
        if (!instance.selfUpdateSupported() || targetVersion == null) {
            return null;
        }
        try {
            return LocalClientReleaseVersion.parse(instance.clientVersion())
                    .directionTo(LocalClientReleaseVersion.parse(targetVersion))
                    .name();
        } catch (IllegalArgumentException exception) {
            // 旧客户端的 0.1.0 版本无法参与时间戳比较，管理页通过 selfUpdateSupported 明确提示重装。
            return null;
        }
    }

    private static Map<String, Boolean> capabilities() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("chat", true);
        values.put("fileManagement", true);
        values.put("nightExecution", true);
        values.put("terminal", false);
        values.put("gitPublish", false);
        values.put("agentConfig", false);
        values.put("protectedAgentExecution", true);
        values.put("attachments", true);
        values.put("collaboration", false);
        return Map.copyOf(values);
    }
}
