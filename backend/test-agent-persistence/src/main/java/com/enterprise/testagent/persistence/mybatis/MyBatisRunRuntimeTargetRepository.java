package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeTarget;
import com.enterprise.testagent.domain.run.RunRuntimeTargetRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Run 运行目标 MyBatis 仓储。 */
@Repository
public class MyBatisRunRuntimeTargetRepository implements RunRuntimeTargetRepository {

    private final RunRuntimeTargetMapper mapper;

    public MyBatisRunRuntimeTargetRepository(RunRuntimeTargetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RunRuntimeTarget> findByRunId(RunId runId) {
        return Optional.ofNullable(mapper.findByRunId(runId.value())).map(this::toDomain);
    }

    @Override
    public void save(RunRuntimeTarget target) {
        int updated = mapper.updateTarget(
                target.runId().value(),
                target.runtimeKind().name(),
                target.localClientInstanceId() == null ? null : target.localClientInstanceId().value());
        if (updated != 1) {
            throw new IllegalStateException("run runtime target update did not affect one row");
        }
    }

    private RunRuntimeTarget toDomain(RunRuntimeTargetRow row) {
        RuntimeKind kind = RuntimeKind.fromNullable(row.targetRuntimeKind());
        return new RunRuntimeTarget(
                new RunId(row.runId()),
                kind,
                row.targetLocalClientInstanceId() == null
                        ? null
                        : new LocalClientInstanceId(row.targetLocalClientInstanceId()));
    }
}
