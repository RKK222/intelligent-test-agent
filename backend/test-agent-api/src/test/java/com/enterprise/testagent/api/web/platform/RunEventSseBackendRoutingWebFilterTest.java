package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.routing.RoutingDecision;
import com.enterprise.testagent.domain.routing.RoutingDecisionRepository;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.event.RunEventLiveBus;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.opencode.runtime.run.RunEventSseRouteService;
import com.enterprise.testagent.opencode.runtime.runtime.AgentRuntimeTargetResolver;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionRunStartResult;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionStreamingApplicationService;
import com.enterprise.testagent.opencode.runtime.runtime.SideQuestionTerminalService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RunEventSseBackendRoutingWebFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");
    private static final String RUN_ID = "run_1234567890abcdef";

    @Test
    void forwardsAgentScopedRunEventSseToProductionBackend() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        BackendJavaProcess target = backend();
        when(routeService.forwardTarget(new RunId(RUN_ID))).thenReturn(Optional.of(target));
        when(forwarder.forward(org.mockito.ArgumentMatchers.any(), eq(target))).thenReturn(Mono.empty());
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/events?lastEventId=7")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .header(TraceConstants.TRACE_ID_HEADER, "trace_1234567890abcdef")
                .header("Last-Event-ID", "6")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        verify(routeService).forwardTarget(new RunId(RUN_ID));
        verify(forwarder).forward(exchange, target);
    }

    @Test
    void forwardsPlatformRunEventSseToProductionBackend() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        BackendJavaProcess target = backend();
        when(routeService.forwardTarget(new RunId(RUN_ID))).thenReturn(Optional.of(target));
        when(forwarder.forward(org.mockito.ArgumentMatchers.any(), eq(target))).thenReturn(Mono.empty());
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/platform/opencode-runtime/runs/" + RUN_ID + "/events")
                .build());

        filter.filter(exchange, chain(exchange1 -> Mono.empty())).block(Duration.ofSeconds(2));

        verify(routeService).forwardTarget(new RunId(RUN_ID));
        verify(forwarder).forward(exchange, target);
    }

    @Test
    void forwardsStartedSideQuestionRunToTheJavaOwningItsPersistedProductionNode() {
        InMemoryRoutingDecisionRepository routingDecisions = new InMemoryRoutingDecisionRepository();
        SessionId mainSessionId = new SessionId("ses_1234567890abcdef");
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        UserId userId = new UserId("usr_1234567890abcdef");
        ExecutionNode productionNode = new ExecutionNode(
                new ExecutionNodeId("node_ocp_1234567890abcdef"),
                "http://server-b:4097",
                ExecutionNodeStatus.READY,
                0,
                1,
                NOW);
        SessionRepository sessions = mock(SessionRepository.class);
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targetResolver = mock(AgentRuntimeTargetResolver.class);
        when(sessions.findById(mainSessionId)).thenReturn(Optional.of(new Session(
                mainSessionId,
                workspaceId,
                "main",
                NOW)));
        when(sessions.save(org.mockito.ArgumentMatchers.any(Session.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(runs.save(org.mockito.ArgumentMatchers.any(Run.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(targetResolver.sessionTarget("opencode", userId, mainSessionId.value(), "trace_side_question"))
                .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                        mock(AgentRuntime.class),
                        productionNode,
                        "/workspace",
                        "remote_main"));
        SideQuestionStreamingApplicationService streamingService = new SideQuestionStreamingApplicationService(
                sessions,
                runs,
                routingDecisions,
                mock(RunEventAppender.class),
                mock(RunEventLiveBus.class),
                targetResolver,
                mock(SideQuestionTerminalService.class));

        SideQuestionRunStartResult started = streamingService.start(
                userId,
                "opencode",
                mainSessionId,
                "what happened?",
                null,
                null,
                "trace_side_question");
        assertThat(routingDecisions.findByRunId(started.runId()))
                .get()
                .extracting(decision -> decision.executionNodeId().value())
                .isEqualTo("node_ocp_1234567890abcdef");

        OpencodeProcessManagementRepository processes = mock(OpencodeProcessManagementRepository.class);
        when(processes.findOpencodeServerProcessById(new OpencodeProcessId("ocp_1234567890abcdef")))
                .thenReturn(Optional.of(process()));
        BackendJavaProcess target = backend();
        RunEventSseRouteService routeService = new RunEventSseRouteService(
                routingDecisions,
                processes,
                routeResolver(target));
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        when(forwarder.forward(org.mockito.ArgumentMatchers.any(), eq(target))).thenReturn(Mono.empty());
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + started.runId().value() + "/events")
                .build());

        filter.filter(exchange, chain(exchange1 -> Mono.empty())).block(Duration.ofSeconds(2));

        verify(forwarder).forward(exchange, target);
    }

    @Test
    void routedHeaderSkipsForwardingToAvoidLoops() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/events")
                .header(BackendHttpForwarder.ROUTED_HEADER, "true")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(routeService, forwarder);
    }

    @Test
    void missingProductionBackendFallsThroughToLocalDbReplay() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        when(routeService.forwardTarget(new RunId(RUN_ID))).thenReturn(Optional.empty());
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/events")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verify(routeService).forwardTarget(new RunId(RUN_ID));
        verifyNoInteractions(forwarder);
    }

    @Test
    void ignoresNonRunEventPaths() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/diff")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(routeService, forwarder);
    }

    private static WebFilterChain chain(java.util.function.Function<org.springframework.web.server.ServerWebExchange, Mono<Void>> delegate) {
        return delegate::apply;
    }

    private static BackendJavaProcess backend() {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("server-b"),
                "http://10.8.0.22:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_backend");
    }

    private static OpencodeServerProcess process() {
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("server-b"),
                new OpencodeContainerId("ctr_1234567890abcdef"),
                4097,
                12345L,
                "http://server-b:4097",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4097",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                "trace_process");
    }

    /** 使用真实公共路由解析器读取目标服务器的最新 Java 心跳，不在测试里复制生产选择规则。 */
    private static BackendJavaRouteResolver routeResolver(BackendJavaProcess target) {
        OpencodeProcessHeartbeatStore heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
        LinuxServer linuxServer = new LinuxServer(
                target.linuxServerId(),
                "Server B",
                LinuxServerStatus.READY,
                Map.of(),
                NOW,
                NOW,
                NOW,
                "trace_server");
        when(heartbeatStore.liveBackendSnapshots())
                .thenReturn(List.of(new BackendRuntimeSnapshot(linuxServer, target)));
        return new BackendJavaRouteResolver(
                heartbeatStore,
                new ManagerControlSettings(
                        "",
                        "http://server-a:8080",
                        new LinuxServerId("server-a"),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 启动服务和 SSE 路由服务共享同一仓储实例，证明消费的是实际保存的生产归属。 */
    private static final class InMemoryRoutingDecisionRepository implements RoutingDecisionRepository {

        private final ConcurrentMap<RunId, RoutingDecision> decisions = new ConcurrentHashMap<>();

        @Override
        public RoutingDecision save(RoutingDecision routingDecision) {
            decisions.put(routingDecision.runId(), routingDecision);
            return routingDecision;
        }

        @Override
        public Optional<RoutingDecision> findByRunId(RunId runId) {
            return Optional.ofNullable(decisions.get(runId));
        }
    }
}
