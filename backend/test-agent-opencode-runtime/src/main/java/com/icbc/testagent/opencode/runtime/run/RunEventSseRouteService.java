package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RunEvent SSE 生产端路由解析服务。
 *
 * <p>路由依据固定为 Run 创建时保存的 routing_decision，不读取当前用户 binding，
 * 避免用户重新初始化 opencode 进程后把旧 Run 的实时事件路由到新服务器。
 */
@Service
public class RunEventSseRouteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunEventSseRouteService.class);
    private static final String EXECUTION_NODE_PROCESS_PREFIX = "node_ocp_";
    private static final String EXECUTION_NODE_PREFIX = "node_";

    private final RoutingDecisionRepository routingDecisionRepository;
    private final OpencodeProcessManagementRepository processRepository;
    private final BackendJavaRouteResolver routeResolver;

    public RunEventSseRouteService(
            RoutingDecisionRepository routingDecisionRepository,
            OpencodeProcessManagementRepository processRepository,
            BackendJavaRouteResolver routeResolver) {
        this.routingDecisionRepository = Objects.requireNonNull(
                routingDecisionRepository,
                "routingDecisionRepository must not be null");
        this.processRepository = Objects.requireNonNull(processRepository, "processRepository must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
    }

    /**
     * 返回需要流式转发的目标 Java；返回空表示当前 Java 可以继续走本地 snapshot + DB replay。
     */
    public Optional<BackendJavaProcess> forwardTarget(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        Optional<RoutingDecision> decision = routingDecisionRepository.findByRunId(runId);
        if (decision.isEmpty()) {
            return Optional.empty();
        }
        Optional<OpencodeProcessId> processId = processId(decision.get());
        if (processId.isEmpty()) {
            return Optional.empty();
        }
        Optional<OpencodeServerProcess> process = processRepository.findOpencodeServerProcessById(processId.get());
        if (process.isEmpty()) {
            return Optional.empty();
        }
        try {
            return routeResolver.remoteTarget(process.get().linuxServerId())
                    .map(routeResolver::requireBackend);
        } catch (PlatformException exception) {
            LOGGER.warn(
                    "RunEvent SSE target backend unavailable, falling back to local replay. runId={} linuxServerId={} errorCode={}",
                    runId.value(),
                    process.get().linuxServerId().value(),
                    exception.errorCode());
            return Optional.empty();
        }
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
