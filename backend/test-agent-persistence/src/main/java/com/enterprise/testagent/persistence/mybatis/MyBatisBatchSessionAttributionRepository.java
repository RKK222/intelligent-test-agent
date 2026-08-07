package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.session.BatchSessionAttributionRepository;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 批量会话归因领域端口的 MyBatis 适配器。 */
@Repository
public class MyBatisBatchSessionAttributionRepository implements BatchSessionAttributionRepository {

    private final BatchSessionAttributionMapper mapper;

    public MyBatisBatchSessionAttributionRepository(BatchSessionAttributionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<SessionId> findSessionId(UserId userId, String itemRequestId) {
        return Optional.ofNullable(mapper.findSessionId(userId.value(), itemRequestId)).map(SessionId::new);
    }

    @Override
    public void lockCreateRequest(UserId userId, String itemRequestId) {
        mapper.lockCreateRequest(userId.value() + ":" + itemRequestId);
    }

    @Override
    public boolean markBatch(
            SessionId sessionId,
            UserId userId,
            String batchId,
            String itemRequestId) {
        return mapper.markBatch(sessionId.value(), userId.value(), batchId, itemRequestId) == 1;
    }
}
