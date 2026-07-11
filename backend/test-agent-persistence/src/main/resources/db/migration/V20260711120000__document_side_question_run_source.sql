-- SIDE_QUESTION 仅扩展既有来源枚举语义，不改变表结构，也不写入任何业务或测试数据。
comment on column sessions.source_type is '会话来源类型：MANUAL/SCHEDULED_TASK/SIDE_QUESTION，默认MANUAL';
comment on column runs.source_type is '运行来源类型：MANUAL/SCHEDULED_TASK/SIDE_QUESTION，默认MANUAL';
comment on column session_messages.source_type is '消息来源类型：MANUAL/SCHEDULED_TASK/SIDE_QUESTION，默认MANUAL';
