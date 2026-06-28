package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.ContainerRuntimeMetrics;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * manager WebSocket 注册、心跳和断连业务服务；注册保留持久拓扑，在线心跳写入 Redis 快照。
 */
@Service
public class ManagerControlApplicationService {

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final BackendJavaProcessLifecycleService backendLifecycle;
    private final Clock clock;

    /**
     * 生产构造器使用系统时钟。
     */
    @Autowired
    public ManagerControlApplicationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaProcessLifecycleService backendLifecycle) {
        this(repository, heartbeatStore, backendLifecycle, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public ManagerControlApplicationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaProcessLifecycleService backendLifecycle,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.backendLifecycle = Objects.requireNonNull(backendLifecycle, "backendLifecycle must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 注册 manager 连接并返回当前后端实例确认消息。
     */
    public ManagerControlMessage register(ManagerControlMessage message) {
        saveTopology(message, ManagerConnectionStatus.CONNECTED);
        return ManagerControlMessage.registered(backendLifecycle.backendProcessId().value(), message.traceId());
    }

    /**
     * 刷新 manager 心跳和容器容量快照。
     */
    public void heartbeat(ManagerControlMessage message) {
        managerHeartbeat(message);
    }

    /**
     * 将 manager 运行心跳写入 Redis；数据库只保留注册时的持久拓扑，不承载在线判断。
     */
    public void managerHeartbeat(ManagerControlMessage message) {
        validateRegistrationLike(message);
        Instant now = Instant.now(clock);
        LinuxServerId linuxServerId = new LinuxServerId(message.linuxServerId());
        OpencodeContainerId containerId = new OpencodeContainerId(message.containerId());
        ContainerManagerId managerId = new ContainerManagerId(message.managerId());
        OpencodeContainer container = new OpencodeContainer(
                containerId,
                linuxServerId,
                blankToDefault(message.containerName(), containerId.value()),
                message.portStart(),
                message.portEnd(),
                message.maxProcesses(),
                message.currentProcesses(),
                OpencodeContainerStatus.READY,
                now,
                now,
                now,
                message.traceId());
        OpencodeContainerManager manager = new OpencodeContainerManager(
                managerId,
                containerId,
                linuxServerId,
                ManagerControlProtocol.VERSION,
                ManagerConnectionStatus.CONNECTED,
                message.capabilities(),
                now,
                now,
                now,
                message.traceId());
        List<OpencodeManagerBackendConnection> connections = connectedBackendProcessIds(message).stream()
                .map(backendId -> new OpencodeManagerBackendConnection(
                        managerId,
                        backendId,
                        ManagerConnectionStatus.CONNECTED,
                        now,
                        now,
                        now,
                        message.traceId()))
                .toList();
        ContainerRuntimeMetrics metrics = new ContainerRuntimeMetrics(
                message.portStart(),
                message.portEnd(),
                message.maxProcesses(),
                message.currentProcesses(),
                message.metricsSource(),
                message.cpuUsagePercent(),
                message.memoryMaxBytes(),
                message.memoryUsedBytes(),
                message.memoryUsagePercent(),
                message.diskReadBytesPerSecond(),
                message.diskWriteBytesPerSecond());
        List<ManagedOpencodeProcessSnapshot> managedProcesses = message.managedProcesses().stream()
                .map(process -> new ManagedOpencodeProcessSnapshot(
                        process.port(),
                        process.pid(),
                        process.baseUrl(),
                        process.sessionPath(),
                        process.configPath(),
                        process.startedAt(),
                        process.startCommand(),
                        process.traceId()))
                .toList();
        heartbeatStore.recordManagerSnapshot(new ManagerRuntimeSnapshot(container, manager, connections, metrics, managedProcesses));
    }

    /**
     * 从 Redis 快照返回当前存活 Java 后端实例列表，供 manager 主动补连缺失 socket。
     */
    public ManagerControlMessage backendListResponse(String traceId) {
        List<ManagerBackendEndpoint> endpoints = heartbeatStore.liveBackendSnapshots().stream()
                .map(snapshot -> endpoint(snapshot.backendProcess()))
                .toList();
        return ManagerControlMessage.backendListResponse(endpoints, traceId);
    }

    /**
     * 标记当前后端实例与 manager 的连接断开。
     */
    public void disconnect(ContainerManagerId managerId, String traceId) {
        Instant now = Instant.now(clock);
        BackendProcessId backendProcessId = backendLifecycle.backendProcessId();
        OpencodeManagerBackendConnection existing = repository
                .findManagerBackendConnection(managerId, backendProcessId)
                .orElse(null);
        Instant connectedAt = existing == null ? now : existing.connectedAt();
        repository.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                managerId,
                backendProcessId,
                ManagerConnectionStatus.DISCONNECTED,
                connectedAt,
                now,
                now,
                traceId));
    }

    private void saveTopology(ManagerControlMessage message, ManagerConnectionStatus connectionStatus) {
        validateRegistrationLike(message);
        Instant now = Instant.now(clock);
        LinuxServerId linuxServerId = new LinuxServerId(message.linuxServerId());
        OpencodeContainerId containerId = new OpencodeContainerId(message.containerId());
        ContainerManagerId managerId = new ContainerManagerId(message.managerId());
        repository.saveLinuxServer(new LinuxServer(
                linuxServerId,
                linuxServerId.value(),
                LinuxServerStatus.READY,
                Map.of("currentProcesses", message.currentProcesses()),
                now,
                repository.findLinuxServerById(linuxServerId).map(LinuxServer::createdAt).orElse(now),
                now,
                message.traceId()));
        OpencodeContainer existingContainer = repository.findContainerById(containerId).orElse(null);
        repository.saveContainer(new OpencodeContainer(
                containerId,
                linuxServerId,
                blankToDefault(message.containerName(), containerId.value()),
                message.portStart(),
                message.portEnd(),
                message.maxProcesses(),
                message.currentProcesses(),
                OpencodeContainerStatus.READY,
                now,
                existingContainer == null ? now : existingContainer.createdAt(),
                now,
                message.traceId()));
        OpencodeContainerManager existingManager = repository.findContainerManagerById(managerId).orElse(null);
        repository.saveContainerManager(new OpencodeContainerManager(
                managerId,
                containerId,
                linuxServerId,
                ManagerControlProtocol.VERSION,
                ManagerConnectionStatus.CONNECTED,
                message.capabilities(),
                now,
                existingManager == null ? now : existingManager.createdAt(),
                now,
                message.traceId()));
        BackendProcessId backendProcessId = backendLifecycle.backendProcessId();
        OpencodeManagerBackendConnection existingConnection = repository
                .findManagerBackendConnection(managerId, backendProcessId)
                .orElse(null);
        repository.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                managerId,
                backendProcessId,
                connectionStatus,
                existingConnection == null ? now : existingConnection.connectedAt(),
                now,
                now,
                message.traceId()));
    }

    private void validateRegistrationLike(ManagerControlMessage message) {
        if (message == null
                || message.managerId() == null
                || message.containerId() == null
                || message.linuxServerId() == null
                || message.portStart() == null
                || message.portEnd() == null
                || message.maxProcesses() == null
                || message.currentProcesses() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "管理进程注册消息缺少必要字段");
        }
    }

    private List<BackendProcessId> connectedBackendProcessIds(ManagerControlMessage message) {
        LinkedHashSet<BackendProcessId> ids = new LinkedHashSet<>();
        for (String rawId : message.connectedBackendProcessIds()) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            ids.add(new BackendProcessId(rawId.trim()));
        }
        return List.copyOf(ids);
    }

    private ManagerBackendEndpoint endpoint(com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess process) {
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

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
