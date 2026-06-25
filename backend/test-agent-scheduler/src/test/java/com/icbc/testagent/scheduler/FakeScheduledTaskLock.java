package com.icbc.testagent.scheduler;

import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
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
