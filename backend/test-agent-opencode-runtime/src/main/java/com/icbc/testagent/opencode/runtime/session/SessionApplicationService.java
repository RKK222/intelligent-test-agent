package com.icbc.testagent.opencode.runtime.session;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionHistoryItem;
import com.icbc.testagent.domain.session.SessionHistoryRepository;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.opencode.runtime.run.RunSessionMessageSnapshotService;
import com.icbc.testagent.opencode.runtime.run.RunSessionTitleWatchService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    /**
     * 创建 Session 应用服务，Controller 不直接访问这些仓储实现。
     */
    @Autowired
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionHistoryRepository sessionHistoryRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService,
            RunSessionTitleWatchService titleWatchService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.sessionHistoryRepository = Objects.requireNonNull(sessionHistoryRepository, "sessionHistoryRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.snapshotService = snapshotService;
        this.titleWatchService = titleWatchService;
    }

    /** 兼容未接入标题监听的既有调用方。 */
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
                null);
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
    public Session createSession(WorkspaceId workspaceId, String title, String traceId) {
        return createSession(null, workspaceId, title, traceId);
    }

    /**
     * 在指定 Workspace 下创建当前用户的 Session，并记录创建人归因供运营统计使用。
     */
    public Session createSession(UserId userId, WorkspaceId workspaceId, String title, String traceId) {
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
        LOGGER.info("Session created, sessionId={}, workspaceId={}, title={}, traceId={}",
                session.sessionId().value(), workspaceId.value(), title, traceId);
        return session;
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
        if (sessionHistoryRepository == null) {
            throw new IllegalStateException("sessionHistoryRepository must be provided for user history query");
        }
        return sessionHistoryRepository.findUserHistory(userId, query, pageRequest);
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

    /**
     * 更新 Session 标题和 pinned 状态，未传字段保持原值。
     */
    public Session updateSession(SessionId sessionId, String title, Boolean pinned, String traceId) {
        Session current = getSession(sessionId);
        String nextTitle = title == null || title.isBlank() ? current.title() : title;
        boolean nextPinned = pinned == null ? current.pinned() : pinned;
        Session updated = sessionRepository.save(current.updateTitleAndPinned(nextTitle, nextPinned, Instant.now(), traceId));
        // 仅标题确实已保存时才使原生 title agent 的旧代际失效；置顶等元数据更新不能中断标题等待。
        if (!nextTitle.equals(current.title()) && titleWatchService != null) {
            titleWatchService.cancelForSession(sessionId, traceId);
        }
        return updated;
    }

    /**
     * 归档 Session；归档后查询接口会按不存在处理。
     */
    public Session archiveSession(SessionId sessionId, String traceId) {
        Session current = getSession(sessionId);
        Session archived = sessionRepository.save(current.archive(Instant.now(), traceId));
        if (titleWatchService != null) {
            titleWatchService.cancelForSession(sessionId, traceId);
        }
        LOGGER.info("Session archived, sessionId={}, traceId={}", sessionId.value(), traceId);
        return archived;
    }

    /**
     * 追加平台侧 Session 消息，role 缺省为 USER；assistant 正文恢复不依赖本地消息表。
     */
    public SessionMessage appendMessage(SessionId sessionId, SessionMessageRole role, String content, String traceId) {
        return appendMessage(null, sessionId, role, content, traceId);
    }

    /**
     * 追加当前用户发送的 Session 消息，并记录 senderUserId 供用户活跃统计使用。
     */
    public SessionMessage appendMessage(UserId userId, SessionId sessionId, SessionMessageRole role, String content, String traceId) {
        getSession(sessionId);
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
        return sessionMessageRepository.findBySessionId(sessionId, pageRequest);
    }
}
