package com.enterprise.testagent.domain.nightexecution;

/** 定时对话的计划模式；仅标准夜间窗口参与十五分钟时段容量控制。 */
public enum NightExecutionScheduleMode {
    NIGHT_WINDOW,
    ADMIN_CUSTOM;

    /** 是否需要预留并释放公共夜间时段容量。 */
    public boolean reservesNightCapacity() {
        return this == NIGHT_WINDOW;
    }
}
