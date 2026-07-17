package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.user.UserId;

/**
 * 公共 Agent/Skill 配置发布期间的新消息闸门；后端 Run 入口必须以此结果为准。
 */
public interface PublicAgentConfigMessageGate {

    /**
     * 返回指定用户的门禁状态；兼容无用户主体的内部调用时，null 表示保守使用全局 rollout 门禁。
     */
    MessageGateStatus status(UserId userId);

    /**
     * 当前闸门状态。rolloutId 便于前端和日志关联具体发布任务。
     */
    record MessageGateStatus(boolean allowed, String rolloutId, String reason) {

        public static MessageGateStatus open() {
            return new MessageGateStatus(true, null, null);
        }

        public static MessageGateStatus blocked(String rolloutId) {
            return new MessageGateStatus(false, rolloutId, "公共 Agent/Skill 配置正在同步，旧会话排空后将自动恢复发送");
        }
    }
}
