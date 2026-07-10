package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.run.ConversationContextIssueLease;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.domain.workspace.TrustedWorkspaceResolver;
import com.icbc.testagent.domain.workspace.TrustedWorkspaceResolution;
import com.icbc.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 会话运行上下文签发与校验服务。
 *
 * <p>bootstrap 只从平台权威仓储、当前 READY 用户进程和后端路径解析器构造上下文；客户端只持有 opaque token。
 */
@Service
public class ConversationContextApplicationService {

    private static final int CONTEXT_VERSION = 1;
    private static final int TOKEN_RANDOM_BYTES = 32;

    private final SessionRepository sessionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final AgentSessionBindingRepository bindingRepository;
    private final ManagedWorkspacePathResolver pathResolver;
    private final TrustedWorkspaceResolver trustedWorkspaceResolver;
    private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;
    private final ConversationContextStore contextStore;
    private final Clock clock;
    private final Supplier<String> tokenFactory;

    /**
     * 生产构造器使用 256-bit SecureRandom token 和 UTC 时钟。
     */
    @Autowired
    public ConversationContextApplicationService(
            SessionRepository sessionRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            AgentSessionBindingRepository bindingRepository,
            TrustedWorkspaceResolver trustedWorkspaceResolver,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            ConversationContextStore contextStore) {
        this(
                sessionRepository,
                assignmentService,
                bindingRepository,
                trustedWorkspaceResolver,
                workspaceAccessAuthorizer,
                contextStore,
                Clock.systemUTC(),
                secureTokenFactory());
    }

    ConversationContextApplicationService(
            SessionRepository sessionRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            AgentSessionBindingRepository bindingRepository,
            TrustedWorkspaceResolver trustedWorkspaceResolver,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            ConversationContextStore contextStore,
            Clock clock,
            Supplier<String> tokenFactory) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.workspaceRepository = null;
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository must not be null");
        this.pathResolver = null;
        this.trustedWorkspaceResolver = Objects.requireNonNull(
                trustedWorkspaceResolver,
                "trustedWorkspaceResolver must not be null");
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
        this.contextStore = Objects.requireNonNull(contextStore, "contextStore must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.tokenFactory = Objects.requireNonNull(tokenFactory, "tokenFactory must not be null");
    }

    /**
     * 可注入时钟和 token factory 的构造器用于稳定覆盖过期与签发测试。
     */
    ConversationContextApplicationService(
            SessionRepository sessionRepository,
            WorkspaceRepository workspaceRepository,
            UserOpencodeProcessAssignmentService assignmentService,
            AgentSessionBindingRepository bindingRepository,
            ManagedWorkspacePathResolver pathResolver,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            ConversationContextStore contextStore,
            Clock clock,
            Supplier<String> tokenFactory) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.trustedWorkspaceResolver = null;
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
        this.contextStore = Objects.requireNonNull(contextStore, "contextStore must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.tokenFactory = Objects.requireNonNull(tokenFactory, "tokenFactory must not be null");
    }

    /**
     * 权威读取会话、工作区、当前用户进程与可复用远端绑定后签发上下文。
     */
    public IssuedConversationContext bootstrap(
            UserId userId,
            String agentId,
            SessionId sessionId,
            String traceId) {
        return bootstrap(userId, agentId, sessionId, traceId, true);
    }

