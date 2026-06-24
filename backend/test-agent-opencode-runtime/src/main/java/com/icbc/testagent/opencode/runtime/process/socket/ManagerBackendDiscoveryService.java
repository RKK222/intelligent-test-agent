package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * manager discovery 查询服务，只返回仍在心跳窗口内的 READY 后端实例。
 */
@Service
public class ManagerBackendDiscoveryService {

    private final OpencodeProcessManagementRepository repository;
    private final ManagerControlSettings settings;
    private final Clock clock;

    /**
     * 生产构造器使用系统时钟。
     */
    @Autowired
    public ManagerBackendDiscoveryService(
            OpencodeProcessManagementRepository repository,
            ManagerControlSettings settings) {
        this(repository, settings, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public ManagerBackendDiscoveryService(
            OpencodeProcessManagementRepository repository,
            ManagerControlSettings settings,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询后端实例直连地址并派生 WebSocket URL。
     */
    public List<ManagerBackendEndpoint> discover(String traceId) {
        Instant minHeartbeatAt = Instant.now(clock).minus(settings.backendStaleAfter());
        return repository.findReadyBackendJavaProcesses(minHeartbeatAt, settings.backendDiscoveryLimit())
                .stream()
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
