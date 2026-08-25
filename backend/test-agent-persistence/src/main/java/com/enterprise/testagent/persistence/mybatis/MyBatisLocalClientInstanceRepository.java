package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 本地客户端实例 MyBatis 仓储实现。 */
@Repository
public class MyBatisLocalClientInstanceRepository implements LocalClientInstanceRepository {

    private final LocalClientMapper mapper;

    public MyBatisLocalClientInstanceRepository(LocalClientMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LocalClientInstance> findById(LocalClientInstanceId clientInstanceId) {
        return Optional.ofNullable(mapper.findInstanceById(clientInstanceId.value())).map(this::toDomain);
    }

    @Override
    public List<LocalClientInstance> findByUserId(UserId userId) {
        return mapper.findInstancesByUserId(userId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(LocalClientInstance instance) {
        int updated = mapper.upsertInstance(new LocalClientInstanceRow(
                instance.clientInstanceId().value(),
                instance.userId().value(),
                instance.clientName(),
                instance.platform(),
                instance.architecture(),
                instance.clientVersion(),
                instance.opencodeVersion(),
                instance.launcherVersion(),
                String.join(",", instance.selfUpdateCapabilities()),
                instance.selfUpdateSupported(),
                instance.lastUpdateStatus(),
                instance.lastUpdateTargetVersion(),
                instance.lastUpdateAt(),
                instance.createdAt(),
                instance.updatedAt(),
                instance.lastConnectedAt(),
                instance.lastDisconnectedAt()));
        if (updated != 1) {
            throw new IllegalStateException("local client instance owner changed during upsert");
        }
    }

    @Override
    public void markReplaced(
            UserId userId,
            LocalClientInstanceId replacedClientInstanceId,
            LocalClientInstanceId replacementClientInstanceId,
            Instant replacedAt) {
        if (mapper.markInstanceReplaced(
                        userId.value(),
                        replacedClientInstanceId.value(),
                        replacementClientInstanceId.value(),
                        replacedAt) != 1) {
            throw new IllegalStateException("local client instance replacement was not persisted");
        }
    }

    @Override
    public void clearReplacement(LocalClientInstanceId clientInstanceId) {
        mapper.clearInstanceReplacement(clientInstanceId.value());
    }

    @Override
    public void markDisconnected(LocalClientInstanceId clientInstanceId, Instant disconnectedAt) {
        mapper.markInstanceDisconnected(clientInstanceId.value(), disconnectedAt);
    }

    @Override
    public List<LocalClientInstance> findAll() {
        return mapper.findAllInstances().stream().map(this::toDomain).toList();
    }

    @Override
    public void updateLastUpdateStatus(
            LocalClientInstanceId clientInstanceId,
            String status,
            String targetVersion,
            Instant observedAt) {
        if (mapper.updateInstanceLastUpdateStatus(
                clientInstanceId.value(), status, targetVersion, observedAt) != 1) {
            throw new IllegalStateException("local client instance does not exist while updating status");
        }
    }

    private LocalClientInstance toDomain(LocalClientInstanceRow row) {
        return new LocalClientInstance(
                new LocalClientInstanceId(row.clientInstanceId()),
                new UserId(row.userId()),
                row.clientName(),
                row.platform(),
                row.architecture(),
                row.clientVersion(),
                row.opencodeVersion(),
                row.launcherVersion(),
                row.selfUpdateCapabilities() == null || row.selfUpdateCapabilities().isBlank()
                        ? List.of()
                        : List.of(row.selfUpdateCapabilities().split(",")),
                row.selfUpdateSupported(),
                row.lastUpdateStatus(),
                row.lastUpdateTargetVersion(),
                row.lastUpdateAt(),
                row.createdAt(),
                row.updatedAt(),
                row.lastConnectedAt(),
                row.lastDisconnectedAt());
    }
}
