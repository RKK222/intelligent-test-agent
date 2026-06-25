package com.icbc.testagent.scheduler;

/**
 * handler 调用 {@link ScheduledTaskContext#throwIfStopRequested()} 后抛出的协作式停止信号。
 */
public class ScheduledTaskStopRequestedException extends RuntimeException {

    public ScheduledTaskStopRequestedException() {
        super("定时任务已收到管理员停止请求");
    }
}
