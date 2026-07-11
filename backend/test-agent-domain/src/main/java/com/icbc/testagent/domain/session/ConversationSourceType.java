package com.icbc.testagent.domain.session;

/**
 * 会话、Run 和消息的来源类型，用于区分人工发起与定时任务后台发起的对话链路。
 */
public enum ConversationSourceType {
    MANUAL,
    SCHEDULED_TASK,
    SIDE_QUESTION
}
