package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 公共 opencode server 状态查询服务，统一执行平台进程存在性检查、manager health 和状态回写。
 *
 * <p>所有需要判断 opencode server 是否运行的业务入口都应复用本服务，避免各自调用
 * {@link OpencodeProcessManagerGateway#checkHealth(OpencodeProcessHealthCommand)} 后重复映射
 * `RUNNING/STOPPED/UNHEALTHY/FAILED`。
 */
@Service
public class OpencodeProcessStatusQueryService {

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final Clock clock;
    private final OpencodeServerAddressResolver addressResolver;
    private final OpencodeWeakHealthHttpClient weakHealthHttpClient;

    /**
     * Spring 生产构造器使用系统 UTC 时钟。
     */
    @Autowired
    public OpencodeProcessStatusQueryService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings) {
        this(repository, gateway, heartbeatStore, Clock.systemUTC(), new OpencodeServerAddressResolver(settings.advertisedHost()));
    }

    /**
     * 兼容旧测试和手工构造路径；没有 settings 时使用进程当前 baseUrl，不主动改写地址。
     */
    public OpencodeProcessStatusQueryService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(repository, gateway, heartbeatStore, Clock.systemUTC(), null);
    }

    /**
     * 测试构造器允许固定时钟，保证状态快照时间稳定。
     */
    OpencodeProcessStatusQueryService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this(repository, gateway, heartbeatStore, clock, null);
    }

    OpencodeProcessStatusQueryService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock,
            OpencodeServerAddressResolver addressResolver) {
        this(repository, gateway, heartbeatStore, clock, addressResolver, new JdkOpencodeWeakHealthHttpClient());
    }

    OpencodeProcessStatusQueryService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock,
            OpencodeServerAddressResolver addressResolver,
            OpencodeWeakHealthHttpClient weakHealthHttpClient) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.addressResolver = addressResolver;
        this.weakHealthHttpClient = Objects.requireNonNull(weakHealthHttpClient, "weakHealthHttpClient must not be null");
    }

    /**
     * 查询指定进程 ID 的真实状态；平台记录不存在时不下发 manager health 命令。
     */
    public OpencodeProcessStatusProbe query(OpencodeProcessId processId, String traceId) {
        Objects.requireNonNull(processId, "processId must not be null");
        Instant checkedAt = Instant.now(clock);
        Optional<OpencodeServerProcess> process = repository.findOpencodeServerProcessById(processId);
        if (process.isEmpty()) {
            return new OpencodeProcessStatusProbe(
                    OpencodeProcessProbeStatus.NOT_STARTED,
                    Optional.empty(),
                    "NOT_RUNNING",
                    "NOT_RUNNING",
                    "TestAgent 进程未启动",
                    checkedAt,
                    true,
                    null);
        }
        return queryExisting(process.get(), checkedAt, traceId);
    }

    /**
     * 前端轮询用弱健康检查：只读取 Redis 快照并直接访问 opencode /global/health。
     *
     * <p>该方法不读取或写入数据库，不调用 manager gateway，也不刷新 Redis heartbeat；
     * 它只表达当前瞬时健康结果，供前端降低 `/processes/me` 强状态查询频率。
     */
    public OpencodeProcessWeakHealthResponse weakHealth(
            OpencodeProcessWeakHealthRequest request,
            String traceId) {
        Objects.requireNonNull(request, "request must not be null");
        Instant checkedAt = Instant.now(clock);
        String linuxServerId = normalized(request.linuxServerId());
        String containerId = normalized(request.containerId());
        if (linuxServerId.isBlank() || containerId.isBlank() || request.port() < 1 || request.port() > 65535) {
            return weakHealthUnavailable(
                    OpencodeProcessWeakHealthStatus.PROCESS_NOT_FOUND,
                    linuxServerId,
                    containerId,
                    request.port(),
                    null,
                    checkedAt,
                    "弱健康检查参数无效");
        }
        Optional<ManagerRuntimeSnapshot> managerSnapshot = heartbeatStore.liveManagerSnapshots().stream()
                .filter(snapshot -> linuxServerId.equals(snapshot.container().linuxServerId().value()))
                .filter(snapshot -> containerId.equals(snapshot.container().containerId().value()))
                .max(java.util.Comparator.comparing(snapshot -> snapshot.manager().lastHeartbeatAt()));
        if (managerSnapshot.isEmpty()) {
            return weakHealthUnavailable(
                    OpencodeProcessWeakHealthStatus.MANAGER_UNAVAILABLE,
                    linuxServerId,
                    containerId,
                    request.port(),
                    null,
                    checkedAt,
                    "Redis 中未找到目标 manager 快照");
        }
        Optional<ManagedOpencodeProcessSnapshot> process = managerSnapshot.get().managedProcesses().stream()
                .filter(candidate -> candidate.port() == request.port())
                .findFirst();
        if (process.isEmpty()) {
            return weakHealthUnavailable(
                    OpencodeProcessWeakHealthStatus.PROCESS_NOT_FOUND,
                    linuxServerId,
                    containerId,
                    request.port(),
                    null,
                    checkedAt,
                    "Redis manager 快照中未找到目标 opencode 进程");
        }
        String baseUrl = weakHealthBaseUrl(process.get());
        OpencodeWeakHealthHttpResult result = weakHealthHttpClient.check(baseUrl, traceId);
        if (result.healthy()) {
            return new OpencodeProcessWeakHealthResponse(
                    true,
                    OpencodeProcessWeakHealthStatus.HEALTHY,
                    "RUNNING",
                    linuxServerId,
                    containerId,
                    request.port(),
                    baseUrl,
                    checkedAt,
                    result.message());
        }
        return weakHealthUnavailable(
                OpencodeProcessWeakHealthStatus.UNHEALTHY,
                linuxServerId,
                containerId,
                request.port(),
                baseUrl,
                checkedAt,
                result.message());
    }

    private OpencodeProcessStatusProbe queryExisting(
            OpencodeServerProcess process,
            Instant checkedAt,
            String traceId) {
        try {
            OpencodeProcessHealthResult health = gateway.checkHealth(new OpencodeProcessHealthCommand(
                    process.processId(),
                    healthBaseUrl(process),
                    traceId));
            if (health == null) {
                // Manager 返回 null 表示网关异常，不持久化，返回 STALE
                return staleProbe(process, checkedAt, "opencode 健康检测未返回结果", ErrorCode.OPENCODE_BAD_GATEWAY);
            }
            if (health.healthy()) {
                OpencodeServerProcess running = saveSnapshot(
                        process,
                        OpencodeServerProcessStatus.RUNNING,
                        process.pid(),
                        health.message(),
                        checkedAt,
                        traceId);
                heartbeatStore.recordOpencodeHeartbeat(running.processId(), checkedAt);
                return new OpencodeProcessStatusProbe(
                        OpencodeProcessProbeStatus.RUNNING,
                        Optional.of(running),
                        "RUNNING",
                        "HEALTHY",
                        health.message(),
                        checkedAt,
                        false,
                        null);
            }
            // 只有进程明确死亡或 Manager 明确返回 not managed/not running 才立即写入 STOPPED
            if (isNotRunningHealthMessage(health.message())) {
                OpencodeServerProcess stopped = saveSnapshot(
                        process,
                        OpencodeServerProcessStatus.STOPPED,
                        null,
                        health.message(),
                        checkedAt,
                        traceId);
                return new OpencodeProcessStatusProbe(
                        OpencodeProcessProbeStatus.NOT_STARTED,
                        Optional.of(stopped),
                        "NOT_RUNNING",
                        "NOT_RUNNING",
                        health.message(),
                        checkedAt,
                        true,
                        null);
            }
            // 普通 HTTP 不健康：不持久化数据库状态，返回 STALE 让调用方使用上次成功数据
            // 注意：当前没有实现连续失败阈值机制，HTTP 长期故障时数据库会保持 RUNNING
            // TODO: 第二阶段引入连续失败计数和状态降级
            return staleProbe(process, checkedAt, health.message(), ErrorCode.OPENCODE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            // 瞬时异常（网络超时、连接拒绝等）不持久化，返回 STALE 状态
            ErrorCode errorCode = exception instanceof PlatformException platformException
                    ? platformException.errorCode()
                    : ErrorCode.OPENCODE_BAD_GATEWAY;
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "TestAgent 健康检测异常"
                    : exception.getMessage();
            return staleProbe(process, checkedAt, message, errorCode);
        }
    }

    /**
     * 瞬时异常时返回 STALE 状态，不修改数据库中的进程状态。
     * 调用方可以继续使用上次成功的数据，展示"状态暂时无法确认"而非立即切红。
     */
    private OpencodeProcessStatusProbe staleProbe(
            OpencodeServerProcess process,
            Instant checkedAt,
            String message,
            ErrorCode errorCode) {
        return new OpencodeProcessStatusProbe(
                OpencodeProcessProbeStatus.STALE,
                Optional.of(process),
                "STALE",
                "STALE",
                message,
                checkedAt,
                false,
                errorCode);
    }

    private OpencodeServerProcess saveSnapshot(
            OpencodeServerProcess process,
            OpencodeServerProcessStatus status,
            Long pid,
            String healthMessage,
            Instant checkedAt,
            String traceId) {
        return repository.saveOpencodeServerProcess(new OpencodeServerProcess(
                process.processId(),
                process.userId(),
                process.linuxServerId(),
                process.containerId(),
                process.port(),
                pid,
                refreshedBaseUrl(process),
                status,
                process.sessionPath(),
                process.configPath(),
                process.startedAt(),
                checkedAt,
                healthMessage,
                process.createdAt(),
                checkedAt,
                traceId));
    }

    private String refreshedBaseUrl(OpencodeServerProcess process) {
        return addressResolver == null ? process.baseUrl() : addressResolver.baseUrl(process.port());
    }

    private String healthBaseUrl(OpencodeServerProcess process) {
        return addressResolver == null ? process.baseUrl() : addressResolver.baseUrl(process.port());
    }

    private String weakHealthBaseUrl(ManagedOpencodeProcessSnapshot process) {
        if (addressResolver != null) {
            return addressResolver.baseUrl(process.port());
        }
        return process.baseUrl();
    }

    private OpencodeProcessWeakHealthResponse weakHealthUnavailable(
            OpencodeProcessWeakHealthStatus status,
            String linuxServerId,
            String containerId,
            int port,
            String baseUrl,
            Instant checkedAt,
            String message) {
        return new OpencodeProcessWeakHealthResponse(
                false,
                status,
                "NOT_RUNNING",
                linuxServerId,
                containerId,
                port,
                baseUrl,
                checkedAt,
                message);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 判断 manager health 的不健康说明是否表示进程不存在或未启动。
     */
    static boolean isNotRunningHealthMessage(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("pid is not alive")
                || normalized.contains("process is not alive")
                || normalized.contains("process is not running")
                || normalized.contains("process not found")
                || normalized.contains("state not found")
                || normalized.contains("already stopped")
                || normalized.contains("not managed");
    }
}
