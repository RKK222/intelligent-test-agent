alter table public_agent_config_rollouts
    add column config_scope varchar(32) not null default 'PUBLIC',
    add column scope_key varchar(128);

alter table public_agent_config_rollouts
    add constraint ck_public_agent_config_rollouts_scope
    check (config_scope in ('PUBLIC', 'APPLICATION'));

comment on column public_agent_config_rollouts.config_scope is
    '共享配置排空范围：PUBLIC 公共共享层，APPLICATION 应用共享层';
comment on column public_agent_config_rollouts.scope_key is
    '范围业务键；APPLICATION 时保存应用版本 ID，PUBLIC 时为空';
