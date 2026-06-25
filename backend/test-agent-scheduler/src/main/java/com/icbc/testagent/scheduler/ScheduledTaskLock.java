package com.icbc.testagent.scheduler;

import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import java.time.Duration;
import java.util.Optional;

/**
 * 定时任务分布式锁端口，首版只提供 Redis 实现，不允许本机锁降级。
 */
public interface ScheduledTaskLock {

    /**
     * 尝试获取 taskKey 的全局锁，成功时返回可续租和释放的租约。
     */
    Optional<ScheduledTaskLockLease> acquire(ScheduledTaskKey taskKey, Duration ttl);
}
