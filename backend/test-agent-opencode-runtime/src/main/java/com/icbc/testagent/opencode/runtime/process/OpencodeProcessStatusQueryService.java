package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
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

    /**
     * Spring 生产构造器使用系统 UTC 时钟。
     */
    @Autowired
    public OpencodeProcessStatusQueryService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(repository, gateway, heartbeatStore, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，保证状态快照时间稳定。
     */
    OpencodeProcessStatusQueryService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
                    "opencode 进程未启动",
                    checkedAt,
                    true,
                    null);
        }
        return queryExisting(process.get(), checkedAt, traceId);
    }

    private OpencodeProcessStatusProbe queryExisting(
            OpencodeServerProcess process,
            Instant checkedAt,
            String traceId) {
        try {
            OpencodeProcessHealthResult health = gateway.checkHealth(new OpencodeProcessHealthCommand(
                    process.processId(),
                    process.baseUrl(),
                    traceId));
            if (health == null) {
                return failedProbe(process, checkedAt, "opencode 健康检测未返回结果", ErrorCode.OPENCODE_BAD_GATEWAY, traceId);
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
            OpencodeServerProcess unhealthy = saveSnapshot(
                    process,
                    OpencodeServerProcessStatus.UNHEALTHY,
                    process.pid(),
                    health.message(),
                    checkedAt,
                    traceId);
            return new OpencodeProcessStatusProbe(
                    OpencodeProcessProbeStatus.HEALTH_CHECK_FAILED,
                    Optional.of(unhealthy),
                    "UNHEALTHY",
                    "UNHEALTHY",
                    health.message(),
                    checkedAt,
                    true,
                    null);
        } catch (RuntimeException exception) {
            ErrorCode errorCode = exception instanceof PlatformException platformException
                    ? platformException.errorCode()
                    : ErrorCode.OPENCODE_BAD_GATEWAY;
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "opencode 健康检测异常"
                    : exception.getMessage();
            return failedProbe(process, checkedAt, message, errorCode, traceId);
        }
    }

    private OpencodeProcessStatusProbe failedProbe(
            OpencodeServerProcess process,
            Instant checkedAt,
            String message,
            ErrorCode errorCode,
            String traceId) {
        OpencodeServerProcess failed = saveSnapshot(
                process,
                OpencodeServerProcessStatus.FAILED,
                process.pid(),
                message,
                checkedAt,
                traceId);
        return new OpencodeProcessStatusProbe(
                OpencodeProcessProbeStatus.HEALTH_CHECK_FAILED,
                Optional.of(failed),
                "CHECK_FAILED",
                "CHECK_FAILED",
                message,
                checkedAt,
                true,
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
                process.baseUrl(),
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

    /**
     * 判断 manager health 的不健康说明是否表示进程不存在或未启动。
     */
    static boolean isNotRunningHealthMessage(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("pid is not alive")
                || normalized.contains("process is not running")
                || normalized.contains("process not found")
                || normalized.contains("state not found")
                || normalized.contains("already stopped")
                || normalized.contains("not managed");
    }
}
