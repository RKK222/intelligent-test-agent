package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeAttention;
import com.enterprise.testagent.domain.session.SessionRuntimeState;
import com.enterprise.testagent.domain.session.SessionRuntimeStateRepository;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 用户级会话运行态 MyBatis 仓储实现，只做只读聚合和领域对象映射。
 */
@Repository
public class MyBatisSessionRuntimeStateRepository implements SessionRuntimeStateRepository {

    private final SessionRuntimeStateMapper mapper;

    public MyBatisSessionRuntimeStateRepository(SessionRuntimeStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SessionRuntimeStateSummary findUserRuntimeState(UserId userId) {
        List<SessionRuntimeState> states = mapper.findUserRuntimeState(userId.value()).stream()
                .map(this::toDomain)
                .toList();
        int questionCount = (int) states.stream()
                .filter(state -> state.attention() == SessionRuntimeAttention.QUESTION)
                .count();
        return new SessionRuntimeStateSummary(states.size(), questionCount, states, Instant.now());
    }

    private SessionRuntimeState toDomain(SessionRuntimeStateRow row) {
        return new SessionRuntimeState(
                new SessionId(row.sessionId()),
                new RunId(row.runId()),
                RunStatus.valueOf(row.runStatus()),
                attention(row.attention()),
                row.attentionEventId(),
                row.attentionAt(),
                row.updatedAt());
    }

    private static SessionRuntimeAttention attention(String value) {
        return value == null ? null : SessionRuntimeAttention.valueOf(value);
    }
}
