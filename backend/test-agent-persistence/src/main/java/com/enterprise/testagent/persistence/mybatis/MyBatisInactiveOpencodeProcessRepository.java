package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.opencodeprocess.InactiveOpencodeProcessCandidate;
import com.enterprise.testagent.domain.opencodeprocess.InactiveOpencodeProcessRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 使用 MyBatis XML 聚合 Run 活跃时间和当前进程绑定。 */
@Repository
public class MyBatisInactiveOpencodeProcessRepository implements InactiveOpencodeProcessRepository {

    private static final int MAX_LIMIT = 1000;

    private final InactiveOpencodeProcessMapper mapper;

    public MyBatisInactiveOpencodeProcessRepository(InactiveOpencodeProcessMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public List<InactiveOpencodeProcessCandidate> findCandidates(
            LinuxServerId linuxServerId,
            Instant activityBefore,
            int limit) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        validate(activityBefore, limit);
        return mapper.findCandidates(linuxServerId.value(), activityBefore, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<InactiveOpencodeProcessCandidate> findCurrentCandidate(
            OpencodeProcessId processId,
            Instant activityBefore) {
        Objects.requireNonNull(processId, "processId must not be null");
        Objects.requireNonNull(activityBefore, "activityBefore must not be null");
        return Optional.ofNullable(mapper.findCurrentCandidate(processId.value(), activityBefore))
                .map(this::toDomain);
    }

    private void validate(Instant activityBefore, int limit) {
        Objects.requireNonNull(activityBefore, "activityBefore must not be null");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private InactiveOpencodeProcessCandidate toDomain(InactiveOpencodeProcessRow row) {
        OpencodeServerProcess process = new OpencodeServerProcess(
                new OpencodeProcessId(row.processId()),
                new UserId(row.userId()),
                new LinuxServerId(row.linuxServerId()),
                new OpencodeContainerId(row.containerId()),
                row.port(),
                row.pid(),
                row.baseUrl(),
                OpencodeServerProcessStatus.valueOf(row.status()),
                row.sessionPath(),
                row.configPath(),
                row.startedAt(),
                row.lastHealthCheckAt(),
                row.healthMessage(),
                row.createdAt(),
                row.updatedAt(),
                row.traceId());
        return new InactiveOpencodeProcessCandidate(process, row.lastActivityAt());
    }
}
