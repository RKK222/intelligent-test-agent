alter table public_agent_config_rollouts
    drop constraint if exists ck_public_agent_config_rollouts_scope;

alter table public_agent_config_rollouts
    add constraint ck_public_agent_config_rollouts_scope
    check (config_scope in ('PUBLIC', 'APPLICATION', 'PERSONAL_APPLICATION'));

comment on column public_agent_config_rollouts.config_scope is
    '配置排空范围：PUBLIC 公共共享层，APPLICATION 应用共享层，PERSONAL_APPLICATION 个人拉取后的单用户应用层';
