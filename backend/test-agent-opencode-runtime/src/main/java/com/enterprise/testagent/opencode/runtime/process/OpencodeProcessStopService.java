package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
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
            ConversationContextStore conversationContextStore) {
        this(gateway, repository, statusQueryService, conversationContextStore, Clock.systemUTC());
    }

    /**
     * 保留三参数手工装配入口；生产 Spring 使用带上下文存储的构造器。
     */
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService) {
        this(gateway, repository, statusQueryService, null, Clock.systemUTC());
    }

    /**
     * 兼容旧测试或手工装配入口；生产路径由 Spring 注入公共状态查询服务。
     */
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository) {
        this(gateway, repository, null, null, Clock.systemUTC());
    }

    /**
     * 无平台进程记录时仍可复用本服务向 manager 下发 stop。
     */
    public OpencodeProcessStopService(OpencodeProcessManagerGateway gateway) {
        this(gateway, null, null, null, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，保证停止后快照时间稳定。
     */
    OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            Clock clock) {
        this(gateway, repository, null, null, clock);
    }

    /**
     * 完整测试构造器允许替换公共状态查询服务。
     */
    OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock) {
        this(gateway, repository, statusQueryService, null, clock);
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
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.repository = repository;
        this.statusQueryService = statusQueryService == null && repository != null
                ? new OpencodeProcessStatusQueryService(repository, gateway, disabledHeartbeatStore(), clock)
                : statusQueryService;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.conversationContextStore = conversationContextStore;
    }

    /**
     * 停止指定 opencode server；tracked 请求必须确认 health 不健康才返回成功。
     */
    public OpencodeProcessControlResult stopAndVerify(OpencodeProcessStopRequest request) {
        OpencodeProcessControlResult stopped = gateway.stopProcess(new OpencodeProcessControlCommand(
                request.containerId(),
                request.port(),
                request.traceId()));
        if (stopped == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程停止未返回结果");
        }
        if (!"STOPPED".equals(stopped.status())) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程停止响应异常");
        }
        if (!request.tracked()) {
            return stopped;
        }
        OpencodeServerProcess process = trackedProcess(request);
        OpencodeProcessStatusProbe probe = statusQueryService.query(request.processId(), request.traceId());
        if (probe.status() != OpencodeProcessProbeStatus.NOT_STARTED) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "TestAgent 进程停止后仍未确认退出",
                    Map.of("processId", process.processId().value(), "port", process.port()));
        }
        OpencodeServerProcess stoppedProcess = probe.process().orElseGet(() -> refreshStoppedProcess(
                process,
                probe.message(),
                Instant.now(clock),
                request.traceId()));
        if (conversationContextStore != null) {
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
                request.traceId());
    }

    private OpencodeServerProcess trackedProcess(OpencodeProcessStopRequest request) {
        if (repository == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 停止缺少进程仓储");
        }
        return repository.findOpencodeServerProcessById(request.processId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "TestAgent 进程不存在",
                        Map.of("processId", request.processId().value(), "port", request.port())));
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
