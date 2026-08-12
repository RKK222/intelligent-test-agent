package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeTarget;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 会话运行目标 MyBatis 仓储。 */
@Repository
public class MyBatisSessionRuntimeTargetRepository implements SessionRuntimeTargetRepository {

    private final SessionRuntimeTargetMapper mapper;

    public MyBatisSessionRuntimeTargetRepository(SessionRuntimeTargetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SessionRuntimeTarget> findBySessionId(SessionId sessionId) {
        return Optional.ofNullable(mapper.findBySessionId(sessionId.value())).map(this::toDomain);
    }

    @Override
    public void save(SessionRuntimeTarget target) {
        int updated = mapper.updateTarget(
                target.sessionId().value(),
                target.runtimeKind().name(),
                target.localClientInstanceId() == null ? null : target.localClientInstanceId().value());
        if (updated != 1) {
            throw new IllegalStateException("session runtime target update did not affect one row");
        }
    }

    private SessionRuntimeTarget toDomain(SessionRuntimeTargetRow row) {
        RuntimeKind runtimeKind = RuntimeKind.fromNullable(row.runtimeKind());
        return new SessionRuntimeTarget(
                new SessionId(row.sessionId()),
                runtimeKind,
                row.localClientInstanceId() == null ? null : new LocalClientInstanceId(row.localClientInstanceId()));
    }
}
