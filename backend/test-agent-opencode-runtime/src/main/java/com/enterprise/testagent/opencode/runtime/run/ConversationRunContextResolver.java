package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessProbeStatus;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStatusProbe;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStatusQueryService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Run 启动上下文策略：新客户端校验 token，兼容期开关允许旧调用继续走数据库链路。
 */
@Service
public class ConversationRunContextResolver {

    private final ConversationContextApplicationService contextService;
    private final ConversationContextProperties properties;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final LegacyRunCompatibilityMetrics compatibilityMetrics;
    private LocalClientConnectionStore localClientConnectionStore;
    private BackendJavaRouteResolver backendJavaRouteResolver;

    @Autowired
    public ConversationRunContextResolver(
            ConversationContextApplicationService contextService,
            ConversationContextProperties properties,
            OpencodeProcessStatusQueryService statusQueryService,
            LegacyRunCompatibilityMetrics compatibilityMetrics) {
        this.contextService = Objects.requireNonNull(contextService, "contextService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.statusQueryService = Objects.requireNonNull(statusQueryService, "statusQueryService must not be null");
        this.compatibilityMetrics = Objects.requireNonNull(
                compatibilityMetrics, "compatibilityMetrics must not be null");
    }

    /** 兼容既有测试构造器；生产始终注入兼容计数器。 */
    public ConversationRunContextResolver(
            ConversationContextApplicationService contextService,
            ConversationContextProperties properties,
            OpencodeProcessStatusQueryService statusQueryService) {
        this(
                contextService,
                properties,
                statusQueryService,
                new LegacyRunCompatibilityMetrics(Optional.empty()));
    }

    /**
     * 兼容旧单元测试和手工构造路径；生产构造器始终注入公共状态查询服务。
     */
    public ConversationRunContextResolver(
            ConversationContextApplicationService contextService,
            ConversationContextProperties properties) {
        this.contextService = Objects.requireNonNull(contextService, "contextService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.statusQueryService = null;
        this.compatibilityMetrics = new LegacyRunCompatibilityMetrics(Optional.empty());
    }

    /**
     * 解析本次 Run 的可信上下文；缺 token 且兼容关闭时在任何数据库读取前拒绝请求。
     */
    public Optional<ConversationRunContext> resolve(UserId userId, String agentId, StartRunInput input) {
        return resolve(userId, agentId, input, "trace_unspecified");
    }

    /**
     * 解析 token 后使用缓存进程快照执行公共健康探测，全程不按 processId 查询数据库。
     */
    public Optional<ConversationRunContext> resolve(
            UserId userId,
            String agentId,
            StartRunInput input,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(input, "input must not be null");
        if (input.contextToken() == null) {
            if (properties.isLegacyRunWithoutContextEnabled()) {
                compatibilityMetrics.recordAcceptedLegacyRun();
                return Optional.empty();
            }
            throw new PlatformException(ErrorCode.CONVERSATION_CONTEXT_REQUIRED);
        }
        ConversationRunContext context = contextService.require(
                input.contextToken(), userId, agentId, input.sessionId());
        if (statusQueryService == null) {
            return Optional.of(context);
        }
        if (context.runtimeKind() == RuntimeKind.LOCAL_CLIENT) {
            requireCurrentLocalConnection(context);
            return Optional.of(context);
        }
        if (context.processSnapshot() == null) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "会话运行上下文缺少进程快照");
        }
        OpencodeProcessStatusProbe probe = statusQueryService.querySnapshot(context.processSnapshot(), traceId);
        if (probe.status() != OpencodeProcessProbeStatus.RUNNING) {
            // 只有公共状态服务明确确认进程已停止时才批量失效；STALE/瞬时故障只拒绝当前 Run，
            // 避免网络抖动把下一次请求放大成完整控制面 bootstrap。
            if (probe.status() == OpencodeProcessProbeStatus.NOT_STARTED) {
                contextService.invalidateProcess(context.processId());
            }
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "TestAgent 进程不可用，请重新初始化",
                    Map.of("processId", context.processId(), "healthStatus", probe.healthStatus()));
        }
        return Optional.of(context);
    }

    /** Run 启动前再次核对客户端、代次和连接所属 Java，禁止使用重连前的上下文。 */
    private void requireCurrentLocalConnection(ConversationRunContext context) {
        if (localClientConnectionStore == null || backendJavaRouteResolver == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "本地客户端连接校验未装配");
        }
        LocalClientConnectionRoute route = localClientConnectionStore
                .find(new LocalClientInstanceId(context.localClientInstanceId()))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.LOCAL_CLIENT_DISCONNECTED,
                        "本地 OpenCode 客户端离线"));
        boolean valid = route.userId().equals(context.userId())
                && route.connectionGeneration() == context.connectionGeneration()
                && backendJavaRouteResolver.isCurrent(route.backendProcessId())
                && route.processStatus() == LocalClientProcessStatus.RUNNING
                && route.opencodeHealthy();
        if (!valid) {
            contextService.invalidateProcess(context.processId());
            throw new PlatformException(
                    ErrorCode.LOCAL_CLIENT_DISCONNECTED,
                    "本地 OpenCode 客户端连接已变化，请重新初始化会话");
        }
    }

    /** 生产装配本地连接 fencing 校验；旧测试构造器保持不变。 */
    @Autowired(required = false)
    void configureLocalClientRuntime(
            LocalClientConnectionStore localClientConnectionStore,
            BackendJavaRouteResolver backendJavaRouteResolver) {
        this.localClientConnectionStore = Objects.requireNonNull(localClientConnectionStore);
        this.backendJavaRouteResolver = Objects.requireNonNull(backendJavaRouteResolver);
    }
}
