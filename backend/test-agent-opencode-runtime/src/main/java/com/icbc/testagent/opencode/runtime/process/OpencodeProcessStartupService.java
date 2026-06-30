package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 公共 opencode server 启动服务，统一执行 start/restart 后的进程快照、健康确认和兼容节点投影。
 *
 * <p>所有会拉起 opencode server 的入口都应调用本服务，避免只收到 manager `STARTED` 就写入
 * `RUNNING`，从而把尚未通过 HTTP health 的进程暴露给前端或 Run 链路。
 */
@Service
public class OpencodeProcessStartupService {

    private static final String OPENCODE_AGENT_ID = "opencode";

    private final OpencodeProcessManagementRepository repository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final Clock clock;

    /**
     * Spring 生产构造器使用系统 UTC 时钟。
     */
    @Autowired
    public OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService) {
        this(repository, executionNodeRepository, gateway, heartbeatStore, statusQueryService, Clock.systemUTC());
    }

    /**
     * 兼容旧测试或手工装配入口；生产路径由 Spring 注入公共状态查询服务。
     */
    public OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(repository, executionNodeRepository, gateway, heartbeatStore, null, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，保证进程快照时间稳定。
     */
    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this(repository, executionNodeRepository, gateway, heartbeatStore, null, clock);
    }

    /**
     * 完整测试构造器允许替换公共状态查询服务。
     */
    OpencodeProcessStartupService(
            OpencodeProcessManagementRepository repository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.statusQueryService = statusQueryService == null
                ? new OpencodeProcessStatusQueryService(repository, gateway, heartbeatStore, clock)
                : statusQueryService;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 调用 manager start 后立即确认健康；只有 health healthy 才返回 RUNNING 进程。
     */
    public OpencodeServerProcess startAndVerify(OpencodeProcessStartupRequest request) {
        OpencodeProcessStartResult started = gateway.startProcess(startCommand(request));
        if (started == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程启动未返回结果");
        }
        return markStartedAndVerify(request, started.pid(), started.message());
    }

    /**
     * 在外部 restart 已返回 STARTED 后，复用同一套候选快照、health 和最终状态回写逻辑。
     */
    public OpencodeServerProcess markStartedAndVerify(
            OpencodeProcessStartupRequest request,
            Long pid,
            String startMessage) {
        Instant now = Instant.now(clock);
        OpencodeProcessId processId = request.processId() == null
                ? new OpencodeProcessId(RuntimeIdGenerator.opencodeProcessId())
                : request.processId();
        Instant createdAt = request.createdAt() == null ? now : request.createdAt();
        OpencodeServerProcess candidate = new OpencodeServerProcess(
                processId,
                request.userId(),
                request.linuxServerId(),
                request.containerId(),
                request.port(),
                pid,
                request.baseUrl(),
                OpencodeServerProcessStatus.STARTING,
                request.sessionPath(),
                request.configPath(),
                now,
                now,
                startMessage == null || startMessage.isBlank() ? "started" : startMessage,
                createdAt,
                now,
                request.traceId());
        repository.saveOpencodeServerProcess(candidate);
        OpencodeProcessStatusProbe probe = statusQueryService.query(candidate.processId(), request.traceId());
        if (probe.status() != OpencodeProcessProbeStatus.RUNNING) {
            ErrorCode errorCode = probe.errorCode() == null ? ErrorCode.OPENCODE_UNAVAILABLE : probe.errorCode();
            throw new PlatformException(
                    errorCode,
                    probe.message(),
                    Map.of("processId", candidate.processId().value(), "port", candidate.port()));
        }
        OpencodeServerProcess running = probe.process().orElse(candidate);
        repository.saveUserBinding(new UserOpencodeProcessBinding(
                running.userId(),
                OPENCODE_AGENT_ID,
                running.processId(),
                running.linuxServerId(),
                running.port(),
                UserOpencodeProcessBindingStatus.ACTIVE,
                request.bindingCreatedAt() == null ? running.createdAt() : request.bindingCreatedAt(),
                running.updatedAt(),
                request.traceId()));
        executionNodeRepository.save(projectExecutionNode(running, running.updatedAt(), request.traceId()));
        return running;
    }

    private OpencodeProcessStartCommand startCommand(OpencodeProcessStartupRequest request) {
        return new OpencodeProcessStartCommand(
                request.userId(),
                request.linuxServerId(),
                request.containerId(),
                request.port(),
                request.baseUrl(),
                request.sessionPath(),
                request.configPath(),
                request.traceId());
    }

    private ExecutionNode projectExecutionNode(OpencodeServerProcess process, Instant now, String traceId) {
        // 新进程表是主数据源；这里仅投影兼容节点，供既有 cancel/diff/runtime 链路按节点 ID 回查。
        return new ExecutionNode(
                new ExecutionNodeId("node_" + process.processId().value()),
                process.baseUrl(),
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                now,
                Set.of("opencode", "user-process"),
                now,
                now,
                traceId);
    }
}
