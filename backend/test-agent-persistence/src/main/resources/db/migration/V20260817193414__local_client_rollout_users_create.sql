create table local_client_rollout_users (
    user_id varchar(128) primary key references users(user_id) on delete cascade,
    enabled boolean not null default true,
    updated_by_user_id varchar(128) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_local_client_rollout_users_enabled
    on local_client_rollout_users(enabled, updated_at desc, user_id);

comment on table local_client_rollout_users is '超级管理员维护的本地客户端下载入口用户灰度名单';
comment on column local_client_rollout_users.enabled is 'true 时允许该用户看到本地客户端下载安装入口';
comment on column local_client_rollout_users.updated_by_user_id is '最近一次启用或移出灰度名单的超级管理员 userId';
