package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionTitleUpdateRepository;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * 通过数据库条件更新保护异步会话标题，避免旧快照覆盖原生或人工标题。
 */
@Repository
public class MyBatisSessionTitleUpdateRepository implements SessionTitleUpdateRepository {

    private final SessionTitleUpdateMapper mapper;

    public MyBatisSessionTitleUpdateRepository(SessionTitleUpdateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean updateTitleIfCurrent(
            SessionId sessionId,
            String expectedTitle,
            String title,
            Instant updatedAt,
            String traceId) {
        return mapper.updateTitleIfCurrent(sessionId.value(), expectedTitle, title, updatedAt, traceId) == 1;
    }
}
