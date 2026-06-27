package com.icbc.testagent.opencode.runtime.process;

/**
 * 当前用户头像菜单展示的 opencode 服务状态，独立于对话门禁使用的进程可用性。
 */
public enum UserOpencodeServiceStatus {
    UNASSIGNED,
    RUNNING,
    NOT_RUNNING
}
