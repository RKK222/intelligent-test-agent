package com.icbc.testagent.opencode.runtime.process;

/**
 * opencode server 强状态查询的归一语义。
 */
public enum OpencodeProcessProbeStatus {
    /** 进程未启动或已停止 */
    NOT_STARTED,
    /** 进程运行中且健康检查通过 */
    RUNNING,
    /** 健康检查失败（进程存在但 HTTP 不健康） */
    HEALTH_CHECK_FAILED,
    /** 状态暂时无法确认（网络超时、网关异常等瞬时故障） */
    STALE
}
