-- 当前 release 主链已晚于旧体验工作区候选版本，使用更高版本前向迁移补齐同一结构。
insert into common_parameters(
    parameter_id, parameter_english, parameter_chinese, parameter_value,
    platform, editable, created_at, updated_at
) values (
    'param_opencode_experience_workspace_dir_all',
    'OPENCODE_EXPERIENCE_WORKSPACE_DIR',
    '平台体验工作区目录',
    'UNCONFIGURED',
    'all',
    true,
    current_timestamp,
    current_timestamp
)
on conflict (parameter_english, platform) do nothing;

-- 已执行旧候选版本时表已存在；未执行时由本迁移创建，避免启用 Flyway outOfOrder。
create table if not exists experience_workspace_bindings (
    linux_server_id varchar(128) primary key,
    workspace_id varchar(128) not null unique,
    configured_parameter_value text not null,
    trace_id varchar(128) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_experience_workspace_bindings_workspace
        foreign key (workspace_id) references workspaces(workspace_id)
);

comment on table experience_workspace_bindings is '每台后端服务器当前生效的平台体验工作区绑定';
comment on column experience_workspace_bindings.linux_server_id is '稳定后端服务器ID，一台服务器仅一条当前绑定';
comment on column experience_workspace_bindings.workspace_id is '当前体验Workspace ID，换目录后指向新记录';
comment on column experience_workspace_bindings.configured_parameter_value is '登记时体验目录通用参数原值';
comment on column experience_workspace_bindings.trace_id is '最近一次登记或切换绑定的链路traceId';
comment on column experience_workspace_bindings.created_at is '本服务器首次登记体验绑定时间';
comment on column experience_workspace_bindings.updated_at is '当前绑定最近更新时间';

create index if not exists idx_application_members_active_user
    on application_members(user_id)
    where deleted_at is null;
