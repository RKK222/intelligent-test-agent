alter table user_notifications drop constraint ck_user_notifications_type;
alter table user_notifications add constraint ck_user_notifications_type check (
    type in (
        'SESSION_SHARED', 'AGENT_CONFIG_DISPOSE_PENDING', 'AGENT_CONFIG_DISPOSE_SUCCEEDED',
        'AGENT_CONFIG_DISPOSE_FAILED', 'AGENT_CONFIG_DISPOSE_SUPERSEDED',
        'OPENCODE_CAPACITY_WARNING',
        'LOCAL_CLIENT_UPDATE_AVAILABLE', 'LOCAL_CLIENT_PUBLIC_CAPABILITY_AVAILABLE'
    )
);

comment on column user_notifications.type is
    '通知业务类型；包含分享、Agent 配置、本地客户端和 OpenCode 容量预警';
