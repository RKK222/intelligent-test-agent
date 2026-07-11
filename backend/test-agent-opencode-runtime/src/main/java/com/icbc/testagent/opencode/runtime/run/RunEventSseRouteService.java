package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * RunEvent SSE 生产端路由解析服务。
 *
 * <p>新模式优先使用 Run 创建时写入 manifest 的生产服务器，legacy 才读取固定 routing decision；
 * 两种模式都不读取当前用户 binding，避免用户重新初始化后把旧 Run 路由到新服务器。
 */
@Service
public class RunEventSseRouteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunEventSseRouteService.class);
    private static final String EXECUTION_NODE_PROCESS_PREFIX = "node_ocp_";
    private static final String EXECUTION_NODE_PREFIX = "node_";

    private final RoutingDecisionRepository routingDecisionRepository;
    private final OpencodeProcessManagementRepository processRepository;
    private final BackendJavaRouteResolver routeResolver;
    private final RunRuntimeStore runRuntimeStore;

    public RunEventSseRouteService(
            RoutingDecisionRepository routingDecisionRepository,
            OpencodeProcessManagementRepository processRepository,
            BackendJavaRouteResolver routeResolver) {
        this(routingDecisionRepository, processRepository, routeResolver, null);
    }

    @Autowired
    public RunEventSseRouteService(
            RoutingDecisionRepository routingDecisionRepository,
            OpencodeProcessManagementRepository processRepository,
            BackendJavaRouteResolver routeResolver,
            RunRuntimeStore runRuntimeStore) {
        this.routingDecisionRepository = Objects.requireNonNull(
                routingDecisionRepository,
                "routingDecisionRepository must not be null");
        this.processRepository = Objects.requireNonNull(processRepository, "processRepository must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.runRuntimeStore = runRuntimeStore;
    }

    /**
     * 返回需要流式转发的目标 Java；返回空表示当前 Java 可以继续走本地 snapshot + DB replay。
     */
    public Optional<BackendJavaProcess> forwardTarget(RunId runId) {
        try {
            return forwardTargetStrict(runId);
        } catch (PlatformException exception) {
            LOGGER.warn(
                    "RunEvent SSE target backend unavailable, falling back to local replay. runId={} errorCode={}",
                    runId.value(),
                    exception.errorCode());
            return Optional.empty();
        }
    }

    /**
     * 返回 Run 原始生产 Java，路由基础设施不可用时直接失败。
     *
     * <p>取消等写操作不得沿用 SSE 的本机回放降级，否则连接落到非 owner Java 时可能误执行副作用。
     */
    public Optional<BackendJavaProcess> forwardTargetStrict(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (runRuntimeStore != null) {
            var manifest = runRuntimeStore.findManifest(runId);
            if (manifest.isPresent()) {
                return forwardTarget(manifest.get().producerLinuxServerId());
            }
        }
        RoutingDecision decision = routingDecisionRepository.findByRunId(runId)
                .orElseThrow(() -> unavailableRoute(runId, "routing_decision_missing"));
        OpencodeProcessId processId = processId(decision)
                .orElseThrow(() -> unavailableRoute(runId, "execution_node_unmapped"));
        OpencodeServerProcess process = processRepository.findOpencodeServerProcessById(processId)
                .orElseThrow(() -> unavailableRoute(runId, "producer_process_missing"));
        return forwardTarget(process.linuxServerId().value());
    }

    private Optional<BackendJavaProcess> forwardTarget(String linuxServerId) {
        LinuxServerId targetServerId = new LinuxServerId(linuxServerId);
        return routeResolver.remoteTarget(targetServerId)
                .map(routeResolver::requireBackend);
    }

    private PlatformException unavailableRoute(RunId runId, String reason) {
        return new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "Run 生产节点路由不可用",
                Map.of("runId", runId.value(), "reason", reason));
    }

    private Optional<OpencodeProcessId> processId(RoutingDecision decision) {
        String executionNodeId = decision.executionNodeId().value();
        if (!executionNodeId.startsWith(EXECUTION_NODE_PROCESS_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new OpencodeProcessId(executionNodeId.substring(EXECUTION_NODE_PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
