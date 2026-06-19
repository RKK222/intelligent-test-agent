package com.example.testagent.app.session;

import com.example.testagent.app.support.RuntimeIdGenerator;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionMessage;
import com.example.testagent.domain.session.SessionMessageId;
import com.example.testagent.domain.session.SessionMessageRepository;
import com.example.testagent.domain.session.SessionMessageRole;
import com.example.testagent.domain.session.SessionRepository;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Session 应用服务，负责编排会话和平台消息持久化，Controller 不直接访问 Repository。
 */
@Service
public class SessionApplicationService {

    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository sessionMessageRepository;

    public SessionApplicationService(
            WorkspaceRepository workspaceRepository,
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "workspaceRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.sessionMessageRepository = Objects.requireNonNull(sessionMessageRepository, "sessionMessageRepository must not be null");
    }

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

    public Session getSession(SessionId sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value())));
        if (session.status() == SessionStatus.ARCHIVED) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在", Map.of("sessionId", sessionId.value()));
        }
        return session;
    }

    public PageResponse<Session> listSessions(String query, PageRequest pageRequest) {
        return sessionRepository.findPage(query, pageRequest);
    }

    public PageResponse<Session> listSessions(WorkspaceId workspaceId, PageRequest pageRequest) {
        if (workspaceRepository.findById(workspaceId).isEmpty()) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在", Map.of("workspaceId", workspaceId.value()));
        }
        return sessionRepository.findByWorkspaceId(workspaceId, pageRequest);
    }

    public Session updateSession(SessionId sessionId, String title, Boolean pinned, String traceId) {
        Session current = getSession(sessionId);
        String nextTitle = title == null || title.isBlank() ? current.title() : title;
        boolean nextPinned = pinned == null ? current.pinned() : pinned;
        return sessionRepository.save(current.updateTitleAndPinned(nextTitle, nextPinned, Instant.now(), traceId));
    }

    public Session archiveSession(SessionId sessionId, String traceId) {
        Session current = getSession(sessionId);
        return sessionRepository.save(current.archive(Instant.now(), traceId));
    }

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

    public PageResponse<SessionMessage> listMessages(SessionId sessionId, PageRequest pageRequest) {
        getSession(sessionId);
        return sessionMessageRepository.findBySessionId(sessionId, pageRequest);
    }
}
