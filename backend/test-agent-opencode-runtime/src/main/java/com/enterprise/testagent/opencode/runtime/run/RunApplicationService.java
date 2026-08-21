package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.agent.runtime.AgentCancelCommand;
import com.enterprise.testagent.agent.runtime.AgentCreateSessionCommand;
import com.enterprise.testagent.agent.runtime.AgentCreateSessionResult;
import com.enterprise.testagent.agent.runtime.AgentPromptPart;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRootRunTerminalObserver;
import com.enterprise.testagent.agent.runtime.AgentRunPromptContext;
import com.enterprise.testagent.agent.runtime.AgentRunSystemPromptContributor;
import com.enterprise.testagent.agent.runtime.AgentSessionMessage;
import com.enterprise.testagent.agent.runtime.AgentStartRunCommand;
import com.enterprise.testagent.agent.runtime.AgentStreamEventsCommand;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunLeaseLifecycle;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate;
import com.enterprise.testagent.domain.hub.ProtectedAgentSelection;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventScopeContext;
import com.enterprise.testagent.domain.event.RunSessionScope;
import com.enterprise.testagent.domain.event.RunSessionScopeRepository;
import com.enterprise.testagent.domain.event.RunSessionScopeSession;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.routing.ExecutionNodeRouter;
import com.enterprise.testagent.domain.routing.RoutingDecision;
import com.enterprise.testagent.domain.routing.RoutingDecisionRepository;
import com.enterprise.testagent.domain.routing.RoutingReason;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunRuntimeTarget;
import com.enterprise.testagent.domain.run.RunRuntimeTargetRepository;
import com.enterprise.testagent.domain.run.RunPersistenceAnchor;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionHistoryRepository;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.event.RunEventLiveBus;
import com.enterprise.testagent.opencode.runtime.model.ModelCatalogApplicationService;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionSessionLockGuard;
import com.enterprise.testagent.opencode.runtime.localclient.LocalRuntimeCapabilityGuard;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedAgentExecutionService;
import com.enterprise.testagent.opencode.runtime.protectedagent.ProtectedOpencodeAgentRuntime;
import com.enterprise.testagent.opencode.runtime.runtime.AgentRuntimeTargetResolver;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import com.enterprise.testagent.opencode.runtime.support.ExperienceWorkspacePathRedactor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Run 应用服务，集中编排持久化、路由、agent 启动/取消和 RunEvent 追加。
 */
