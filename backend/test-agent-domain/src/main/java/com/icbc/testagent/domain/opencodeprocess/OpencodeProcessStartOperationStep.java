package com.icbc.testagent.domain.opencodeprocess;

/**
 * 当前用户 opencode 进程启动公共链路的进度步骤，枚举顺序用于前端计算步骤状态。
 */
public enum OpencodeProcessStartOperationStep {
    VALIDATING_REQUEST("校验请求"),
    CHECKING_ASSIGNMENT("确认分配"),
    SELECTING_CONTAINER("选择容器"),
    PREPARING_STARTUP("准备启动参数"),
    STARTING_PROCESS("进程启动"),
    SAVING_CANDIDATE("记录候选进程"),
    CHECKING_PROCESS("检查进程"),
    HEALTH_CHECKING("健康检查"),
    SAVING_BINDING("写入绑定"),
    COMPLETED("完成");

    private final String displayName;

    OpencodeProcessStartOperationStep(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
