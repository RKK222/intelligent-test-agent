package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRevoker;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrant;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrantStore;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 客户端认证后的实例登记、generation fencing、心跳与连接级模型授权。 */
@Service
public class LocalClientRegistrationService {

    static final Duration MODEL_GRANT_TTL = Duration.ofSeconds(30);

    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final LocalClientModelGrantStore modelGrantStore;
    private final Clock clock;
    private LocalClientConnectionRevoker connectionRevoker;

    @Autowired
    public LocalClientRegistrationService(
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientModelGrantStore modelGrantStore) {
        this(instanceRepository, connectionStore, modelGrantStore, Clock.systemUTC());
    }

    LocalClientRegistrationService(
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientModelGrantStore modelGrantStore,
            Clock clock) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.connectionStore = Objects.requireNonNull(connectionStore, "connectionStore must not be null");
        this.modelGrantStore = Objects.requireNonNull(modelGrantStore, "modelGrantStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 方法注入保持既有构造测试稳定；生产注册在用户行锁内收敛为唯一实时连接。 */
    @Autowired
    void setConnectionRevoker(LocalClientConnectionRevoker connectionRevoker) {
        this.connectionRevoker = Objects.requireNonNull(connectionRevoker);
    }

    @Transactional
    public Registration register(
            UserId userId,
            LocalClientPayloads.Register payload,
            BackendProcessId backendProcessId,
            String observedRemoteAddress) {
        return register(userId, payload, backendProcessId, observedRemoteAddress, null);
    }

    @Transactional
    public Registration register(
            UserId userId,
            LocalClientPayloads.Register payload,
            BackendProcessId backendProcessId,
            String observedRemoteAddress,
            String traceId) {
        instanceRepository.lockUser(userId);
        LocalClientInstanceId clientInstanceId = new LocalClientInstanceId(payload.clientInstanceId());
        String platform = normalizePlatform(payload.platform());
        String architecture = normalizeArchitecture(payload.architecture());
        requireSupportedPlatform(platform, architecture);
        if (!"1.18.4".equals(payload.opencodeVersion())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "客户端必须使用 OpenCode 1.18.4");
        }
        String clientName = requireText(payload.clientName(), "clientName", 255);
        String clientVersion = requireText(payload.clientVersion(), "clientVersion", 64);
        String launcherVersion = optionalText(payload.launcherVersion(), "launcherVersion", 32);
        List<String> capabilities = validateCapabilities(payload.capabilities());
        boolean selfUpdateSupported = launcherVersion != null && capabilities.contains("SELF_UPDATE_V1");
        if (selfUpdateSupported) {
            try {
                LocalClientReleaseVersion.parse(clientVersion);
            } catch (IllegalArgumentException exception) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "自更新客户端版本格式无效");
            }
        }
        List<String> reportedAddresses = validateAddresses(payload.reportedAddresses());
        Optional<LocalClientInstance> existing = instanceRepository.findById(clientInstanceId);
        if (existing.isPresent() && !existing.get().userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "客户端实例不属于当前密钥用户");
        }

        Optional<LocalClientConnectionRoute> previousRoute = connectionStore.find(clientInstanceId);
        Instant now = Instant.now(clock);
        long generation = connectionStore.nextGeneration();
        String rawModelGrant = LocalClientSecretSupport.generateGrant();
        String grantFingerprint = LocalClientSecretSupport.fingerprint(rawModelGrant);
        Instant grantExpiresAt = now.plus(MODEL_GRANT_TTL);
        LocalClientModelGrant modelGrant = new LocalClientModelGrant(
                grantFingerprint, clientInstanceId, userId, generation, grantExpiresAt);
        LocalClientConnectionRoute route = new LocalClientConnectionRoute(
                clientInstanceId,
                userId,
                backendProcessId,
                generation,
                observedRemoteAddress,
                reportedAddresses,
                null,
                LocalClientProcessStatus.STOPPED,
                null,
                null,
                false,
                now,
                now);
        instanceRepository.save(new LocalClientInstance(
                clientInstanceId,
                userId,
                clientName,
                platform,
                architecture,
                clientVersion,
                payload.opencodeVersion(),
                launcherVersion,
                capabilities,
                selfUpdateSupported,
                existing.map(LocalClientInstance::lastUpdateStatus).orElse(null),
                existing.map(LocalClientInstance::lastUpdateTargetVersion).orElse(null),
                existing.map(LocalClientInstance::lastUpdateAt).orElse(null),
                existing.map(LocalClientInstance::createdAt).orElse(now),
                now,
                now,
                existing.map(LocalClientInstance::lastDisconnectedAt).orElse(null)));
        boolean grantPublished = false;
        boolean routePublished = false;
        try {
            modelGrantStore.save(modelGrant, MODEL_GRANT_TTL);
            grantPublished = true;
            connectionStore.save(route, LocalClientProtocol.CONNECTION_TTL);
            routePublished = true;
            cleanupPublishedStateOnRollback(route, grantFingerprint);
        } catch (RuntimeException exception) {
            if (routePublished) {
                connectionStore.delete(clientInstanceId, generation);
            }
            if (grantPublished) {
                modelGrantStore.delete(grantFingerprint);
            }
            throw exception;
        }
        if (connectionRevoker != null) {
            connectionRevoker.revokeAllExcept(
                    userId,
                    clientInstanceId,
                    generation,
                    "USER_CONNECTION_SUPERSEDED",
                    traceId);
        }
        // 路由发布后再清除替换标记：与工作区接管并发时，在线旧实例最终一定重新进入活动实例投影。
        // 其工作区仍需逐个通过目录身份校验，不能因重连自动抢回。
        instanceRepository.clearReplacement(clientInstanceId);
        return new Registration(
                route,
                previousRoute.orElse(null),
                rawModelGrant,
                grantFingerprint,
                grantExpiresAt,
                selfUpdateSupported);
    }

    /** PostgreSQL 最终提交失败时按 generation/fingerprint 清理先发布的短 TTL Redis 状态。 */
    private void cleanupPublishedStateOnRollback(LocalClientConnectionRoute route, String grantFingerprint) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                connectionStore.delete(route.clientInstanceId(), route.connectionGeneration());
                modelGrantStore.delete(grantFingerprint);
            }
        });
    }

    public LocalClientConnectionRoute heartbeat(
            LocalClientInstanceId clientInstanceId,
            long generation,
            BackendProcessId backendProcessId,
            String modelGrantFingerprint,
            LocalClientPayloads.Heartbeat heartbeat) {
        LocalClientConnectionRoute current = requireCurrent(clientInstanceId, generation, backendProcessId);
        Instant now = Instant.now(clock);
        LocalClientProcessStatus status;
        try {
            status = LocalClientProcessStatus.valueOf(requireText(
                    heartbeat.processStatus(), "processStatus", 32).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "客户端进程状态无效");
        }
        Integer port = heartbeat.opencodePort();
        if (port != null && (port < 1024 || port > 65535)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "客户端 OpenCode 端口无效");
        }
        LocalClientConnectionRoute refreshed = new LocalClientConnectionRoute(
                current.clientInstanceId(),
                current.userId(),
                current.backendProcessId(),
                current.connectionGeneration(),
                current.observedRemoteAddress(),
                validateAddresses(heartbeat.reportedAddresses()),
                port,
                status,
                heartbeat.processId(),
                heartbeat.processStartedAt(),
                heartbeat.opencodeHealthy(),
                current.connectedAt(),
                now);
        if (!connectionStore.refresh(refreshed, LocalClientProtocol.CONNECTION_TTL)) {
            throw new PlatformException(ErrorCode.CONFLICT, "客户端连接已被更新连接替代");
        }
        Instant grantExpiresAt = now.plus(MODEL_GRANT_TTL);
        modelGrantStore.save(new LocalClientModelGrant(
                modelGrantFingerprint,
                clientInstanceId,
                current.userId(),
                generation,
                grantExpiresAt), MODEL_GRANT_TTL);
        return refreshed;
    }

    @Transactional
    public void disconnect(LocalClientInstanceId clientInstanceId, long generation) {
        boolean deletedCurrentRoute = connectionStore.delete(clientInstanceId, generation);
        modelGrantStore.deleteForConnection(clientInstanceId, generation);
        if (deletedCurrentRoute) {
            instanceRepository.markDisconnected(clientInstanceId, Instant.now(clock));
        }
    }

    public LocalClientConnectionRoute requireCurrent(
            LocalClientInstanceId clientInstanceId,
            long generation,
            BackendProcessId backendProcessId) {
        LocalClientConnectionRoute route = connectionStore.find(clientInstanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地客户端已离线"));
        if (route.connectionGeneration() != generation || !route.backendProcessId().equals(backendProcessId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端连接已换代或迁移后台节点");
        }
        return route;
    }

    private static String normalizePlatform(String value) {
        return requireText(value, "platform", 64).toLowerCase(Locale.ROOT);
    }

    private static String normalizeArchitecture(String value) {
        String architecture = requireText(value, "architecture", 64).toLowerCase(Locale.ROOT);
        return "aarch64".equals(architecture) ? "arm64" : architecture;
    }

    private static void requireSupportedPlatform(String platform, String architecture) {
        if (!"arm64".equals(architecture) || !("darwin".equals(platform) || "linux".equals(platform))) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "首版客户端仅支持 darwin-arm64 和 linux-arm64-glibc");
        }
    }

    private static List<String> validateAddresses(List<String> addresses) {
        if (addresses == null) {
            return List.of();
        }
        if (addresses.size() > 16) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "客户端上报地址数量超过限制");
        }
        return addresses.stream().map(address -> requireText(address, "reportedAddress", 255)).toList();
    }

    private static List<String> validateCapabilities(List<String> capabilities) {
        if (capabilities == null) {
            return List.of();
        }
        if (capabilities.size() > 16) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "客户端能力数量超过限制");
        }
        return capabilities.stream()
                .map(value -> requireText(value, "capability", 64))
                .peek(value -> {
                    if (!value.matches("[A-Z0-9_]+")) {
                        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "客户端能力格式无效");
                    }
                })
                .distinct()
                .toList();
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 无效");
        }
        return value.trim();
    }

    public record Registration(
            LocalClientConnectionRoute route,
            LocalClientConnectionRoute previousRoute,
            String rawModelGrant,
            String modelGrantFingerprint,
            Instant modelGrantExpiresAt,
            boolean selfUpdateSupported) {
    }
}
