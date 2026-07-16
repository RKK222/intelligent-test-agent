package com.enterprise.testagent.scheduler;

import java.time.Instant;

/**
 * 唤醒后台 runner 的轻量端口，供管理服务创建手动运行后提示尽快扫描。
 */
public interface ScheduledTaskDispatcher {

    /**
     * 唤醒后台扫描线程；未启用扫描时允许为空操作。
     */
    void wakeUp();

    /**
     * 返回当前进程内后台扫描线程是否正在运行。
     */
    default boolean runnerRunning() {
        return false;
    }

    /**
     * 返回最近一次扫描开始时间。
     */
    default Instant lastScanStartedAt() {
        return null;
    }

    /**
     * 返回最近一次扫描结束时间。
     */
    default Instant lastScanFinishedAt() {
        return null;
    }

    /**
     * 返回最近一次扫描异常消息。
     */
    default String lastScanErrorMessage() {
        return null;
    }
}
