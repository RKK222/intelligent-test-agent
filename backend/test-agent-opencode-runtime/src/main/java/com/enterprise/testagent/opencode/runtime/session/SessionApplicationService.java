package com.enterprise.testagent.opencode.runtime.session;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionHistoryItem;
import com.enterprise.testagent.domain.session.SessionHistoryRepository;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.session.SessionRuntimeTarget;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextSessionRevocation;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.UserWorkspaceQueryRepository;
import com.enterprise.testagent.opencode.runtime.run.RunSessionMessageSnapshotService;
import com.enterprise.testagent.opencode.runtime.run.RunSessionTitleWatchService;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionSessionLockGuard;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session 应用服务，负责编排会话和平台消息持久化，Controller 不直接访问 Repository。
 */
@Service
public class SessionApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionApplicationService.class);

    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final SessionHistoryRepository sessionHistoryRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final RunSessionMessageSnapshotService snapshotService;
    private final RunSessionTitleWatchService titleWatchService;
    private final ConversationContextStore conversationContextStore;
    private NightExecutionSessionLockGuard nightExecutionLockGuard;
    private RunResendRepository runResendRepository;
    private UserWorkspaceQueryRepository userWorkspaceQueryRepository;
    private UserNotificationApplicationService notificationService;
    private ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer;
    private LocalClientWorkspaceRepository localClientWorkspaceRepository;
    private SessionRuntimeTargetRepository sessionRuntimeTargetRepository;

    /**
     * 创建 Session 应用服务，Controller 不直接访问这些仓储实现。
     */
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionHistoryRepository sessionHistoryRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService) {
        this(
                workspaceRepository,
                sessionRepository,
                sessionHistoryRepository,
                sessionMessageRepository,
                snapshotService,
                null,
                null);
    }

    /**
     * 生产构造器同时注入会话上下文存储和原生标题监听，使归档在一个业务入口完成两类运行态收敛。
     */
    @Autowired
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionHistoryRepository sessionHistoryRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService,
            ConversationContextStore conversationContextStore,
            RunSessionTitleWatchService titleWatchService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.sessionHistoryRepository = Objects.requireNonNull(sessionHistoryRepository, "sessionHistoryRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.snapshotService = snapshotService;
        this.titleWatchService = titleWatchService;
        this.conversationContextStore = conversationContextStore;
    }

    /** 兼容只接入会话上下文存储的既有调用方。 */
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionHistoryRepository sessionHistoryRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService,
            ConversationContextStore conversationContextStore) {
        this(
                workspaceRepository,
                sessionRepository,
                sessionHistoryRepository,
                sessionMessageRepository,
                snapshotService,
                conversationContextStore,
                null);
    }

    /** 兼容只接入标题监听的既有调用方。 */
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionHistoryRepository sessionHistoryRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionTitleWatchService titleWatchService) {
        this(
                workspaceRepository,
                sessionRepository,
                sessionHistoryRepository,
                sessionMessageRepository,
                snapshotService,
                null,
                titleWatchService);
    }

    /**
     * 创建兼容旧测试的服务实例，历史查询端口缺失时禁止调用用户级历史方法。
     */
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.sessionHistoryRepository = null;
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.snapshotService = snapshotService;
        this.titleWatchService = null;
        this.conversationContextStore = null;
    }

    /** 为会话变更测试和非 Web 调用方提供可选标题监听取消服务。 */
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionTitleWatchService titleWatchService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.sessionHistoryRepository = null;
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.snapshotService = snapshotService;
        this.titleWatchService = titleWatchService;
        this.conversationContextStore = null;
    }

    /**
     * 创建兼容旧测试的服务实例，未传快照服务时只读取数据库快照。
     */
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository) {
        this(workspaceRepository, sessionRepository, sessionMessageRepository, null);
    }

    /**
     * 在指定 Workspace 下创建平台 Session，创建前先确认 Workspace 存在。
     */
    @Transactional
    public Session createSession(WorkspaceId workspaceId, String title, String traceId) {
        return createSession(null, workspaceId, title, traceId);
    }

    /**
     * 在指定 Workspace 下创建当前用户的 Session，并记录创建人归因供运营统计使用。
     */
    @Transactional
    public Session createSession(UserId userId, WorkspaceId workspaceId, String title, String traceId) {
        requireUserWorkspace(userId, workspaceId);
        if (workspaceRepository.findById(workspaceId).isEmpty()) {
            LOGGER.warn("Cannot create session: workspace not found, workspaceId={}, traceId={}", workspaceId.value(), traceId);
            throw new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value()));
        }
        Instant now = Instant.now();
        Session draft = new Session(
                new SessionId(RuntimeIdGenerator.sessionId()),
                workspaceId,
                title,
                SessionStatus.ACTIVE,
                now,
                now,
                traceId);
        Session session = sessionRepository.save(userId == null
                ? draft
                : draft.withSource(ConversationSourceType.MANUAL, null, userId));
        freezeRuntimeTarget(session, workspaceId);
        LOGGER.info("Session created, sessionId={}, workspaceId={}, title={}, traceId={}",
                session.sessionId().value(), workspaceId.value(), title, traceId);
        return session;
    }

    /** 新会话与本地工作区实例绑定，后续断线或换代都不得转去服务端 OpenCode。 */
    private void freezeRuntimeTarget(Session session, WorkspaceId workspaceId) {
        if (sessionRuntimeTargetRepository == null) {
            return;
        }
        var localBinding = localClientWorkspaceRepository == null
                ? null
                : localClientWorkspaceRepository.findByWorkspaceId(workspaceId).orElse(null);
        sessionRuntimeTargetRepository.save(localBinding == null
                ? SessionRuntimeTarget.server(session.sessionId())
                : new SessionRuntimeTarget(
                        session.sessionId(),
                        com.enterprise.testagent.domain.runtime.RuntimeKind.LOCAL_CLIENT,
                        localBinding.clientInstanceId()));
    }

    /** 可选装配保留既有纯单元测试构造器；生产环境始终冻结 session 运行目标。 */
    @Autowired(required = false)
    void configureRuntimeTargetRepositories(
            LocalClientWorkspaceRepository localClientWorkspaceRepository,
            SessionRuntimeTargetRepository sessionRuntimeTargetRepository) {
        this.localClientWorkspaceRepository = Objects.requireNonNull(
                localClientWorkspaceRepository, "localClientWorkspaceRepository must not be null");
        this.sessionRuntimeTargetRepository = Objects.requireNonNull(
                sessionRuntimeTargetRepository, "sessionRuntimeTargetRepository must not be null");
    }

    /** 历史 Session 缺少新字段时按服务端目标解释，保持旧数据可反序列化。 */
    public SessionRuntimeTarget runtimeTarget(SessionId sessionId) {
        return sessionRuntimeTargetRepository == null
                ? SessionRuntimeTarget.server(sessionId)
                : sessionRuntimeTargetRepository.findBySessionId(sessionId)
                        .orElseGet(() -> SessionRuntimeTarget.server(sessionId));
    }

    /**
     * 查询未归档 Session；归档 Session 对外按不存在处理。
     */
    public Session getSession(SessionId sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value())));
        if (session.status() == SessionStatus.ARCHIVED) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value()));
        }
        return session;
    }

    /** 按用户归因规则读取单个会话；不向调用方暴露“存在但不属于当前用户”的差异。 */
    public Session getSession(UserId userId, SessionId sessionId) {
        return getSession(userId, sessionId, false);
    }

    /**
     * 排查入口可显式读取用户已软删除的会话，普通用户入口仍把 ARCHIVED 视为不存在。
     */
    public Session getSession(UserId userId, SessionId sessionId, boolean includeArchived) {
        if (sessionHistoryRepository == null) {
            throw new IllegalStateException("sessionHistoryRepository must be provided for user session query");
        }
        return sessionHistoryRepository.findUserSession(userId, sessionId, includeArchived)
                .map(SessionHistoryItem::session)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Session 不存在",
                        Map.of("sessionId", sessionId.value())));
    }

    /**
     * 按查询词分页列出 Session，分页上限由 PageRequest 负责约束。
     */
    public PageResponse<Session> listSessions(String query, PageRequest pageRequest) {
        return sessionRepository.findPage(query, pageRequest);
    }

    /**
     * 按当前登录用户查询历史 Session；不校验当前应用成员关系，避免用户离开应用后丢失自己的历史记录。
     */
    public PageResponse<SessionHistoryItem> listUserSessions(UserId userId, String query, PageRequest pageRequest) {
        return listUserSessions(userId, query, false, pageRequest);
    }

    /**
     * 排查入口显式选择时包含 ARCHIVED 会话；SIDE_QUESTION 等内部会话仍由查询端口排除。
     */
    public PageResponse<SessionHistoryItem> listUserSessions(
            UserId userId,
            String query,
            boolean includeArchived,
            PageRequest pageRequest) {
        if (sessionHistoryRepository == null) {
            throw new IllegalStateException("sessionHistoryRepository must be provided for user history query");
        }
        return sessionHistoryRepository.findUserHistory(userId, query, includeArchived, pageRequest);
    }

    /**
     * 分页列出指定 Workspace 下的 Session，Workspace 不存在时返回 NOT_FOUND。
     */
    public PageResponse<Session> listSessions(WorkspaceId workspaceId, PageRequest pageRequest) {
        if (workspaceRepository.findById(workspaceId).isEmpty()) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value()));
        }
        return sessionRepository.findByWorkspaceId(workspaceId, pageRequest);
    }

    /** 校验工作区和其中会话均属于当前用户后再返回分页。 */
    public PageResponse<Session> listSessions(UserId userId, WorkspaceId workspaceId, PageRequest pageRequest) {
        requireUserWorkspace(userId, workspaceId);
        PageResponse<SessionHistoryItem> page = sessionHistoryRepository.findUserWorkspaceHistory(
                userId, workspaceId, pageRequest);
        return new PageResponse<>(
                page.items().stream().map(SessionHistoryItem::session).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    /**
     * 更新 Session 标题和 pinned 状态，未传字段保持原值；纯置顶变更保留历史排序时间。
     */
    public Session updateSession(SessionId sessionId, String title, Boolean pinned, String traceId) {
        Session current = getSession(sessionId);
        String nextTitle = title == null || title.isBlank() ? current.title() : title;
        boolean nextPinned = pinned == null ? current.pinned() : pinned;
        // updatedAt 是普通会话组的排序锚点；置顶/取消置顶属于展示元数据，不能把旧会话抬到普通组最前。
        Instant nextUpdatedAt = nextTitle.equals(current.title()) ? current.updatedAt() : Instant.now();
        Session updated = sessionRepository.save(current.updateTitleAndPinned(nextTitle, nextPinned, nextUpdatedAt, traceId));
        // 仅标题确实已保存时才使原生 title agent 的旧代际失效；置顶等元数据更新不能中断标题等待。
        if (!nextTitle.equals(current.title()) && titleWatchService != null) {
            titleWatchService.cancelForSession(sessionId, traceId);
        }
        return updated;
    }

    /** 更新用户自己的会话。 */
    public Session updateSession(UserId userId, SessionId sessionId, String title, Boolean pinned, String traceId) {
        requireUserSessionWriteAccess(userId, sessionId);
        return updateSession(sessionId, title, pinned, traceId);
    }

    /**
     * 仅推进会话内容修订时间，用于压缩等远端正文变更通知协作者刷新快照；不改变标题、置顶或标题监听。
     */
    public Session touchSession(SessionId sessionId, String traceId) {
        Session current = getSession(sessionId);
        return sessionRepository.save(current.updateTitleAndPinned(
                current.title(), current.pinned(), Instant.now(), traceId));
    }

    /**
     * 归档 Session；归档后查询接口会按不存在处理。
     */
    public Session archiveSession(SessionId sessionId, String traceId) {
        return archiveSession(null, sessionId, traceId);
    }

    /**
     * 归档 Session，并在持久化前建立会话撤销 gate，阻止并发 bootstrap 把旧快照迟到写回。
     */
    public Session archiveSession(UserId userId, SessionId sessionId, String traceId) {
        requireNightExecutionUnlocked(sessionId);
        Session current = userId == null
                ? getSession(sessionId)
                : requireUserSessionWriteAccess(userId, sessionId);
        ConversationContextSessionRevocation revocation = conversationContextStore == null
                ? null
                : conversationContextStore.revokeSession(sessionId);
        Session archived;
        try {
            archived = sessionRepository.save(current.archive(Instant.now(), traceId));
        } catch (RuntimeException persistFailure) {
            if (revocation != null) {
                try {
                    conversationContextStore.restoreSessionRevocation(revocation);
                } catch (RuntimeException restoreFailure) {
                    // 数据库未归档但 gate 回滚失败时保持 fail-closed，并保留两个异常供排查。
                    persistFailure.addSuppressed(restoreFailure);
                }
            }
            throw persistFailure;
        }
        if (titleWatchService != null) {
            titleWatchService.cancelForSession(sessionId, traceId);
        }
        if (notificationService != null) {
            try {
                notificationService.invalidateSessionSharesBySession(
                        sessionId, "SESSION_ARCHIVED", traceId);
            } catch (RuntimeException notificationFailure) {
                // 会话已经归档；通知查询仍会按会话状态派生不可用，实时角标由周期校准收敛。
                LOGGER.warn("会话归档成功但分享通知失效写入失败 sessionId={} traceId={}",
                        sessionId.value(), traceId, notificationFailure);
            }
        }
        LOGGER.info("Session archived, sessionId={}, traceId={}", sessionId.value(), traceId);
        return archived;
    }

    /**
     * 追加平台侧 Session 消息，role 缺省为 USER；assistant 正文恢复不依赖本地消息表。
     */
    public SessionMessage appendMessage(SessionId sessionId, SessionMessageRole role, String content, String traceId) {
        return appendMessage((UserId) null, sessionId, role, content, traceId);
    }

    /**
     * 追加当前用户发送的 Session 消息，并记录 senderUserId 供用户活跃统计使用。
     */
    public SessionMessage appendMessage(UserId userId, SessionId sessionId, SessionMessageRole role, String content, String traceId) {
        requireNightExecutionUnlocked(sessionId);
        if (userId == null) {
            getSession(sessionId);
        } else {
            requireUserSessionWriteAccess(userId, sessionId);
        }
        SessionMessageRole resolvedRole = role == null ? SessionMessageRole.USER : role;
        SessionMessage draft = new SessionMessage(
                new SessionMessageId(RuntimeIdGenerator.messageId()),
                sessionId,
                resolvedRole,
                content,
                Instant.now(),
                traceId);
        return sessionMessageRepository.save(userId == null
                ? draft
                : draft.withSource(ConversationSourceType.MANUAL, null, userId));
    }

    /** 分享会话的平台消息入口；执行所属人和实际发送人分别记录，且不改写认证主体。 */
    public SessionMessage appendMessage(
            com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext context,
            SessionId sessionId,
            SessionMessageRole role,
            String content,
            String traceId) {
        Objects.requireNonNull(context, "context must not be null");
        context.requireChat();
        context.requireSession(sessionId);
        requireNightExecutionUnlocked(sessionId);
        getSession(sessionId);
        SessionMessageRole resolvedRole = role == null ? SessionMessageRole.USER : role;
        SessionMessage draft = new SessionMessage(
                new SessionMessageId(RuntimeIdGenerator.messageId()), sessionId, resolvedRole,
                content, Instant.now(), traceId)
                .withSource(ConversationSourceType.MANUAL, null, context.executionOwnerUserId())
                .withSender(context.actorUserId(), context.actorUnifiedAuthId(), context.delegated());
        return sessionMessageRepository.save(draft);
    }

    /** 可选 setter 保持大量既有手工构造测试兼容；生产 Spring 装配始终注入数据库锁门禁。 */
    @Autowired(required = false)
    void setNightExecutionLockGuard(NightExecutionSessionLockGuard nightExecutionLockGuard) {
        this.nightExecutionLockGuard = nightExecutionLockGuard;
    }

    /** 历史查询按已提交重发关系过滤，防止滚动发布或清理延迟让旧回答复活。 */
    @Autowired(required = false)
    void setRunResendRepository(RunResendRepository runResendRepository) {
        this.runResendRepository = runResendRepository;
    }

    /** 生产装配必须注入用户工作区查询端口；手工构造的单元测试可按需显式调用该 setter。 */
    @Autowired
    void setUserWorkspaceQueryRepository(UserWorkspaceQueryRepository userWorkspaceQueryRepository) {
        this.userWorkspaceQueryRepository = userWorkspaceQueryRepository;
    }

    /** 生产装配注入通知服务；兼容大量按构造器创建的会话单元测试。 */
    @Autowired(required = false)
    void setNotificationService(UserNotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 体验工作区没有个人关联行，创建会话前必须改走体验实时访问策略。 */
    @Autowired
    void setExperienceWorkspaceAccessAuthorizer(
            ExperienceWorkspaceAccessAuthorizer experienceWorkspaceAccessAuthorizer) {
        this.experienceWorkspaceAccessAuthorizer = experienceWorkspaceAccessAuthorizer;
    }

    private void requireNightExecutionUnlocked(SessionId sessionId) {
        if (nightExecutionLockGuard != null) {
            nightExecutionLockGuard.requireUnlocked(sessionId);
        }
    }

    /** 历史会话保持可读，但体验资格或当前绑定失效后不能再修改会话及追加本地消息。 */
    private Session requireUserSessionWriteAccess(UserId userId, SessionId sessionId) {
        Session session = getSession(userId, sessionId);
        if (experienceWorkspaceAccessAuthorizer != null
                && experienceWorkspaceAccessAuthorizer.isExperienceWorkspace(session.workspaceId())) {
            experienceWorkspaceAccessAuthorizer.requireAccess(userId, session.workspaceId());
        }
        return session;
    }

    /**
     * 分页列出 Session 消息，先校验 Session 未归档且存在。
     */
    public PageResponse<SessionMessage> listMessages(SessionId sessionId, PageRequest pageRequest) {
        return listMessages(sessionId, pageRequest, "trace_unspecified");
    }

    /**
     * 分页列出 Session 消息，优先刷新 agent 投影，刷新失败时使用数据库快照 fallback。
     */
    public PageResponse<SessionMessage> listMessages(SessionId sessionId, PageRequest pageRequest, String traceId) {
        return listMessages(sessionId, pageRequest, traceId, true);
    }

    /**
     * 分页列出 Session 消息；反馈映射等只读场景可关闭远端刷新，避免兼容接口重复拉取快照。
     */
    public PageResponse<SessionMessage> listMessages(
            SessionId sessionId,
            PageRequest pageRequest,
            String traceId,
            boolean refreshSnapshot) {
        Session session = getSession(sessionId);
        if (refreshSnapshot && snapshotService != null) {
            snapshotService.refreshSessionSnapshot("opencode", session, traceId);
        }
        PageResponse<SessionMessage> page = sessionMessageRepository.findBySessionId(sessionId, pageRequest);
        if (runResendRepository == null) return page;
        java.util.Set<RunId> suppressed = new HashSet<>(
                runResendRepository.findDispatchedSourceRunIds(sessionId));
        // WAITING/REVERTING/REVERTED 已有后端替代 USER，历史读取必须隐藏源轮次，避免刷新时出现双气泡。
        runResendRepository.findActiveBySession(sessionId)
                .map(com.enterprise.testagent.domain.run.RunResend::sourceRunId)
                .ifPresent(suppressed::add);
        if (suppressed.isEmpty()) return page;
        List<SessionMessage> visible = page.items().stream()
                .filter(message -> message.runId() == null || !suppressed.contains(message.runId()))
                .toList();
        long removed = page.items().size() - visible.size();
        return new PageResponse<>(visible, page.page(), page.size(), Math.max(0, page.total() - removed));
    }

    /** 分页读取用户自己的会话消息。 */
    public PageResponse<SessionMessage> listMessages(
            UserId userId,
            SessionId sessionId,
            PageRequest pageRequest,
            String traceId,
            boolean refreshSnapshot) {
        Session session = getSession(userId, sessionId);
        return listMessages(
                sessionId,
                pageRequest,
                traceId,
                refreshSnapshot && currentExperienceRuntimeAvailable(userId, session));
    }

    /**
     * 历史 Session 可继续展示数据库快照；只有当前体验绑定仍有效时才允许控制器读取远端 Session tree。
     */
    public boolean canUseLiveRuntime(UserId userId, SessionId sessionId) {
        return currentExperienceRuntimeAvailable(userId, getSession(userId, sessionId));
    }

    private boolean currentExperienceRuntimeAvailable(UserId userId, Session session) {
        if (experienceWorkspaceAccessAuthorizer == null
                || !experienceWorkspaceAccessAuthorizer.isExperienceWorkspace(session.workspaceId())) {
            return true;
        }
        try {
            experienceWorkspaceAccessAuthorizer.requireAccess(userId, session.workspaceId());
            return true;
        } catch (PlatformException exception) {
            // 参数换目录、加入应用或服务器变化后，历史读取降级到已持久化快照，绝不触发远端访问。
            return false;
        }
    }

    /** 按替代 Run 精确读取平台 USER，供共享实时同步绕过历史分页和远端快照刷新。 */
    public SessionMessage getUserMessageForRun(SessionId sessionId, RunId runId) {
        getSession(sessionId);
        return sessionMessageRepository.findUserBySessionIdAndRunId(sessionId, runId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Run 用户消息不存在",
                        Map.of("sessionId", sessionId.value(), "runId", runId.value())));
    }

    /** 当前用户版本先按历史归属隐藏越权差异，再执行同一精确消息查询。 */
    public SessionMessage getUserMessageForRun(UserId userId, SessionId sessionId, RunId runId) {
        getSession(userId, sessionId);
        return getUserMessageForRun(sessionId, runId);
    }

    /** 按 Run 精确读取完整轮次；不触发远端刷新，也不受当前重发的历史隐藏规则影响。 */
    public List<SessionMessage> listMessagesForRun(SessionId sessionId, RunId runId) {
        getSession(sessionId);
        List<SessionMessage> messages = sessionMessageRepository.findBySessionIdAndRunId(sessionId, runId);
        if (messages.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "Run 会话消息不存在",
                    Map.of("sessionId", sessionId.value(), "runId", runId.value()));
        }
        return messages;
    }

    /** 当前用户版本先校验会话归属，再执行同一精确轮次查询。 */
    public List<SessionMessage> listMessagesForRun(UserId userId, SessionId sessionId, RunId runId) {
        getSession(userId, sessionId);
        return listMessagesForRun(sessionId, runId);
    }

    /** 按替代 Run 精确读取平台 USER，供共享实时同步绕过历史分页和远端快照刷新。 */
    public SessionMessage getUserMessageForRun(SessionId sessionId, RunId runId) {
        getSession(sessionId);
        return sessionMessageRepository.findUserBySessionIdAndRunId(sessionId, runId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Run 用户消息不存在",
                        Map.of("sessionId", sessionId.value(), "runId", runId.value())));
    }

    /** 当前用户版本先按历史归属隐藏越权差异，再执行同一精确消息查询。 */
    public SessionMessage getUserMessageForRun(UserId userId, SessionId sessionId, RunId runId) {
        getSession(userId, sessionId);
        return getUserMessageForRun(sessionId, runId);
    }

    /** 按 Run 精确读取完整轮次；不触发远端刷新，也不受当前重发的历史隐藏规则影响。 */
    public List<SessionMessage> listMessagesForRun(SessionId sessionId, RunId runId) {
        getSession(sessionId);
        List<SessionMessage> messages = sessionMessageRepository.findBySessionIdAndRunId(sessionId, runId);
        if (messages.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "Run 会话消息不存在",
                    Map.of("sessionId", sessionId.value(), "runId", runId.value()));
        }
        return messages;
    }

    /** 当前用户版本先校验会话归属，再执行同一精确轮次查询。 */
    public List<SessionMessage> listMessagesForRun(UserId userId, SessionId sessionId, RunId runId) {
        getSession(userId, sessionId);
        return listMessagesForRun(sessionId, runId);
    }

    private void requireUserWorkspace(UserId userId, WorkspaceId workspaceId) {
        if (userId == null || userWorkspaceQueryRepository == null) {
            return;
        }
        if (experienceWorkspaceAccessAuthorizer != null
                && experienceWorkspaceAccessAuthorizer.isExperienceWorkspace(workspaceId)) {
            experienceWorkspaceAccessAuthorizer.requireAccess(userId, workspaceId);
            return;
        }
        if (userWorkspaceQueryRepository.findUserWorkspace(userId, workspaceId).isEmpty()) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "Workspace 不存在",
                    Map.of("workspaceId", workspaceId.value()));
        }
    }
}
