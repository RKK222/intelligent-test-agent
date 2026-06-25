package com.icbc.testagent.opencode.runtime.session;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.opencode.runtime.run.RunSessionMessageSnapshotService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Session 应用服务，负责编排会话和平台消息持久化，Controller 不直接访问 Repository。
 */
@Service
public class SessionApplicationService {

    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final RunSessionMessageSnapshotService snapshotService;

    /**
     * 创建 Session 应用服务，Controller 不直接访问这些仓储实现。
     */
    @Autowired
    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository,
            RunSessionMessageSnapshotService snapshotService) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
        this.snapshotService = snapshotService;
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
        if (workspaceRepository.findById(workspaceId).isEmpty()) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value()));
        }
        Instant now = Instant.now();
        return sessionRepository.save(new Session(
                new SessionId(RuntimeIdGenerator.sessionId()),
                workspaceId,
                title,
                SessionStatus.ACTIVE,
                now,
                now,
                traceId));
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
        return sessionRepository.save(current.updateTitleAndPinned(nextTitle, nextPinned, Instant.now(), traceId));
    }

    /**
     * 归档 Session；归档后查询接口会按不存在处理。
     */
    public Session archiveSession(SessionId sessionId, String traceId) {
        Session current = getSession(sessionId);
        return sessionRepository.save(current.archive(Instant.now(), traceId));
    }

    /**
     * 追加平台侧 Session 消息，role 缺省为 USER；assistant 正文恢复不依赖本地消息表。
     */
    public SessionMessage appendMessage(SessionId sessionId, SessionMessageRole role, String content, String traceId) {
        getSession(sessionId);
        SessionMessageRole resolvedRole = role == null ? SessionMessageRole.USER : role;
        return sessionMessageRepository.save(new SessionMessage(
                new SessionMessageId(RuntimeIdGenerator.messageId()),
                sessionId,
                resolvedRole,
                content,
                Instant.now(),
                traceId));
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
        Session session = getSession(sessionId);
        if (snapshotService != null) {
            snapshotService.refreshSessionSnapshot("opencode", session, traceId);
        }
        return sessionMessageRepository.findBySessionId(sessionId, pageRequest);
    }
}
