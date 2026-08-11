-- 扩展受控通知类型与动作；dispose 目标只保存 rolloutId，不允许任意 URL。
alter table user_notifications drop constraint ck_user_notifications_type;
alter table user_notifications add constraint ck_user_notifications_type check (
    type in (
        'SESSION_SHARED',
        'AGENT_CONFIG_DISPOSE_PENDING',
        'AGENT_CONFIG_DISPOSE_SUCCEEDED',
        'AGENT_CONFIG_DISPOSE_FAILED',
        'AGENT_CONFIG_DISPOSE_SUPERSEDED'
    )
);

alter table user_notifications drop constraint ck_user_notifications_action_type;
alter table user_notifications add constraint ck_user_notifications_action_type check (
    action_type in ('SESSION_SHARE', 'NONE', 'RESTART_OWN_PROCESS')
);

comment on column user_notifications.type is '通知业务类型；Agent 配置 dispose 类型表达等待、成功、失败或已替代状态';
comment on column user_notifications.action_type is '受控动作：会话分享、无动作或重启当前用户进程';
