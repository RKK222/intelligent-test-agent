package com.icbc.testagent.scheduler;

/**
 * 唤醒后台 runner 的轻量端口，供管理服务创建手动运行后提示尽快扫描。
 */
public interface ScheduledTaskDispatcher {

    /**
     * 唤醒后台扫描线程；未启用扫描时允许为空操作。
     */
    void wakeUp();
}
