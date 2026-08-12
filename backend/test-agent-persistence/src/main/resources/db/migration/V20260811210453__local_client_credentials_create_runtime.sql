create table local_client_credentials (
    user_id varchar(128) primary key references users(user_id) on delete cascade,
    encrypted_client_key text not null,
    client_key_fingerprint varchar(64) not null unique,
    key_hint varchar(64) not null,
    version bigint not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint ck_local_client_credentials_version check(version > 0),
    constraint ck_local_client_credentials_status check(status in ('ACTIVE', 'REVOKED')),
    constraint ck_local_client_credentials_revoked check(
        (status = 'ACTIVE' and revoked_at is null)
        or (status = 'REVOKED' and revoked_at is not null)
    )
);

create table local_client_instances (
    client_instance_id varchar(128) primary key,
    user_id varchar(128) not null references users(user_id) on delete cascade,
    client_name varchar(255) not null,
    platform varchar(64) not null,
    architecture varchar(64) not null,
    client_version varchar(64) not null,
    opencode_version varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    last_connected_at timestamp with time zone,
    last_disconnected_at timestamp with time zone,
    constraint uk_local_client_instances_owner unique(client_instance_id, user_id)
);

create table local_client_workspaces (
    workspace_id varchar(128) primary key references workspaces(workspace_id) on delete cascade,
    user_id varchar(128) not null references users(user_id) on delete cascade,
    client_instance_id varchar(128) not null,
    normalized_root_path text not null,
    root_digest varchar(64) not null,
    file_system_identity varchar(512) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_local_client_workspace_owner foreign key(client_instance_id, user_id)
        references local_client_instances(client_instance_id, user_id),
    constraint uk_local_client_workspace_root unique(user_id, client_instance_id, root_digest)
);

alter table sessions add column runtime_kind varchar(32) not null default 'SERVER_PROCESS';
alter table sessions add column local_client_instance_id varchar(128);
alter table sessions add constraint fk_sessions_local_client
    foreign key(local_client_instance_id) references local_client_instances(client_instance_id);
alter table sessions add constraint ck_sessions_runtime_target check(
    (runtime_kind = 'SERVER_PROCESS' and local_client_instance_id is null)
    or (runtime_kind = 'LOCAL_CLIENT' and local_client_instance_id is not null)
);

alter table runs add column target_runtime_kind varchar(32) not null default 'SERVER_PROCESS';
alter table runs add column target_local_client_instance_id varchar(128);
alter table runs add constraint fk_runs_local_client
    foreign key(target_local_client_instance_id) references local_client_instances(client_instance_id);
alter table runs add constraint ck_runs_runtime_target check(
    (target_runtime_kind = 'SERVER_PROCESS' and target_local_client_instance_id is null)
    or (target_runtime_kind = 'LOCAL_CLIENT' and target_local_client_instance_id is not null)
);

alter table night_execution_tasks add column target_runtime_kind varchar(32) not null default 'SERVER_PROCESS';
alter table night_execution_tasks add column target_local_client_instance_id varchar(128);
alter table night_execution_tasks alter column target_linux_server_id drop not null;
alter table night_execution_tasks add constraint fk_night_execution_local_client
    foreign key(target_local_client_instance_id) references local_client_instances(client_instance_id);
alter table night_execution_tasks add constraint ck_night_execution_runtime_target check(
    (target_runtime_kind = 'SERVER_PROCESS'
        and target_local_client_instance_id is null
        and target_linux_server_id is not null)
    or (target_runtime_kind = 'LOCAL_CLIENT'
        and target_local_client_instance_id is not null
        and target_linux_server_id is null)
);

create index idx_local_client_instances_user on local_client_instances(user_id, updated_at desc);
create index idx_local_client_workspaces_client on local_client_workspaces(client_instance_id, workspace_id);
create index idx_sessions_local_client on sessions(local_client_instance_id, updated_at);
create index idx_runs_local_client on runs(target_local_client_instance_id, updated_at);
create index idx_night_execution_local_client
    on night_execution_tasks(target_local_client_instance_id, status, slot_start);

comment on table local_client_credentials is '每用户唯一的本地 OpenCode 客户端认证密钥';
comment on column local_client_credentials.encrypted_client_key is '可重复复制的 client key 密文，禁止直接查询输出';
comment on column local_client_credentials.client_key_fingerprint is 'client key 的 SHA-256 摘要，用于认证';
comment on column local_client_credentials.key_hint is '设置页展示的掩码提示';
comment on column local_client_credentials.version is '轮换版本；轮换和撤销时使旧连接失效';

comment on table local_client_instances is '用户本地 OpenCode 客户端稳定实例和连接历史';
comment on column local_client_instances.client_instance_id is '客户端首次启动生成的 lci_ 稳定实例 ID';
comment on column local_client_instances.last_connected_at is '最近一次完成 WSS 认证时间';
comment on column local_client_instances.last_disconnected_at is '最近一次连接关闭时间';

comment on table local_client_workspaces is '平台工作区到用户本地客户端目录的绑定';
comment on column local_client_workspaces.normalized_root_path is '客户端 toRealPath 后的规范化根路径';
comment on column local_client_workspaces.root_digest is '规范化根路径 SHA-256 摘要，用于 ticket 防换根';
comment on column local_client_workspaces.file_system_identity is '注册时客户端观测的文件系统身份';

comment on column sessions.runtime_kind is '会话冻结的 OpenCode 运行目标类型';
comment on column sessions.local_client_instance_id is 'LOCAL_CLIENT 会话冻结的客户端实例';
comment on column runs.target_runtime_kind is 'Run 启动时冻结的运行目标类型';
comment on column runs.target_local_client_instance_id is 'Run 启动时冻结的本地客户端实例';
comment on column night_execution_tasks.target_runtime_kind is '夜间任务创建时冻结的运行目标类型';
comment on column night_execution_tasks.target_local_client_instance_id is '夜间任务冻结的本地客户端实例';
