package com.enterprise.testagent.common.id;

import java.util.UUID;

/**
 * Runtime API 使用的业务 ID 生成器，统一生成带领域前缀的外部可见 ID。
 */
public final class RuntimeIdGenerator {

    /**
     * 工具类不允许实例化，所有 ID 生成入口都通过静态方法暴露。
     */
    private RuntimeIdGenerator() {
    }

    /**
     * 生成 Workspace 外部 ID，返回值固定使用 `wrk_` 前缀，便于 API 与日志中识别资源类型。
     */
    public static String workspaceId() {
        return prefixed("wrk_");
    }

    /**
     * 生成平台 Session ID，返回值只表示平台会话，不可与远端 opencode session id 混用。
     */
    public static String sessionId() {
        return prefixed("ses_");
    }

    /**
     * 生成 Run ID，供运行编排、事件流和 Diff 操作共同引用同一次运行。
     */
    public static String runId() {
        return prefixed("run_");
    }

    /**
     * 生成会话消息 ID，供平台持久化消息和恢复投影时稳定定位消息。
     */
    public static String messageId() {
        return prefixed("msg_");
    }

    /**
     * 生成 AI 回复反馈 ID，供满意/不满意反馈 API 和运营明细稳定引用。
     */
    public static String feedbackId() {
        return prefixed("fb_");
    }

    /**
     * 生成运营汇总任务运行 ID，便于记录 rollup job 排障轨迹。
     */
    public static String analyticsJobRunId() {
        return prefixed("ajr_");
    }

    /**
     * 生成 PTY ticket ID，返回值仅用于短生命周期终端连接授权。
     */
    public static String terminalTicketId() {
        return prefixed("pty_");
    }

    /**
     * 生成用户外部 ID，返回值固定使用 {@code usr_} 前缀。
     */
    public static String userId() {
        return prefixed("usr_");
    }

    /**
     * 生成登录日志 ID，返回值固定使用 {@code log_} 前缀。
     */
    public static String logId() {
        return prefixed("log_");
    }

    /**
     * 生成字典 ID，返回值固定使用 {@code dict_} 前缀。
     */
    public static String dictId() {
        return prefixed("dict_");
    }

    /**
     * 生成代码库配置 ID，返回值固定使用 {@code repo_} 前缀。
     */
    public static String repositoryId() {
        return prefixed("repo_");
    }

    /**
     * 生成应用工作空间配置 ID，返回值固定使用 {@code awp_} 前缀。
     */
    public static String applicationWorkspaceId() {
        return prefixed("awp_");
    }

    /**
     * 生成用户 SSH key 配置 ID，返回值固定使用 {@code ssh_} 前缀。
     */
    public static String sshKeyId() {
        return prefixed("ssh_");
    }

    /**
     * 生成应用版本工作区 ID，返回值固定使用 {@code awv_} 前缀。
     */
    public static String applicationWorkspaceVersionId() {
        return prefixed("awv_");
    }

    /**
     * 生成应用版本工作区服务器副本 ID，返回值固定使用 {@code awr_} 前缀。
     */
    public static String applicationWorkspaceVersionReplicaId() {
        return prefixed("awr_");
    }

    /**
     * 生成个人工作区 ID，返回值固定使用 {@code psw_} 前缀。
     */
    public static String personalWorkspaceId() {
        return prefixed("psw_");
    }

    /**
     * 生成工作区同步记录 ID，返回值固定使用 {@code sync_} 前缀。
     */
    public static String workspaceSyncRecordId() {
        return prefixed("sync_");
    }

    /**
     * 生成服务器广播事件 ID，返回值固定使用 {@code sbe_} 前缀。
     */
    public static String serverBroadcastEventId() {
        return prefixed("sbe_");
    }

    /**
     * 生成 Agent 配置 worktree 记录 ID，返回值固定使用 {@code agw_} 前缀。
     */
    public static String agentConfigWorktreeId() {
        return prefixed("agw_");
    }

    /**
     * 生成公共 Agent 配置发布排空任务 ID。
     */
    public static String publicAgentConfigRolloutId() {
        return prefixed("acr_");
    }

    /**
     * 生成公共 Agent 配置发布所登记的 opencode 进程目标 ID。
     */
    public static String publicAgentConfigRolloutTargetId() {
        return prefixed("act_");
    }

    /**
     * 生成后端 Java 进程 ID，返回值固定使用 {@code bjp_} 前缀。
     */
    public static String backendProcessId() {
        return prefixed("bjp_");
    }

    /**
     * 生成容器管理进程 ID，返回值固定使用 {@code mgr_} 前缀。
     */
    public static String containerManagerId() {
        return prefixed("mgr_");
    }

    /**
     * 生成 opencode server 进程 ID，返回值固定使用 {@code ocp_} 前缀。
     */
    public static String opencodeProcessId() {
        return prefixed("ocp_");
    }

    /**
     * 生成管理进程控制命令 ID，返回值固定使用 {@code mcmd_} 前缀。
     */
    public static String managerCommandId() {
        return prefixed("mcmd_");
    }

    /**
     * 生成定时任务运行记录 ID，返回值固定使用 {@code str_} 前缀。
     */
    public static String scheduledTaskRunId() {
        return prefixed("str_");
    }

    /**
     * 生成用户级定时任务计划 ID，返回值固定使用 {@code stp_} 前缀。
     */
    public static String scheduledTaskPlanId() {
        return prefixed("stp_");
    }

    /**
     * 按给定领域前缀拼接无横线 UUID；调用方必须传入已约定的稳定前缀。
     */
    private static String prefixed(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
