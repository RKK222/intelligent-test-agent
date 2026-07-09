package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 当前后端 Java 进程生命周期服务，负责保留本实例持久拓扑并把在线快照写入 Redis。
 */
@Service
public class BackendJavaProcessLifecycleService {

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ManagerControlSettings settings;
    private final Clock clock;
    private final BackendRuntimeMetricsCollector metricsCollector;
    private final BackendProcessId backendProcessId;
    private final Instant startedAt;

    /**
     * 生产构造器使用系统时钟和启动时生成的稳定 backendProcessId。
     */
    @Autowired
    public BackendJavaProcessLifecycleService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            CommonParameterValues commonParameterValues) {
        this(repository, heartbeatStore, settings, Clock.systemUTC(), new BackendRuntimeMetricsCollector(metricsDiskPath(commonParameterValues)));
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
        this(repository, heartbeatStore, settings, clock, new BackendRuntimeMetricsCollector());
    }

    /**
     * 完整构造器允许测试注入指标采集器。
     */
    public BackendJavaProcessLifecycleService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            Clock clock,
            BackendRuntimeMetricsCollector metricsCollector) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector must not be null");
        this.backendProcessId = new BackendProcessId(RuntimeIdGenerator.backendProcessId());
        this.startedAt = Instant.now(clock);
    }

    /**
     * 运行管理磁盘容量优先监控系统数据根目录；通用参数缺失或路径非法时回退 Java 当前工作目录。
     */
    private static Path metricsDiskPath(CommonParameterValues commonParameterValues) {
        if (commonParameterValues != null) {
            try {
                return commonParameterValues.resolvedValue("SYS_DATA_ROOT_DIR")
                        .filter(value -> !value.isBlank())
                        .map(value -> Path.of(value.trim()).toAbsolutePath().normalize())
                        .orElseGet(() -> Path.of("").toAbsolutePath().normalize());
            } catch (RuntimeException ignored) {
                return Path.of("").toAbsolutePath().normalize();
            }
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    /**
     * 返回当前 Java 进程实例 ID，供 manager 连接和命令路由记录使用。
     */
    public BackendProcessId backendProcessId() {
        return backendProcessId;
    }

    /**
     * 返回当前 Java 进程所属的稳定 Linux 服务器身份，供统一后端路由解析复用。
     */
    public LinuxServerId linuxServerId() {
        return settings.linuxServerId();
    }

    /**
     * 返回当前 Java 对其它后端可访问的 HTTP 监听地址。
     */
    public String listenUrl() {
        return settings.listenUrl();
    }

    /**
     * 返回当前服务器对其它 Java、manager 和 opencode server 可访问的主机名/IP。
     */
    public String advertisedHost() {
        return settings.advertisedHost();
    }

    /**
     * 注册或刷新当前后端实例 Redis 心跳。
     *
     * <p>除写入本实例的 Redis 运行快照外，还会在拓扑首次落库或状态变化时为同服务器下所有
     * {@code connection_status = CONNECTED} 的容器管理进程补齐到本实例的连接行，
     * 让本地开发环境在迁移中预置了 manager 但还没有 manager WebSocket 注册时，
     * 仍能通过 {@code findHealthyContainersConnectedToBackend*} 查询到本机容器，
     * 前端用户进程状态从 {@code UNAVAILABLE} 升级为 {@code READY}。
     *
     * <p>该自举仅在数据库中确实不存在 (manager, backend) 连接行时才插入，
     * 已有行只更新 {@code last_heartbeat_at} 和 {@code status}；管理进程 WebSocket
     * 真连上后由 {@code ManagerControlApplicationService.register} 维护持久连接行，
     * 在线连接状态由 Redis manager 快照表达。
     */
    public void registerHeartbeat(String traceId) {
        Instant now = Instant.now(clock);
        LinuxServer existingServer = repository.findLinuxServerById(settings.linuxServerId()).orElse(null);
        LinuxServer linuxServer = new LinuxServer(
                settings.linuxServerId(),
                settings.linuxServerId().value(),
                LinuxServerStatus.READY,
                Map.of(
                        "backendListenUrl", settings.listenUrl(),
                        "serverAdvertisedHost", settings.advertisedHost(),
                        "backendWorkingDirectory", Path.of("").toAbsolutePath().normalize().toString()),
                now,
                existingServer == null ? now : existingServer.createdAt(),
                now,
                traceId);
        BackendJavaProcess existing = repository.findBackendJavaProcessById(backendProcessId).orElse(null);
        BackendJavaProcess backendProcess = new BackendJavaProcess(
                backendProcessId,
                settings.linuxServerId(),
                settings.listenUrl(),
                BackendJavaProcessStatus.READY,
                startedAt,
                now,
                // 首次心跳可能与 manager 注册并发执行；创建时间固定为进程启动时间，
                // 避免较早心跳后写入时把 updatedAt 更新到另一请求创建时间之前。
                existing == null ? startedAt : existing.createdAt(),
                now,
                traceId);
        BackendRuntimeMetrics metrics = metricsCollector.sample();
        heartbeatStore.recordBackendSnapshot(new BackendRuntimeSnapshot(linuxServer, backendProcess, metrics));
        if (shouldPersistServer(existingServer, linuxServer)) {
            repository.saveLinuxServer(linuxServer);
        }
        if (shouldPersistBackend(existing, backendProcess)) {
            repository.saveBackendJavaProcess(backendProcess);
            bootstrapLocalManagerConnections(now, traceId);
        }
    }

    /**
     * 为同 Linux 服务器下所有 CONNECTED 容器管理进程补齐到本后端实例的连接行。
     * 仅在 {@link OpencodeProcessManagementRepository#findManagerBackendConnection}
     * 查询为空时插入，已有连接行只更新心跳和状态。
     * FK 违规（如引用的 backend_process_id 在父表中不存在）不阻断启动，仅记录警告。
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
                try {
                    repository.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                            existing.managerId(),
                            existing.backendProcessId(),
                            ManagerConnectionStatus.CONNECTED,
                            existing.connectedAt(),
                            now,
                            now,
                            traceId));
                } catch (RuntimeException e) {
                    // 数据完整性冲突不阻断启动
                }
                continue;
            }
            try {
                repository.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                        manager.managerId(),
                        backendProcessId,
                        ManagerConnectionStatus.CONNECTED,
                        now,
                        now,
                        now,
                        traceId));
            } catch (RuntimeException e) {
                // FK 违规不阻断启动，在线状态以 WebSocket 和 Redis 快照为准
            }
        }
    }

    /**
     * 当前 Java 进程停止时尽量标记离线；真实在线视图会随 Redis 快照过期自动消失。
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

    private boolean shouldPersistServer(LinuxServer existing, LinuxServer current) {
        return existing == null
                || existing.status() != LinuxServerStatus.READY
                || !Objects.equals(existing.capacitySummary(), current.capacitySummary());
    }

    private boolean shouldPersistBackend(BackendJavaProcess existing, BackendJavaProcess current) {
        return existing == null
                || existing.status() != BackendJavaProcessStatus.READY
                || !Objects.equals(existing.listenUrl(), current.listenUrl())
                || !Objects.equals(existing.linuxServerId(), current.linuxServerId());
    }

    private static OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() { }
        };
    }
}