    /**
     * 历史 workspace 首次安全回绑会主动提升代次；允许一次完整权威重读，禁止无限重试掩盖持续竞态。
     */
    private IssuedConversationContext bootstrap(
            UserId userId,
            String agentId,
            SessionId sessionId,
            String traceId,
            boolean retryOnInvalidation) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        String normalizedAgentId = normalizeAgentId(agentId);
        ConversationContextIssueLease issueLease = contextStore.beginIssue(userId, sessionId);
        Session session = sessionRepository.findById(sessionId)
                .filter(item -> item.status() == SessionStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话不存在"));
        if (session.createdByUserId() != null && !session.createdByUserId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权为该会话创建运行上下文");
        }
        // 权限校验必须先于历史 Workspace 回绑，避免已撤权用户触发任何可信绑定写入。
        workspaceAccessAuthorizer.requireAccess(userId, session.workspaceId());
        TrustedWorkspaceResolution workspaceResolution = trustedWorkspace(session, traceId);
        Workspace trustedWorkspace = workspaceResolution.workspace();
        if (workspaceResolution.bindingChanged()) {
            if (!retryOnInvalidation) {
                throw new PlatformException(
                        ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                        "Workspace 可信绑定持续变化，请重试");
            }
            // 自回绑已主动提升代次，本轮不尝试保存；全新租约会重新读取 Session、Workspace 和成员权限。
            return bootstrap(userId, normalizedAgentId, sessionId, traceId, false);
        }
        UserOpencodeProcessAssignment assignment =
                assignmentService.requireReadyProcess(userId, normalizedAgentId, traceId);
        OpencodeServerProcess processSnapshot = requireProcessSnapshot(assignment);
        String processId = processSnapshot.processId().value();
        if (trustedWorkspace.linuxServerId() == null
                || !trustedWorkspace.linuxServerId().equals(assignment.linuxServerId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Workspace 与用户 TestAgent 进程不在同一服务器",
                    Map.of(
                            "workspaceId", trustedWorkspace.workspaceId().value(),
                            "workspaceLinuxServerId", String.valueOf(trustedWorkspace.linuxServerId()),
                            "processLinuxServerId", assignment.linuxServerId()));
        }
        AgentSessionBinding bindingSnapshot = bindingRepository.findBySessionIdAndAgentId(sessionId, normalizedAgentId)
                .filter(binding -> binding.executionNodeId().equals(assignment.node().executionNodeId()))
                .orElseGet(() -> legacyBindingSnapshot(
                        session,
                        normalizedAgentId,
                        assignment.node().executionNodeId(),
                        traceId));
        Instant expiresAt = clock.instant().plus(ConversationContextStore.CONTEXT_TTL);
        ConversationRunContext context = new ConversationRunContext(
                userId,
                normalizedAgentId,
                processId,
                assignment.linuxServerId(),
                processSnapshot,
                session,
                trustedWorkspace,
                assignment.node(),
                bindingSnapshot,
                CONTEXT_VERSION,
                expiresAt);
        String contextToken = tokenFactory.get();
        if (contextToken == null || contextToken.isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "会话运行上下文签发失败");
        }
        if (!contextStore.saveIfCurrent(contextToken, context, issueLease)) {
            throw new PlatformException(
                    ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                    "会话运行上下文在签发期间已失效，请重试");
        }
        return new IssuedConversationContext(contextToken, context);
    }

    private TrustedWorkspaceResolution trustedWorkspace(Session session, String traceId) {
        Workspace workspace;
        if (trustedWorkspaceResolver != null) {
            TrustedWorkspaceResolution resolution = trustedWorkspaceResolver.resolveTrustedWorkspace(
                    session.workspaceId(),
                    traceId);
            workspace = resolution.workspace();
            if (workspace.status() != WorkspaceStatus.ACTIVE) {
                throw new PlatformException(ErrorCode.NOT_FOUND, "工作区不存在");
            }
            return resolution;
        } else {
            workspace = workspaceRepository.findById(session.workspaceId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "工作区不存在"));
            workspace = pathResolver.withResolvedRootPath(workspace);
        }
        if (workspace.status() != WorkspaceStatus.ACTIVE) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "工作区不存在");
        }
        return new TrustedWorkspaceResolution(workspace, false);
    }

    /**
     * 校验 token 与当前认证用户、agent、会话及版本绑定，拒绝客户端借用其它上下文。
     */
    public ConversationRunContext require(
            String contextToken,
            UserId userId,
            String agentId,
            SessionId sessionId) {
        if (contextToken == null || contextToken.isBlank()) {
            throw new PlatformException(ErrorCode.CONVERSATION_CONTEXT_REQUIRED);
        }
        String normalizedAgentId = normalizeAgentId(agentId);
        String normalizedToken = contextToken.trim();
        ConversationRunContext context = contextStore.peek(normalizedToken)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONVERSATION_CONTEXT_EXPIRED));
        if (!matches(context, userId, normalizedAgentId, sessionId)) {
            throw new PlatformException(
                    ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                    "会话运行上下文已过期或与当前请求不匹配");
        }
        ConversationRunContext refreshed = contextStore.touch(normalizedToken, context)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONVERSATION_CONTEXT_EXPIRED));
        if (!matches(refreshed, userId, normalizedAgentId, sessionId)) {
            throw new PlatformException(
                    ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                    "会话运行上下文已过期或与当前请求不匹配");
        }
        return refreshed;
    }

    /**
     * 会话归档、权限或进程归属变化时按用户与会话清理所有已签发 token。
     */
    public void invalidate(UserId userId, SessionId sessionId) {
        contextStore.invalidate(userId, sessionId);
    }

    /**
     * 动态健康确认进程不可用时，按进程反向索引清除所有关联 token。
     */
    public void invalidateProcess(String processId) {
        contextStore.invalidateProcess(processId);
    }

    private static OpencodeServerProcess requireProcessSnapshot(UserOpencodeProcessAssignment assignment) {
        if (assignment == null
                || assignment.linuxServerId() == null
                || assignment.node() == null
                || assignment.processSnapshot() == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "当前用户 TestAgent 进程缺少可信运行归属",
                    Map.of());
        }
        String nodeId = assignment.node().executionNodeId().value();
        if (!nodeId.startsWith("node_ocp_")) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "当前用户 TestAgent 进程缺少可信进程标识",
                    Map.of("executionNodeId", nodeId));
        }
        String processId = nodeId.substring("node_".length());
        if (!assignment.processSnapshot().processId().value().equals(processId)
                || !assignment.processSnapshot().linuxServerId().value().equals(assignment.linuxServerId())) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "当前用户 TestAgent 进程快照与运行目标不匹配",
                    Map.of("processId", processId));
        }
        return assignment.processSnapshot();
    }

    private static AgentSessionBinding legacyBindingSnapshot(
            Session session,
            String agentId,
            com.icbc.testagent.domain.node.ExecutionNodeId executionNodeId,
            String traceId) {
        if (!session.hasOpencodeSessionMapping()
                || !session.opencodeExecutionNodeId().equals(executionNodeId)) {
            return null;
        }
        return new AgentSessionBinding(
                session.sessionId(),
                agentId,
                session.opencodeSessionId(),
                executionNodeId,
                session.createdAt(),
                session.updatedAt(),
                traceId);
    }

    private static String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "agentId 不能为空");
        }
        return agentId.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(
            ConversationRunContext context,
            UserId userId,
            String agentId,
            SessionId sessionId) {
        return context.userId().equals(userId)
                && context.sessionId().equals(sessionId)
                && context.agentId().equals(agentId)
                && context.contextVersion() == CONTEXT_VERSION
                && context.expiresAt().isAfter(clock.instant());
    }

    private static Supplier<String> secureTokenFactory() {
        SecureRandom secureRandom = new SecureRandom();
        return () -> {
            byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
            secureRandom.nextBytes(randomBytes);
            return "ctx_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        };
    }

    /**
     * 签发结果仅向 API 暴露 opaque token、版本和过期时间，不返回内部进程地址或可信路径。
     */
    public record IssuedConversationContext(String contextToken, ConversationRunContext context) {

        public IssuedConversationContext {
            if (contextToken == null || contextToken.isBlank()) {
                throw new IllegalArgumentException("contextToken must not be blank");
            }
            Objects.requireNonNull(context, "context must not be null");
        }
    }
}
