package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前用户本地客户端实例查询与所有权校验。 */
@Service
public class LocalClientInstanceApplicationService {

    private static final Map<String, Boolean> CAPABILITIES = capabilities();

    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;

    public LocalClientInstanceApplicationService(
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
    }

    @Transactional(readOnly = true)
    public List<LocalClientInstanceResponses.InstanceView> list(UserId userId) {
        return instanceRepository.findByUserId(userId).stream().map(this::view).toList();
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

    private LocalClientInstanceResponses.InstanceView view(LocalClientInstance instance) {
        LocalClientConnectionRoute route = connectionStore.find(instance.clientInstanceId())
                .filter(candidate -> candidate.userId().equals(instance.userId()))
                .orElse(null);
        return new LocalClientInstanceResponses.InstanceView(
                instance.clientInstanceId().value(),
                instance.clientName(),
                instance.platform(),
                instance.architecture(),
                instance.clientVersion(),
                instance.opencodeVersion(),
                route != null,
                route == null ? 0 : route.connectionGeneration(),
                route == null ? List.of() : route.reportedAddresses(),
                route == null ? null : route.observedRemoteAddress(),
                route == null ? null : route.opencodePort(),
                route == null ? "OFFLINE" : route.processStatus().name(),
                route != null && route.opencodeHealthy(),
                route == null ? null : route.processId(),
                route == null ? null : route.processStartedAt(),
                route == null ? null : route.lastHeartbeatAt(),
                instance.lastConnectedAt(),
                instance.lastDisconnectedAt(),
                CAPABILITIES);
    }

    private static Map<String, Boolean> capabilities() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("chat", true);
        values.put("fileManagement", true);
        values.put("nightExecution", true);
        values.put("terminal", false);
        values.put("gitPublish", false);
        values.put("agentConfig", false);
        values.put("attachments", false);
        values.put("collaboration", false);
        return Map.copyOf(values);
    }
}
