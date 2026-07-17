package com.enterprise.testagent.domain.configuration;

/**
 * 公共 Agent/Skill 配置发布期间的新消息闸门；后端 Run 入口必须以此结果为准。
 */
public interface PublicAgentConfigMessageGate {

    MessageGateStatus status();

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
