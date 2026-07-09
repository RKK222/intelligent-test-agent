package com.icbc.testagent.scheduler;

/**
 * 定时任务 Redis 锁只读检查结果；不暴露锁 token，避免误用诊断接口控制锁。
 */
public record ScheduledTaskLockInspection(
        boolean checkable,
        String lockKey,
        boolean locked,
        Long ttlMillis,
        String errorMessage) {

    public static ScheduledTaskLockInspection unlocked(String lockKey) {
        return new ScheduledTaskLockInspection(true, lockKey, false, null, null);
    }

    public static ScheduledTaskLockInspection locked(String lockKey, Long ttlMillis) {
        return new ScheduledTaskLockInspection(true, lockKey, true, ttlMillis, null);
    }

    public static ScheduledTaskLockInspection unavailable(String lockKey, String errorMessage) {
        return new ScheduledTaskLockInspection(false, lockKey, false, null, errorMessage);
    }
}
