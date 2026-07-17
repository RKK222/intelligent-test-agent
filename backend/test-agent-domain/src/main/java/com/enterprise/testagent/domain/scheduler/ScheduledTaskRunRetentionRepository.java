package com.enterprise.testagent.domain.scheduler;

import java.time.Instant;

/**
 * 定时任务运行记录保留策略持久化端口，具体 SQL 由 persistence 模块实现。
 */
public interface ScheduledTaskRunRetentionRepository {

    /**
     * 删除结束时间早于截止时间的已结束运行记录，并返回实际删除行数。
     *
     * @param cutoff 保留边界；早于该时间的记录才允许清理
     * @return 实际删除的记录数
     */
    int deleteEndedBefore(Instant cutoff);
}
