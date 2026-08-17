package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.localclient.LocalClientRolloutEntry;
import com.enterprise.testagent.domain.localclient.LocalClientRolloutRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 本地客户端灰度名单 MyBatis 仓储实现。 */
@Repository
public class MyBatisLocalClientRolloutRepository implements LocalClientRolloutRepository {

    private final LocalClientMapper mapper;

    public MyBatisLocalClientRolloutRepository(LocalClientMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isEnabled(UserId userId) {
        return mapper.isRolloutEnabled(userId.value());
    }

    @Override
    public Optional<LocalClientRolloutEntry> findByUserId(UserId userId) {
        return Optional.ofNullable(mapper.findRolloutByUserId(userId.value())).map(MyBatisLocalClientRolloutRepository::toDomain);
    }

    @Override
    public List<LocalClientRolloutEntry> findEnabledPage(long offset, int limit) {
        return mapper.findEnabledRolloutPage(offset, limit).stream()
                .map(MyBatisLocalClientRolloutRepository::toDomain)
                .toList();
    }

    @Override
    public long countEnabled() {
        return mapper.countEnabledRollout();
    }

    @Override
    public void save(LocalClientRolloutEntry entry) {
        mapper.upsertRollout(new LocalClientRolloutRow(
                entry.userId().value(), entry.enabled(), entry.updatedByUserId().value(),
                entry.createdAt(), entry.updatedAt()));
    }

    @Override
    public boolean disable(UserId userId, UserId updatedByUserId, Instant updatedAt) {
        return mapper.disableRollout(userId.value(), updatedByUserId.value(), updatedAt) == 1;
    }

    private static LocalClientRolloutEntry toDomain(LocalClientRolloutRow row) {
        return new LocalClientRolloutEntry(
                new UserId(row.userId()), row.enabled(), new UserId(row.updatedByUserId()),
                row.createdAt(), row.updatedAt());
    }
}