@Service
public class RunApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunApplicationService.class);
    private static final int ROUTING_CANDIDATE_LIMIT = 50;
    private static final String DEFAULT_OPENCODE_AGENT = "build";
    private static final String WORKSPACE_ATTACHMENT_CONTEXT_TYPE = "workspace_attachment";
    private static final String WORKSPACE_ATTACHMENT_DELIVERY_MODE = "deliveryMode";
    private static final String NATIVE_ATTACHMENT_DELIVERY = "native";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> LIVE_DIFF_TOOLS = Set.of("write", "edit", "apply_patch");
    private static final Duration TRANSPORT_ERROR_TERMINAL_GRACE = Duration.ofMillis(300);
    private static final Duration TITLE_WAIT_RECONNECT_DELAY = Duration.ofSeconds(1);
    private static final int INTERACTION_RECONCILE_ATTEMPTS = 30;
    private static final Duration REMOTE_CANCEL_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<RunEventType> OUTPUT_ACTIVITY_TYPES = Set.of(
            RunEventType.ASSISTANT_MESSAGE_DELTA,
            RunEventType.MESSAGE_UPDATED,
            RunEventType.MESSAGE_REMOVED,
            RunEventType.MESSAGE_PART_UPDATED,
            RunEventType.MESSAGE_PART_REMOVED,
            RunEventType.MESSAGE_PART_DELTA,
            RunEventType.SESSION_DIFF,
            RunEventType.SESSION_STATUS,
            RunEventType.TODO_UPDATED,
            RunEventType.TOOL_STARTED,
            RunEventType.TOOL_FINISHED,
            RunEventType.DIFF_PROPOSED,
            RunEventType.SESSION_CREATED,
            RunEventType.SESSION_UPDATED,
            RunEventType.SESSION_DELETED,
            RunEventType.TEST_FINISHED,
            RunEventType.SESSION_ERROR,
            RunEventType.SESSION_CHILD_DISCOVERED,
            RunEventType.SESSION_SCOPE_UPDATED,
            RunEventType.PERMISSION_ASKED,
            RunEventType.PERMISSION_REPLIED,
            RunEventType.QUESTION_ASKED,
            RunEventType.QUESTION_REPLIED,
            RunEventType.QUESTION_REJECTED,
            RunEventType.VCS_BRANCH_UPDATED,
            RunEventType.LSP_UPDATED,
            RunEventType.MCP_TOOLS_CHANGED,
            RunEventType.REFERENCE_UPDATED,
            RunEventType.FILE_EDITED,
            RunEventType.FILE_WATCHER_UPDATED);
    private static final Set<RunEventType> ASK_REQUEST_TYPES = Set.of(
            RunEventType.PERMISSION_ASKED,
            RunEventType.QUESTION_ASKED);
    private static final Set<RunEventType> ASK_RESOLVED_TYPES = Set.of(
            RunEventType.PERMISSION_REPLIED,
            RunEventType.QUESTION_REPLIED,
            RunEventType.QUESTION_REJECTED);
    private static final Set<RunEventType> TERMINAL_TYPES = Set.of(
            RunEventType.RUN_SUCCEEDED,
            RunEventType.RUN_FAILED,
            RunEventType.RUN_CANCELLED);
    private static final Duration LEGACY_SCHEDULED_DISPATCH_LEASE = Duration.ofMinutes(5);

    private final WorkspaceRepository workspaceRepository;
    private final com.enterprise.testagent.domain.session.SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final RunEventAppender runEventAppender;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final AgentSessionBindingRepository agentSessionBindingRepository;
    private final RunEventLiveBus runEventLiveBus;
    private final RunEventPersistencePolicy runEventPersistencePolicy;
    private final ModelCatalogApplicationService modelCatalogService;
    private final UserOpencodeProcessAssignmentService userProcessAssignmentService;
    private final ManagedWorkspacePathResolver workspacePathResolver;
    private final AgentRuntimeTargetResolver runtimeTargetResolver;
    private final RunSessionMessageSnapshotService snapshotService;
    private final RunSessionScopeRepository runSessionScopeRepository;
    private final RunSessionScopeRuntimeCache runSessionScopeRuntimeCache;
    private RunSessionScopeRouter runSessionScopeRouter;
    private final RunActivityStateStore runActivityStateStore;
    private final RunSessionTitleWatchService sessionTitleWatchService;
    private final ConversationRunContextResolver conversationContextResolver;
    private RunRuntimeStore runRuntimeStore;
    private RunStorageModeSelector runStorageModeSelector;
    private RunSummaryPersistencePort runSummaryPersistencePort;
    private RunTerminalProjectionService runTerminalProjectionService;
    private BackendInstanceIdentity backendInstanceIdentity;
    private RunRuntimeTargetRepository runRuntimeTargetRepository;
    private LocalRuntimeCapabilityGuard localRuntimeCapabilityGuard;
    private PublicAgentConfigMessageGate publicConfigMessageGate = ignored ->
            PublicAgentConfigMessageGate.MessageGateStatus.open();
    private RunOwnerLeaseSupervisor ownerLeaseSupervisor;
    private RunRuntimeLossConvergenceScheduler runtimeLossScheduler;
    private NightExecutionSessionLockGuard nightExecutionLockGuard;
    private UserRuntimeDisposeCoordinator userRuntimeDisposeCoordinator;
    private ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;
    private SessionHistoryRepository sessionHistoryRepository;
    private List<ScheduledRunLifecycleObserver> scheduledRunLifecycleObservers = List.of();
    private RunDispatchAcceptanceProbe runDispatchAcceptanceProbe;
    private List<RunRootSessionErrorObserver> rootSessionErrorObservers = List.of();
    private RunResendCancellationService resendCancellationService;
    private List<AgentRunSystemPromptContributor> runSystemPromptContributors = List.of();
    private List<AgentRootRunTerminalObserver> rootRunTerminalObservers = List.of();
    private AutomationReferenceRunLeaseLifecycle automationReferenceRunLeaseLifecycle;
    private ProtectedAgentExecutionService protectedAgentExecutionService;
    private final ExecutionNodeRouter executionNodeRouter = new ExecutionNodeRouter();

    /**
     * 创建兼容旧装配的 Run 编排服务；未显式注入运行态 Redis 存储时使用降级 no-op。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                modelCatalogService,
                userProcessAssignmentService,
                workspacePathResolver,
                snapshotService,
                runSessionScopeRepository,
                runSessionScopeRuntimeCache,
                null);
    }

    /** 创建生产用 Run 编排服务，显式注入实时事件总线、持久化策略和运行态 Redis 存储。 */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                modelCatalogService,
                userProcessAssignmentService,
                workspacePathResolver,
                snapshotService,
                runSessionScopeRepository,
                runSessionScopeRuntimeCache,
                runActivityStateStore,
                null,
                null,
                null);
    }

    /**
     * 兼容只注入会话上下文策略的调用方。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            ConversationRunContextResolver conversationContextResolver) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                modelCatalogService,
                userProcessAssignmentService,
                workspacePathResolver,
                snapshotService,
                runSessionScopeRepository,
                runSessionScopeRuntimeCache,
                runActivityStateStore,
                conversationContextResolver,
                null,
                null);
    }

    /** 兼容只注入原生标题监听服务的调用方。 */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            RunSessionTitleWatchService sessionTitleWatchService) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                modelCatalogService,
                userProcessAssignmentService,
                workspacePathResolver,
                snapshotService,
                runSessionScopeRepository,
                runSessionScopeRuntimeCache,
                runActivityStateStore,
                null,
                sessionTitleWatchService,
                null);
    }

    /** 兼容同时注入会话上下文策略与原生标题监听、但尚未接入 Redis Run 数据面的调用方。 */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            ConversationRunContextResolver conversationContextResolver,
            RunSessionTitleWatchService sessionTitleWatchService) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                modelCatalogService,
                userProcessAssignmentService,
                workspacePathResolver,
                snapshotService,
                runSessionScopeRepository,
                runSessionScopeRuntimeCache,
                runActivityStateStore,
                conversationContextResolver,
                sessionTitleWatchService,
                null);
    }

    /** 兼容接入会话上下文、原生标题监听与 Redis Run 数据面，但尚未接入终态摘要投影的调用方。 */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            ConversationRunContextResolver conversationContextResolver,
            RunSessionTitleWatchService sessionTitleWatchService,
            RunRuntimeStore runRuntimeStore) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.routingDecisionRepository = Objects.requireNonNull(routingDecisionRepository, "routingDecisionRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.agentRuntimeRegistry = Objects.requireNonNull(agentRuntimeRegistry, "agentRuntimeRegistry must not be null");
        this.agentSessionBindingRepository = Objects.requireNonNull(agentSessionBindingRepository, "agentSessionBindingRepository must not be null");
        this.runEventLiveBus = Objects.requireNonNull(runEventLiveBus, "runEventLiveBus must not be null");
        this.runEventPersistencePolicy = Objects.requireNonNull(runEventPersistencePolicy, "runEventPersistencePolicy must not be null");
        this.modelCatalogService = modelCatalogService;
        this.userProcessAssignmentService = userProcessAssignmentService;
        this.workspacePathResolver = Objects.requireNonNull(workspacePathResolver, "workspacePathResolver must not be null");
        this.runtimeTargetResolver = new AgentRuntimeTargetResolver(
                workspaceRepository,
                sessionRepository,
                executionNodeRepository,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                userProcessAssignmentService,
                this.workspacePathResolver);
        this.snapshotService = snapshotService == null
                ? new RunSessionMessageSnapshotService(
                        runRepository,
                        sessionRepository,
                        sessionMessageRepository,
                        executionNodeRepository,
                        agentRuntimeRegistry,
                        agentSessionBindingRepository,
                        runSessionScopeRepository,
                        new ObjectMapper())
                : snapshotService;
        this.runSessionScopeRepository = runSessionScopeRepository;
        this.runSessionScopeRuntimeCache = runSessionScopeRuntimeCache == null
                ? RunSessionScopeRuntimeCache.disabled()
                : runSessionScopeRuntimeCache;
        this.runRuntimeStore = runRuntimeStore;
        this.runSessionScopeRouter = runRuntimeStore == null
                ? new RunSessionScopeRouter(runSessionScopeRepository, this.runSessionScopeRuntimeCache)
                : new RunSessionScopeRouter(runSessionScopeRepository, this.runSessionScopeRuntimeCache, runRuntimeStore);
        this.runActivityStateStore = runActivityStateStore == null
                ? new RunActivityStateStore(null)
                : runActivityStateStore;
        this.conversationContextResolver = conversationContextResolver;
        this.sessionTitleWatchService = sessionTitleWatchService;
        this.runStorageModeSelector = null;
        this.runSummaryPersistencePort = null;
        this.runTerminalProjectionService = null;
        this.backendInstanceIdentity = null;
    }

    /** 兼容接入终态摘要投影、但未注入原生标题监听的调用方。 */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            ConversationRunContextResolver conversationContextResolver,
            RunRuntimeStore runRuntimeStore,
            RunStorageModeSelector runStorageModeSelector,
            RunSummaryPersistencePort runSummaryPersistencePort,
            RunTerminalProjectionService runTerminalProjectionService,
            BackendInstanceIdentity backendInstanceIdentity) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                modelCatalogService,
                userProcessAssignmentService,
                workspacePathResolver,
                snapshotService,
                runSessionScopeRepository,
                runSessionScopeRuntimeCache,
                runActivityStateStore,
                conversationContextResolver,
                null,
                Objects.requireNonNull(runRuntimeStore, "runRuntimeStore must not be null"));
        this.runStorageModeSelector = Objects.requireNonNull(runStorageModeSelector, "runStorageModeSelector must not be null");
        this.runSummaryPersistencePort = Objects.requireNonNull(runSummaryPersistencePort, "runSummaryPersistencePort must not be null");
        this.runTerminalProjectionService = Objects.requireNonNull(
                runTerminalProjectionService, "runTerminalProjectionService must not be null");
        this.backendInstanceIdentity = Objects.requireNonNull(backendInstanceIdentity, "backendInstanceIdentity must not be null");
        this.ownerLeaseSupervisor = null;
        this.runtimeLossScheduler = null;
    }

    /** 完整接入上下文、标题监听、Redis Run 数据面和终态摘要投影。 */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            ConversationRunContextResolver conversationContextResolver,
            RunSessionTitleWatchService sessionTitleWatchService,
            RunRuntimeStore runRuntimeStore,
            RunStorageModeSelector runStorageModeSelector,
            RunSummaryPersistencePort runSummaryPersistencePort,
            RunTerminalProjectionService runTerminalProjectionService,
            BackendInstanceIdentity backendInstanceIdentity) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                modelCatalogService,
                userProcessAssignmentService,
                workspacePathResolver,
                snapshotService,
                runSessionScopeRepository,
                runSessionScopeRuntimeCache,
                runActivityStateStore,
                conversationContextResolver,
                sessionTitleWatchService,
                Objects.requireNonNull(runRuntimeStore, "runRuntimeStore must not be null"));
        this.runStorageModeSelector = Objects.requireNonNull(runStorageModeSelector, "runStorageModeSelector must not be null");
        this.runSummaryPersistencePort = Objects.requireNonNull(runSummaryPersistencePort, "runSummaryPersistencePort must not be null");
        this.runTerminalProjectionService = Objects.requireNonNull(
                runTerminalProjectionService, "runTerminalProjectionService must not be null");
        this.backendInstanceIdentity = Objects.requireNonNull(backendInstanceIdentity, "backendInstanceIdentity must not be null");
        this.ownerLeaseSupervisor = null;
        this.runtimeLossScheduler = null;
    }

    /** 兼容未注入标题监听的 owner lease 手工装配。 */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            ConversationRunContextResolver conversationContextResolver,
            RunRuntimeStore runRuntimeStore,
            RunStorageModeSelector runStorageModeSelector,
            RunSummaryPersistencePort runSummaryPersistencePort,
            RunTerminalProjectionService runTerminalProjectionService,
            BackendInstanceIdentity backendInstanceIdentity,
            RunOwnerLeaseSupervisor ownerLeaseSupervisor) {
        this(
                workspaceRepository, sessionRepository, runRepository, sessionMessageRepository,
                executionNodeRepository, routingDecisionRepository, runEventAppender, agentRuntimeRegistry,
                agentSessionBindingRepository, runEventLiveBus, runEventPersistencePolicy, modelCatalogService,
                userProcessAssignmentService, workspacePathResolver, snapshotService, runSessionScopeRepository,
                runSessionScopeRuntimeCache, runActivityStateStore, conversationContextResolver, null, runRuntimeStore,
                runStorageModeSelector, runSummaryPersistencePort, runTerminalProjectionService, backendInstanceIdentity);
        this.ownerLeaseSupervisor = Objects.requireNonNull(ownerLeaseSupervisor, "ownerLeaseSupervisor must not be null");
    }

    /** Spring 生产构造器同时接入原生标题监听与 owner lease 监督器。 */
    @Autowired
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy,
            ModelCatalogApplicationService modelCatalogService,
            UserOpencodeProcessAssignmentService userProcessAssignmentService,
            ManagedWorkspacePathResolver workspacePathResolver,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionScopeRepository runSessionScopeRepository,
            RunSessionScopeRuntimeCache runSessionScopeRuntimeCache,
            RunActivityStateStore runActivityStateStore,
            ConversationRunContextResolver conversationContextResolver,
            RunSessionTitleWatchService sessionTitleWatchService,
            RunRuntimeStore runRuntimeStore,
            RunStorageModeSelector runStorageModeSelector,
            RunSummaryPersistencePort runSummaryPersistencePort,
            RunTerminalProjectionService runTerminalProjectionService,
            BackendInstanceIdentity backendInstanceIdentity,
            RunOwnerLeaseSupervisor ownerLeaseSupervisor) {
        this(
                workspaceRepository, sessionRepository, runRepository, sessionMessageRepository,
                executionNodeRepository, routingDecisionRepository, runEventAppender, agentRuntimeRegistry,
                agentSessionBindingRepository, runEventLiveBus, runEventPersistencePolicy, modelCatalogService,
                userProcessAssignmentService, workspacePathResolver, snapshotService, runSessionScopeRepository,
                runSessionScopeRuntimeCache, runActivityStateStore, conversationContextResolver, sessionTitleWatchService,
                runRuntimeStore, runStorageModeSelector, runSummaryPersistencePort, runTerminalProjectionService,
                backendInstanceIdentity);
        this.ownerLeaseSupervisor = Objects.requireNonNull(ownerLeaseSupervisor, "ownerLeaseSupervisor must not be null");
    }

    /** Redis 运行态持续丢失收敛采用方法注入，避免继续扩张已有兼容构造器参数。 */
    @Autowired
    void configureRuntimeLossScheduler(RunRuntimeLossConvergenceScheduler runtimeLossScheduler) {
        this.runtimeLossScheduler = Objects.requireNonNull(
                runtimeLossScheduler, "runtimeLossScheduler must not be null");
    }

    /** 公共 Agent/Skill 发布期间的硬闸门放在 Run 应用入口，不能只依赖前端按钮状态。 */
    @Autowired
    void configurePublicConfigMessageGate(PublicAgentConfigMessageGate messageGate) {
        this.publicConfigMessageGate = Objects.requireNonNull(messageGate, "messageGate must not be null");
    }

    /** Run 启动前读取与 dispose 共用的用户级闸门，避免释放全部 workspace 实例时产生新任务竞态。 */
    @Autowired(required = false)
    void configureUserRuntimeDisposeCoordinator(UserRuntimeDisposeCoordinator coordinator) {
        this.userRuntimeDisposeCoordinator = Objects.requireNonNull(
                coordinator, "coordinator must not be null");
    }

    /** 无上下文 token 的兼容入口也必须执行当前工作区资格校验。 */
    @Autowired
    void configureWorkspaceAccessAuthorizer(ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer) {
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer, "workspaceAccessAuthorizer must not be null");
    }

    /** 旧会话可能缺少创建人字段，生产环境按既有历史归因规则确认调用用户。 */
    @Autowired
    void configureSessionHistoryRepository(SessionHistoryRepository sessionHistoryRepository) {
        this.sessionHistoryRepository = Objects.requireNonNull(
                sessionHistoryRepository, "sessionHistoryRepository must not be null");
    }

    /** 读取 MyBatis 已随 Run 锚点原子冻结的目标，避免 Redis 丢失后误按服务端记录恢复。 */
    @Autowired(required = false)
    void configureRunRuntimeTargetRepository(RunRuntimeTargetRepository repository) {
        this.runRuntimeTargetRepository = Objects.requireNonNull(repository);
    }

    /** capability 响应只用于界面展示；Run 入口仍需拒绝伪造的本地附件 part。 */
    @Autowired(required = false)
    void configureLocalRuntimeCapabilityGuard(LocalRuntimeCapabilityGuard guard) {
        this.localRuntimeCapabilityGuard = Objects.requireNonNull(guard);
    }

    /** 关系型目标缺失代表旧服务端 Run；本地 Run 从不允许用该兼容值覆盖显式记录。 */
    public RunRuntimeTarget runtimeTarget(RunId runId) {
        return runRuntimeTargetRepository == null
                ? new RunRuntimeTarget(runId, RuntimeKind.SERVER_PROCESS, null)
                : runRuntimeTargetRepository.findByRunId(runId)
                        .orElseGet(() -> new RunRuntimeTarget(runId, RuntimeKind.SERVER_PROCESS, null));
    }

    /**
     * 创建兼容旧装配的服务实例，不显式传入快照服务时内部构造默认实现。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            RunEventLiveBus runEventLiveBus,
            RunEventPersistencePolicy runEventPersistencePolicy) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                runEventLiveBus,
                runEventPersistencePolicy,
                null,
                null,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                null,
                null);
    }

    /**
     * 创建兼容旧测试的服务实例，使用默认实时事件总线和默认事件持久化策略。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository,
            UserOpencodeProcessAssignmentService userProcessAssignmentService) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                new RunEventLiveBus(),
                new RunEventPersistencePolicy(),
                null,
                userProcessAssignmentService,
                ManagedWorkspacePathResolver.legacyOnly(),
                null,
                null,
                null);
    }

    /**
     * 创建兼容旧测试的服务实例，使用默认实时事件总线和默认事件持久化策略。
     */
    public RunApplicationService(
            WorkspaceRepository workspaceRepository,
            com.enterprise.testagent.domain.session.SessionRepository sessionRepository,
            RunRepository runRepository,
            SessionMessageRepository sessionMessageRepository,
            ExecutionNodeRepository executionNodeRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RunEventAppender runEventAppender,
            AgentRuntimeRegistry agentRuntimeRegistry,
            AgentSessionBindingRepository agentSessionBindingRepository) {
        this(
                workspaceRepository,
                sessionRepository,
                runRepository,
                sessionMessageRepository,
                executionNodeRepository,
                routingDecisionRepository,
                runEventAppender,
                agentRuntimeRegistry,
                agentSessionBindingRepository,
                null);
    }

    /**
     * 以纯文本 prompt 启动 Run，兼容早期只传字符串的调用方。
     */
    public Run startRun(SessionId sessionId, String prompt, String traceId) {
        return startRun(agentRuntimeRegistry.defaultAgentId(), StartRunInput.ofPrompt(sessionId, prompt), traceId);
    }

    /**
     * 按指定 agent 启动纯文本 Run，agentId 来源于 URL path。
     */
    public Run startRun(String agentId, SessionId sessionId, String prompt, String traceId) {
        return startRun(agentId, StartRunInput.ofPrompt(sessionId, prompt), traceId);
    }

    /**
     * 启动一次平台 Run：创建本地 Run/消息、路由 agent 节点、启动远端 prompt 并订阅事件流。
     */
    public Run startRun(StartRunInput input, String traceId) {
        return startRun(agentRuntimeRegistry.defaultAgentId(), input, traceId);
    }

    /**
     * 启动一次指定 agent 的平台 Run，所有 agent 复用同一 RunEvent 和错误处理链路。
     */
    public Run startRun(String agentId, StartRunInput input, String traceId) {
        return startRunInternal(null, agentId, input, traceId, RunSource.manual());
    }

    /**
     * 以当前登录用户启动默认 opencode Run；HTTP 入口使用该方法强制执行用户进程防护。
     */
    public Run startRun(UserId userId, StartRunInput input, String traceId) {
        return startRun(userId, agentRuntimeRegistry.defaultAgentId(), input, traceId);
    }

    /**
     * 以当前登录用户启动指定 agent Run；opencode 会先解析用户专属进程。
     */
    public Run startRun(UserId userId, String agentId, StartRunInput input, String traceId) {
        return startRunInternal(
                Objects.requireNonNull(userId, "userId must not be null"), agentId, input, traceId, RunSource.manual());
    }

    /** 使用显式双身份启动 Run；普通 HTTP 入口也可借此补齐统一认证号快照。 */
    public Run startRun(RunActorAttribution attribution, StartRunInput input, String traceId) {
        return startRun(attribution, agentRuntimeRegistry.defaultAgentId(), input, traceId);
    }

    /** 使用显式双身份启动指定 agent Run。 */
    public Run startRun(
            RunActorAttribution attribution,
            String agentId,
            StartRunInput input,
            String traceId) {
        Objects.requireNonNull(attribution, "attribution must not be null");
        return startRunInternal(
                Objects.requireNonNull(attribution.executionOwnerUserId()), agentId, input, traceId,
                RunSource.manual(), null, attribution);
    }

    /** 分享会话发送入口；认证 actor 保持不变，OpenCode 执行身份显式使用会话所属人。 */
    public Run startRun(
            com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext context,
            StartRunInput input,
            String traceId) {
        return startRun(context, agentRuntimeRegistry.defaultAgentId(), input, traceId);
    }

    /** 指定 agent 的分享会话发送入口。 */
    public Run startRun(
            com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext context,
            String agentId,
            StartRunInput input,
            String traceId) {
        Objects.requireNonNull(context, "context must not be null");
        context.requireChat();
        context.requireSession(input.sessionId());
        return startRunInternal(
                context.executionOwnerUserId(), agentId, input, traceId, RunSource.manual(), null,
                RunActorAttribution.from(context));
    }

    /** 调度器内部入口；来源由服务端固定，浏览器无法伪造。 */
    public Run startScheduledRun(UserId userId, StartRunInput input, String sourceRefId, String traceId) {
        return startScheduledRun(userId, input, new ScheduledRunMetadata(sourceRefId, null), traceId);
    }

    /**
     * 启动带内部生命周期元数据的普通 Scheduled Run；回调发生在 Run 锚点和异步 prompt 已受理之后。
     */
    public Run startScheduledRun(
            UserId userId,
            StartRunInput input,
            ScheduledRunMetadata metadata,
            String traceId) {
        return startScheduledRun(RunActorAttribution.direct(userId), input, metadata, traceId);
    }

    /** 调度任务沿用创建时的真实创建人快照，但所有 OpenCode 副作用仍使用任务所属人。 */
    public Run startScheduledRun(
            RunActorAttribution attribution,
            StartRunInput input,
            ScheduledRunMetadata metadata,
            String traceId) {
        Objects.requireNonNull(attribution, "attribution must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Run run;
        try {
            run = startRunInternal(
                    Objects.requireNonNull(attribution.executionOwnerUserId(), "userId must not be null"),
                    agentRuntimeRegistry.defaultAgentId(),
                    input,
                    traceId,
                    RunSource.scheduled(metadata.sourceRefId(), metadata.dispatchAttemptId()),
                    null,
                    attribution);
        } catch (RuntimeException failure) {
            notifyScheduledRunRejected(metadata, failure, traceId);
            throw failure;
        }
        // 受理回调异常不触发 rejected 回调；锚点已经存在，补偿任务会按幂等键修复业务状态。
        for (ScheduledRunLifecycleObserver observer : scheduledRunLifecycleObservers) {
            observer.onAccepted(metadata, run);
        }
        return run;
    }

    private void notifyScheduledRunRejected(
            ScheduledRunMetadata metadata,
            RuntimeException failure,
            String traceId) {
        for (ScheduledRunLifecycleObserver observer : scheduledRunLifecycleObservers) {
            try {
                observer.onRejected(metadata, failure);
            } catch (RuntimeException observerFailure) {
                LOGGER.warn(
                        "Scheduled Run 拒绝回调失败，sourceRefId={}, traceId={}, exceptionType={}",
                        metadata.sourceRefId(), traceId, observerFailure.getClass().getSimpleName());
            }
        }
    }

    private Run startRunInternal(
            UserId userId,
            String agentId,
            StartRunInput input,
            String traceId,
            RunSource source) {
        return startRunInternal(
                userId, agentId, input, traceId, source, null, RunActorAttribution.direct(userId));
    }

    /**
     * 执行已经由重发状态机预留的 PENDING Run；固定 runId/messageId 保证恢复时不会重复投递。
     */
    public Run startResendRun(
            UserId userId,
            StartRunInput input,
            RunId replacementRunId,
            ConversationSourceType sourceType,
            String sourceRefId,
            String traceId) {
        Objects.requireNonNull(replacementRunId, "replacementRunId must not be null");
        return startRunInternal(
                userId,
                agentRuntimeRegistry.defaultAgentId(),
                input,
                traceId,
                new RunSource(sourceType, sourceRefId, null),
                replacementRunId,
                RunActorAttribution.direct(userId));
    }

    private Run startRunInternal(
            UserId userId,
            String agentId,
            StartRunInput input,
            String traceId,
            RunSource source,
            RunId reservedRunId,
            RunActorAttribution actorAttribution) {
        RunActorAttribution attribution = actorAttribution == null
                ? RunActorAttribution.direct(userId)
                : actorAttribution;
        if (reservedRunId == null
                && source.type() == ConversationSourceType.MANUAL
                && nightExecutionLockGuard != null) {
            nightExecutionLockGuard.requireUnlocked(input.sessionId());
        }
        boolean protectedSelection = ProtectedAgentSelection.isProtected(input.agent());
        String requestedRuntimeAgentId = protectedSelection
                ? ProtectedOpencodeAgentRuntime.AGENT_ID
                : agentId;
        String resolvedAgentId = agentRuntimeRegistry.normalize(requestedRuntimeAgentId);
        if (AgentRuntimeRegistry.DEFAULT_AGENT_ID.equals(resolvedAgentId)) {
            publicConfigMessageGate.requireAllowed(userId);
        }
        if (userRuntimeDisposeCoordinator != null) {
            userRuntimeDisposeCoordinator.requireNotDisposing(userId, traceId);
        }
        // 网页 contextToken 仍按会话原本的本地 opencode 目标签发；受保护运行只复用其身份、Workspace
        // 和连接 generation 校验，绝不复用其中的本地执行节点或 agent binding。
        String contextAgentId = protectedSelection
                ? agentRuntimeRegistry.defaultAgentId()
                : resolvedAgentId;
        ConversationRunContext conversationContext = resolveConversationContext(userId, contextAgentId, input, traceId);
        LOGGER.info("Run starting, userId={}, agentId={}, sessionId={}, traceId={}",
                userId != null ? userId.value() : "anonymous",
                resolvedAgentId,
                input.sessionId().value(),
                traceId);
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Instant now = Instant.now();
        SessionId sessionId = input.sessionId();
        String prompt = input.effectivePrompt();
        Session session = conversationContext == null
                ? findSession(sessionId)
                : conversationContext.sessionSnapshot();
        if (conversationContext == null) {
            requireAuthenticatedLegacyRunAccess(userId, session);
        }
        UserOpencodeProcessAssignment userProcessAssignment = protectedSelection
                ? null
                : conversationContext == null
                ? resolveUserProcessAssignment(userId, resolvedAgentId, traceId)
                : null;
        // 新上下文已经缓存 binding 快照，首次远端会话可由“无 binding”判断，避免为标题监听额外查询消息表。
        boolean firstUserRun = protectedSelection
                ? false
                : conversationContext == null
                ? isFirstUserRun(sessionId)
                : conversationContext.bindingSnapshot() == null;
        if (sessionTitleWatchService != null) {
            sessionTitleWatchService.closeTitleWaitForNextRun(sessionId, traceId);
        }
        Workspace workspace = conversationContext == null
                ? findWorkspace(session.workspaceId())
                : conversationContext.workspaceSnapshot();
        if (localRuntimeCapabilityGuard != null
                && input.parts().stream().anyMatch(this::isWorkspaceAttachment)) {
            localRuntimeCapabilityGuard.requireWorkspaceSupported(
                    workspace.workspaceId(),
                    "attachments",
                    "本地 OpenCode 工作区首版不开放聊天附件");
        }
        ModelSelection modelSelection = resolveModelSelection(input.model());
        String opencodeAgent = protectedSelection ? DEFAULT_OPENCODE_AGENT : resolveOpencodeAgent(input);
        Run pending = reservedRunId == null
                ? new Run(
                        new RunId(RuntimeIdGenerator.runId()),
                        session.sessionId(),
                        workspace.workspaceId(),
                        RunStatus.PENDING,
                        now,
                        now,
                        traceId)
                : requireReservedRun(reservedRunId, session, workspace);
        if (userId != null) {
            pending = pending.withSource(source.type(), source.refId(), userId);
        }
        // 重发状态机已经把实际操作人写入预留 Run；执行阶段仍以所属人调用 OpenCode，
        // 但不能用所属人覆盖该可信归因，否则分享成员重发后会被永久记成会话所属人。
        if (reservedRunId == null && attribution.actualSenderUserId() != null) {
            pending = pending.withMessageSender(
                    attribution.actualSenderUserId(),
                    attribution.actualSenderUnifiedAuthId(),
                    attribution.sentBySharedUser());
        }
        // 预留重发 Run 的发送人已经由重发状态机确认；用户消息投影也必须复用同一归因。
        // 执行入口收到的 actorAttribution 仍是 OpenCode 所属人，不能再把 session_messages 写回所属人。
        RunActorAttribution messageAttribution = reservedRunId != null && pending.messageSenderUserId() != null
                ? new RunActorAttribution(
                        attribution.executionOwnerUserId(),
                        pending.messageSenderUserId(),
                        pending.messageSenderUnifiedAuthId(),
                        pending.messageSentBySharedUser())
                : attribution;
        pending = pending.withRuntimeSelection(
                protectedSelection ? resolvedAgentId : opencodeAgent,
                firstText(modelSelection.modelId(), input.model()));
        ProtectedAgentExecutionService.ExecutionContext protectedContext = null;
        if (protectedSelection) {
            if (userId == null || protectedAgentExecutionService == null) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "受保护 Agent 运行服务未装配");
            }
            if (input.command() != null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "受保护 Agent 不支持原生命令模式");
            }
            protectedContext = protectedAgentExecutionService.prepare(userId, pending, session, workspace, input.agent());
        }
        RunStorageMode storageMode = runStorageModeSelector == null
                ? RunStorageMode.LEGACY_FULL
                : runStorageModeSelector.select(userId, input, conversationContext);
        // 受保护修订审计和文件授权当前固定走关系型 durable 事件，不允许运行中切换到摘要模式。
        if (protectedSelection) {
            storageMode = RunStorageMode.LEGACY_FULL;
        }
        // 预留 Run 已有关系型 PENDING 锚点；当前版本沿 legacy 明细链启动，避免重复插入摘要锚点。
        if (reservedRunId != null) {
            storageMode = RunStorageMode.LEGACY_FULL;
        }
        Optional<RunPersistenceAnchor> existingScheduledAnchor = findExistingScheduledAnchor(
                source, input, pending);
        if (existingScheduledAnchor.isPresent()) {
            RunPersistenceAnchor existing = existingScheduledAnchor.orElseThrow();
            if (legacyScheduledAnchorAccepted(existing)) {
                return anchorRun(existing);
            }
            // rollout 模式可能在重试之间变化；未完成的 legacy 锚点必须沿原模式和同一 runId 恢复。
            storageMode = RunStorageMode.LEGACY_FULL;
        }
        if (storageMode == RunStorageMode.REDIS_SUMMARY) {
            return startRedisSummaryRun(
                    userId,
                    resolvedAgentId,
                    input,
                    traceId,
                    conversationContext,
                    runtime,
                    session,
                    workspace,
                    modelSelection,
                    opencodeAgent,
                    pending,
                    prompt,
                    now);
        }
        // legacy 也必须具备服务端稳定 dispatch ID，供远端 user、平台消息和 Run scope 建立同一因果锚点。
        String dispatchMessageId = input.messageId() == null
                ? runtime.createDispatchMessageId()
                : input.messageId();
        LegacyScheduledAnchorClaim scheduledClaim = claimLegacyScheduledAnchor(
                source, userId, input, pending, dispatchMessageId, existingScheduledAnchor, now);
        if (scheduledClaim.accepted()) {
            return scheduledClaim.run();
        }
        pending = scheduledClaim.run();
        dispatchMessageId = scheduledClaim.dispatchMessageId();
        if (!scheduledClaim.managed()) {
            runRepository.save(pending);
        }
        boolean userMessageCreated;
        if (reservedRunId != null || scheduledClaim.managed()) {
            userMessageCreated = ensureUserMessage(
                    session.sessionId(), pending.runId(), prompt, input.parts(), userId,
                    dispatchMessageId, traceId, now, source, messageAttribution);
        } else {
            saveUserMessage(
                    session.sessionId(), pending.runId(), prompt, input.parts(), userId,
                    dispatchMessageId, traceId, now, source, messageAttribution);
            userMessageCreated = true;
        }
        if (userMessageCreated) {
            Map<String, Object> createdPayload = new LinkedHashMap<>();
            createdPayload.put("status", RunStatus.PENDING.name());
            if (protectedContext != null) {
                createdPayload.put("protectedAgent", protectedContext.auditPayload());
            }
            append(pending.runId(), RunEventType.RUN_CREATED, traceId, now,
                    Map.copyOf(createdPayload), storageMode);
        }

        try {
            acquireAutomationReferenceRunLeases(pending, workspace, userId, traceId);
            AgentRoutingTarget target = protectedSelection
                    ? resolveServerAgentTarget(resolvedAgentId, session, pending.runId(), now, traceId)
                    : userProcessAssignment == null
                    ? (conversationContext == null
                            ? resolveAgentTarget(resolvedAgentId, session, pending.runId(), now, traceId)
                            : conversationContextTarget(conversationContext, pending.runId(), now, traceId))
                    : userProcessTarget(userProcessAssignment, pending.runId(), now, traceId);
            LOGGER.debug("Run routed, runId={}, nodeId={}, reason={}, traceId={}",
                    pending.runId().value(),
                    target.node().executionNodeId().value(),
                    target.decision().reason().name(),
                    traceId);
            routingDecisionRepository.save(target.decision());
            if (protectedContext != null) {
                protectedAgentExecutionService.configureMcp(protectedContext, runtime, target.node(), traceId);
            }
            AgentSessionBinding binding = !protectedSelection
                            && conversationContext != null
                            && conversationContext.bindingSnapshot() != null
                    ? conversationContext.bindingSnapshot()
                    : runtimeTargetResolver.ensureAgentSession(
                            resolvedAgentId,
                            runtime,
                            session,
                            workspace,
                            target.node(),
                            protectedContext == null
                                    ? workspaceRootPath(workspace)
                                    : protectedContext.serverDirectory(),
                            traceId);
            RunSessionTitleWatchRegistry.TitleWatchToken titleWatchToken = registerFirstRunTitleWatch(
                    resolvedAgentId,
                    firstUserRun,
                    session,
                    pending,
                    prompt,
                    runtime,
                    target.node(),
                    workspace,
                    binding.remoteSessionId());
            Run running;
            if (pending.status() == RunStatus.PENDING) {
                running = runRepository.save(pending.start(Instant.now()));
                append(running.runId(), RunEventType.RUN_STARTED, traceId, Instant.now(),
                        Map.of("status", RunStatus.RUNNING.name()), storageMode);
            } else if (pending.status() == RunStatus.RUNNING) {
                // 锚点可能在状态已转 RUNNING、handoff 标记尚未写入时崩溃；继续复用同一 Run。
                running = pending;
            } else {
                throw new RunOwnershipLostException("legacy Scheduled Run 已不处于可恢复状态");
            }
            recordRootSessionScope(
                    resolvedAgentId,
                    running,
                    binding.remoteSessionId(),
                    dispatchMessageId,
                    traceId,
                    storageMode);
            LOGGER.info("Run started, runId={}, nodeId={}, remoteSessionId={}, traceId={}",
                    running.runId().value(),
                    target.node().executionNodeId().value(),
                    binding.remoteSessionId(),
                    traceId);
            // 先订阅事件再触发 prompt，避免 opencode 快速失败或快速返回时平台漏掉终态事件。
            subscribeAgentEvents(
                    resolvedAgentId,
                    runtime,
                    running,
                    binding.remoteSessionId(),
                    target.node(),
                    workspace,
                    storageMode,
                    traceId,
                    titleWatchToken);
            RunDispatchAcceptance recoveredDispatch = probeRecoveredLegacyScheduledDispatch(
                    scheduledClaim,
                    running,
                    resolvedAgentId,
                    binding.remoteSessionId(),
                    dispatchMessageId,
                    target.node(),
                    traceId);
            if (recoveredDispatch == RunDispatchAcceptance.ACCEPTED) {
                // 远端已存在稳定 user message 时只恢复订阅和 durable handoff，绝不能再次进入执行 loop。
                renewLegacyScheduledDispatchClaim(scheduledClaim, source, running.runId());
                markLegacyScheduledDispatchAccepted(scheduledClaim, source, running.runId());
                return running;
            }
            if (recoveredDispatch == RunDispatchAcceptance.UNKNOWN) {
                // 查询失败或未穷尽时宁可等待下一次补偿，也不能把“不确定”当作未接收而重复发送。
                throw new RunOwnershipLostException("legacy Scheduled Run 远端接收状态暂不可确认");
            }
            List<AgentPromptPart> promptParts = toAgentPromptParts(input, workspace);
            String contributedSystemPrompt = systemPrompt(running, prompt, input.command() != null, traceId);
            String effectiveSystemPrompt = protectedContext == null
                    ? contributedSystemPrompt
                    : combineSystemPrompt(protectedContext.systemPrompt(), contributedSystemPrompt);
            AgentStartRunCommand command = new AgentStartRunCommand(
                    target.node(),
                    binding.remoteSessionId(),
                    protectedContext == null ? workspaceRootPath(workspace) : protectedContext.serverDirectory(),
                    null,
                    prompt,
                    promptParts,
                    dispatchMessageId,
                    opencodeAgent,
                    effectiveSystemPrompt,
                    modelSelection.providerId(),
                    modelSelection.modelId(),
                    input.variant(),
                    protectedContext == null ? Map.of() : protectedContext.toolPermissions(),
                    input.command(),
                    commandArguments(input, promptParts),
                    traceId);
            renewLegacyScheduledDispatchClaim(scheduledClaim, source, running.runId());
            // prompt_async/command 的 HTTP 结果不是 Run 终态，后台调用异常需给 root 终态留出到达窗口。
            if (reservedRunId != null) {
                // 重发 started 事件要求原生替代消息已经受理，因此固定 Run 路径等待 HTTP 接收结果。
                var accepted = Mono.defer(() -> runtime.startRun(command)).block();
                if (accepted == null || !accepted.accepted()) {
                    throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "替代消息未被远端受理");
                }
            } else {
                Mono.defer(() -> runtime.startRun(command))
                        .subscribe(
                                ignored -> {
                                },
                                error -> failRunFromDispatch(
                                        resolvedAgentId, running, RunStorageMode.LEGACY_FULL, traceId, error));
            }
            markLegacyScheduledDispatchAccepted(scheduledClaim, source, running.runId());
            return running;
        } catch (PlatformException exception) {
            PlatformException safeException = sanitizeExperienceException(workspace, exception);
            renewLegacyScheduledDispatchClaim(scheduledClaim, source, pending.runId());
            LOGGER.error("Run failed to start, runId={}, errorCode={}, traceId={}",
                    pending.runId().value(),
                    safeException.errorCode().name(),
                    traceId);
            Run failed = runRepository.save(pending.fail(Instant.now()));
            append(failed.runId(), RunEventType.RUN_FAILED, traceId, Instant.now(),
                    Map.of("errorCode", safeException.errorCode().name()), storageMode);
            snapshotService.persistRunSnapshot(resolvedAgentId, failed, traceId);
            runSessionScopeRouter.finishRun(failed.runId());
            notifyRootRunTerminalObservers(failed.runId(), failed.status(), traceId);
            markLegacyScheduledDispatchAccepted(scheduledClaim, source, failed.runId());
            throw safeException;
        }
    }

    private Run requireReservedRun(RunId runId, Session session, Workspace workspace) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "预留替代 Run 不存在"));
        if (run.status() != RunStatus.PENDING
                || !run.sessionId().equals(session.sessionId())
                || !run.workspaceId().equals(workspace.workspaceId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "预留替代 Run 状态或会话边界不匹配");
        }
        return run;
    }

    /**
     * 新模式启动链路：Redis 初始化和单条无原文锚点 INSERT 全部成功后才允许创建远端 Session/发送 prompt。
     */
    private Run startRedisSummaryRun(
            UserId userId,
            String resolvedAgentId,
            StartRunInput input,
            String traceId,
            ConversationRunContext context,
            AgentRuntime runtime,
            Session session,
            Workspace workspace,
            ModelSelection modelSelection,
            String opencodeAgent,
            Run pending,
            String prompt,
            Instant now) {
        requireRedisSummaryDependencies(context);
        Optional<Run> existing = redisSummaryRetry(input, context, pending);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        if (!runRuntimeStore.claimClientRequest(session.sessionId(), input.clientRequestId(), pending.runId())) {
            return redisSummaryRetry(input, context, pending)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                            "clientRequestId 已被占用但 Run 运行态尚不可见"));
        }

        // 恢复探针依赖该 ID 判断本 Run 是否已被 OpenCode 接收，必须由服务端为每个新 Run 唯一生成。
        String dispatchMessageId = runtime.createDispatchMessageId();
        AgentRoutingTarget target = conversationContextTarget(context, pending.runId(), now, traceId);
        RunRuntimeManifest manifest = new RunRuntimeManifest(
                pending.runId(),
                RunStorageMode.REDIS_SUMMARY,
                userId,
                pending.messageSenderUserId(),
                pending.messageSenderUnifiedAuthId(),
                pending.messageSentBySharedUser(),
                session.sessionId(),
                workspace.workspaceId(),
                resolvedAgentId,
                input.clientRequestId(),
                dispatchMessageId,
                context.linuxServerId(),
                backendInstanceIdentity.backendProcessId(),
                target.node().executionNodeId().value(),
                context.processId(),
                context.remoteSessionId(),
                RunStatus.PENDING,
                0L,
                0L,
                1L,
                0L,
                false,
                0L,
                0L,
                null,
                null,
                null,
                now.plus(RunRuntimeStore.ACTIVE_TTL),
                now,
                now,
                context.runtimeKind(),
                context.localClientInstanceId(),
                context.connectionGeneration());
        boolean anchorInserted = false;
        try {
            runRuntimeStore.initialize(
                    manifest,
                    new RunRuntimeInput(
                            pending.runId(),
                            prompt,
                            runtimeInputParts(input),
                            dispatchMessageId,
                            now,
                            workspaceRootPath(workspace),
                            target.node().baseUrl()));
            Instant startedAt = Instant.now();
            Run running = pending.start(startedAt);
            RunRuntimeManifest runningManifest = runRuntimeStore.findManifest(running.runId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run manifest 不存在"));
            boolean inserted = runSummaryPersistencePort.insertAnchor(new RunPersistenceAnchor(
                    running.runId(),
                    running.sessionId(),
                    running.workspaceId(),
                    RunStatus.RUNNING,
                    RunStorageMode.REDIS_SUMMARY,
                    1L,
                    input.clientRequestId(),
                    context.linuxServerId(),
                    target.node().executionNodeId().value(),
                    context.processId(),
                    context.remoteSessionId(),
                    dispatchMessageId,
                    null,
                    null,
                    null,
                    RunSummaryIdentifiers.assistant(running.runId()),
                    traceId,
                    running.createdAt(),
                    running.updatedAt(),
                    runningManifest.detailsExpiresAt(),
                    running.sourceType(),
                    running.sourceRefId(),
                    running.triggeredByUserId(),
                    resolvedAgentId,
                    firstText(modelSelection.modelId(), input.model()),
                    running.messageSenderUserId(),
                    running.messageSenderUnifiedAuthId(),
                    running.messageSentBySharedUser(),
                    context.runtimeKind(),
                    context.localClientInstanceId()));
            if (!inserted) {
                // 锚点幂等冲突意味着本轮绝不会派发，必须清掉刚初始化的 Redis active/history 详情。
                runRuntimeStore.discardBeforeDispatch(running.runId());
                RunPersistenceAnchor existingAnchor = runSummaryPersistencePort
                        .findBySessionAndClientRequestId(session.sessionId(), input.clientRequestId())
                        .orElseThrow(() -> new PlatformException(
                                ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                                "Run 幂等锚点冲突但既有记录不可见"));
                requireSameScheduledSource(existingAnchor, pending);
                // 短 claim 窗口内发生并发时，新 Run 的 compare-delete 可能刚移除临时映射；
                // 用数据库唯一锚点的稳定 runId 重新确认，避免后续重试反复初始化再撞唯一索引。
                runRuntimeStore.confirmClientRequest(
                        session.sessionId(), input.clientRequestId(), existingAnchor.runId());
                return anchorRun(existingAnchor);
            }
            anchorInserted = true;

            // 关系库唯一活动会话锁与 Redis 原子占用都成功后，才允许发布任何可见事件。
            append(pending.runId(), RunEventType.RUN_CREATED, traceId, now,
                    Map.of(
                            "status", RunStatus.PENDING.name(),
                            "storageMode", RunStorageMode.REDIS_SUMMARY.name(),
                            "clientRequestId", input.clientRequestId(),
                            "assistantSummaryMessageId", RunSummaryIdentifiers.assistant(pending.runId()).value()),
                    RunStorageMode.REDIS_SUMMARY);
            append(running.runId(), RunEventType.RUN_STARTED, traceId, startedAt,
                    Map.of("status", RunStatus.RUNNING.name()), RunStorageMode.REDIS_SUMMARY);

            RunOwnerLeaseSupervisor.OwnershipHandle ownership = null;
            boolean subscriptionHandedOff = false;
            String remoteSessionIdForConvergence = context.remoteSessionId();
            try {
                acquireAutomationReferenceRunLeases(running, workspace, userId, traceId);
                if (!runRuntimeStore.confirmClientRequest(
                        session.sessionId(), input.clientRequestId(), running.runId())) {
                    LOGGER.warn(
                            "Run 锚点已写入但 clientRequestId 映射正由并发请求收敛，runId={}, traceId={}",
                            running.runId().value(), traceId);
                }
                ownership = claimInitialOwnership(running.runId());
                RunOwnerLeaseSupervisor.OwnershipHandle claimedOwnership = ownership;
                requireOwnedIfPresent(claimedOwnership);
                boolean initialBinding = context.bindingSnapshot() == null;
                AgentSessionBinding binding = !initialBinding
                        ? context.bindingSnapshot()
                        : createInitialAgentSession(
                                resolvedAgentId,
                                runtime,
                                session,
                                workspace,
                                target.node(),
                                traceId,
                                claimedOwnership);
                remoteSessionIdForConvergence = binding.remoteSessionId();
                requireOwnedIfPresent(claimedOwnership);
                RunOwnerLease claimedLease = ownerLeaseIfPresent(claimedOwnership);
                if (claimedLease == null) {
                    runRuntimeStore.bindRemoteSession(running.runId(), binding.remoteSessionId());
                } else {
                    runRuntimeStore.bindRemoteSession(running.runId(), binding.remoteSessionId(), claimedLease);
                }
                requireOwnedIfPresent(claimedOwnership);
                if (initialBinding) {
                    // Redis 已记录可恢复的 remoteSessionId 后，再次校验 fencing 并执行首次控制面绑定写入。
                    runSummaryPersistencePort.persistInitialAgentBinding(binding);
                }
                requireOwnedIfPresent(claimedOwnership);
                RunSessionTitleWatchRegistry.TitleWatchToken titleWatchToken = registerFirstRunTitleWatch(
                        resolvedAgentId,
                        initialBinding,
                        session,
                        running,
                        prompt,
                        runtime,
                        target.node(),
                        workspace,
                        binding.remoteSessionId());
                recordRootSessionScope(
                        resolvedAgentId,
                        running,
                        binding.remoteSessionId(),
                        dispatchMessageId,
                        traceId,
                        RunStorageMode.REDIS_SUMMARY,
                        ownerLeaseIfPresent(claimedOwnership));
                requireOwnedIfPresent(claimedOwnership);
                subscribeAgentEvents(
                        resolvedAgentId,
                        runtime,
                        running,
                        binding.remoteSessionId(),
                        target.node(),
                        workspace,
                        RunStorageMode.REDIS_SUMMARY,
                        traceId,
                        titleWatchToken,
                        claimedOwnership,
                        dispatchMessageId);
                List<AgentPromptPart> promptParts = toAgentPromptParts(input, workspace);
                AgentStartRunCommand command = new AgentStartRunCommand(
                        target.node(),
                        binding.remoteSessionId(),
                        workspaceRootPath(workspace),
                        null,
                        prompt,
                        promptParts,
                        dispatchMessageId,
                        opencodeAgent,
                        systemPrompt(running, prompt, input.command() != null, traceId),
                        modelSelection.providerId(),
                        modelSelection.modelId(),
                        input.variant(),
                        input.command(),
                        commandArguments(input, promptParts),
                        traceId);
                Mono.defer(() -> {
                            requireOwnedIfPresent(claimedOwnership);
                            return runtime.startRun(command);
                        })
                        .subscribe(
                                ignored -> {
                                },
                                error -> failRunFromDispatchIfOwned(
                                        resolvedAgentId, running, RunStorageMode.REDIS_SUMMARY,
                                        traceId, error, claimedOwnership));
                subscriptionHandedOff = true;
                return running;
            } catch (RunOwnershipLostException exception) {
                // 旧 owner 失去 fencing 后不得把仍由新 owner 执行的 Run 误写为失败。
                throw new PlatformException(ErrorCode.CONFLICT, "Run owner lease 已转移");
            } catch (PlatformException exception) {
                PlatformException safeException = sanitizeExperienceException(workspace, exception);
                handleRedisSummaryStartupFailure(
                        resolvedAgentId,
                        runtime,
                        running,
                        remoteSessionIdForConvergence,
                        target.node(),
                        workspace,
                        dispatchMessageId,
                        traceId,
                        safeException.errorCode().name(),
                        safeException.getMessage(),
                        ownership,
                        exception);
                throw safeException;
            } catch (RuntimeException exception) {
                String safeMessage = safeWorkspaceErrorMessage(workspace, exception);
                handleRedisSummaryStartupFailure(
                        resolvedAgentId,
                        runtime,
                        running,
                        remoteSessionIdForConvergence,
                        target.node(),
                        workspace,
                        dispatchMessageId,
                        traceId,
                        "START_FAILED",
                        safeMessage,
                        ownership,
                        exception);
                if (ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspace.workspaceId())) {
                    throw new PlatformException(ErrorCode.INTERNAL_ERROR, "体验工作区运行启动失败");
                }
                throw exception;
            } finally {
                if (ownership != null && !subscriptionHandedOff) {
                    releaseOwnershipBestEffort(ownership, running.runId(), traceId);
                }
            }
        } catch (RuntimeException exception) {
            // 锚点写入前失败时绝不触发远端副作用；request claim 释放后允许客户端安全重试。
            if (!anchorInserted) {
                discardBeforeDispatchBestEffort(
                        pending.runId(), session.sessionId(), input.clientRequestId(), traceId);
            }
            throw exception;
        }
    }

    private void releaseOwnershipBestEffort(
            RunOwnerLeaseSupervisor.OwnershipHandle ownership,
            RunId runId,
            String traceId) {
        try {
            ownerLeaseSupervisor.release(ownership);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Run owner lease 释放失败，等待 TTL，runId={}, traceId={}, exceptionType={}",
                    runId.value(), traceId, exception.getClass().getSimpleName());
        }
    }

    private RunOwnerLeaseSupervisor.OwnershipHandle claimInitialOwnership(RunId runId) {
        if (ownerLeaseSupervisor == null) {
            return null;
        }
        com.enterprise.testagent.domain.run.RunOwnerLease claimed = runRuntimeStore
                .claimOwnerLease(runId, backendInstanceIdentity.backendProcessId())
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "Run 已由其它 Java 接管"));
        return ownerLeaseSupervisor.adopt(claimed)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "Run owner lease 已失效"));
    }

    private void requireOwnedIfPresent(RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        if (ownership != null) {
            ownerLeaseSupervisor.requireOwned(ownership);
        }
    }

    private RunOwnerLease ownerLeaseIfPresent(RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        return ownership == null ? null : ownership.lease();
    }

    private void failRunFromDispatchIfOwned(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        if (ownership != null) {
            try {
                ownerLeaseSupervisor.requireOwned(ownership);
            } catch (RunOwnershipLostException ignored) {
                return;
            }
        }
        scheduleRunFailureAfterTerminalGrace(
                agentId, run, storageMode, traceId, error, "dispatch_response", ownership);
    }

    private void discardBeforeDispatchBestEffort(
            RunId runId,
            SessionId sessionId,
            String clientRequestId,
            String traceId) {
        try {
            runRuntimeStore.discardBeforeDispatch(runId);
        } catch (RuntimeException cleanupError) {
            LOGGER.warn(
                    "清理未派发 Redis Run 详情失败，等待 TTL/后续读路径自清理，runId={}, traceId={}, exceptionType={}",
                    runId.value(), traceId, cleanupError.getClass().getSimpleName());
        }
        // initialize 失败时 manifest 可能尚不存在，保留 compare-delete 作为 client request 索引兜底。
        try {
            runRuntimeStore.releaseClientRequest(sessionId, clientRequestId, runId);
        } catch (RuntimeException cleanupError) {
            LOGGER.warn(
                    "释放未派发 Run clientRequest 索引失败，runId={}, traceId={}, exceptionType={}",
                    runId.value(), traceId, cleanupError.getClass().getSimpleName());
        }
    }

    private void requireRedisSummaryDependencies(ConversationRunContext context) {
        if (context == null
                || runRuntimeStore == null
                || runSummaryPersistencePort == null
                || runTerminalProjectionService == null
                || backendInstanceIdentity == null
                || ownerLeaseSupervisor == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Redis 摘要运行链路未完整配置");
        }
        if (!context.linuxServerId().equals(backendInstanceIdentity.linuxServerId())) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "会话上下文未路由到绑定服务器");
        }
    }

    private Optional<Run> redisSummaryRetry(
            StartRunInput input,
            ConversationRunContext context,
            Run pending) {
        Optional<RunId> runId = runRuntimeStore.findByClientRequest(input.sessionId(), input.clientRequestId());
        if (runId.isEmpty()) {
            return Optional.empty();
        }
        // Redis manifest 可能已经初始化但 PostgreSQL 锚点尚未写入；只以关系型唯一锚点确认幂等成功，
        // 禁止把 crash 窗口中的未派发 RUNNING manifest 直接返回给客户端。
        return runSummaryPersistencePort
                .findBySessionAndClientRequestId(context.sessionId(), input.clientRequestId())
                .map(anchor -> {
                    requireSameScheduledSource(anchor, pending);
                    return anchorRun(anchor);
                });
    }

    /** 先查询既有 Scheduled 锚点，rollout 模式变化时仍沿原 Run 模式恢复。 */
    private Optional<RunPersistenceAnchor> findExistingScheduledAnchor(
            RunSource source,
            StartRunInput input,
            Run pending) {
        if (!usesLegacyScheduledAnchor(source, input)) {
            return Optional.empty();
        }
        Optional<RunPersistenceAnchor> existing = runSummaryPersistencePort
                .findBySessionAndClientRequestId(pending.sessionId(), input.clientRequestId());
        existing.ifPresent(anchor -> requireSameScheduledSource(anchor, pending));
        return existing;
    }

    /**
     * legacy Scheduled Run 先写关系型唯一锚点；anchor-only 冲突必须按 attempt/lease 认领后续跑同一 Run。
     */
    private LegacyScheduledAnchorClaim claimLegacyScheduledAnchor(
            RunSource source,
            UserId userId,
            StartRunInput input,
            Run pending,
            String dispatchMessageId,
            Optional<RunPersistenceAnchor> preloaded,
            Instant now) {
        if (!usesLegacyScheduledAnchor(source, input)) {
            return new LegacyScheduledAnchorClaim(pending, dispatchMessageId, false, false, false);
        }
        RunPersistenceAnchor existing = preloaded.orElse(null);
        if (existing == null) {
            boolean inserted = runSummaryPersistencePort.insertAnchor(new RunPersistenceAnchor(
                    pending.runId(),
                    pending.sessionId(),
                    pending.workspaceId(),
                    pending.status(),
                    RunStorageMode.LEGACY_FULL,
                    0L,
                    input.clientRequestId(),
                    backendInstanceIdentity == null ? null : backendInstanceIdentity.linuxServerId(),
                    null,
                    null,
                    null,
                    dispatchMessageId,
                    source.dispatchAttemptId(),
                    now.plus(LEGACY_SCHEDULED_DISPATCH_LEASE),
                    null,
                    null,
                    pending.traceId(),
                    pending.createdAt(),
                    pending.updatedAt(),
                    null,
                    pending.sourceType(),
                    pending.sourceRefId(),
                    userId,
                    pending.agentId(),
                    pending.modelId(),
                    pending.messageSenderUserId(),
                    pending.messageSenderUnifiedAuthId(),
                    pending.messageSentBySharedUser()));
            if (inserted) {
                return new LegacyScheduledAnchorClaim(pending, dispatchMessageId, true, false, false);
            }
            existing = runSummaryPersistencePort
                    .findBySessionAndClientRequestId(pending.sessionId(), input.clientRequestId())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                            "Run 幂等锚点冲突但既有记录不可见"));
            requireSameScheduledSource(existing, pending);
        }
        if (legacyScheduledAnchorAccepted(existing)) {
            return new LegacyScheduledAnchorClaim(
                    anchorRun(existing), existing.dispatchMessageId(), true, true, true);
        }
        if (!runSummaryPersistencePort.claimLegacyScheduledDispatch(
                existing.runId(), source.refId(), source.dispatchAttemptId(),
                now.plus(LEGACY_SCHEDULED_DISPATCH_LEASE), now)) {
            RunPersistenceAnchor latest = runSummaryPersistencePort
                    .findBySessionAndClientRequestId(pending.sessionId(), input.clientRequestId())
                    .orElse(existing);
            requireSameScheduledSource(latest, pending);
            if (legacyScheduledAnchorAccepted(latest)) {
                return new LegacyScheduledAnchorClaim(
                        anchorRun(latest), latest.dispatchMessageId(), true, true, true);
            }
            throw new RunOwnershipLostException("legacy Scheduled Run 启动租约仍由其它 attempt 持有");
        }
        return new LegacyScheduledAnchorClaim(
                anchorRun(existing), existing.dispatchMessageId(), true, false, true);
    }

    /**
     * 未完成 handoff 的 legacy Scheduled Run 在重投前精确探测远端稳定消息。
     * UNKNOWN 必须停止本次恢复，避免 prompt_async 已受理但本地标记未落库时重复执行。
     */
    private RunDispatchAcceptance probeRecoveredLegacyScheduledDispatch(
            LegacyScheduledAnchorClaim claim,
            Run run,
            String agentId,
            String remoteSessionId,
            String dispatchMessageId,
            ExecutionNode node,
            String traceId) {
        if (!claim.recovered()) {
            return RunDispatchAcceptance.NOT_ACCEPTED;
        }
        if (runDispatchAcceptanceProbe == null || backendInstanceIdentity == null) {
            return RunDispatchAcceptance.UNKNOWN;
        }
        try {
            RunDispatchAcceptance acceptance = runDispatchAcceptanceProbe.probe(new RunDispatchProbeRequest(
                    run.runId(),
                    agentId,
                    remoteSessionId,
                    dispatchMessageId,
                    node.executionNodeId().value(),
                    node.baseUrl(),
                    null,
                    backendInstanceIdentity.linuxServerId(),
                    traceId));
            return acceptance == null ? RunDispatchAcceptance.UNKNOWN : acceptance;
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "legacy Scheduled Run 远端接收状态探测失败，runId={}, traceId={}, exceptionType={}",
                    run.runId().value(), traceId, failure.getClass().getSimpleName());
            return RunDispatchAcceptance.UNKNOWN;
        }
    }

    private boolean usesLegacyScheduledAnchor(RunSource source, StartRunInput input) {
        return source.type() == ConversationSourceType.SCHEDULED_TASK
                && source.dispatchAttemptId() != null
                && input.clientRequestId() != null
                && runSummaryPersistencePort != null;
    }

    /** Redis 锚点由 owner recovery 收敛；legacy 必须有 handoff 标记或已形成明确终态。 */
    private boolean legacyScheduledAnchorAccepted(RunPersistenceAnchor anchor) {
        return anchor.storageMode() != RunStorageMode.LEGACY_FULL
                || anchor.scheduledDispatchAcceptedAt() != null
                || anchor.status().isTerminal();
    }

    /** 远端副作用前最后一次续租，旧 attempt 失去 fencing 后不得发送 prompt 或写失败终态。 */
    private void renewLegacyScheduledDispatchClaim(
            LegacyScheduledAnchorClaim claim,
            RunSource source,
            RunId runId) {
        if (!claim.managed()) return;
        Instant now = Instant.now();
        if (!runSummaryPersistencePort.claimLegacyScheduledDispatch(
                runId, source.refId(), source.dispatchAttemptId(),
                now.plus(LEGACY_SCHEDULED_DISPATCH_LEASE), now)) {
            throw new RunOwnershipLostException("legacy Scheduled Run 启动 attempt 已失效");
        }
    }

    /** handoff 标记是夜间补偿判断 legacy Run 已受理的唯一活动态依据。 */
    private void markLegacyScheduledDispatchAccepted(
            LegacyScheduledAnchorClaim claim,
            RunSource source,
            RunId runId) {
        if (!claim.managed()) return;
        if (!runSummaryPersistencePort.markLegacyScheduledDispatchAccepted(
                runId, source.dispatchAttemptId(), Instant.now())) {
            throw new RunOwnershipLostException("legacy Scheduled Run 受理标记 attempt 已失效");
        }
    }

    /** 客户端可提交幂等 ID，但绝不能借此把新夜间任务绑定到同会话的历史 Run。 */
    private void requireSameScheduledSource(RunPersistenceAnchor existing, Run pending) {
        if (pending.sourceType() != ConversationSourceType.SCHEDULED_TASK) {
            return;
        }
        if (existing.sourceType() != ConversationSourceType.SCHEDULED_TASK
                || !Objects.equals(existing.sourceRefId(), pending.sourceRefId())
                || !Objects.equals(existing.triggeredByUserId(), pending.triggeredByUserId())
                || !existing.workspaceId().equals(pending.workspaceId())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Scheduled Run 幂等请求号已被其它任务使用");
        }
    }

    private Run anchorRun(RunPersistenceAnchor anchor) {
        return new Run(
                anchor.runId(),
                anchor.sessionId(),
                anchor.workspaceId(),
                anchor.status(),
                anchor.createdAt(),
                anchor.updatedAt(),
                anchor.traceId(),
                com.enterprise.testagent.domain.run.TokenUsage.empty(),
                null,
                anchor.sourceType(),
                anchor.sourceRefId(),
                anchor.triggeredByUserId(),
                anchor.agentId(),
                anchor.modelId());
    }

    private List<Map<String, Object>> runtimeInputParts(StartRunInput input) {
        return input.parts().stream()
                .map(part -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapped = OBJECT_MAPPER.convertValue(part, Map.class);
                    return Map.copyOf(mapped);
                })
                .toList();
    }

    private AgentSessionBinding createInitialAgentSession(
            String agentId,
            AgentRuntime runtime,
            Session session,
            Workspace workspace,
            ExecutionNode node,
            String traceId,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        AgentCreateSessionResult created = runtime.createSession(new AgentCreateSessionCommand(
                        node,
                        workspaceRootPath(workspace),
                        null,
                        session.title(),
                        traceId))
                .block();
        if (created == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "agent 创建会话未返回结果");
        }
        // createSession 是远端副作用；返回后必须再次确认 fencing，再写关系型 binding。
        requireOwnedIfPresent(ownership);
        Instant now = Instant.now();
        AgentSessionBinding binding = new AgentSessionBinding(
                session.sessionId(),
                agentId,
                created.remoteSessionId(),
                node.executionNodeId(),
                now,
                now,
                traceId);
        return binding;
    }

    private boolean failRedisSummaryRunIfOwned(
            Run run,
            String traceId,
            String reasonCode,
            String safeMessage,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        if (ownerLeaseSupervisor == null) {
            // 兼容旧测试/手工装配；生产 REDIS_SUMMARY 始终注入 owner supervisor。
            failRedisSummaryRun(run, traceId, reasonCode, safeMessage, null);
            return true;
        }
        if (ownership == null) {
            return false;
        }
        try {
            ownerLeaseSupervisor.requireOwned(ownership);
            failRedisSummaryRun(run, traceId, reasonCode, safeMessage, ownership);
            return true;
        } catch (RunOwnershipLostException ignored) {
            // 新 owner 会继续恢复或收敛，本执行者不得写任何终态副作用。
            return false;
        }
    }

    /**
     * 锚点已写入后的启动异常必须先尝试 fenced 终态；若 Redis 本身不可用，则进入固定 30 秒收敛，
     * 不允许留下永久 RUNNING 锚点，也不把原始 prompt 降级写入 PostgreSQL。
     */
    private void handleRedisSummaryStartupFailure(
            String agentId,
            AgentRuntime runtime,
            Run run,
            String remoteSessionId,
            ExecutionNode node,
            Workspace workspace,
            String dispatchMessageId,
            String traceId,
            String reasonCode,
            String safeMessage,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership,
            Throwable originalFailure) {
        boolean runtimeLost = runtimeStateUnavailable(originalFailure);
        try {
            if (failRedisSummaryRunIfOwned(run, traceId, reasonCode, safeMessage, ownership)) {
                return;
            }
        } catch (RuntimeException terminalFailure) {
            if (!runtimeStateUnavailable(terminalFailure)) {
                throw terminalFailure;
            }
            runtimeLost = true;
        }
        if (!runtimeLost) {
            return;
        }
        scheduleStartupRuntimeLossConvergence(
                agentId,
                runtime,
                run,
                remoteSessionId,
                node,
                workspace,
                dispatchMessageId,
                traceId);
    }

    private void scheduleStartupRuntimeLossConvergence(
            String agentId,
            AgentRuntime runtime,
            Run run,
            String remoteSessionId,
            ExecutionNode node,
            Workspace workspace,
            String dispatchMessageId,
            String traceId) {
        if (runtimeLossScheduler == null || run.triggeredByUserId() == null) {
            LOGGER.warn(
                    "Redis 摘要 Run 启动失败后无法调度运行态收敛，runId={}, traceId={}",
                    run.runId().value(), traceId);
            return;
        }
        RunRuntimeLossRequest request = new RunRuntimeLossRequest(
                run.runId(),
                run.sessionId(),
                run.triggeredByUserId(),
                run.messageSenderUserId(),
                run.messageSenderUnifiedAuthId(),
                run.messageSentBySharedUser(),
                agentId,
                dispatchMessageId,
                remoteSessionId,
                workspaceRootPath(workspace),
                run.sourceType(),
                run.sourceRefId(),
                traceId);
        runtimeLossScheduler.schedule(
                request,
                runtime,
                node,
                () -> closeUndispatchedRunAfterRuntimeRecovery(run, traceId));
    }

    /** Redis 在 grace 内恢复时，该启动请求仍从未派发；用条件接管的新 token 关闭它，禁止误发 prompt。 */
    private void closeUndispatchedRunAfterRuntimeRecovery(Run run, String traceId) {
        RunOwnerLeaseSupervisor.OwnershipHandle ownership = null;
        try {
            RunRuntimeManifest manifest = runRuntimeStore.findManifest(run.runId()).orElse(null);
            if (manifest == null || !manifest.active()) {
                return;
            }
            RunOwnerLease lease = runRuntimeStore
                    .claimOwnerLeaseIfUnchanged(manifest, backendInstanceIdentity.backendProcessId())
                    .orElse(null);
            if (lease == null) {
                return;
            }
            ownership = ownerLeaseSupervisor.adopt(lease).orElse(null);
            if (ownership == null) {
                runRuntimeStore.releaseOwnerLease(lease);
                return;
            }
            ownerLeaseSupervisor.requireOwned(ownership);
            failRedisSummaryRun(
                    run,
                    traceId,
                    "START_RUNTIME_STATE_LOST",
                    "运行态不可用，Run 未派发并已终止",
                    ownership);
        } catch (RunOwnershipLostException ignored) {
            // 条件接管后又发生竞争时由新 owner 负责恢复，本执行者不写终态。
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Redis 恢复后关闭未派发 Run 失败，runId={}, traceId={}, exceptionType={}",
                    run.runId().value(), traceId, exception.getClass().getSimpleName());
            throw exception;
        } finally {
            if (ownership != null) {
                releaseOwnershipBestEffort(ownership, run.runId(), traceId);
            }
        }
    }

    private void failRedisSummaryRun(
            Run run,
            String traceId,
            String reasonCode,
            String safeMessage,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        Instant occurredAt = Instant.now();
        append(run.runId(), RunEventType.RUN_FAILED, traceId, occurredAt,
                RunTerminalProjectionOutboxPayload.payload(
                        Map.of("errorCode", reasonCode, "message", safeMessage == null ? "Run 启动失败" : safeMessage),
                        "LOCAL_START",
                        reasonCode,
                        safeMessage,
                        false),
                RunStorageMode.REDIS_SUMMARY,
                ownership);
        runTerminalProjectionService.project(
                run.runId(),
                RunStatus.FAILED,
                "LOCAL_START",
                reasonCode,
                safeMessage,
                false,
                traceId);
        runSessionScopeRouter.finishRun(run.runId());
    }

    private UserOpencodeProcessAssignment resolveUserProcessAssignment(UserId userId, String agentId, String traceId) {
        return runtimeTargetResolver.resolveUserProcessAssignment(userId, agentId, traceId).orElse(null);
    }

    private ConversationRunContext resolveConversationContext(
            UserId userId,
            String agentId,
            StartRunInput input,
            String traceId) {
        if (userId == null || conversationContextResolver == null) {
            return null;
        }
        return conversationContextResolver.resolve(userId, agentId, input, traceId).orElse(null);
    }

    /**
     * 兼容无 token 请求仅保留协议兼容，不豁免会话归因与工作区实时鉴权。
     * 体验参数改配、用户加入应用或成员撤权都会在创建 Run 记录前失败关闭。
     */
    private void requireAuthenticatedLegacyRunAccess(UserId userId, Session session) {
        if (userId == null) {
            return;
        }
        boolean visible = session.status() == SessionStatus.ACTIVE;
        if (visible && session.createdByUserId() != null) {
            visible = session.createdByUserId().equals(userId);
        } else if (visible && sessionHistoryRepository != null) {
            visible = sessionHistoryRepository.findUserSession(userId, session.sessionId()).isPresent();
        }
        if (!visible) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "Session 不存在",
                    Map.of("sessionId", session.sessionId().value()));
        }
        if (workspaceAccessAuthorizer != null) {
            workspaceAccessAuthorizer.requireAccess(userId, session.workspaceId());
        }
    }

    private void recordRootSessionScope(
            String agentId,
            Run run,
            String remoteSessionId,
            String dispatchMessageId,
            String traceId,
            RunStorageMode storageMode) {
        recordRootSessionScope(agentId, run, remoteSessionId, dispatchMessageId, traceId, storageMode, null);
    }

    private void recordRootSessionScope(
            String agentId,
            Run run,
            String remoteSessionId,
            String dispatchMessageId,
            String traceId,
            RunStorageMode storageMode,
            RunOwnerLease ownerLease) {
        Instant now = Instant.now();
        Map<String, Object> metadata = Map.of(
                "agentId", agentId,
                "dispatchMessageId", dispatchMessageId);
        RunSessionScope scope = new RunSessionScope(
                run.runId(),
                remoteSessionId,
                1L,
                traceId,
                now,
                now,
                metadata);
        RunSessionScopeSession rootSession = new RunSessionScopeSession(
                run.runId(),
                remoteSessionId,
                remoteSessionId,
                null,
                false,
                "ROOT",
                null,
                null,
                null,
                traceId,
                now,
                now,
                metadata);
        if (storageMode == RunStorageMode.REDIS_SUMMARY) {
            if (runRuntimeStore == null) {
                throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run Redis 运行态未配置");
            }
            if (ownerLease == null) {
                runRuntimeStore.saveScope(scope, rootSession);
            } else {
                runRuntimeStore.saveScope(scope, rootSession, ownerLease);
            }
            return;
        }
        runSessionScopeRuntimeCache.recordScopeSession(scope, rootSession);
        if (runSessionScopeRepository == null) {
            return;
        }
        try {
            runSessionScopeRepository.upsertScope(scope);
            runSessionScopeRepository.upsertSession(rootSession);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to persist run session root scope, runId={}, remoteSessionId={}, traceId={}",
                    run.runId().value(),
                    remoteSessionId,
                    traceId,
                    exception);
        }
    }

    private AgentRoutingTarget userProcessTarget(
            UserOpencodeProcessAssignment assignment,
            RunId runId,
            Instant now,
            String traceId) {
        // 用户进程节点可能是本地直联合成节点，先落兼容节点，避免后续路由审计和 binding 外键失败。
        ExecutionNode node = executionNodeRepository.save(assignment.node());
        return new AgentRoutingTarget(
                node,
                new RoutingDecision(runId, node.executionNodeId(), RoutingReason.MANUAL_OVERRIDE, now, traceId));
    }

    private AgentRoutingTarget conversationContextTarget(
            ConversationRunContext context,
            RunId runId,
            Instant now,
            String traceId) {
        ExecutionNode node = context.executionNodeSnapshot();
        return new AgentRoutingTarget(
                node,
                new RoutingDecision(runId, node.executionNodeId(), RoutingReason.MANUAL_OVERRIDE, now, traceId));
    }

    /**
     * opencode prompt_async 会把 agent 写入 assistant message；不传时会触发 SQLite 非空约束失败。
     */
    private String resolveOpencodeAgent(StartRunInput input) {
        return Optional.ofNullable(input.agent())
                .or(() -> Optional.ofNullable(input.mode()))
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_OPENCODE_AGENT);
    }

    /**
     * 将平台 prompt parts 转成 opencode prompt_async parts，缺少显式文本时保留 legacy prompt。
     */
    private List<AgentPromptPart> toAgentPromptParts(StartRunInput input, Workspace workspace) {
        if (input.parts().isEmpty()) {
            return List.of(AgentPromptPart.text(input.effectivePrompt()));
        }
        List<AgentPromptPart> parts = new ArrayList<>();
        boolean hasTextPart = input.parts().stream()
                .anyMatch(part -> "text".equals(part.type()) && part.text() != null && !part.text().isBlank());
        if (input.prompt() != null && !hasTextPart) {
            parts.add(AgentPromptPart.text(input.prompt()));
        }
        parts.addAll(input.parts().stream()
                .map(part -> toAgentPromptPart(part, workspace))
                .filter(Objects::nonNull)
                .toList());
        return parts.isEmpty() ? List.of(AgentPromptPart.text(input.effectivePrompt())) : parts;
    }

    /**
     * OpenCode command 只接受 file part，会过滤工作区工具型附件的 text part。
     * 因此仅在 slash command 中把本轮降级附件提示追加到 arguments，兼容 $ARGUMENTS、位置参数和 subtask 命令。
     */
    private String commandArguments(StartRunInput input, List<AgentPromptPart> parts) {
        if (input.command() == null) {
            return input.arguments();
        }
        List<String> workspaceAttachments = parts.stream()
                .filter(part -> "text".equals(part.type()))
                .filter(part -> WORKSPACE_ATTACHMENT_CONTEXT_TYPE.equals(part.source().get("contextType")))
                .map(AgentPromptPart::text)
                .toList();
        if (workspaceAttachments.isEmpty()) {
            return input.arguments();
        }
        String prefix = input.arguments() == null || input.arguments().isBlank()
                ? ""
                : input.arguments() + "\n\n";
        return prefix
                + "以下是本轮用户明确选择的工作区附件。请只使用列出的精确 workspacePath，"
                + "不要扫描附件目录或选取历史同名文件：\n"
                + String.join("\n\n", workspaceAttachments);
    }

    /**
     * 按 part 类型分发到 opencode text/file/agent 表达，未知类型静默丢弃。
     */
    private AgentPromptPart toAgentPromptPart(StartRunInput.PromptPart part, Workspace workspace) {
        if (part.type() == null) {
            return null;
        }
        return switch (part.type()) {
            case "text" -> part.text() == null ? null : AgentPromptPart.text(part.text());
            case "file" -> toAgentFilePart(part, workspace);
            case "agent" -> toAgentAgentPart(part);
            case "reference" -> toReferenceTextPart(part);
            case "subtask" -> AgentPromptPart.subtask(
                    part.text(),
                    firstText(part.label(), part.name(), "子任务"),
                    firstText(part.agentId(), part.name(), DEFAULT_OPENCODE_AGENT),
                    part.metadata());
            default -> null;
        };
    }

    /**
     * 将平台文件上下文转成 opencode part。聊天上传附件按 deliveryMode 分流，平台来源元数据只用于分流和用户消息历史。
     */
    private AgentPromptPart toAgentFilePart(StartRunInput.PromptPart part, Workspace workspace) {
        if (isWorkspaceAttachment(part) && !usesNativeAttachmentDelivery(part)) {
            return toWorkspaceAttachmentTextPart(part, workspace);
        }
        String mime = firstText(part.mimeType(), "text/plain");
        String filename = firstText(part.name(), filenameFromPath(part.path()), "attachment");
        String text = sourceText(part);
        if (text != null) {
            String url = "data:" + mime + ";base64," + Base64.getEncoder()
                    .encodeToString(text.getBytes(StandardCharsets.UTF_8));
            return AgentPromptPart.file(url, mime, filename, fileSource(part, text));
        }
        if (part.url() != null) {
            // OpenCode 的 FilePartInput.source 可选，但一旦存在就必须同时包含 text/type/path。
            return AgentPromptPart.file(part.url(), mime, filename, Map.of());
        }
        if (part.path() != null) {
            // 路径型原生附件不内联正文，因此省略可选 source；路径仍先经过 Workspace 根边界校验。
            return AgentPromptPart.file(workspaceFileUrl(workspace, part.path()), mime, filename, Map.of());
        }
        return null;
    }

    /**
     * 工作区聊天附件已经通过平台文件 RPC 落盘，只向智能体暴露受控相对路径。
     * 这样既不把大文件再次内联到 Run 请求，也不触发模型对 Excel 等媒体类型的能力校验。
     */
    private AgentPromptPart toWorkspaceAttachmentTextPart(StartRunInput.PromptPart part, Workspace workspace) {
        if (part.path() == null) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "工作区附件缺少文件路径",
                    Map.of("filename", firstText(part.name(), "attachment")));
        }
        String relativePath = workspaceRelativeFilePath(workspace, part.path());
        String filename = firstText(part.name(), filenameFromPath(relativePath), "attachment");
        String mime = firstText(part.mimeType(), "application/octet-stream");
        LinkedHashMap<String, String> attachment = new LinkedHashMap<>();
        attachment.put("filename", filename);
        attachment.put("workspacePath", relativePath);
        attachment.put("mimeType", mime);
        String metadata = OBJECT_MAPPER.valueToTree(attachment).toString();
        LinkedHashMap<String, Object> source = new LinkedHashMap<>(attachment);
        source.put("contextType", WORKSPACE_ATTACHMENT_CONTEXT_TYPE);
        source.put(WORKSPACE_ATTACHMENT_DELIVERY_MODE, "workspace");
        return AgentPromptPart.text(
                "用户上传的附件已保存在当前工作区。附件信息：" + metadata
                        + "\n请根据用户任务使用工作区工具读取或处理 workspacePath 指向的文件。该路径相对于当前工作目录。",
                source);
    }

    /** 判断 file part 是否来自聊天附件上传入口。 */
    private boolean isWorkspaceAttachment(StartRunInput.PromptPart part) {
        return WORKSPACE_ATTACHMENT_CONTEXT_TYPE.equals(part.source().get("contextType"));
    }

    /** 原生投递标记只改变 OpenCode part 形态，工作区路径仍经过同一安全根校验。 */
    private boolean usesNativeAttachmentDelivery(StartRunInput.PromptPart part) {
        return NATIVE_ATTACHMENT_DELIVERY.equals(part.source().get(WORKSPACE_ATTACHMENT_DELIVERY_MODE));
    }

    /**
     * 将平台 agent part 转成 opencode agent part，缺少名称时丢弃。
     */
    private AgentPromptPart toAgentAgentPart(StartRunInput.PromptPart part) {
        String agentName = firstText(part.name(), part.agentId());
        if (agentName == null) {
            return null;
        }
        return AgentPromptPart.agent(agentName, agentSource(part));
    }

    /**
     * 将 reference part 降级为文本提示，避免 opencode 不认识平台引用类型。
     */
    private AgentPromptPart toReferenceTextPart(StartRunInput.PromptPart part) {
        String label = firstText(part.label(), part.id(), part.uri());
        if (label == null) {
            return null;
        }
        String suffix = part.uri() == null ? "" : " (" + part.uri() + ")";
        return AgentPromptPart.text("Reference: " + label + suffix);
    }

    /** 构造 OpenCode 1.18.4 允许的完整 FileSource；平台 contextType/行号元数据不进入远端协议。 */
    private Map<String, Object> fileSource(StartRunInput.PromptPart part, String text) {
        String path = firstText(part.path(), part.name());
        if (path == null || text == null) {
            return Map.of();
        }
        return Map.of(
                "type", "file",
                "path", path,
                "text", Map.of(
                        "value", text,
                        "start", sourceNumber(part.source(), "start", 0),
                        "end", sourceNumber(part.source(), "end", text.length())));
    }

    /**
     * 构造 agent source 元数据，保留前端输入范围用于 opencode 侧上下文显示。
     */
    private Map<String, Object> agentSource(StartRunInput.PromptPart part) {
        String value = sourceText(part);
        if (value == null) {
            return Map.of();
        }
        return Map.of(
                "value", value,
                "start", sourceNumber(part.source(), "start", 0),
                "end", sourceNumber(part.source(), "end", value.length()));
    }

    /**
     * 将 workspace 相对路径转成 file URL，并拒绝路径穿越到 workspace 根目录外。
     */
    private String workspaceFileUrl(Workspace workspace, String path) {
        return workspaceFilePath(workspace, path).toUri().toString();
    }

    /**
     * 返回规范化后的工作区相对文件路径，供智能体工具在当前工作目录内读取。
     */
    private String workspaceRelativeFilePath(Workspace workspace, String path) {
        Path root = workspaceRoot(workspace);
        Path target = workspaceFilePath(workspace, path);
        String relativePath = root.relativize(target).toString().replace('\\', '/');
        if (relativePath.isBlank()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "工作区附件必须指向文件",
                    Map.of("path", path, "workspaceId", workspace.workspaceId().value()));
        }
        return relativePath;
    }

    /**
     * 解析并校验工作区文件路径，拒绝绝对路径和越出当前 Workspace 的路径。
     */
    private Path workspaceFilePath(Workspace workspace, String path) {
        Path root = workspaceRoot(workspace);
        Path target = root.resolve(path).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "文件上下文必须位于当前 Workspace 内",
                    Map.of("path", path, "workspaceId", workspace.workspaceId().value()));
        }
        return target;
    }

    /**
     * 从 prompt part 的 content 或 source.text 中提取非空文本内容。
     */
    private String sourceText(StartRunInput.PromptPart part) {
        if (part.content() != null) {
            return part.content();
        }
        Object text = part.source().get("text");
        if (text instanceof String value && !value.isBlank()) {
            return value;
        }
        if (text instanceof Map<?, ?> nested) {
            Object value = nested.get("value");
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    /**
     * 从 source Map 中读取整数字段，缺失或非数字时使用 fallback。
     */
    private int sourceNumber(Map<String, Object> source, String key, int fallback) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    /**
     * 从路径中提取文件名，路径异常或没有文件名时保留原始 path。
     */
    private String filenameFromPath(String path) {
        if (path == null) {
            return null;
        }
        Path filename = Path.of(path).getFileName();
        return filename == null ? path : filename.toString();
    }

    /**
     * 解析 provider/model 形式的模型选择，格式不合法时不向 opencode 指定模型。
     */
    private ModelSelection parseModel(String model) {
        if (model == null) {
            return new ModelSelection(null, null);
        }
        int slash = model.indexOf('/');
        if (slash <= 0 || slash == model.length() - 1) {
            return new ModelSelection(null, null);
        }
        return new ModelSelection(model.substring(0, slash), model.substring(slash + 1));
    }

    /**
     * 模型目录由 opencode 原生配置文件决定，Java 端只解析 provider/model 并透传。
     */
    private ModelSelection resolveModelSelection(String model) {
        return parseModel(model);
    }

    private ModelCatalogEntry toModelCatalogEntry(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String providerId = modelCatalogText(payload.get("providerId"), payload.get("providerID"));
        String modelId = modelCatalogText(payload.get("id"), payload.get("modelId"), payload.get("modelID"));
        if (providerId == null || modelId == null) {
            return null;
        }
        return new ModelCatalogEntry(providerId, modelId, Boolean.TRUE.equals(payload.get("defaultModel")));
    }

    private String modelCatalogText(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * 返回第一个非空白字符串，用于可选字段兜底。
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 查询本地 Run，不存在时抛出统一 NOT_FOUND 平台异常。
     */
    public Run getRun(RunId runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Run 不存在", Map.of("runId", runId.value())));
    }

    /**
     * 校验认证用户是否拥有指定 Run。新模式只读取 Redis manifest，避免详情/SSE 建连重新访问 PostgreSQL；
     * legacy 或 manifest 已过期时才回查 Run 与 Session，并对任一已记录的归属字段执行 fail-closed 校验。
     */
    public void requireRunAccess(UserId userId, RunId runId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        if (runRuntimeStore != null) {
            Optional<RunRuntimeManifest> manifest = runRuntimeStore.findManifest(runId);
            if (manifest.isPresent() && manifest.get().storageMode() == RunStorageMode.REDIS_SUMMARY) {
                if (userId.equals(manifest.get().userId())) {
                    return;
                }
                throw forbiddenRunAccess();
            }
        }

        Run run = getRun(runId);
        Session session = findSession(run.sessionId());
        boolean ownerRecorded = false;
        if (run.triggeredByUserId() != null) {
            ownerRecorded = true;
            if (!userId.equals(run.triggeredByUserId())) {
                throw forbiddenRunAccess();
            }
        }
        if (session.createdByUserId() != null) {
            ownerRecorded = true;
            if (!userId.equals(session.createdByUserId())) {
                throw forbiddenRunAccess();
            }
        }
        if (!ownerRecorded) {
            throw forbiddenRunAccess();
        }
    }

    /** 分享读取只校验固定 session/workspace，不把真实 actor 写成 Run 执行所属人。 */
    public void requireRunAccess(
            com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext context,
            RunId runId) {
        sharedRun(context, runId);
    }

    /** 停止权限仅属于会话所属人或该 Run 的实际消息发送人，降为只读后仍保留停止权。 */
    public void requireRunStopAccess(
            com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext context,
            RunId runId) {
        Run run = sharedRun(context, runId);
        if (!context.ownerAccess()
                && !context.actorUserId().equals(run.messageSenderUserId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "仅会话所属人或消息发送人可以停止该 Run");
        }
    }

    private Run sharedRun(
            com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext context,
            RunId runId) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Run run = runRuntimeStore == null
                ? getRun(runId)
                : runRuntimeStore.findManifest(runId)
                        .filter(manifest -> manifest.storageMode() == RunStorageMode.REDIS_SUMMARY)
                        .map(this::runtimeRun)
                        .orElseGet(() -> getRun(runId));
        context.requireSession(run.sessionId());
        context.requireWorkspace(run.workspaceId());
        return run;
    }

    /**
     * 远端恢复、SSE、取消与 diff 副作用在历史归因之外，还必须复核当前 Workspace 权限。
     * 纯数据库 Run 详情仍可使用 {@link #requireRunAccess(UserId, RunId)} 保留历史只读视图。
     */
    public void requireLiveRunAccess(UserId userId, RunId runId) {
        requireRunAccess(userId, runId);
        WorkspaceId workspaceId = workspaceIdForRun(runId);
        if (workspaceAccessAuthorizer != null) {
            workspaceAccessAuthorizer.requireAccess(userId, workspaceId);
        }
    }

    /** 仅体验 Run 需要为长连接启动固定间隔的实时 Workspace 资格复核。 */
    public boolean isExperienceRun(RunId runId) {
        return ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspaceIdForRun(runId));
    }

    private WorkspaceId workspaceIdForRun(RunId runId) {
        if (runRuntimeStore != null) {
            WorkspaceId runtimeWorkspaceId = runRuntimeStore.findManifest(runId)
                    .map(RunRuntimeManifest::workspaceId)
                    .orElse(null);
            if (runtimeWorkspaceId != null) {
                return runtimeWorkspaceId;
            }
        }
        return getRun(runId).workspaceId();
    }

    /** 越权统一使用不携带 Run 元数据的安全错误，避免通过差异响应枚举归属。 */
    private PlatformException forbiddenRunAccess() {
        return new PlatformException(ErrorCode.FORBIDDEN, "无权访问该 Run");
    }

    /** 返回 Redis manifest 中的创建时固定模式和详情窗口；legacy/旧数据保持空。 */
    public Optional<RunStorageMetadata> storageMetadata(RunId runId) {
        if (runRuntimeStore == null) {
            return Optional.empty();
        }
        return runRuntimeStore.findManifest(runId)
                .map(manifest -> new RunStorageMetadata(
                        manifest.storageMode(), manifest.clientRequestId(), manifest.detailsExpiresAt()));
    }

    /**
     * SSE 建连先读 Redis manifest；仅在 manifest 已消失时低频查询无原文锚点以区分 legacy 与详情过期。
     */
    public RunStorageMode eventStorageMode(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (runRuntimeStore == null) {
            return RunStorageMode.LEGACY_FULL;
        }
        Optional<RunRuntimeManifest> manifest = runRuntimeStore.findManifest(runId);
        if (manifest.isPresent()) {
            return manifest.orElseThrow().storageMode();
        }
        if (runSummaryPersistencePort != null
                && runSummaryPersistencePort.findDetailsLocator(runId)
                        .filter(locator -> locator.storageMode() == RunStorageMode.REDIS_SUMMARY)
                        .isPresent()) {
            throw new PlatformException(
                    ErrorCode.RUN_DETAILS_EXPIRED,
                    "Run 详情已过期",
                    Map.of("runId", runId.value()));
        }
        return RunStorageMode.LEGACY_FULL;
    }

    /**
     * 查询指定会话最近的非终态 Run，供前端刷新后恢复 SSE 订阅。
     */
    public Optional<Run> findActiveRun(SessionId sessionId) {
        findSession(sessionId);
        Optional<Run> activeRun = runRepository.findLatestActiveBySessionId(sessionId);
        // 历史会话重新打开时，OpenCode 全局事件流可能已错过 root session.idle；只在远端最新消息已明确
        // finish=stop 时补写成功终态，不能仅因页面刷新或远端 session 存在就结束 Run。
        activeRun.ifPresent(this::reconcileActiveRunFromRemoteFinalMessage);
        return runRepository.findLatestActiveBySessionId(sessionId);
    }

    /**
     * 服务重启后扫描 legacy active Run，主动补偿已经完成但原 SSE 终态丢失的会话；只读远端消息，不会重新发送 prompt。
     */
    public int reconcileLegacyActiveRuns(String traceId, BooleanSupplier stopRequested) {
        BooleanSupplier shouldStop = stopRequested == null ? () -> false : stopRequested;
        int reconciled = 0;
        // findStaleActiveRuns 的 SQL 只筛 LEGACY_FULL；未来时间上界等价于“当前所有 active legacy Run”。
        Instant upperBound = Instant.now().plusSeconds(1);
        for (Run candidate : runRepository.findStaleActiveRuns(upperBound, 200)) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            Optional<Run> current = runRepository.findById(candidate.runId());
            if (current.isEmpty() || current.get().status().isTerminal()) {
                continue;
            }
            reconcileActiveRunFromRemoteFinalMessage(current.get());
            if (runRepository.findById(candidate.runId())
                    .map(run -> run.status().isTerminal())
                    .orElse(false)) {
                reconciled++;
            }
        }
        return reconciled;
    }

    /**
     * 用远端最终 assistant 消息补偿丢失的终态事件，避免实际已完成的历史 Run 永久停在运行中。
     */
    private void reconcileActiveRunFromRemoteFinalMessage(Run activeRun) {
        try {
            if (runActivityStateStore.hasPendingAsk(activeRun.runId())) {
                // ask 尚未回复时，远端可能已经有一条 finish=stop 的中间 assistant 消息；必须保留 RUNNING。
                return;
            }
            String resolvedAgentId = runtimeAgentIdForRun(activeRun);
            Session session = findSession(activeRun.sessionId());
            Optional<AgentSessionBinding> binding = runtimeTargetResolver.findAgentBinding(
                    resolvedAgentId, session, activeRun.traceId());
            if (binding.isEmpty()) {
                return;
            }
            Optional<ExecutionNode> node = executionNodeRepository.findById(binding.get().executionNodeId());
            if (node.isEmpty()) {
                return;
            }
            AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
            finishedAssistantAfterRootIdle(
                            runtime,
                            node.get(),
                            binding.get().remoteSessionId(),
                            activeRun,
                            activeRun.traceId())
                    .ifPresent(finalMessage -> completeActiveRunAfterInteraction(
                            activeRun,
                            binding.get().remoteSessionId(),
                            resolvedAgentId,
                            finalMessage,
                            activeRun.traceId()));
        } catch (RuntimeException exception) {
            // active-run fallback 不能因补偿探测失败而阻断历史会话打开；下一次读取仍会重试。
            LOGGER.debug(
                    "Active Run remote final message is not available yet, runId={}, traceId={}, errorType={}",
                    activeRun.runId().value(),
                    activeRun.traceId(),
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * Run.agentId 在旧数据中保存的是 OpenCode 内部 build/plan 角色，而非平台 AgentRuntime ID；
     * 查询历史 Run 的远端消息时必须回退到默认 opencode runtime，不能把 build 当作注册 runtime。
     */
    private String runtimeAgentIdForRun(Run run) {
        String candidate = agentRuntimeRegistry.normalize(run.agentId());
        // Run.agentId 可能是 OpenCode 内置 build/plan 角色，而平台只注册 opencode runtime；
        // 不再用 require(candidate) 探测未知角色，避免每轮重启补偿扫描制造 NOT_FOUND 错误日志。
        return agentRuntimeRegistry.isRegistered(candidate)
                ? candidate
                : agentRuntimeRegistry.defaultAgentId();
    }

    /**
     * question/permission 回复成功后兜底观察远端最终 assistant 消息。
     * 部分 OpenCode 版本不会把 ask 恢复后的 message/idle 推送到既有事件订阅；这里只在精确 Run 轮次的
     * 最新 assistant 已 {@code finish=stop}、root session 当前确实 idle 且该 Run 仍非终态时补写成功事实。
     */
    public void reconcileAfterInteractionReply(SessionId sessionId, String agentId, String traceId) {
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        Optional<Run> activeRun = findLatestActiveRunForInteraction(sessionId);
        if (activeRun.isEmpty()) {
            return;
        }
        Session session = findSession(sessionId);
        Optional<AgentSessionBinding> binding = runtimeTargetResolver.findAgentBinding(resolvedAgentId, session, traceId);
        if (binding.isEmpty()) {
            return;
        }
        Optional<ExecutionNode> node = executionNodeRepository.findById(binding.get().executionNodeId());
        if (node.isEmpty()) {
            return;
        }
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        Mono.delay(TITLE_WAIT_RECONNECT_DELAY)
                .repeat(INTERACTION_RECONCILE_ATTEMPTS - 1L)
                .publishOn(Schedulers.boundedElastic())
                .concatMap(ignored -> Mono.fromCallable(() -> finishedAssistantAfterRootIdle(
                                runtime,
                                node.get(),
                                binding.get().remoteSessionId(),
                                activeRun.orElseThrow(),
                                traceId))
                        .onErrorReturn(Optional.empty()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .next()
                .subscribe(
                        finalMessage -> completeActiveRunAfterInteraction(
                                activeRun.orElseThrow(),
                                binding.get().remoteSessionId(),
                                resolvedAgentId,
                                finalMessage,
                                traceId),
                        error -> LOGGER.warn(
                                "Failed to reconcile Run after interaction reply, sessionId={}, traceId={}",
                                sessionId.value(),
                                traceId,
                                error));
    }

    /**
     * question 回复 HTTP 成功后补记既有 {@code question.replied} 事件。部分 OpenCode 版本会让
     * ask 恢复后的事件脱离原订阅；平台不能因此继续把该请求判定为待回答。
     */
    public void recordQuestionReplyAcknowledged(
            SessionId sessionId,
            String remoteSessionId,
            String requestId,
            List<List<String>> answers,
            String traceId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(answers, "answers must not be null");
        Optional<Run> activeRun = findLatestActiveRunForInteraction(sessionId);
        if (activeRun.isEmpty()) {
            return;
        }
        Run current = activeRun.orElseThrow();
        RunStorageMode storageMode = runRuntimeStore == null
                ? RunStorageMode.LEGACY_FULL
                : runRuntimeStore.storageMode(current.runId());
        append(
                current.runId(),
                RunEventType.QUESTION_REPLIED,
                traceId,
                Instant.now(),
                Map.of(
                        "sessionID", remoteSessionId,
                        "requestID", requestId,
                        "answers", answers,
                        "source", "interaction_reply_ack"),
                storageMode);
    }

    /**
     * 终态补偿必须同时满足精确轮次消息和 root idle；任一远端投影缺失或格式异常都失败关闭。
     */
    private Optional<AgentSessionMessage> finishedAssistantAfterRootIdle(
            AgentRuntime runtime,
            ExecutionNode node,
            String remoteSessionId,
            Run expectedRun,
            String traceId) {
        Optional<Run> currentRun = findCurrentReconcileRun(expectedRun);
        if (currentRun.isEmpty()) {
            return Optional.empty();
        }
        Run current = currentRun.orElseThrow();
        RunDispatchMessageAnchorResolver.Anchor anchor = RunDispatchMessageAnchorResolver.resolve(
                sessionMessageRepository, current.sessionId(), current.runId());
        if (runRuntimeStore != null) {
            Optional<RunRuntimeManifest> manifest = runRuntimeStore.findManifest(current.runId())
                    .filter(candidate -> candidate.storageMode() == RunStorageMode.REDIS_SUMMARY);
            if (manifest.isPresent()) {
                RunRuntimeManifest runtimeManifest = manifest.orElseThrow();
                if (!runtimeManifest.active()
                        || !current.sessionId().equals(runtimeManifest.sessionId())
                        || (runtimeManifest.rootRemoteSessionId() != null
                                && !runtimeManifest.rootRemoteSessionId().equals(remoteSessionId))) {
                    return Optional.empty();
                }
                anchor = RunDispatchMessageAnchorResolver.merge(anchor, runtimeManifest.dispatchMessageId());
            }
        }
        if (anchor.conflicted()) {
            return Optional.empty();
        }
        RunTurnMessageSelector.Selection selection = RunMessageRecoveryService.loadRunTurnMessages(
                runtime,
                node,
                remoteSessionId,
                traceId,
                anchor.dispatchMessageId(),
                current.createdAt(),
                current.updatedAt());
        if (!selection.resolved()) {
            return Optional.empty();
        }
        AgentSessionMessage latestAssistant = null;
        for (AgentSessionMessage message : selection.messages()) {
            if ("assistant".equals(textValue(message.message().get("role")).orElse(null))) {
                latestAssistant = message;
            }
        }
        if (latestAssistant == null
                || !"stop".equals(textValue(latestAssistant.message().get("finish")).orElse(null))) {
            return Optional.empty();
        }
        Workspace workspace = findWorkspace(current.workspaceId());
        var statusResult = runtime.runtime(new AgentRuntimeCommand(
                        node,
                        "GET",
                        "/session/status",
                        workspaceRootPath(workspace),
                        null,
                        Map.of(),
                        null,
                        traceId))
                .block();
        if (statusResult == null || !statusResult.body().isObject()) {
            return Optional.empty();
        }
        // OpenCode 1.18.4 的 status map 只保留 busy/retry；idle 会删除对应 session key。
        return statusResult.body().has(remoteSessionId)
                ? Optional.empty()
                : Optional.of(latestAssistant);
    }

    private void completeActiveRunAfterInteraction(
            Run expectedRun,
            String remoteSessionId,
            String agentId,
            AgentSessionMessage finalMessage,
            String traceId) {
        if (runRuntimeStore != null) {
            Optional<RunRuntimeManifest> runtimeManifest = runRuntimeStore.findManifest(expectedRun.runId())
                    .filter(manifest -> manifest.storageMode() == RunStorageMode.REDIS_SUMMARY);
            if (runtimeManifest.isPresent()) {
                completeRedisSummaryRunAfterInteraction(
                        expectedRun,
                        runtimeManifest.orElseThrow(),
                        remoteSessionId,
                        finalMessage,
                        traceId);
                return;
            }
        }
        runRepository.findById(expectedRun.runId())
                .filter(current -> current.sessionId().equals(expectedRun.sessionId()))
                .filter(current -> current.status() == RunStatus.RUNNING)
                .ifPresent(current -> {
            Instant occurredAt = Instant.now();
            RunStorageMode storageMode = runRuntimeStore == null
                    ? RunStorageMode.LEGACY_FULL
                    : runRuntimeStore.storageMode(current.runId());
            Run succeeded = current.succeed(occurredAt);
            saveRunIfStatus(succeeded, current.status(), traceId, "interaction_reply_reconcile")
                    .ifPresent(saved -> {
                        publishRecoveredFinalMessage(
                                saved,
                                remoteSessionId,
                                finalMessage,
                                traceId,
                                occurredAt,
                                storageMode);
                        append(
                                saved.runId(),
                                RunEventType.RUN_SUCCEEDED,
                                traceId,
                                occurredAt,
                                Map.of(
                                        "status", RunStatus.SUCCEEDED.name(),
                                        "source", "interaction_reply_reconcile",
                                        "sessionID", remoteSessionId),
                                storageMode);
                        snapshotService.persistRunSnapshot(agentId, saved, traceId);
                        notifyRootRunTerminalObservers(saved.runId(), saved.status(), traceId);
                    });
        });
    }

    /** 精确按 runId 完成 Redis Run；有 owner 监督器时先原子接管，阻断旧事件订阅的竞态写入。 */
    private void completeRedisSummaryRunAfterInteraction(
            Run expectedRun,
            RunRuntimeManifest expectedManifest,
            String remoteSessionId,
            AgentSessionMessage finalMessage,
            String traceId) {
        if (!expectedManifest.active()
                || expectedManifest.status() != RunStatus.RUNNING
                || !expectedManifest.sessionId().equals(expectedRun.sessionId())
                || (expectedManifest.rootRemoteSessionId() != null
                        && !expectedManifest.rootRemoteSessionId().equals(remoteSessionId))) {
            return;
        }
        RunOwnerLeaseSupervisor.OwnershipHandle ownership = null;
        try {
            if (ownerLeaseSupervisor != null) {
                ownership = runRuntimeStore
                        .claimOwnerLeaseIfUnchanged(expectedManifest, backendInstanceIdentity.backendProcessId())
                        .flatMap(ownerLeaseSupervisor::adopt)
                        .orElse(null);
                if (ownership == null) {
                    return;
                }
                ownerLeaseSupervisor.requireOwned(ownership);
            }
            RunRuntimeManifest currentManifest = runRuntimeStore.findManifest(expectedRun.runId()).orElse(null);
            if (currentManifest == null
                    || !currentManifest.active()
                    || currentManifest.status() != RunStatus.RUNNING
                    || !currentManifest.sessionId().equals(expectedRun.sessionId())) {
                return;
            }
            Instant occurredAt = Instant.now();
            Run succeeded = runtimeRun(currentManifest).succeed(occurredAt);
            publishRecoveredFinalMessage(
                    succeeded,
                    remoteSessionId,
                    finalMessage,
                    traceId,
                    occurredAt,
                    RunStorageMode.REDIS_SUMMARY,
                    ownership);
            append(
                    succeeded.runId(),
                    RunEventType.RUN_SUCCEEDED,
                    traceId,
                    occurredAt,
                    RunTerminalProjectionOutboxPayload.payload(
                            Map.of(
                                    "status", RunStatus.SUCCEEDED.name(),
                                    "source", "interaction_reply_reconcile",
                                    "sessionID", remoteSessionId),
                            "INTERACTION_REPLY_RECONCILE",
                            "COMPLETED",
                            null,
                            false),
                    RunStorageMode.REDIS_SUMMARY,
                    ownership);
            runTerminalProjectionService.project(
                    succeeded.runId(),
                    RunStatus.SUCCEEDED,
                    "INTERACTION_REPLY_RECONCILE",
                    "COMPLETED",
                    null,
                    false,
                    traceId);
        } finally {
            if (ownership != null) {
                releaseOwnershipBestEffort(ownership, expectedRun.runId(), traceId);
            }
        }
    }

    /** 轮询始终绑定最初的 runId，Run 已终态或被替换后不得转而完成同 Session 的新 Run。 */
    private Optional<Run> findCurrentReconcileRun(Run expectedRun) {
        if (runRuntimeStore != null) {
            Optional<RunRuntimeManifest> manifest = runRuntimeStore.findManifest(expectedRun.runId())
                    .filter(candidate -> candidate.storageMode() == RunStorageMode.REDIS_SUMMARY);
            if (manifest.isPresent()) {
                RunRuntimeManifest current = manifest.orElseThrow();
                return current.status() == RunStatus.RUNNING
                                && current.sessionId().equals(expectedRun.sessionId())
                        ? Optional.of(runtimeRun(current))
                        : Optional.empty();
            }
        }
        return runRepository.findById(expectedRun.runId())
                .filter(current -> current.sessionId().equals(expectedRun.sessionId()))
                .filter(current -> current.status() == RunStatus.RUNNING);
    }

    /** 新模式的 ask 恢复先读 Redis active 索引，legacy 才回查关系型 Run。 */
    private Optional<Run> findLatestActiveRunForInteraction(SessionId sessionId) {
        if (runRuntimeStore != null) {
            Optional<Run> runtimeRun = runRuntimeStore.findActiveBySession(sessionId)
                    .filter(manifest -> manifest.storageMode() == RunStorageMode.REDIS_SUMMARY)
                    .map(this::runtimeRun);
            if (runtimeRun.isPresent()) {
                return runtimeRun;
            }
        }
        return runRepository.findLatestActiveBySessionId(sessionId);
    }

    /** ask 恢复后的最终消息可能未经过原 SSE，先补发同形态瞬态消息事件，再发布 Run 终态。 */
    private void publishRecoveredFinalMessage(
            Run run,
            String remoteSessionId,
            AgentSessionMessage finalMessage,
            String traceId,
            Instant occurredAt,
            RunStorageMode storageMode) {
        publishRecoveredFinalMessage(
                run, remoteSessionId, finalMessage, traceId, occurredAt, storageMode, null);
    }

    private void publishRecoveredFinalMessage(
            Run run,
            String remoteSessionId,
            AgentSessionMessage finalMessage,
            String traceId,
            Instant occurredAt,
            RunStorageMode storageMode,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        LinkedHashMap<String, Object> message = new LinkedHashMap<>(finalMessage.message());
        String messageId = textValue(message.get("id")).orElse(null);
        if (messageId != null) {
            message.putIfAbsent("messageID", messageId);
            message.putIfAbsent("messageId", messageId);
        }
        Map<String, Object> scope = Map.of(
                "rootSessionId", remoteSessionId,
                "sessionId", remoteSessionId,
                "isChildSession", false);
        LinkedHashMap<String, Object> messagePayload = new LinkedHashMap<>(scope);
        messagePayload.put("message", Map.copyOf(message));
        publishTransient(sanitizeExperienceEvent(run.workspaceId(), new RunEventDraft(
                run.runId(),
                RunEventType.MESSAGE_UPDATED,
                traceId,
                occurredAt,
                Map.copyOf(messagePayload))), storageMode, ownership);
        for (Map<String, Object> originalPart : finalMessage.parts()) {
            LinkedHashMap<String, Object> part = new LinkedHashMap<>(originalPart);
            if (messageId != null) {
                part.putIfAbsent("messageID", messageId);
                part.putIfAbsent("messageId", messageId);
            }
            textValue(part.get("id")).ifPresent(partId -> {
                part.putIfAbsent("partID", partId);
                part.putIfAbsent("partId", partId);
            });
            LinkedHashMap<String, Object> partPayload = new LinkedHashMap<>(scope);
            partPayload.put("part", Map.copyOf(part));
            if (messageId != null) {
                partPayload.put("messageID", messageId);
                partPayload.put("messageId", messageId);
            }
            publishTransient(sanitizeExperienceEvent(run.workspaceId(), new RunEventDraft(
                    run.runId(),
                    RunEventType.MESSAGE_PART_UPDATED,
                    traceId,
                    occurredAt,
                    Map.copyOf(partPayload))), storageMode, ownership);
        }
    }

    private void publishTransient(RunEventDraft draft, RunStorageMode storageMode) {
        publishTransient(draft, storageMode, null);
    }

    private void publishTransient(
            RunEventDraft draft,
            RunStorageMode storageMode,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        RunEventDraft sanitized = runEventPersistencePolicy.sanitizeForPersistence(draft);
        if (!runEventAppender.publishTransient(sanitized, storageMode, ownerLeaseIfPresent(ownership))) {
            runEventLiveBus.publishTransient(sanitized);
        }
    }

    /**
     * 新客户端 active-run fallback 优先读取 Redis session 索引；用户进入新模式后即使当前为空也不回查数据库。
     */
    public Optional<Run> findActiveRun(UserId userId, SessionId sessionId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (runRuntimeStore != null && runRuntimeStore.hasUserRuntimeState(userId)) {
            return runRuntimeStore.findActiveBySession(sessionId)
                    .filter(manifest -> userId.equals(manifest.userId()))
                    .map(manifest -> {
                        if (workspaceAccessAuthorizer != null) {
                            workspaceAccessAuthorizer.requireAccess(userId, manifest.workspaceId());
                        }
                        return runtimeRun(manifest);
                    });
        }
        Session session = findSession(sessionId);
        // legacy fallback 会触发远端 reconcile，必须先 fail-closed 校验 ACTIVE、归因和实时体验绑定。
        requireAuthenticatedLegacyRunAccess(userId, session);
        return findActiveRun(sessionId);
    }

    private Run runtimeRun(RunRuntimeManifest manifest) {
        return new Run(
                manifest.runId(),
                manifest.sessionId(),
                manifest.workspaceId(),
                manifest.status(),
                manifest.createdAt(),
                manifest.updatedAt(),
                "trace_runtime_redis",
                com.enterprise.testagent.domain.run.TokenUsage.empty(),
                null,
                ConversationSourceType.MANUAL,
                null,
                manifest.userId(),
                manifest.agentId(),
                null,
                manifest.messageSenderUserId(),
                manifest.messageSenderUnifiedAuthId(),
                manifest.messageSentBySharedUser());
    }

    /**
     * 请求取消 Run：先更新本地状态，再尽力通知默认 agent 远端 session abort，最后落取消事件。
     */
    public Run cancelRun(RunId runId, String traceId) {
        return cancelRun(agentRuntimeRegistry.defaultAgentId(), runId, traceId);
    }

    /**
     * 请求取消指定 agent Run；旧 URL 默认传入 opencode，新 URL 由 path 决定。
     */
    public Run cancelRun(String agentId, RunId runId, String traceId) {
        if (resendCancellationService != null) {
            Optional<Run> cancelledWaiting = resendCancellationService.cancelWaiting(runId, traceId);
            if (cancelledWaiting.isPresent()) {
                return cancelledWaiting.orElseThrow();
            }
        }
        String resolvedAgentId = agentRuntimeRegistry.normalize(agentId);
        LOGGER.info("Run cancellation requested, runId={}, agentId={}, traceId={}", runId.value(), resolvedAgentId, traceId);
        if (runRuntimeStore != null) {
            Optional<RunRuntimeManifest> runtimeManifest = runRuntimeStore.findManifest(runId)
                    .filter(manifest -> manifest.storageMode() == RunStorageMode.REDIS_SUMMARY);
            if (runtimeManifest.isPresent()) {
                AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
                return cancelRedisSummaryRun(resolvedAgentId, runtime, runtimeManifest.orElseThrow(), traceId);
            }
        }
        Run run = getRun(runId);
        String storedRuntimeAgentId = runtimeAgentIdForRun(run);
        if (agentRuntimeRegistry.defaultAgentId().equals(resolvedAgentId)
                && !agentRuntimeRegistry.defaultAgentId().equals(storedRuntimeAgentId)) {
            resolvedAgentId = storedRuntimeAgentId;
        }
        AgentRuntime runtime = agentRuntimeRegistry.require(resolvedAgentId);
        if (run.status().isTerminal()) {
            LOGGER.warn("Cannot cancel terminal run, runId={}, status={}, traceId={}", runId.value(), run.status().name(), traceId);
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run 已结束，不能取消",
                    Map.of("runId", runId.value(), "status", run.status().name()));
        }
        Run cancelling = run.status() == RunStatus.CANCELLING
                ? run
                : runRepository.save(run.requestCancel(Instant.now()));
        if (cancelling.status() == RunStatus.CANCELLING) {
            append(runId, RunEventType.RUN_CANCELLING, traceId, Instant.now(), Map.of("status", cancelling.status().name()));
        }
        RoutingDecision decision = routingDecisionRepository.findByRunId(runId).orElse(null);
        if (decision != null) {
            Session session = findSession(run.sessionId());
            Workspace workspace = findWorkspace(run.workspaceId());
            runtimeTargetResolver.findAgentBinding(resolvedAgentId, session, traceId).ifPresent(binding ->
                    executionNodeRepository.findById(binding.executionNodeId()).ifPresent(node ->
                            runtime.cancelSession(new AgentCancelCommand(
                                        node,
                                        binding.remoteSessionId(),
                                        workspaceRootPath(workspace),
                                        null,
                                        traceId))
                                    .block()));
        }
        Run cancelled = cancelling.status() == RunStatus.CANCELLED
                ? cancelling
                : runRepository.save(cancelling.cancel(Instant.now()));
        append(runId, RunEventType.RUN_CANCELLED, traceId, Instant.now(), Map.of("status", cancelled.status().name()));
        snapshotService.persistRunSnapshot(resolvedAgentId, cancelled, traceId);
        runSessionScopeRouter.finishRun(runId);
        LOGGER.info("Run cancelled, runId={}, traceId={}", runId.value(), traceId);
        return cancelled;
    }

    /** 新模式取消只写 Redis 运行态和终态摘要事务；远端取消所需路径/节点属于显式低频控制面读取。 */
    private Run cancelRedisSummaryRun(
            String resolvedAgentId,
            AgentRuntime runtime,
            RunRuntimeManifest manifest,
            String traceId) {
        if (!resolvedAgentId.equals(manifest.agentId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run agent 与取消路径不一致",
                    Map.of("runId", manifest.runId().value(), "agentId", manifest.agentId()));
        }
        if (manifest.status().isTerminal()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run 已结束，不能取消",
                    Map.of("runId", manifest.runId().value(), "status", manifest.status().name()));
        }
        RunOwnerLeaseSupervisor.OwnershipHandle ownership = null;
        try {
            if (ownerLeaseSupervisor != null) {
                RunOwnerLease lease = runRuntimeStore.claimOwnerLeaseIfUnchanged(
                                manifest, backendInstanceIdentity.backendProcessId())
                        .orElseThrow(() -> new PlatformException(
                                ErrorCode.CONFLICT,
                                "Run 状态已变化或正由其它 Java 处理"));
                ownership = ownerLeaseSupervisor.adopt(lease)
                        .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "Run owner lease 已失效"));
                ownerLeaseSupervisor.requireOwned(ownership);
            }
            Instant now = Instant.now();
            if (manifest.status() == RunStatus.RUNNING) {
                append(
                        manifest.runId(),
                        RunEventType.RUN_CANCELLING,
                        traceId,
                        now,
                        Map.of("status", RunStatus.CANCELLING.name()),
                        RunStorageMode.REDIS_SUMMARY,
                        ownership);
            }

            boolean remoteStopConfirmed = cancelRedisSummaryRemoteBestEffort(runtime, manifest, traceId);
            requireOwnedIfPresent(ownership);
            Instant cancelledAt = Instant.now();
            append(
                    manifest.runId(),
                    RunEventType.RUN_CANCELLED,
                    traceId,
                    cancelledAt,
                    RunTerminalProjectionOutboxPayload.payload(
                            Map.of("status", RunStatus.CANCELLED.name()),
                            "USER_CANCEL",
                            "USER_REQUESTED",
                            null,
                            remoteStopConfirmed),
                    RunStorageMode.REDIS_SUMMARY,
                    ownership);
            // terminal append 已原子封闭 owner 接管窗口，DB CAS 失败由终态重试队列收敛。
            runTerminalProjectionService.project(
                    manifest.runId(),
                    RunStatus.CANCELLED,
                    "USER_CANCEL",
                    "USER_REQUESTED",
                    null,
                    remoteStopConfirmed,
                    traceId);
            runSessionScopeRouter.finishRun(manifest.runId());
            RunRuntimeManifest terminal = runRuntimeStore.findManifest(manifest.runId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "Run manifest 不存在"));
            LOGGER.info(
                    "Redis summary Run cancelled, runId={}, remoteStopConfirmed={}, traceId={}",
                    manifest.runId().value(),
                    remoteStopConfirmed,
                    traceId);
            return runtimeRun(terminal);
        } finally {
            if (ownership != null) {
                releaseOwnershipBestEffort(ownership, manifest.runId(), traceId);
            }
        }
    }

    private boolean cancelRedisSummaryRemoteBestEffort(
            AgentRuntime runtime,
            RunRuntimeManifest manifest,
            String traceId) {
        try {
            if (manifest.rootRemoteSessionId() == null || manifest.rootRemoteSessionId().isBlank()) {
                return false;
            }
            ExecutionNode node = executionNodeRepository
                    .findById(new ExecutionNodeId(manifest.executionNodeId()))
                    .orElse(null);
            Workspace workspace = workspaceRepository.findById(manifest.workspaceId()).orElse(null);
            if (node == null || workspace == null) {
                return false;
            }
            var result = runtime.cancelSession(new AgentCancelCommand(
                            node,
                            manifest.rootRemoteSessionId(),
                            workspaceRootPath(workspace),
                            null,
                            traceId))
                    .block(REMOTE_CANCEL_TIMEOUT);
            return result != null && result.cancelled();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Best-effort remote cancellation failed, runId={}, traceId={}, exceptionType={}",
                    manifest.runId().value(),
                    traceId,
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 保存用户消息投影，记录 runId 以支持按每次对话查询用户输入与消耗。
     */
    private void saveUserMessage(
            SessionId sessionId,
            RunId runId,
            String prompt,
            List<StartRunInput.PromptPart> parts,
            UserId userId,
            String remoteMessageId,
            String traceId,
            Instant createdAt,
            RunSource source,
            RunActorAttribution attribution) {
        sessionMessageRepository.save(newUserMessageProjection(
                sessionId, runId, prompt, parts, userId, remoteMessageId, traceId, createdAt,
                source.type(), source.refId(), attribution));
    }

    /**
     * 创建用户消息的关系库投影；重发预约和真正执行必须复用同一字段规则，避免共享发送人归因漂移。
     */
    static SessionMessage newUserMessageProjection(
            SessionId sessionId,
            RunId runId,
            String prompt,
            List<?> parts,
            UserId userId,
            String remoteMessageId,
            String traceId,
            Instant createdAt,
            ConversationSourceType sourceType,
            String sourceRefId,
            RunActorAttribution attribution) {
        SessionMessage message = new SessionMessage(
                new SessionMessageId(RuntimeIdGenerator.messageId()),
                sessionId,
                SessionMessageRole.USER,
                prompt,
                createdAt,
                traceId,
                runId,
                null,
                remoteMessageId,
                userPromptPartsJson(parts, traceId).orElse(null),
                null,
                null,
                createdAt);
        SessionMessage sourced = userId == null
                ? message
                : message.withSource(sourceType, sourceRefId, userId);
        if (attribution != null && attribution.actualSenderUserId() != null) {
            sourced = sourced.withSender(
                    attribution.actualSenderUserId(),
                    attribution.actualSenderUnifiedAuthId(),
                    attribution.sentBySharedUser());
        }
        return sourced;
    }

    /**
     * 重发预约和 anchor-only legacy Run 均复用稳定 remote message id；既有消息必须精确属于同一 Run/来源/用户。
     */
    private boolean ensureUserMessage(
            SessionId sessionId,
            RunId runId,
            String prompt,
            List<StartRunInput.PromptPart> parts,
            UserId userId,
            String remoteMessageId,
            String traceId,
            Instant createdAt,
            RunSource source,
            RunActorAttribution attribution) {
        Optional<SessionMessage> existing = sessionMessageRepository
                .findBySessionIdAndRemoteMessageId(sessionId, remoteMessageId);
        if (existing.isEmpty()) {
            saveUserMessage(
                    sessionId, runId, prompt, parts, userId,
                    remoteMessageId, traceId, createdAt, source, attribution);
            return true;
        }
        SessionMessage message = existing.orElseThrow();
        if (message.role() != SessionMessageRole.USER
                || !Objects.equals(message.runId(), runId)
                || message.sourceType() != source.type()
                || !Objects.equals(message.sourceRefId(), source.refId())
                || !Objects.equals(message.senderUserId(), attribution.actualSenderUserId())
                || message.sentBySharedUser() != attribution.sentBySharedUser()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "Run 稳定消息号已被其它消息使用");
        }
        return false;
    }

    /** 可选 setter 保持既有单元测试构造器稳定；生产装配会注入数据库会话锁。 */
    @Autowired(required = false)
    void setNightExecutionLockGuard(NightExecutionSessionLockGuard nightExecutionLockGuard) {
        this.nightExecutionLockGuard = nightExecutionLockGuard;
    }

    /** 可选观察者列表保持现有测试构造器稳定，并避免把夜间字段暴露到 Run 聚合。 */
    @Autowired(required = false)
    void setScheduledRunLifecycleObservers(List<ScheduledRunLifecycleObserver> observers) {
        this.scheduledRunLifecycleObservers = observers == null ? List.of() : List.copyOf(observers);
    }

    /** 复用普通 Run 恢复的远端稳定消息探测器，避免扩张已有生产构造器。 */
    @Autowired(required = false)
    void setRunDispatchAcceptanceProbe(RunDispatchAcceptanceProbe probe) {
        this.runDispatchAcceptanceProbe = probe;
    }

    /** 自动重发等扩展只观察已提交的根 session.error，避免把 transport failure 当作模型错误。 */
    @Autowired(required = false)
    void setRootSessionErrorObservers(List<RunRootSessionErrorObserver> observers) {
        this.rootSessionErrorObservers = observers == null ? List.of() : List.copyOf(observers);
    }

    /** WAITING 重发可由现有 Run 停止入口取消，进入 revert 后仍由状态机恢复。 */
    @Autowired(required = false)
    void setRunResendCancellationService(RunResendCancellationService cancellationService) {
        this.resendCancellationService = cancellationService;
    }

    /** 启动前扩展只贡献受控 system 上下文；任一扩展失败时保持原 Run 无上下文继续。 */
    @Autowired(required = false)
    void setRunSystemPromptContributors(List<AgentRunSystemPromptContributor> contributors) {
        this.runSystemPromptContributors = contributors == null ? List.of() : List.copyOf(contributors);
    }

    /** 受保护运行服务保持可选 setter，避免扩张大量历史单元测试构造器。 */
    @Autowired(required = false)
    void setProtectedAgentExecutionService(ProtectedAgentExecutionService service) {
        this.protectedAgentExecutionService = Objects.requireNonNull(service, "service must not be null");
    }

    /** 根 Run 终态观察者只能执行后处理，失败不能改变已提交的终态。 */
    @Autowired(required = false)
    void setRootRunTerminalObservers(List<AgentRootRunTerminalObserver> observers) {
        this.rootRunTerminalObservers = observers == null ? List.of() : List.copyOf(observers);
    }

    /** 自动化引用只在 Run 锚点落库后记录代次租约，不向 OpenCode 出站上下文追加任何内容。 */
    @Autowired(required = false)
    void setAutomationReferenceRunLeaseLifecycle(AutomationReferenceRunLeaseLifecycle lifecycle) {
        this.automationReferenceRunLeaseLifecycle = lifecycle;
    }

    private String systemPrompt(Run run, String prompt, boolean command, String traceId) {
        if (runSystemPromptContributors.isEmpty()) {
            return null;
        }
        AgentRunPromptContext context = new AgentRunPromptContext(run, prompt, command, traceId);
        List<String> additions = new ArrayList<>();
        for (AgentRunSystemPromptContributor contributor : runSystemPromptContributors) {
            try {
                contributor.contribute(context)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .ifPresent(additions::add);
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "Run system prompt contributor failed open, runId={}, traceId={}, contributorType={}, exceptionType={}",
                        run.runId().value(), traceId, contributor.getClass().getSimpleName(),
                        failure.getClass().getSimpleName());
            }
        }
        return additions.isEmpty() ? null : String.join("\n\n", additions);
    }

    private String combineSystemPrompt(String protectedPrompt, String contributedPrompt) {
        if (contributedPrompt == null || contributedPrompt.isBlank()) {
            return protectedPrompt;
        }
        return protectedPrompt + "\n\n[平台运行上下文]\n" + contributedPrompt;
    }

    private void notifyRootRunTerminalObservers(RunId runId, RunStatus status, String traceId) {
        for (AgentRootRunTerminalObserver observer : rootRunTerminalObservers) {
            try {
                observer.onTerminal(runId, status, traceId);
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "Root Run terminal observer failed open, runId={}, traceId={}, observerType={}, exceptionType={}",
                        runId.value(), traceId, observer.getClass().getSimpleName(),
                        failure.getClass().getSimpleName());
            }
        }
        if (automationReferenceRunLeaseLifecycle != null) {
            try {
                automationReferenceRunLeaseLifecycle.release(runId, traceId);
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "Automation reference Run lease release failed open, runId={}, traceId={}, exceptionType={}",
                        runId.value(), traceId, failure.getClass().getSimpleName());
            }
        }
    }

    private void acquireAutomationReferenceRunLeases(
            Run run,
            Workspace workspace,
            UserId userId,
            String traceId) {
        if (automationReferenceRunLeaseLifecycle != null) {
            automationReferenceRunLeaseLifecycle.acquire(run, workspace, userId, traceId);
        }
    }

    private record LegacyScheduledAnchorClaim(
            Run run,
            String dispatchMessageId,
            boolean managed,
            boolean accepted,
            boolean recovered) {
    }

    private record RunSource(
            ConversationSourceType type,
            String refId,
            String dispatchAttemptId) {
        static RunSource manual() {
            return new RunSource(ConversationSourceType.MANUAL, null, null);
        }

        static RunSource scheduled(String refId, String dispatchAttemptId) {
            return new RunSource(ConversationSourceType.SCHEDULED_TASK, refId, dispatchAttemptId);
        }
    }

    /** 注册条件只在写入本轮用户消息前判断，遍历分页结果而不是假定前 200 条足以代表会话历史。 */
    private boolean isFirstUserRun(SessionId sessionId) {
        if (sessionTitleWatchService == null) {
            return false;
        }
        int page = 1;
        final int size = 200;
        while (true) {
            var response = sessionMessageRepository.findBySessionId(sessionId, new PageRequest(page, size));
            if (response.items().stream().anyMatch(message -> message.role() == SessionMessageRole.USER)) {
                return false;
            }
            if (response.items().isEmpty() || (long) page * size >= response.total()) {
                return true;
            }
            page++;
        }
    }

    /** 仅 OpenCode 首轮且页面标题仍是首条消息临时标题时注册原生 title 监听。 */
    private RunSessionTitleWatchRegistry.TitleWatchToken registerFirstRunTitleWatch(
            String agentId,
            boolean firstUserRun,
            Session session,
            Run run,
            String prompt,
            AgentRuntime runtime,
            ExecutionNode node,
            Workspace workspace,
            String remoteSessionId) {
        if (sessionTitleWatchService == null || !"opencode".equals(agentId) || !firstUserRun) {
            return null;
        }
        String expectedTitle = OpencodeSessionTitlePolicy.initialPlatformTitle(prompt);
        if (!expectedTitle.equals(session.title())) {
            return null;
        }
        return sessionTitleWatchService.registerFirstRun(
                session.sessionId(),
                run.runId(),
                runtime,
                node,
                workspaceRootPath(workspace),
                null,
                remoteSessionId,
                expectedTitle);
    }

    /** 兼容 OpenCode 直出和 sync 包装事件的远端 session 字段。 */
    private Optional<String> eventSessionId(Map<String, Object> payload) {
        Optional<String> direct = firstMapText(payload, "sessionID", "sessionId");
        if (direct.isPresent()) {
            return direct;
        }
        return mapValue(payload.get("rawPayload"))
                .flatMap(rawPayload -> mapValue(rawPayload.get("properties")))
                .flatMap(properties -> firstMapText(properties, "sessionID", "sessionId"));
    }

    /**
     * 保存用户原始 prompt parts，历史对话可据此恢复本轮关联文件/选区 chip。
     */
    private static Optional<String> userPromptPartsJson(List<?> parts, String traceId) {
        if (parts == null || parts.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OBJECT_MAPPER.writeValueAsString(parts));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Failed to serialize user prompt parts, traceId={}", traceId, exception);
            return Optional.empty();
        }
    }

    /**
     * 查询 Session，不存在时转换为统一 NOT_FOUND 异常。
     */
    private Session findSession(SessionId sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value())));
    }

    /**
     * 查询 Workspace，不存在时转换为统一 NOT_FOUND 异常。
     */
    private Workspace findWorkspace(com.enterprise.testagent.domain.workspace.WorkspaceId workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value())));
    }

    /**
     * 解析本次 Run 的 agent 目标节点；已有远端 session 时强制粘滞到绑定节点。
     */
    private AgentRoutingTarget resolveAgentTarget(String agentId, Session session, RunId runId, Instant now, String traceId) {
        Optional<AgentSessionBinding> binding = runtimeTargetResolver.findAgentBinding(agentId, session, traceId);
        if (binding.isPresent()) {
            ExecutionNode node = executionNodeRepository.findById(binding.get().executionNodeId())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "会话绑定的 agent 执行节点不存在",
                            Map.of(
                                    "sessionId", session.sessionId().value(),
                                    "agentId", agentId,
                                    "nodeId", binding.get().executionNodeId().value())));
            if (!node.canAcceptRun()) {
                throw new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "会话绑定的 agent 执行节点不可用",
                        Map.of(
                                "sessionId", session.sessionId().value(),
                                "agentId", agentId,
                                "nodeId", node.executionNodeId().value(),
                                "status", node.status().name()));
            }
            return new AgentRoutingTarget(
                    node,
                    new RoutingDecision(runId, node.executionNodeId(), RoutingReason.STICKY_SESSION, now, traceId));
        }

        RoutingDecision decision = executionNodeRouter.route(
                runId,
                executionNodeRepository.findRoutableNodes(ROUTING_CANDIDATE_LIMIT),
                now,
                traceId);
        ExecutionNode node = executionNodeRepository.findById(decision.executionNodeId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "路由节点不存在",
                        Map.of("nodeId", decision.executionNodeId().value())));
        return new AgentRoutingTarget(node, decision);
    }

    /** 受保护 Agent 的 sticky 和首次路由都只接受服务器进程，禁止误选本地客户端投影节点。 */
    private AgentRoutingTarget resolveServerAgentTarget(
            String agentId,
            Session session,
            RunId runId,
            Instant now,
            String traceId) {
        Optional<AgentSessionBinding> binding = runtimeTargetResolver.findAgentBinding(agentId, session, traceId);
        if (binding.isPresent()) {
            ExecutionNode node = executionNodeRepository.findById(binding.get().executionNodeId())
                    .filter(candidate -> candidate.runtimeKind() == RuntimeKind.SERVER_PROCESS)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "受保护 Agent 绑定的服务器节点不存在"));
            if (!node.canAcceptRun()) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "受保护 Agent 绑定的服务器节点不可用");
            }
            return new AgentRoutingTarget(
                    node,
                    new RoutingDecision(runId, node.executionNodeId(), RoutingReason.STICKY_SESSION, now, traceId));
        }
        List<ExecutionNode> serverNodes = executionNodeRepository.findRoutableNodes(ROUTING_CANDIDATE_LIMIT).stream()
                .filter(node -> node.runtimeKind() == RuntimeKind.SERVER_PROCESS)
                .toList();
        RoutingDecision decision = executionNodeRouter.route(runId, serverNodes, now, traceId);
        ExecutionNode node = executionNodeRepository.findById(decision.executionNodeId())
                .filter(candidate -> candidate.runtimeKind() == RuntimeKind.SERVER_PROCESS)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "受保护 Agent 路由的服务器节点不存在"));
        return new AgentRoutingTarget(node, decision);
    }

    /**
     * 追加平台 RunEvent，统一封装 RunEventDraft 构造。
     */
    private void append(RunId runId, RunEventType type, String traceId, Instant occurredAt, Map<String, Object> payload) {
        RunEventDraft draft = sanitizeExperienceEventForRun(
                new RunEventDraft(runId, type, traceId, occurredAt, payload));
        recordRuntimeActivity(draft);
        runEventAppender.append(draft);
    }

    private void append(
            RunId runId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload,
            RunStorageMode storageMode) {
        append(runId, type, traceId, occurredAt, payload, storageMode, null);
    }

    private void append(
            RunId runId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload,
            RunStorageMode storageMode,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        RunEventDraft draft = sanitizeExperienceEventForRun(
                new RunEventDraft(runId, type, traceId, occurredAt, payload));
        recordRuntimeActivity(draft);
        runEventAppender.append(draft, storageMode, ownerLeaseIfPresent(ownership));
    }

    /**
     * 订阅 agent 事件流，事件处理串行 offload，避免阻塞 Netty 线程。
     */
    private void subscribeAgentEvents(
            String agentId,
            AgentRuntime runtime,
            Run run,
            String remoteSessionId,
            ExecutionNode node,
            Workspace workspace,
            RunStorageMode storageMode,
            String traceId,
            RunSessionTitleWatchRegistry.TitleWatchToken titleWatchToken) {
        subscribeAgentEvents(
                agentId, runtime, run, remoteSessionId, node, workspace, storageMode, traceId,
                titleWatchToken, null, null);
    }

    private void subscribeAgentEvents(
            String agentId,
            AgentRuntime runtime,
            Run run,
            String remoteSessionId,
            ExecutionNode node,
            Workspace workspace,
            RunStorageMode storageMode,
            String traceId,
            RunSessionTitleWatchRegistry.TitleWatchToken titleWatchToken,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership,
            String dispatchMessageId) {
        RunEventScopeContext rootScope = RunEventScopeContext.root(run.runId(), remoteSessionId);
        Flux<RunEventDraft> stream = Flux.defer(() -> runtime.streamRunEvents(new AgentStreamEventsCommand(
                        node,
                        run.runId(),
                        remoteSessionId,
                        workspaceRootPath(workspace),
                        null,
                        traceId)));
        if (titleWatchToken != null) {
            stream = stream
                    // 标题等待的意外断线只做一次冻结路由补偿，不允许把已经成功的 Run 回滚为失败。
                    .onErrorResume(error -> {
                        if (sessionTitleWatchService == null || !sessionTitleWatchService.isWaitingForTitle(titleWatchToken)) {
                            return Flux.error(error);
                        }
                        return Flux.fromStream(sessionTitleWatchService
                                .compensateAfterTitleWaitDisconnect(titleWatchToken, traceId)
                                .stream());
                    })
                    // SSE 正常结束但标题仍在后台生成时，以同一不可变 token 重连；取消后状态已变更，不会重连。
                    .repeatWhen(completions -> completions
                            .delayElements(TITLE_WAIT_RECONNECT_DELAY)
                            .takeWhile(ignored -> sessionTitleWatchService != null
                                    && sessionTitleWatchService.isWaitingForTitle(titleWatchToken)))
                    .takeUntilOther(titleWatchToken.cancellationSignal());
        }
        stream
                .filter(draft -> acceptsTitleWatchEvent(titleWatchToken, draft))
                // title agent 完成消息不是平台对话正文；在 scope router 前读取最终 session 标题并转换为 root session.updated。
                .concatMap(draft -> titleCompletionEvent(titleWatchToken, draft))
                .concatMap(draft -> Mono.fromCallable(() -> {
                            requireOwnedIfPresent(ownership);
                            return runSessionScopeRouter.route(
                                    rootScope,
                                    draft,
                                    storageMode,
                                    ownerLeaseIfPresent(ownership));
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable)
                        .onErrorResume(error -> {
                            LOGGER.warn(
                                    "Failed to route opencode stream event, runId={}, eventType={}, traceId={}, exceptionType={}",
                                    run.runId().value(),
                                    draft.type().wireName(),
                                    traceId,
                                    error.getClass().getSimpleName());
                            return storageMode == RunStorageMode.REDIS_SUMMARY
                                    ? Flux.error(error)
                                    : Flux.empty();
                        }))
                // 成功 root Run 会转入 TITLE_WAIT，直至原生标题或主动取消；失败仍按既有规则立即结束订阅。
                .takeUntil(draft -> shouldCloseAgentEventStream(titleWatchToken, draft))
                .takeUntilOther(ownership == null ? Mono.never() : ownership.lost())
                // opencode stream 来自 Netty 线程，事件入库或实时发布必须串行 offload，且本地 DB 抖动不能误判为 Run 失败。
                .concatMap(draft -> Mono.fromRunnable(
                                () -> {
                                    if (ownership != null) {
                                        ownerLeaseSupervisor.requireOwned(ownership);
                                    }
                                    appendStreamEvent(agentId, run, workspace, storageMode, draft, ownership);
                                })
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(error -> {
                            LOGGER.warn(
                                    "Failed to handle opencode stream event, runId={}, eventType={}, traceId={}, exceptionType={}",
                                    run.runId().value(),
                                    draft.type().wireName(),
                                    traceId,
                                    error.getClass().getSimpleName());
                            return storageMode == RunStorageMode.REDIS_SUMMARY
                                    ? Mono.error(error)
                                    : Mono.empty();
                        }))
                .doOnError(error -> {
                    if (!(error instanceof RunOwnershipLostException)) {
                        boolean scheduled = scheduleRuntimeLossConvergence(
                                agentId,
                                runtime,
                                run,
                                remoteSessionId,
                                node,
                                workspace,
                                storageMode,
                                dispatchMessageId,
                                traceId,
                                error);
                        if (!scheduled) {
                            failRunFromStream(agentId, run, storageMode, traceId, error, ownership);
                        }
                    }
                })
                .doFinally(ignored -> {
                    // 终态、owner 转移和 Redis 故障调度都必须释放本机 scope 状态，事实数据仍保留在 Redis。
                    runSessionScopeRouter.finishRun(run.runId());
                    if (ownership != null) {
                        try {
                            ownerLeaseSupervisor.release(ownership);
                        } catch (RuntimeException releaseError) {
                            LOGGER.warn(
                                    "Run owner lease 释放失败，等待 TTL，runId={}, traceId={}, exceptionType={}",
                                    run.runId().value(), traceId, releaseError.getClass().getSimpleName());
                        }
                    }
                })
                .subscribe(ignored -> {
                }, ignored -> {
                    // 错误已在 doOnError 中落库，这里消费异常以避免 Reactor dropped error 日志。
                });
    }

    /** 仅标题等待/读取态收窄事件；ACTIVE 或 CLOSED 都不得影响主 Run 事件流。 */
    private boolean acceptsTitleWatchEvent(
            RunSessionTitleWatchRegistry.TitleWatchToken token,
            RunEventDraft draft) {
        if (token == null
                || token.state() == RunSessionTitleWatchRegistry.State.ACTIVE
                || token.state() == RunSessionTitleWatchRegistry.State.CLOSED) {
            return true;
        }
        if (draft.type() == RunEventType.SESSION_UPDATED) {
            return eventSessionId(draft.payload())
                    .map(token.remoteSessionId()::equals)
                    .orElse(false);
        }
        return sessionTitleWatchService != null && sessionTitleWatchService.isCompletedTitleAgentMessage(token, draft);
    }

    private Mono<RunEventDraft> titleCompletionEvent(
            RunSessionTitleWatchRegistry.TitleWatchToken token,
            RunEventDraft draft) {
        if (sessionTitleWatchService == null || !sessionTitleWatchService.isCompletedTitleAgentMessage(token, draft)) {
            return Mono.just(draft);
        }
        return Mono.justOrEmpty(sessionTitleWatchService.completeTitleAgentMessage(token, draft));
    }

    /** root 成功后切换到 TITLE_WAIT；其他终态继续结束当前远端事件流。 */
    private boolean shouldCloseAgentEventStream(
            RunSessionTitleWatchRegistry.TitleWatchToken token,
            RunEventDraft draft) {
        if (draft.type() == RunEventType.RUN_SUCCEEDED
                && token != null
                && sessionTitleWatchService != null
                && sessionTitleWatchService.enterTitleWait(token)) {
            return false;
        }
        return draft.type() == RunEventType.RUN_SUCCEEDED || draft.type() == RunEventType.RUN_FAILED;
    }

    /** Redis 短暂抖动先等待 30 秒；延迟任务只捕获安全 ID、可信路径和节点快照。 */
    private boolean scheduleRuntimeLossConvergence(
            String agentId,
            AgentRuntime runtime,
            Run run,
            String remoteSessionId,
            ExecutionNode node,
            Workspace workspace,
            RunStorageMode storageMode,
            String dispatchMessageId,
            String traceId,
            Throwable error) {
        if (storageMode != RunStorageMode.REDIS_SUMMARY
                || runtimeLossScheduler == null
                || run.triggeredByUserId() == null
                || dispatchMessageId == null
                || !runtimeStateUnavailable(error)) {
            return false;
        }
        runtimeLossScheduler.schedule(
                new RunRuntimeLossRequest(
                        run.runId(),
                        run.sessionId(),
                        run.triggeredByUserId(),
                        run.messageSenderUserId(),
                        run.messageSenderUnifiedAuthId(),
                        run.messageSentBySharedUser(),
                        agentId,
                        dispatchMessageId,
                        remoteSessionId,
                        workspaceRootPath(workspace),
                        run.sourceType(),
                        run.sourceRefId(),
                        traceId),
                runtime,
                node);
        return true;
    }

    private boolean runtimeStateUnavailable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PlatformException platform
                    && (platform.errorCode() == ErrorCode.RUNTIME_STATE_UNAVAILABLE
                    || platform.errorCode() == ErrorCode.RUN_DETAILS_EXPIRED)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 处理单个 agent 事件：终态事件落库并更新 Run，瞬态消息事件只发布 live bus。
     */
    private void appendStreamEvent(
            String agentId,
            Run originalRun,
            Workspace workspace,
            RunStorageMode storageMode,
            RunEventDraft draft,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        RunEventDraft eventDraft = withUserMessageAttribution(
                originalRun,
                synchronizeRootSessionTitle(
                        originalRun,
                        sanitizeExperienceEvent(workspace, draft)));
        if (eventDraft.type() == RunEventType.RUN_SUCCEEDED && isTitleWatchPending(originalRun.runId())) {
            eventDraft = withPendingPlatformSessionTitle(eventDraft);
        }
        eventDraft = sanitizeExperienceEvent(workspace, eventDraft);
        recordRuntimeActivity(eventDraft);
        if (eventDraft.type() == RunEventType.RUN_SUCCEEDED || eventDraft.type() == RunEventType.RUN_FAILED) {
            RunStatus terminalStatus = eventDraft.type() == RunEventType.RUN_SUCCEEDED
                    ? RunStatus.SUCCEEDED
                    : RunStatus.FAILED;
            if (storageMode == RunStorageMode.REDIS_SUMMARY) {
                String terminalReasonCode = terminalStatus == RunStatus.SUCCEEDED
                        ? "COMPLETED"
                        : "REMOTE_FAILED";
                String safeErrorMessage = terminalStatus == RunStatus.FAILED
                        ? firstMapText(eventDraft.payload(), "message", "error").orElse(null)
                        : null;
                RunEventDraft terminalDraft = RunTerminalProjectionOutboxPayload.enrich(
                        eventDraft,
                        "REMOTE_ROOT",
                        terminalReasonCode,
                        safeErrorMessage,
                        false);
                runEventAppender.append(
                        runEventPersistencePolicy.sanitizeForPersistence(terminalDraft),
                        storageMode,
                        ownerLeaseIfPresent(ownership));
                // fenced terminal append 已把 manifest 原子推进终态，后续接管会被拒绝，可安全执行一次 DB CAS。
                runTerminalProjectionService.project(
                        originalRun.runId(),
                        terminalStatus,
                        "REMOTE_ROOT",
                        terminalReasonCode,
                        safeErrorMessage,
                        false,
                        eventDraft.traceId());
                notifyRootSessionErrorObservers(
                        originalRun.applyTerminalFact(terminalStatus, eventDraft.occurredAt()),
                        eventDraft);
                return;
            }
            Run current = runRepository.findById(originalRun.runId()).orElse(originalRun);
            // root run.succeeded/run.failed 是远端会话终态事实源；它允许纠正先到的 transport error 临时失败。
            Run terminal = current.applyTerminalFact(terminalStatus, eventDraft.occurredAt());
            Run saved = runRepository.save(terminal);
            runEventAppender.append(runEventPersistencePolicy.sanitizeForPersistence(eventDraft), storageMode);
            snapshotService.persistRunSnapshot(agentId, saved, eventDraft.traceId());
            notifyRootRunTerminalObservers(saved.runId(), saved.status(), eventDraft.traceId());
            notifyRootSessionErrorObservers(saved, eventDraft);
            return;
        }
        if (eventDraft.type() == RunEventType.MESSAGE_PART_UPDATED) {
            appendLiveDiffFromToolPart(originalRun, workspace, storageMode, eventDraft, ownership);
        }
        if (!runEventPersistencePolicy.shouldPersist(eventDraft)) {
            RunEventDraft sanitized = runEventPersistencePolicy.sanitizeForPersistence(eventDraft);
            if (!runEventAppender.publishTransient(
                    sanitized, storageMode, ownerLeaseIfPresent(ownership))) {
                // 兼容手工装配/旧测试构造器；生产 appender 与本服务注入同一个 live bus。
                runEventLiveBus.publishTransient(sanitized);
            }
            return;
        }
        runEventAppender.append(
                runEventPersistencePolicy.sanitizeForPersistence(eventDraft),
                storageMode,
                ownerLeaseIfPresent(ownership));
    }

    /**
     * OpenCode 不区分分享成员，因此只在平台事件投影中为 user message 补齐真实发送人，
     * 且以可信 Run 锚点覆盖远端同名字段，避免客户端被伪造归因误导。
     */
    private RunEventDraft withUserMessageAttribution(Run run, RunEventDraft draft) {
        if (draft.type() != RunEventType.MESSAGE_UPDATED || run.messageSenderUserId() == null) {
            return draft;
        }
        Object rawMessage = draft.payload().get("message");
        if (!(rawMessage instanceof Map<?, ?> message)
                || !"user".equalsIgnoreCase(String.valueOf(message.get("role")))) {
            return draft;
        }
        LinkedHashMap<String, Object> attributedMessage = new LinkedHashMap<>();
        message.forEach((key, value) -> attributedMessage.put(String.valueOf(key), value));
        attributedMessage.put("senderUserId", run.messageSenderUserId().value());
        if (run.messageSenderUnifiedAuthId() != null) {
            attributedMessage.put("senderUnifiedAuthId", run.messageSenderUnifiedAuthId());
        } else {
            attributedMessage.remove("senderUnifiedAuthId");
        }
        attributedMessage.put("sentBySharedUser", run.messageSentBySharedUser());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(draft.payload());
        payload.put("message", Map.copyOf(attributedMessage));
        payload.put("senderUserId", run.messageSenderUserId().value());
        if (run.messageSenderUnifiedAuthId() != null) {
            payload.put("senderUnifiedAuthId", run.messageSenderUnifiedAuthId());
        } else {
            payload.remove("senderUnifiedAuthId");
        }
        payload.put("sentBySharedUser", run.messageSentBySharedUser());
        return new RunEventDraft(
                draft.runId(), draft.type(), draft.traceId(), draft.occurredAt(),
                Map.copyOf(payload), draft.scopeContext());
    }

    private RunEventDraft sanitizeExperienceEvent(WorkspaceId workspaceId, RunEventDraft draft) {
        if (!ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspaceId)) {
            return draft;
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在"));
        return sanitizeExperienceEvent(workspace, draft);
    }

    /** 本地启动/传输错误等不经过 OpenCode stream 的事件也必须走同一体验投影边界。 */
    private RunEventDraft sanitizeExperienceEventForRun(RunEventDraft draft) {
        WorkspaceId workspaceId = null;
        if (runRuntimeStore != null) {
            workspaceId = runRuntimeStore.findManifest(draft.runId())
                    .map(RunRuntimeManifest::workspaceId)
                    .orElse(null);
        }
        if (workspaceId == null) {
            workspaceId = runRepository.findById(draft.runId())
                    .map(Run::workspaceId)
                    .orElse(null);
        }
        // anchor-only 恢复会在 Run 聚合回填前追加固定状态事件；该 payload 不含远端内容。
        return workspaceId == null ? draft : sanitizeExperienceEvent(workspaceId, draft);
    }

    /** 体验 Run 进入持久化或 live bus 前递归投影全部 payload，包含 tool input/output 与错误详情。 */
    @SuppressWarnings("unchecked")
    private RunEventDraft sanitizeExperienceEvent(Workspace workspace, RunEventDraft draft) {
        if (!ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspace.workspaceId())) {
            return draft;
        }
        Map<String, Object> safePayload = (Map<String, Object>) ExperienceWorkspacePathRedactor.redact(
                draft.payload(), workspace.rootPath());
        return new RunEventDraft(
                draft.runId(),
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                safePayload,
                draft.scopeContext());
    }

    /** 只接受 OpenCode mapper 从根 session.error 派生的失败事实；观察者失败不回滚 Run 终态。 */
    private void notifyRootSessionErrorObservers(Run terminalRun, RunEventDraft eventDraft) {
        if (eventDraft.type() != RunEventType.RUN_FAILED
                || !Boolean.TRUE.equals(eventDraft.payload().get("derived"))
                || !"session.error".equals(eventDraft.payload().get("derivedFromRawType"))) {
            return;
        }
        for (RunRootSessionErrorObserver observer : rootSessionErrorObservers) {
            try {
                observer.onRootSessionError(terminalRun, eventDraft);
            } catch (RuntimeException observerFailure) {
                LOGGER.warn(
                        "Root session.error observer failed, runId={}, traceId={}, exceptionType={}",
                        terminalRun.runId().value(), eventDraft.traceId(),
                        observerFailure.getClass().getSimpleName());
            }
        }
    }

    /**
     * 将 OpenCode 内置 title agent 发出的 root session.updated 标题回写到平台会话。
     */
    private RunEventDraft synchronizeRootSessionTitle(Run originalRun, RunEventDraft draft) {
        if (sessionTitleWatchService != null) {
            return sessionTitleWatchService.synchronizeNativeTitle(draft);
        }
        if (draft.type() != RunEventType.SESSION_UPDATED) {
            return draft;
        }
        RunEventScopeContext scope = draft.scopeContext();
        // 只有路由器已确认的 root scope 才能改平台标题，避免 child 或未知远端会话覆盖当前会话。
        if (scope == null
                || scope.childSession()
                || !scope.rootSessionId().equals(scope.sessionId())) {
            return draft;
        }
        try {
            return sessionUpdatedTitle(draft.payload())
                    // OpenCode 的默认标题只是 title agent 的执行前状态，不能覆盖页面的首条消息临时标题。
                    .filter(title -> !OpencodeSessionTitlePolicy.isDefaultTitle(title))
                    .flatMap(title -> sessionRepository.findById(originalRun.sessionId())
                            // 额外校验平台会话绑定的远端 root id，防止陈旧或错误路由的事件改写标题。
                            .filter(session -> scope.rootSessionId().equals(session.opencodeSessionId()))
                            .map(session -> {
                                sessionRepository.save(session.updateTitleAndPinned(
                                        title,
                                        session.pinned(),
                                        draft.occurredAt(),
                                        draft.traceId()));
                                return withSynchronizedPlatformSessionTitle(draft, title.trim());
                            }))
                    .orElse(draft);
        } catch (RuntimeException exception) {
            // 标题只是会话展示增强，仓储短暂故障不能阻断原始 session.updated 的持久化与 SSE 发布。
            LOGGER.warn(
                    "同步 OpenCode 会话标题失败，继续处理原始事件，runId={}, remoteSessionId={}, traceId={}",
                    originalRun.runId().value(),
                    scope.rootSessionId(),
                    draft.traceId(),
                    exception);
            return draft;
        }
    }

    /**
     * 标题成功保存后复制事件草稿，为前端提供不依赖 OpenCode 原始报文的同步结果。
     */
    private RunEventDraft withSynchronizedPlatformSessionTitle(RunEventDraft draft, String title) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(draft.payload());
        payload.put("platformSessionTitleSynchronized", true);
        payload.put("platformSessionTitle", title);
        return new RunEventDraft(
                draft.runId(),
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                payload,
                draft.scopeContext());
    }

    /** root 成功但原生 title 尚未到达时，通知前端继续消费同一 Run SSE。 */
    private RunEventDraft withPendingPlatformSessionTitle(RunEventDraft draft) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(draft.payload());
        payload.put("platformSessionTitlePending", true);
        return new RunEventDraft(
                draft.runId(),
                draft.type(),
                draft.traceId(),
                draft.occurredAt(),
                payload,
                draft.scopeContext());
    }

    private boolean isTitleWatchPending(RunId runId) {
        return sessionTitleWatchService != null
                && sessionTitleWatchService.tokenForRun(runId)
                        .map(token -> token.state() == RunSessionTitleWatchRegistry.State.TITLE_WAIT
                                || token.state() == RunSessionTitleWatchRegistry.State.TITLE_READING)
                        .orElse(false);
    }

    /**
     * 兼容 OpenCode 直出事件和 sync 包裹事件中的 session 标题字段。
     */
    private Optional<String> sessionUpdatedTitle(Map<String, Object> payload) {
        return mapValue(payload.get("info"))
                .flatMap(info -> textValue(info.get("title")))
                .or(() -> mapValue(payload.get("rawPayload"))
                        .flatMap(rawPayload -> mapValue(rawPayload.get("properties")))
                        .flatMap(properties -> mapValue(properties.get("info")))
                        .flatMap(info -> textValue(info.get("title"))));
    }

    /**
     * 维护 stale 收敛使用的轻量 Redis 状态。当前 ask 类事件只有 permission/question 两组，待回复期间不允许被定时任务判为超时。
     */
    private void recordRuntimeActivity(RunEventDraft draft) {
        try {
            RunEventType type = draft.type();
            if (OUTPUT_ACTIVITY_TYPES.contains(type)) {
                runActivityStateStore.recordOutput(draft.runId(), draft.occurredAt());
            }
            if (ASK_REQUEST_TYPES.contains(type)) {
                runActivityStateStore.markPendingAsk(draft.runId(), type, draft.occurredAt());
            }
            if (ASK_RESOLVED_TYPES.contains(type)) {
                runActivityStateStore.clearPendingAsk(draft.runId());
            }
            if (TERMINAL_TYPES.contains(type)) {
                runActivityStateStore.clearRunState(draft.runId());
            }
        } catch (RuntimeException exception) {
            LOGGER.debug(
                    "Run activity state update skipped, runId={}, eventType={}, traceId={}, exceptionType={}",
                    draft.runId().value(),
                    draft.type().wireName(),
                    draft.traceId(),
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 从 agent tool part 完成态派生轻量 Diff 事件，供前端在 Run 未结束时实时刷新文件树。
     */
    private void appendLiveDiffFromToolPart(
            Run originalRun,
            Workspace workspace,
            RunStorageMode storageMode,
            RunEventDraft draft,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        try {
            liveDiffFromToolPart(originalRun, workspace, draft)
                    .ifPresent(diff -> runEventAppender.append(
                            runEventPersistencePolicy.sanitizeForPersistence(diff),
                            storageMode,
                            ownerLeaseIfPresent(ownership)));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to derive live diff from tool part, runId={}, traceId={}, exceptionType={}",
                    originalRun.runId().value(),
                    draft.traceId(),
                    exception.getClass().getSimpleName());
            if (storageMode == RunStorageMode.REDIS_SUMMARY) {
                throw exception;
            }
        }
    }

    private Optional<RunEventDraft> liveDiffFromToolPart(
            Run originalRun,
            Workspace workspace,
            RunEventDraft draft) {
        Map<String, Object> rawPart = mapValue(draft.payload().get("part")).orElse(draft.payload());
        if (!"tool".equals(textValue(rawPart.get("type")).orElse(null))) {
            return Optional.empty();
        }
        Map<String, Object> state = mapValue(rawPart.get("state")).orElse(Map.of());
        String status = firstMapText(rawPart, "status").or(() -> firstMapText(state, "status")).orElse("");
        if (!"completed".equals(status)) {
            return Optional.empty();
        }
        String tool = firstMapText(rawPart, "toolName", "tool", "name").orElse("");
        if (!LIVE_DIFF_TOOLS.contains(tool)) {
            return Optional.empty();
        }
        Map<String, Object> input = mapValue(state.get("input"))
                .or(() -> mapValue(rawPart.get("input")))
                .orElse(Map.of());
        Map<String, Object> metadata = mapValue(state.get("metadata"))
                .or(() -> mapValue(rawPart.get("metadata")))
                .orElse(Map.of());
        List<LiveDiffFile> files = extractToolDiffFiles(tool, input, metadata, workspace);
        if (files.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RunEventDraft(
                originalRun.runId(),
                RunEventType.DIFF_PROPOSED,
                draft.traceId(),
                draft.occurredAt(),
                liveDiffPayload(tool, rawPart, draft.payload(), files)));
    }

    private Map<String, Object> liveDiffPayload(
            String tool,
            Map<String, Object> rawPart,
            Map<String, Object> rawPayload,
            List<LiveDiffFile> files) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "tool");
        payload.put("tool", tool);
        firstMapText(rawPayload, "messageID", "messageId")
                .or(() -> firstMapText(rawPart, "messageID", "messageId"))
                .ifPresent(messageId -> {
                    payload.put("messageID", messageId);
                    payload.put("messageId", messageId);
                });
        firstMapText(rawPayload, "partID", "partId")
                .or(() -> firstMapText(rawPart, "partID", "partId", "id"))
                .ifPresent(partId -> {
                    payload.put("partID", partId);
                    payload.put("partId", partId);
                });
        payload.put("files", files.stream().map(LiveDiffFile::toPayload).toList());
        return Map.copyOf(payload);
    }

    private List<LiveDiffFile> extractToolDiffFiles(
            String tool,
            Map<String, Object> input,
            Map<String, Object> metadata,
            Workspace workspace) {
        if ("edit".equals(tool)) {
            return mapValue(metadata.get("filediff"))
                    .flatMap(file -> diffFileFromMap(file, workspace, "modified"))
                    .map(List::of)
                    .orElse(List.of());
        }
        if ("apply_patch".equals(tool)) {
            Object filesValue = metadata.get("files");
            if (!(filesValue instanceof List<?> items)) {
                return List.of();
            }
            return items.stream()
                    .map(this::mapValue)
                    .flatMap(Optional::stream)
                    .map(file -> diffFileFromMap(file, workspace, statusFromPatchType(firstMapText(file, "type", "status").orElse(null))))
                    .flatMap(Optional::stream)
                    .toList();
        }
        String path = firstMapText(metadata, "filepath", "filePath", "path", "file")
                .or(() -> firstMapText(input, "filePath", "path", "file"))
                .flatMap(value -> normalizeWorkspacePath(workspace, value))
                .orElse(null);
        return path == null ? List.of() : List.of(new LiveDiffFile(path, "", 0, 0, "modified", false));
    }

    private Optional<LiveDiffFile> diffFileFromMap(Map<String, Object> value, Workspace workspace, String fallbackStatus) {
        Optional<String> path = firstMapText(value, "path", "file", "filePath", "relativePath")
                .flatMap(raw -> normalizeWorkspacePath(workspace, raw));
        if (path.isEmpty()) {
            return Optional.empty();
        }
        Optional<Integer> additions = numberValue(value.get("additions"));
        Optional<Integer> deletions = numberValue(value.get("deletions"));
        return Optional.of(new LiveDiffFile(
                path.get(),
                firstMapText(value, "patch", "diff").orElse(""),
                additions.orElse(0),
                deletions.orElse(0),
                firstMapText(value, "status").orElse(fallbackStatus),
                additions.isPresent() && deletions.isPresent()));
    }

    private String statusFromPatchType(String type) {
        if (type == null) {
            return "modified";
        }
        return switch (type) {
            case "add", "added", "create", "created" -> "added";
            case "delete", "deleted", "remove", "removed" -> "deleted";
            default -> "modified";
        };
    }

    private Optional<String> normalizeWorkspacePath(Workspace workspace, String rawPath) {
        if (rawPath == null || rawPath.isBlank() || "/dev/null".equals(rawPath)) {
            return Optional.empty();
        }
        String path = rawPath.replaceFirst("^([ab])/", "");
        String root = workspaceRootPath(workspace);
        if (path.equals(root) || path.startsWith(root + "/")) {
            path = path.substring(root.length()).replaceFirst("^/+", "");
        }
        path = path.replaceFirst("^\\./", "");
        return path.isBlank() || path.startsWith("/") ? Optional.empty() : Optional.of(path);
    }

    private Path workspaceRoot(Workspace workspace) {
        return workspacePathResolver.resolve(workspace.rootPath()).toAbsolutePath().normalize();
    }

    private String workspaceRootPath(Workspace workspace) {
        return workspaceRoot(workspace).toString();
    }

    private Optional<Map<String, Object>> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key instanceof String stringKey) {
                map.put(stringKey, item);
            }
        });
        return Optional.of(map);
    }

    private Optional<String> firstMapText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Optional<String> value = textValue(map.get(key));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> textValue(Object value) {
        return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    private Optional<Integer> numberValue(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 事件流异常时尽力把 Run 标记失败；失败落库本身异常只记录日志。
     */
    private void failRunFromStream(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error) {
        failRunFromStream(agentId, run, storageMode, traceId, error, null);
    }

    private void failRunFromStream(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        if (ErrorCode.LOCAL_CLIENT_DISCONNECTED.name().equals(platformErrorCode(error))) {
            // 本地连接断开后不得由恢复器重订阅或重发可能修改文件的请求，直接以稳定原因码终止。
            failRunFromStreamNow(agentId, run, storageMode, traceId, error, ownership);
            return;
        }
        if (isStreamingTransportError(error)) {
            scheduleRunFailureAfterTerminalGrace(
                    agentId, run, storageMode, traceId, error, "stream_transport", null);
            return;
        }
        failRunFromStreamNow(agentId, run, storageMode, traceId, error, ownership);
    }

    /**
     * 远程 prompt/command 已经可能产生业务副作用，调用响应异常只能作为候选失败。
     */
    private void failRunFromDispatch(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error) {
        scheduleRunFailureAfterTerminalGrace(
                agentId, run, storageMode, traceId, error, "dispatch_response", null);
    }

    private void scheduleRunFailureAfterTerminalGrace(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error,
            String source,
            RunOwnerLeaseSupervisor.OwnershipHandle retainedOwnership) {
        LOGGER.info(
                "Delay Run failure for terminal arbitration, runId={}, storageMode={}, source={}, errorCode={}, "
                        + "exceptionType={}, delayMs={}, traceId={}",
                run.runId().value(),
                storageMode,
                source,
                platformErrorCode(error),
                error.getClass().getSimpleName(),
                TRANSPORT_ERROR_TERMINAL_GRACE.toMillis(),
                traceId);
        Mono.delay(TRANSPORT_ERROR_TERMINAL_GRACE)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        ignored -> {
                            if (retainedOwnership == null) {
                                failRunAfterTransportGrace(
                                        agentId, run, storageMode, traceId, error, source);
                            } else {
                                failRunAfterDispatchGraceWithOwnership(
                                        agentId, run, storageMode, traceId, error, source, retainedOwnership);
                            }
                        },
                        delayedError -> LOGGER.warn(
                                "Failed to schedule delayed Run failure, runId={}, storageMode={}, source={}, "
                                        + "errorCode={}, traceId={}, exceptionType={}",
                                run.runId().value(),
                                storageMode,
                                source,
                                platformErrorCode(error),
                                traceId,
                                delayedError.getClass().getSimpleName()));
    }

    private String platformErrorCode(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PlatformException platformException) {
                return platformException.errorCode().name();
            }
            current = current.getCause();
        }
        return "UNKNOWN";
    }

    /** transport grace 到期后重新竞争 owner；其它 Java 已接管时旧执行者不得落终态。 */
    private void failRunAfterTransportGrace(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error,
            String source) {
        if (storageMode != RunStorageMode.REDIS_SUMMARY || ownerLeaseSupervisor == null) {
            Run current = runRepository.findById(run.runId()).orElse(run);
            if (current.status().isTerminal()) {
                logDelayedFailureSuppressed(run, storageMode, traceId, source, error, current.status());
                return;
            }
            logDelayedFailurePersisting(run, storageMode, traceId, source, error);
            failRunFromStreamNow(agentId, run, storageMode, traceId, error, null);
            return;
        }
        RunOwnerLeaseSupervisor.OwnershipHandle ownership = null;
        try {
            RunRuntimeManifest expected = runRuntimeStore.findManifest(run.runId()).orElse(null);
            if (expected == null || !expected.active()) {
                logDelayedFailureSuppressed(
                        run,
                        storageMode,
                        traceId,
                        source,
                        error,
                        expected == null ? null : expected.status());
                return;
            }
            ownership = runRuntimeStore
                    .claimOwnerLeaseIfUnchanged(expected, backendInstanceIdentity.backendProcessId())
                    .flatMap(ownerLeaseSupervisor::adopt)
                    .orElse(null);
            if (ownership == null) {
                LOGGER.info(
                        "Suppress delayed Run failure because owner changed, runId={}, storageMode={}, source={}, "
                                + "errorCode={}, traceId={}",
                        run.runId().value(),
                        storageMode,
                        source,
                        platformErrorCode(error),
                        traceId);
                return;
            }
            logDelayedFailurePersisting(run, storageMode, traceId, source, error);
            failRunFromStreamNow(agentId, run, storageMode, traceId, error, ownership);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Delayed Run stream failure deferred to current owner, runId={}, storageMode={}, source={}, "
                            + "errorCode={}, traceId={}, exceptionType={}",
                    run.runId().value(), storageMode, source, platformErrorCode(error), traceId,
                    exception.getClass().getSimpleName());
        } finally {
            if (ownership != null) {
                releaseOwnershipBestEffort(ownership, run.runId(), traceId);
            }
        }
    }

    /** dispatch 响应异常期间保留原 owner，避免提前关闭仍可达的 root 事件流。 */
    private void failRunAfterDispatchGraceWithOwnership(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error,
            String source,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        try {
            RunRuntimeManifest manifest = runRuntimeStore.findManifest(run.runId()).orElse(null);
            if (manifest == null || !manifest.active()) {
                logDelayedFailureSuppressed(
                        run,
                        storageMode,
                        traceId,
                        source,
                        error,
                        manifest == null ? null : manifest.status());
                return;
            }
            ownerLeaseSupervisor.requireOwned(ownership);
            logDelayedFailurePersisting(run, storageMode, traceId, source, error);
            if (failRunFromStreamNow(agentId, run, storageMode, traceId, error, ownership)) {
                releaseOwnershipBestEffort(ownership, run.runId(), traceId);
            }
        } catch (RunOwnershipLostException ignored) {
            LOGGER.info(
                    "Suppress delayed Run failure after ownership transfer, runId={}, storageMode={}, source={}, "
                            + "errorCode={}, traceId={}",
                    run.runId().value(),
                    storageMode,
                    source,
                    platformErrorCode(error),
                    traceId);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Defer delayed Run failure because runtime state is unavailable, runId={}, storageMode={}, "
                            + "source={}, errorCode={}, traceId={}, exceptionType={}",
                    run.runId().value(), storageMode, source, platformErrorCode(error), traceId,
                    exception.getClass().getSimpleName());
        }
    }

    private void logDelayedFailureSuppressed(
            Run run,
            RunStorageMode storageMode,
            String traceId,
            String source,
            Throwable error,
            RunStatus terminalStatus) {
        LOGGER.info(
                "Suppress delayed Run failure because root terminal already won, runId={}, storageMode={}, "
                        + "source={}, terminalStatus={}, errorCode={}, traceId={}",
                run.runId().value(),
                storageMode,
                source,
                terminalStatus,
                platformErrorCode(error),
                traceId);
    }

    private void logDelayedFailurePersisting(
            Run run,
            RunStorageMode storageMode,
            String traceId,
            String source,
            Throwable error) {
        LOGGER.warn(
                "Persist delayed Run failure after terminal grace, runId={}, storageMode={}, source={}, "
                        + "errorCode={}, exceptionType={}, traceId={}",
                run.runId().value(),
                storageMode,
                source,
                platformErrorCode(error),
                error.getClass().getSimpleName(),
                traceId);
    }

    private boolean failRunFromStreamNow(
            String agentId,
            Run run,
            RunStorageMode storageMode,
            String traceId,
            Throwable error,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership) {
        boolean terminalPersisted = false;
        try {
            String safeMessage = safeRunErrorMessage(run, error);
            if (storageMode == RunStorageMode.REDIS_SUMMARY) {
                RunRuntimeManifest manifest = runRuntimeStore.findManifest(run.runId()).orElse(null);
                if (manifest == null || manifest.status().isTerminal()) {
                    return false;
                }
                Instant occurredAt = Instant.now();
                boolean localDisconnected =
                        ErrorCode.LOCAL_CLIENT_DISCONNECTED.name().equals(platformErrorCode(error));
                String terminalReason = localDisconnected
                        ? ErrorCode.LOCAL_CLIENT_DISCONNECTED.name()
                        : "TRANSPORT_ERROR";
                if (ownership != null) {
                    ownerLeaseSupervisor.requireOwned(ownership);
                }
                append(run.runId(), RunEventType.RUN_FAILED, traceId, occurredAt,
                        RunTerminalProjectionOutboxPayload.payload(
                                Map.of(
                                        "error", Map.of(
                                                "name", error.getClass().getSimpleName(),
                                                "message", safeMessage),
                                        "message", safeMessage),
                                terminalReason,
                                localDisconnected ? "LOCAL_CLIENT_DISCONNECTED" : "STREAM_ERROR",
                                safeMessage,
                                false),
                        RunStorageMode.REDIS_SUMMARY,
                        ownership);
                terminalPersisted = true;
                // fenced terminal append 成功后 manifest 已终态，不再允许其它 owner 接管。
                runTerminalProjectionService.project(
                        run.runId(),
                        RunStatus.FAILED,
                        terminalReason,
                        localDisconnected ? "LOCAL_CLIENT_DISCONNECTED" : "STREAM_ERROR",
                        safeMessage,
                        false,
                        traceId);
                return true;
            }
            Run current = runRepository.findById(run.runId()).orElse(run);
            if (!current.status().isTerminal()) {
                Instant occurredAt = Instant.now();
                Run failed = current.fail(occurredAt);
                Optional<Run> saved = saveRunIfStatus(failed, current.status(), traceId, "stream_error");
                if (saved.isPresent()) {
                    Run savedRun = saved.orElseThrow();
                    terminalPersisted = true;
                    append(savedRun.runId(), RunEventType.RUN_FAILED, traceId, occurredAt,
                            Map.of(
                                    "error", Map.of(
                                            "name", error.getClass().getSimpleName(),
                                            "message", safeMessage),
                                    "message", safeMessage));
                    snapshotService.persistRunSnapshot(agentId, savedRun, traceId);
                }
            }
            return terminalPersisted;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to persist opencode stream failure, runId={}, traceId={}, exceptionType={}",
                    run.runId().value(), traceId, exception.getClass().getSimpleName());
            return terminalPersisted;
        } finally {
            runSessionScopeRouter.finishRun(run.runId());
        }
    }

    private boolean isStreamingTransportError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("streaming response failed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 事件流错误会展示给前端，只保留单行短消息，避免泄露堆栈、路径或过长第三方响应。
     */
    private String safeStreamErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        String firstLine = message.lines().findFirst().orElse(error.getClass().getSimpleName()).trim();
        if (firstLine.length() <= 300) {
            return firstLine;
        }
        return firstLine.substring(0, 300);
    }

    private String safeWorkspaceErrorMessage(Workspace workspace, Throwable error) {
        String safeMessage = safeStreamErrorMessage(error);
        if (!ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspace.workspaceId())) {
            return safeMessage;
        }
        return ExperienceWorkspacePathRedactor.redactText(safeMessage, workspace.rootPath());
    }

    private String safeRunErrorMessage(Run run, Throwable error) {
        if (!ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(run.workspaceId())) {
            return safeStreamErrorMessage(error);
        }
        return workspaceRepository.findById(run.workspaceId())
                .map(workspace -> safeWorkspaceErrorMessage(workspace, error))
                .orElse("体验工作区运行失败");
    }

    /** 体验启动错误返回前移除物理根与原始 cause，统一异常日志也无法回溯出真实目录。 */
    @SuppressWarnings("unchecked")
    private PlatformException sanitizeExperienceException(Workspace workspace, PlatformException exception) {
        if (!ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(workspace.workspaceId())) {
            return exception;
        }
        Map<String, Object> safeDetails = (Map<String, Object>) ExperienceWorkspacePathRedactor.redact(
                exception.details(), workspace.rootPath());
        return new PlatformException(
                exception.errorCode(),
                ExperienceWorkspacePathRedactor.redactText(exception.getMessage(), workspace.rootPath()),
                safeDetails);
    }

    /**
     * 条件保存 Run 状态，CAS 失败代表已有更新先落库，不能再追加冲突终态事件或快照。
     */
    private Optional<Run> saveRunIfStatus(Run candidate, RunStatus expectedStatus, String traceId, String reason) {
        Run saved = runRepository.saveIfStatus(candidate, expectedStatus);
        if (saved == candidate) {
            return Optional.of(saved);
        }
        LOGGER.info(
                "Skipped stale Run status write, runId={}, expectedStatus={}, requestedStatus={}, actualStatus={}, reason={}, traceId={}",
                candidate.runId().value(),
                expectedStatus.name(),
                candidate.status().name(),
                saved.status().name(),
                reason,
                traceId);
        return Optional.empty();
    }

    private record AgentRoutingTarget(ExecutionNode node, RoutingDecision decision) {
    }

    private record LiveDiffFile(
            String path,
            String patch,
            int additions,
            int deletions,
            String status,
            boolean countsKnown) {

        private Map<String, Object> toPayload() {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", path);
            payload.put("patch", patch);
            payload.put("additions", additions);
            payload.put("deletions", deletions);
            payload.put("status", status);
            return Map.copyOf(payload);
        }
    }

    private record ModelSelection(String providerId, String modelId) {
    }

    private record ModelCatalogEntry(String providerId, String modelId, boolean defaultModel) {
    }
}
