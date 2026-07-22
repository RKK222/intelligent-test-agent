package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 公共 opencode server 停止服务，统一执行 manager stop、停止后 health 确认和进程快照回写。
 *
 * <p>所有停止入口都应调用本服务，避免只信任 manager `STOPPED` 回包而没有确认
 * opencode server 已不可达。
 */
@Service
public class OpencodeProcessStopService {

    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final UserRepository userRepository;
    private final Clock clock;
    private final ConversationContextStore conversationContextStore;

    /**
     * Spring 生产构造器使用系统 UTC 时钟。
     */
    @Autowired
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService,
            UserRepository userRepository,
            ConversationContextStore conversationContextStore) {
        this(
                gateway,
                repository,
                statusQueryService,
                userRepository,
                conversationContextStore,
                Clock.systemUTC());
    }

    /**
     * 兼容旧手工装配入口；缺少用户仓储时，tracked 停止会 fail closed。
     */
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService,
            ConversationContextStore conversationContextStore) {
        this(gateway, repository, statusQueryService, null, conversationContextStore, Clock.systemUTC());
    }

    /**
     * 保留三参数手工装配入口；生产 Spring 使用带上下文存储的构造器。
     */
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService) {
        this(gateway, repository, statusQueryService, null, null, Clock.systemUTC());
    }

    /**
     * 手工装配 tracked 停止时显式提供用户仓储，确保可解析 manager 归属身份。
     */
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService,
            UserRepository userRepository) {
        this(gateway, repository, statusQueryService, userRepository, null, Clock.systemUTC());
    }

    /**
     * 兼容旧测试或手工装配入口；生产路径由 Spring 注入公共状态查询服务。
     */
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository) {
        this(gateway, repository, null, null, null, Clock.systemUTC());
    }

    /**
     * 无平台进程记录时仍可复用本服务向 manager 下发 stop。
     */
    public OpencodeProcessStopService(OpencodeProcessManagerGateway gateway) {
        this(gateway, null, null, null, null, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，保证停止后快照时间稳定。
     */
    OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            Clock clock) {
        this(gateway, repository, null, null, null, clock);
    }

    /**
     * 完整测试构造器允许替换公共状态查询服务。
     */
    OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock) {
        this(gateway, repository, statusQueryService, null, null, clock);
    }

    /**
     * 完整测试构造器允许验证停止后的上下文失效。
     */
    OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService,
            ConversationContextStore conversationContextStore,
            Clock clock) {
        this(gateway, repository, statusQueryService, null, conversationContextStore, clock);
    }

    /**
     * 完整测试构造器允许验证身份解析、状态探测和上下文失效。
     */
    OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService,
            UserRepository userRepository,
            ConversationContextStore conversationContextStore,
            Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.repository = repository;
        this.statusQueryService = statusQueryService == null && repository != null
                ? new OpencodeProcessStatusQueryService(repository, gateway, disabledHeartbeatStore(), clock)
                : statusQueryService;
        this.userRepository = userRepository;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.conversationContextStore = conversationContextStore;
    }

    /**
     * 停止指定 opencode server；tracked 请求必须确认 health 不健康才返回成功。
     */
    public OpencodeProcessControlResult stopAndVerify(OpencodeProcessStopRequest request) {
        if (!request.tracked()) {
            return requireStopped(gateway.stopProcess(new OpencodeProcessControlCommand(
                    request.containerId(),
                    request.port(),
                    request.traceId())));
        }
        // 先固定请求看到的数据库生命周期；迟到请求不得把同坐标的新 PID 当成自己的停止目标。
        OpencodeServerProcess process = requireCurrentRuntimeGeneration(
                trackedProcess(request),
                "TestAgent 进程停止目标已被并发修改");
        // 停止前探测只读取 manager 最新 PID，不提前改变数据库代次或 heartbeat。
        OpencodeProcessStatusProbe preflight = statusQueryService.querySnapshotReadOnly(process, request.traceId());
        if (preflight.status() == OpencodeProcessProbeStatus.NOT_STARTED) {
            // 只读探测确认 manager 已无进程后，再通过普通状态查询以原代次 CAS 回写 STOPPED。
            OpencodeProcessStatusProbe persistedStop = statusQueryService.querySnapshot(process, request.traceId());
            if (persistedStop.status() != OpencodeProcessProbeStatus.NOT_STARTED) {
                throw new PlatformException(
                        ErrorCode.OPENCODE_BAD_GATEWAY,
                        "TestAgent 进程停止状态在回写前已变化",
                        Map.of("processId", process.processId().value(), "port", process.port()));
            }
            return stoppedResult(process, persistedStop, request.traceId(), true);
        }
        if (preflight.status() == OpencodeProcessProbeStatus.STALE) {
            throw new PlatformException(
                    preflight.errorCode() == null ? ErrorCode.OPENCODE_BAD_GATEWAY : preflight.errorCode(),
                    "TestAgent 进程停止前无法确认当前实例",
                    Map.of("processId", process.processId().value(), "port", process.port()));
        }
        OpencodeServerProcess current = preflight.process().orElse(process);
        if (current.pid() == null || current.pid() < 1) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "TestAgent 进程停止前无法确认当前 PID",
                    Map.of("processId", current.processId().value(), "port", current.port()));
        }
        // manager PID 只作为本次停止凭据；停止后状态回写继续使用原持久化代次作为 CAS expected。
        OpencodeServerProcess persistenceExpected = requireCurrentRuntimeGeneration(
                process,
                "TestAgent 进程停止前生命周期已被并发修改");
        String unifiedAuthId = unifiedAuthId(current);
        requireStopped(gateway.stopOwnedProcess(new OpencodeProcessOwnedStopCommand(
                current.containerId(),
                current.port(),
                unifiedAuthId,
                current.pid(),
                request.traceId())));
        OpencodeProcessStatusProbe postStop = statusQueryService.querySnapshot(persistenceExpected, request.traceId());
        if (postStop.status() != OpencodeProcessProbeStatus.NOT_STARTED) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "TestAgent 进程停止后仍未确认退出",
                    Map.of("processId", current.processId().value(), "port", current.port()));
        }
        return stoppedResult(persistenceExpected, postStop, request.traceId(), true);
    }

    /**
     * 启动竞争补偿只停止本次 manager 新建的精确实例，并以只读探测确认退出。
     *
     * <p>该路径不得根据已迁移的数据库 assignment 回写状态或刷新 heartbeat。
     */
    public OpencodeProcessControlResult stopStartedInstanceAndVerify(
            OpencodeServerProcess process,
            String unifiedAuthId,
            long expectedPid,
            String traceId) {
        Objects.requireNonNull(process, "process must not be null");
        requireStopped(gateway.stopOwnedProcess(new OpencodeProcessOwnedStopCommand(
                process.containerId(),
                process.port(),
                unifiedAuthId,
                expectedPid,
                traceId)));
        OpencodeProcessStatusProbe postStop = statusQueryService.querySnapshotReadOnly(process, traceId);
        if (postStop.status() != OpencodeProcessProbeStatus.NOT_STARTED) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "TestAgent 启动补偿停止后仍未确认退出",
                    Map.of("processId", process.processId().value(), "port", process.port()));
        }
        return stoppedResult(process, postStop, traceId, false);
    }

    private OpencodeProcessControlResult requireStopped(OpencodeProcessControlResult stopped) {
        if (stopped == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程停止未返回结果");
        }
        if (!"STOPPED".equals(stopped.status())) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程停止响应异常");
        }
        return stopped;
    }

    private OpencodeProcessControlResult stoppedResult(
            OpencodeServerProcess process,
            OpencodeProcessStatusProbe probe,
            String traceId,
            boolean invalidateContext) {
        OpencodeServerProcess stoppedProcess = probe.process().orElseGet(() -> refreshStoppedProcess(
                process,
                probe.message(),
                Instant.now(clock),
                traceId));
        if (invalidateContext && conversationContextStore != null) {
            conversationContextStore.invalidateProcess(stoppedProcess.processId().value());
        }
        return new OpencodeProcessControlResult(
                "stop",
                "STOPPED",
                stoppedProcess.port(),
                null,
                stoppedProcess.baseUrl(),
                stoppedProcess.sessionPath(),
                stoppedProcess.configPath(),
                false,
                probe.message(),
                traceId);
    }

    private String unifiedAuthId(OpencodeServerProcess process) {
        if (userRepository == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 停止缺少用户身份仓储");
        }
        return userRepository.findByUserId(process.userId())
                .map(User::unifiedAuthId)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "TestAgent 进程缺少可验证的用户身份",
                        Map.of("processId", process.processId().value(), "port", process.port())));
    }

    private OpencodeServerProcess trackedProcess(OpencodeProcessStopRequest request) {
        if (request.processSnapshot() != null) {
            return request.processSnapshot();
        }
        if (repository == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 停止缺少进程仓储");
        }
        return repository.findOpencodeServerProcessById(request.processId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "TestAgent 进程不存在",
                        Map.of("processId", request.processId().value(), "port", request.port())));
    }

    /**
     * 读取并匹配当前权威运行代次，确保后续 health/stop 不会采用同坐标的新生命周期。
     */
    private OpencodeServerProcess requireCurrentRuntimeGeneration(
            OpencodeServerProcess expected,
            String message) {
        if (repository == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 停止缺少进程仓储");
        }
        OpencodeServerProcess current = repository.findOpencodeServerProcessById(expected.processId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "TestAgent 进程不存在",
                        Map.of("processId", expected.processId().value(), "port", expected.port())));
        if (!sameRuntimeGeneration(current, expected)) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    message,
                    Map.of("processId", expected.processId().value(), "port", expected.port()));
        }
        return current;
    }

    /** 与数据库 runtime CAS 使用同一代次字段，避免本地校验和持久化条件分叉。 */
    private boolean sameRuntimeGeneration(
            OpencodeServerProcess current,
            OpencodeServerProcess expected) {
        return current.processId().equals(expected.processId())
                && current.userId().equals(expected.userId())
                && current.linuxServerId().equals(expected.linuxServerId())
                && current.containerId().equals(expected.containerId())
                && current.port() == expected.port()
                && current.status() == expected.status()
                && Objects.equals(current.pid(), expected.pid())
                && Objects.equals(current.traceId(), expected.traceId());
    }

    private OpencodeServerProcess refreshStoppedProcess(
            OpencodeServerProcess process,
            String healthMessage,
            Instant now,
            String traceId) {
        return new OpencodeServerProcess(
                process.processId(),
                process.userId(),
                process.linuxServerId(),
                process.containerId(),
                process.port(),
                null,
                process.baseUrl(),
                OpencodeServerProcessStatus.STOPPED,
                process.sessionPath(),
                process.configPath(),
                process.startedAt(),
                now,
                healthMessage,
                process.createdAt(),
                now,
                traceId);
    }

    private static com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return new com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(com.enterprise.testagent.domain.opencodeprocess.LinuxServerId linuxServerId, Instant heartbeatAt) { }
            @Override public void recordBackendSnapshot(com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot snapshot) { }
            @Override public void recordManagerSnapshot(com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot snapshot) { }
            @Override public void recordOpencodeHeartbeat(com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId processId, Instant heartbeatAt) { }
            @Override public java.util.List<com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot> liveBackendSnapshots() { return java.util.List.of(); }
            @Override public java.util.List<com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot> liveManagerSnapshots() { return java.util.List.of(); }
            @Override public java.util.Set<com.enterprise.testagent.domain.opencodeprocess.LinuxServerId> liveBackendServerIds() { return java.util.Set.of(); }
            @Override public java.util.Set<com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId> liveOpencodeProcessIds() { return java.util.Set.of(); }
            @Override public void cleanupExpiredHeartbeats() { }
        };
    }
}
