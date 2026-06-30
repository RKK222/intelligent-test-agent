package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 后端 Java 路由解析器，集中处理当前服务器身份、Redis 后端快照和 manager 容器归属。
 */
@Service
public class BackendJavaRouteResolver {

    private static final int DEFAULT_BACKEND_LIMIT = 500;

    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ManagerControlSettings settings;
    private final Clock clock;

    /**
     * 生产构造器使用 UTC 系统时钟。
     */
    @Autowired
    public BackendJavaRouteResolver(
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings) {
        this(heartbeatStore, settings, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public BackendJavaRouteResolver(
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            Clock clock) {
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 返回当前 Java 控制的 Linux 服务器身份。
     */
    public LinuxServerId currentLinuxServerId() {
        return settings.linuxServerId();
    }

    /**
     * 返回当前 Java 控制的 Linux 服务器身份字符串。
     */
    public String currentLinuxServerIdValue() {
        return currentLinuxServerId().value();
    }

    /**
     * 判断目标服务器是否就是当前 Java。
     */
    public boolean isCurrent(LinuxServerId linuxServerId) {
        return currentLinuxServerId().equals(linuxServerId);
    }

    /**
     * 判断目标服务器是否就是当前 Java。
     */
    public boolean isCurrent(String linuxServerId) {
        return linuxServerId != null && currentLinuxServerIdValue().equals(linuxServerId.trim());
    }

    /**
     * 只有目标不是当前 Java 时返回远端路由目标。
     */
    public Optional<LinuxServerId> remoteTarget(LinuxServerId linuxServerId) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        return isCurrent(linuxServerId) ? Optional.empty() : Optional.of(linuxServerId);
    }

    /**
     * 只有目标不是当前 Java 时返回远端路由目标。
     */
    public Optional<String> remoteTarget(String linuxServerId) {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            return Optional.empty();
        }
        LinuxServerId parsed = new LinuxServerId(linuxServerId.trim());
        return remoteTarget(parsed).map(LinuxServerId::value);
    }

    /**
     * 查询目标服务器最新在线 Java；当前服务器没有 Redis 快照时使用本地 listenUrl 兜底。
     */
    public BackendJavaProcess requireBackend(LinuxServerId linuxServerId) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        if (isCurrent(linuxServerId)) {
            return latestBackendsByServer().getOrDefault(linuxServerId.value(), currentBackend());
        }
        BackendJavaProcess backend = latestBackendsByServer().get(linuxServerId.value());
        if (backend == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", linuxServerId.value()));
        }
        return backend;
    }

    /**
     * 查询目标服务器最新在线 Java。
     */
    public BackendJavaProcess requireBackend(String linuxServerId) {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", ""));
        }
        return requireBackend(new LinuxServerId(linuxServerId.trim()));
    }

    /**
     * 返回每台服务器最新在线 Java，包含当前 Java 的本地兜底。
     */
    public Map<String, BackendJavaProcess> liveBackendsByServer() {
        Map<String, BackendJavaProcess> result = latestBackendsByServer();
        result.putIfAbsent(currentLinuxServerIdValue(), currentBackend());
        return result;
    }

    /**
     * 返回远端服务器最新在线 Java。
     */
    public Map<String, BackendJavaProcess> remoteBackendsByServer() {
        Map<String, BackendJavaProcess> result = liveBackendsByServer();
        result.remove(currentLinuxServerIdValue());
        return result;
    }

    /**
     * 返回在线 Java 快照，包含当前 Java 的本地兜底。
     */
    public List<BackendRuntimeSnapshot> liveBackendSnapshots(int limit) {
        Map<String, BackendRuntimeSnapshot> snapshotsByServer = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : heartbeatStore.liveBackendSnapshots()) {
            BackendJavaProcess backend = snapshot.backendProcess();
            snapshotsByServer.merge(
                    backend.linuxServerId().value(),
                    snapshot,
                    (left, right) -> latestBackend(left.backendProcess(), right.backendProcess()) == right.backendProcess()
                            ? right
                            : left);
        }
        snapshotsByServer.putIfAbsent(currentLinuxServerIdValue(), currentBackendSnapshot());
        int resolvedLimit = limit < 1 ? DEFAULT_BACKEND_LIMIT : limit;
        return snapshotsByServer.values().stream()
                .limit(resolvedLimit)
                .toList();
    }

    /**
     * 按最新 manager 快照定位容器归属服务器。
     */
    public Optional<LinuxServerId> containerLinuxServerId(OpencodeContainerId containerId) {
        Objects.requireNonNull(containerId, "containerId must not be null");
        return heartbeatStore.liveManagerSnapshots().stream()
                .map(ManagerRuntimeSnapshot::container)
                .filter(container -> container.containerId().equals(containerId))
                .max(Comparator.comparing(OpencodeContainer::lastHeartbeatAt))
                .map(OpencodeContainer::linuxServerId);
    }

    private Map<String, BackendJavaProcess> latestBackendsByServer() {
        Map<String, BackendJavaProcess> result = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : heartbeatStore.liveBackendSnapshots()) {
            BackendJavaProcess backend = snapshot.backendProcess();
            result.merge(backend.linuxServerId().value(), backend, this::latestBackend);
        }
        return result;
    }

    private BackendJavaProcess latestBackend(BackendJavaProcess left, BackendJavaProcess right) {
        return right.lastHeartbeatAt().isAfter(left.lastHeartbeatAt()) ? right : left;
    }

    private BackendRuntimeSnapshot currentBackendSnapshot() {
        Instant now = Instant.now(clock);
        LinuxServer server = new LinuxServer(
                currentLinuxServerId(),
                currentLinuxServerIdValue(),
                LinuxServerStatus.READY,
                Map.of("backendListenUrl", settings.listenUrl()),
                now,
                now,
                now,
                "trace_current_backend");
        return new BackendRuntimeSnapshot(server, currentBackend(now));
    }

    private BackendJavaProcess currentBackend() {
        return currentBackend(Instant.now(clock));
    }

    private BackendJavaProcess currentBackend(Instant now) {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_current_backend"),
                currentLinuxServerId(),
                settings.listenUrl(),
                BackendJavaProcessStatus.READY,
                now,
                now,
                now,
                now,
                "trace_current_backend");
    }
}
