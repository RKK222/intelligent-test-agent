package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import java.time.Duration;

/**
 * 已获取的分布式锁租约，释放和续租都必须按 token 校验所有权。
 */
public interface ScheduledTaskLockLease extends AutoCloseable {

    ScheduledTaskKey taskKey();

    String lockKey();

    String token();

    Duration ttl();

    /**
     * 使用原 token 续租。
     */
    boolean renew();

    /**
     * 使用原 token 释放锁。
     */
    boolean release();

    /**
     * AutoCloseable 适配，避免调用方忘记释放锁。
     */
    @Override
    default void close() {
        release();
    }
}
