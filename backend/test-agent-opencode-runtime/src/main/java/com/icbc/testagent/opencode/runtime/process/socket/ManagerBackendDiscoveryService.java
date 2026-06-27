package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * manager 后端列表查询服务，只返回 Redis 快照中仍在线的 Java 后端实例。
 */
@Service
public class ManagerBackendDiscoveryService {

    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ManagerControlSettings settings;

    /**
     * 生产构造器通过 Redis 心跳快照发现当前存活后端。
     */
    @Autowired
    public ManagerBackendDiscoveryService(
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings) {
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * 查询后端实例直连地址并派生 WebSocket URL。
     */
    public List<ManagerBackendEndpoint> discover(String traceId) {
        return heartbeatStore.liveBackendSnapshots()
                .stream()
                .limit(settings.backendDiscoveryLimit())
                .map(snapshot -> snapshot.backendProcess())
                .map(this::endpoint)
                .toList();
    }

    private ManagerBackendEndpoint endpoint(BackendJavaProcess process) {
        return new ManagerBackendEndpoint(
                process.backendProcessId().value(),
                process.linuxServerId().value(),
                process.listenUrl(),
                webSocketUrl(process.listenUrl()),
                process.lastHeartbeatAt());
    }

    private String webSocketUrl(String listenUrl) {
        String trimmed = listenUrl.endsWith("/") ? listenUrl.substring(0, listenUrl.length() - 1) : listenUrl;
        if (trimmed.startsWith("https://")) {
            return "wss://" + trimmed.substring("https://".length()) + "/api/internal/platform/opencode-runtime/manager/ws";
        }
        if (trimmed.startsWith("http://")) {
            return "ws://" + trimmed.substring("http://".length()) + "/api/internal/platform/opencode-runtime/manager/ws";
        }
        return trimmed + "/api/internal/platform/opencode-runtime/manager/ws";
    }
}
