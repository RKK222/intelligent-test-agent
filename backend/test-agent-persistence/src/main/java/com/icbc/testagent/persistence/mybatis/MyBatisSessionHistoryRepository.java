package com.icbc.testagent.persistence.mybatis;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionHistoryItem;
import com.icbc.testagent.domain.session.SessionHistoryRepository;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.session.SessionWorkspaceContext;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.util.Locale;
import org.springframework.stereotype.Repository;

/**
 * 用户级历史会话 MyBatis Repository，负责把列表投影重建为领域对象。
 */
@Repository
public class MyBatisSessionHistoryRepository implements SessionHistoryRepository {

    private final SessionHistoryMapper mapper;

    /**
     * 注入 MyBatis mapper；事务和连接生命周期由 MyBatis-Spring 管理。
     */
    public MyBatisSessionHistoryRepository(SessionHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResponse<SessionHistoryItem> findUserHistory(UserId userId, String query, PageRequest pageRequest) {
        String queryPattern = searchPattern(query);
        var rows = mapper.findUserHistory(userId.value(), queryPattern, pageRequest.size(), pageRequest.offset());
        long total = mapper.countUserHistory(userId.value(), queryPattern);
        return new PageResponse<>(
                rows.stream().map(this::toHistoryItem).toList(),
                pageRequest.page(),
                pageRequest.size(),
                total);
    }

    private SessionHistoryItem toHistoryItem(SessionHistoryRow row) {
        return new SessionHistoryItem(
                new Session(
                        new SessionId(row.sessionId()),
                        new WorkspaceId(row.workspaceId()),
                        row.title(),
                        SessionStatus.valueOf(row.status()),
                        row.createdAt(),
                        row.updatedAt(),
                        row.traceId(),
                        row.opencodeSessionId(),
                        executionNodeId(row.opencodeExecutionNodeId()),
                        Boolean.TRUE.equals(row.pinned()),
                        sourceType(row.sourceType()),
                        row.sourceRefId(),
                        userId(row.createdByUserId())),
                new SessionWorkspaceContext(
                        blankToNull(row.appId()),
                        blankToNull(row.appName()),
                        blankToNull(row.applicationWorkspaceId()),
                        blankToNull(row.workspaceName()),
                        blankToNull(row.versionId()),
                        blankToNull(row.version())));
    }

    private String searchPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private ExecutionNodeId executionNodeId(String value) {
        return value == null ? null : new ExecutionNodeId(value);
    }

    private ConversationSourceType sourceType(String value) {
        return value == null ? ConversationSourceType.MANUAL : ConversationSourceType.valueOf(value);
    }

    private UserId userId(String value) {
        return value == null ? null : new UserId(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
