create table automation_workspace_active_versions (
    application_workspace_id varchar(128) primary key,
    version_id varchar(128) not null unique,
    activated_by_user_id varchar(128),
    activated_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_automation_active_versions_workspace
        foreign key (application_workspace_id) references application_workspaces(workspace_id) on delete cascade,
    constraint fk_automation_active_versions_version
        foreign key (version_id) references application_workspace_versions(version_id),
    constraint fk_automation_active_versions_user
        foreign key (activated_by_user_id) references users(user_id) on delete set null
);

comment on table automation_workspace_active_versions is '自动化代码库工作空间配置当前激活的只读版本';
comment on column automation_workspace_active_versions.application_workspace_id is '自动化代码库工作空间配置业务ID';
comment on column automation_workspace_active_versions.version_id is '当前激活的应用版本工作区业务ID';
comment on column automation_workspace_active_versions.activated_by_user_id is '最近一次显式激活操作人，存量回填使用版本创建人';
comment on column automation_workspace_active_versions.activated_at is '版本激活时间';
comment on column automation_workspace_active_versions.created_at is '激活记录创建时间';
comment on column automation_workspace_active_versions.updated_at is '激活记录更新时间';

insert into automation_workspace_active_versions(
    application_workspace_id,
    version_id,
    activated_by_user_id,
    activated_at,
    created_at,
    updated_at
)
select ranked.application_workspace_id,
       ranked.version_id,
       ranked.created_by_user_id,
       ranked.created_at,
       current_timestamp,
       current_timestamp
from (
    select version.application_workspace_id,
           version.version_id,
           version.created_by_user_id,
           version.created_at,
           row_number() over (
               partition by version.application_workspace_id
               order by version.created_at desc, version.version_id desc
           ) as position
    from application_workspace_versions version
    join application_workspaces workspace
      on workspace.workspace_id = version.application_workspace_id
    join code_repositories repository
      on repository.repository_id = workspace.repository_id
    where repository.repository_type = 'AUTOMATION_CODE_REPOSITORY'
      and version.status = 'ACTIVE'
) ranked
where ranked.position = 1;
