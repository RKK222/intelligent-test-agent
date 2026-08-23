-- 保存用户级巡检投影；第一张表决定 migration 描述中的表名前缀。
create table application_workspace_git_access_checks (
    user_id varchar(128) not null references users(user_id) on delete cascade,
    application_workspace_id varchar(128) not null references application_workspaces(workspace_id) on delete cascade,
    status varchar(32) not null,
    reason varchar(64),
    message varchar(255),
    checked_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (user_id, application_workspace_id),
    constraint ck_application_workspace_git_access_status
        check (status in ('ACCESSIBLE', 'INACCESSIBLE', 'UNKNOWN')),
    constraint ck_application_workspace_git_access_details check (
        (status = 'ACCESSIBLE' and reason is null and message is null)
        or (status in ('INACCESSIBLE', 'UNKNOWN') and reason is not null and message is not null)
    )
);

create table local_workspace_git_access_checks (
    user_id varchar(128) not null references users(user_id) on delete cascade,
    workspace_id varchar(128) not null references workspaces(workspace_id) on delete cascade,
    status varchar(32) not null,
    reason varchar(64),
    message varchar(255),
    checked_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (user_id, workspace_id),
    constraint ck_local_workspace_git_access_status
        check (status in ('ACCESSIBLE', 'INACCESSIBLE', 'UNKNOWN')),
    constraint ck_local_workspace_git_access_details check (
        (status = 'ACCESSIBLE' and reason is null and message is null)
        or (status in ('INACCESSIBLE', 'UNKNOWN') and reason is not null and message is not null)
    )
);

create index idx_application_workspace_git_access_checked
    on application_workspace_git_access_checks(checked_at, application_workspace_id);
create index idx_local_workspace_git_access_checked
    on local_workspace_git_access_checks(checked_at, workspace_id);

comment on table application_workspace_git_access_checks is '用户对应用工作空间关联 Git 仓库的定时只读权限巡检结果';
comment on table local_workspace_git_access_checks is '用户本地客户端工作区的定时 Git 远端权限巡检结果';
comment on column application_workspace_git_access_checks.message is '可直接展示的固定脱敏原因，不保存命令、URL、路径或 stderr';
comment on column local_workspace_git_access_checks.message is '可直接展示的固定脱敏原因，不保存命令、URL、路径或 stderr';
