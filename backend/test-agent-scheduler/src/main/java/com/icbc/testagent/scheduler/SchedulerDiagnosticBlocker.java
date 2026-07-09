package com.icbc.testagent.scheduler;

/**
 * 阻止定时任务运行的稳定诊断 code 和面向管理员的说明。
 */
public record SchedulerDiagnosticBlocker(String code, String message) {
}
