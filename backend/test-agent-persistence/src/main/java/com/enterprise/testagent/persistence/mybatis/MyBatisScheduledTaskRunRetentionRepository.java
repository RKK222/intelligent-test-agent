package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunRetentionRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * 定时任务运行记录保留策略的 MyBatis 适配器，只负责转发领域端口调用。
 */
@Repository
public class MyBatisScheduledTaskRunRetentionRepository implements ScheduledTaskRunRetentionRepository {

    private final ScheduledTaskRunRetentionMapper mapper;

    /**
     * 注入 XML mapper，SQL、事务和数据库方言适配由 MyBatis-Spring 统一管理。
     */
    public MyBatisScheduledTaskRunRetentionRepository(ScheduledTaskRunRetentionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public int deleteEndedBefore(Instant cutoff) {
        return mapper.deleteEndedBefore(Objects.requireNonNull(cutoff, "cutoff must not be null"));
    }
}
