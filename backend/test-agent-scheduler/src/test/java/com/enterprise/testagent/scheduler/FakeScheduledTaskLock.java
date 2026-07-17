package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import java.time.Duration;
import java.util.Optional;

/**
 * runner 单元测试使用的可控锁实现。
 */
final class FakeScheduledTaskLock implements ScheduledTaskLock {

    boolean acquire = true;
    int acquireCount;
    int releaseCount;
    int renewCount;

    @Override
    public Optional<ScheduledTaskLockLease> acquire(ScheduledTaskKey taskKey, Duration ttl) {
        acquireCount++;
        if (!acquire) {
            return Optional.empty();
        }
        return Optional.of(new Lease(taskKey, ttl));
    }

    @Override
    public ScheduledTaskLockInspection inspect(ScheduledTaskKey taskKey) {
        return acquire
                ? ScheduledTaskLockInspection.unlocked("fake:" + taskKey.value())
                : ScheduledTaskLockInspection.locked("fake:" + taskKey.value(), 30_000L);
    }

    private final class Lease implements ScheduledTaskLockLease {
        private final ScheduledTaskKey taskKey;
        private final Duration ttl;

        private Lease(ScheduledTaskKey taskKey, Duration ttl) {
            this.taskKey = taskKey;
            this.ttl = ttl;
        }

        @Override
        public ScheduledTaskKey taskKey() {
            return taskKey;
        }

        @Override
        public String lockKey() {
            return "fake:" + taskKey.value();
        }

        @Override
        public String token() {
            return "token";
        }

        @Override
        public Duration ttl() {
            return ttl;
        }

        @Override
        public boolean renew() {
            renewCount++;
            return true;
        }

        @Override
        public boolean release() {
            releaseCount++;
            return true;
        }
    }
}
