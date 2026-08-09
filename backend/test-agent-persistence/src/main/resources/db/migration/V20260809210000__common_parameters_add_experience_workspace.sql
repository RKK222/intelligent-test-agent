-- 平台体验目录由管理员在每台后端服务器人工准备；迁移不创建目录、不执行 git init。
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

-- 每台后端服务器只维护一个当前体验 Workspace；换目录时旧 Workspace 继续保留历史会话与运行外键。
create table experience_workspace_bindings (
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

-- 体验鉴权会在每条文件 RPC 复核用户是否仍有有效应用；按用户建立部分索引避免成员表全表扫描。
create index idx_application_members_active_user
    on application_members(user_id)
    where deleted_at is null;
