package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 当前后端 Java 进程生命周期服务，负责把本实例写入运行拓扑表并维护心跳。
 */
@Service
public class BackendJavaProcessLifecycleService {

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ManagerControlSettings settings;
    private final Clock clock;
    private final BackendProcessId backendProcessId;
    private final Instant startedAt;

    /**
     * 生产构造器使用系统时钟和启动时生成的稳定 backendProcessId。
     */
    @Autowired
    public BackendJavaProcessLifecycleService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings) {
        this(repository, heartbeatStore, settings, Clock.systemUTC());
    }

    /**
     * 兼容测试和旧装配调用，未显式提供心跳端口时不写 Redis。
     */
    public BackendJavaProcessLifecycleService(
            OpencodeProcessManagementRepository repository,
            ManagerControlSettings settings) {
        this(repository, disabledHeartbeatStore(), settings, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public BackendJavaProcessLifecycleService(
            OpencodeProcessManagementRepository repository,
            ManagerControlSettings settings,
            Clock clock) {
        this(repository, disabledHeartbeatStore(), settings, clock);
    }

    /**
     * 测试构造器允许固定时钟并注入心跳端口。
     */
    public BackendJavaProcessLifecycleService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.backendProcessId = new BackendProcessId(RuntimeIdGenerator.backendProcessId());
        this.startedAt = Instant.now(clock);
    }

    /**
     * 返回当前 Java 进程实例 ID，供 manager 连接和命令路由记录使用。
     */
    public BackendProcessId backendProcessId() {
        return backendProcessId;
    }

    /**
     * 注册或刷新当前后端实例心跳。
     *
     * <p>除刷新本实例的 Linux 服务器、心跳和 Redis 索引外，还会为同服务器下所有
     * {@code connection_status = CONNECTED} 的容器管理进程补齐到本实例的连接行，
     * 让本地开发环境在迁移中预置了 manager 但还没有 manager WebSocket 注册时，
     * 仍能通过 {@code findHealthyContainersConnectedToBackend*} 查询到本机容器，
     * 前端用户进程状态从 {@code UNAVAILABLE} 升级为 {@code READY}。
     *
     * <p>该自举仅在数据库中确实不存在 (manager, backend) 连接行时才插入，
     * 已有行只更新 {@code last_heartbeat_at} 和 {@code status}；管理进程 WebSocket
     * 真连上后由 {@code ManagerControlApplicationService.register/heartbeat} 继续维护。
     */
    public void registerHeartbeat(String traceId) {
        Instant now = Instant.now(clock);
        repository.saveLinuxServer(new LinuxServer(
                settings.linuxServerId(),
                settings.linuxServerId().value(),
                LinuxServerStatus.READY,
                Map.of(
                        "backendListenUrl", settings.listenUrl(),
                        "backendWorkingDirectory", Path.of("").toAbsolutePath().normalize().toString()),
                now,
                repository.findLinuxServerById(settings.linuxServerId()).map(LinuxServer::createdAt).orElse(now),
                now,
                traceId));
        BackendJavaProcess existing = repository.findBackendJavaProcessById(backendProcessId).orElse(null);
        repository.saveBackendJavaProcess(new BackendJavaProcess(
                backendProcessId,
                settings.linuxServerId(),
                settings.listenUrl(),
                BackendJavaProcessStatus.READY,
                startedAt,
                now,
                existing == null ? now : existing.createdAt(),
                now,
                traceId));
        heartbeatStore.recordBackendHeartbeat(backendProcessId, now);
        bootstrapLocalManagerConnections(now, traceId);
    }

    /**
     * 为同 Linux 服务器下所有 CONNECTED 容器管理进程补齐到本后端实例的连接行。
     * 仅在 {@link OpencodeProcessManagementRepository#findManagerBackendConnection}
     * 查询为空时插入，已有连接行只更新心跳和状态。
     */
    private void bootstrapLocalManagerConnections(Instant now, String traceId) {
        List<OpencodeContainerManager> managers = repository.findContainerManagers(500);
        for (OpencodeContainerManager manager : managers) {
            if (manager.connectionStatus() != ManagerConnectionStatus.CONNECTED) {
                continue;
            }
            if (!settings.linuxServerId().equals(manager.linuxServerId())) {
                continue;
            }
            OpencodeManagerBackendConnection existing = repository
                    .findManagerBackendConnection(manager.managerId(), backendProcessId)
                    .orElse(null);
            if (existing != null) {
                repository.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                        existing.managerId(),
                        existing.backendProcessId(),
                        ManagerConnectionStatus.CONNECTED,
                        existing.connectedAt(),
                        now,
                        now,
                        traceId));
                continue;
            }
            repository.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                    manager.managerId(),
                    backendProcessId,
                    ManagerConnectionStatus.CONNECTED,
                    now,
                    now,
                    now,
                    traceId));
        }
    }

    /**
     * 当前 Java 进程停止时尽量标记离线，便于 manager discovery 排除。
     */
    public void markOffline(String traceId) {
        Instant now = Instant.now(clock);
        BackendJavaProcess existing = repository.findBackendJavaProcessById(backendProcessId).orElse(null);
        repository.saveBackendJavaProcess(new BackendJavaProcess(
                backendProcessId,
                settings.linuxServerId(),
                settings.listenUrl(),
                BackendJavaProcessStatus.OFFLINE,
                existing == null ? startedAt : existing.startedAt(),
                now,
                existing == null ? now : existing.createdAt(),
                now,
                traceId));
    }

    /**
     * 清理 Redis 心跳索引中已经超过 5 分钟 TTL 的进程 ID。
     */
    public void cleanupExpiredHeartbeats() {
        heartbeatStore.cleanupExpiredHeartbeats();
    }

    private static OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return new OpencodeProcessHeartbeatStore() {
            @Override public boolean enabled() { return false; }
            @Override public void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt) { }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
            @Override public Set<BackendProcessId> liveBackendProcessIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() { }
        };
    }
}
