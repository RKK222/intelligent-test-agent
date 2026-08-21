create table application_automation_references (
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    active_generation bigint,
    pending_generation bigint,
    next_generation bigint not null default 1,
    lock_version bigint not null default 0,
    status varchar(32) not null,
    operation_type varchar(32) not null,
    trace_id varchar(128) not null,
    last_error varchar(1000),
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (app_id, repository_id),
    constraint fk_application_automation_references_app
        foreign key (app_id) references applications(app_id),
    constraint fk_application_automation_references_repository
        foreign key (repository_id) references code_repositories(repository_id),
    constraint chk_application_automation_references_generations check (
        (active_generation is null or active_generation >= 1)
        and (pending_generation is null or pending_generation >= 1)
        and next_generation >= 1
        and lock_version >= 0
        and (active_generation is null or pending_generation is null or active_generation <> pending_generation)
    ),
    constraint chk_application_automation_references_status check (
        status in ('UNINITIALIZED', 'INITIALIZING', 'VERIFYING', 'SYNCHRONIZING', 'READY', 'FAILED')
    ),
    constraint chk_application_automation_references_operation check (
        operation_type in ('CONFIGURE', 'SYNCHRONIZE', 'VERIFY_POINTERS')
    )
);

create table application_automation_reference_generations (
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    generation bigint not null,
    branch varchar(255) not null,
    directory_path varchar(1024) not null,
    description varchar(2000) not null,
    merge_enabled boolean not null default false,
    target_commit_hash varchar(128) not null,
    status varchar(32) not null,
    operation_type varchar(32) not null,
    operated_by_user_id varchar(128) not null,
    operation_id varchar(128) not null,
    trace_id varchar(128) not null,
    last_error varchar(1000),
    activated_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (app_id, repository_id, generation),
    constraint fk_application_automation_reference_generations_state
        foreign key (app_id, repository_id)
        references application_automation_references(app_id, repository_id) on delete cascade,
    constraint fk_application_automation_reference_generations_user
        foreign key (operated_by_user_id) references users(user_id),
    constraint uk_application_automation_reference_generations_operation
        unique (app_id, repository_id, operation_id),
    constraint chk_application_automation_reference_generations_generation check (generation >= 1),
    constraint chk_application_automation_reference_generations_merge check (merge_enabled = false),
    constraint chk_application_automation_reference_generations_status check (
        status in ('SYNCHRONIZING', 'READY', 'FAILED', 'RETIRED')
    ),
    constraint chk_application_automation_reference_generations_operation check (
        operation_type in ('CONFIGURE', 'SYNCHRONIZE', 'VERIFY_POINTERS')
    )
);

create table application_automation_reference_replicas (
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    generation bigint not null,
    linux_server_id varchar(128) not null,
    status varchar(32) not null,
    current_branch varchar(255),
    current_commit_hash varchar(128),
    retry_count integer not null default 0,
    next_retry_at timestamp,
    lease_token varchar(128),
    lease_until timestamp,
    last_error varchar(1000),
    synced_at timestamp,
    verified_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (app_id, repository_id, generation, linux_server_id),
    constraint fk_application_automation_reference_replicas_generation
        foreign key (app_id, repository_id, generation)
        references application_automation_reference_generations(app_id, repository_id, generation) on delete cascade,
    constraint chk_application_automation_reference_replicas_generation check (generation >= 1),
    constraint chk_application_automation_reference_replicas_retry_count check (retry_count >= 0),
    constraint chk_application_automation_reference_replicas_status check (
        status in ('PENDING', 'PROCESSING', 'READY', 'RETRY_WAIT', 'BLOCKED', 'DEFERRED')
    )
);

create table application_automation_reference_run_leases (
    run_id varchar(128) not null,
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    generation bigint not null,
    linux_server_id varchar(128) not null,
    created_at timestamp not null,
    primary key (run_id, app_id, repository_id),
    constraint fk_application_automation_reference_run_leases_run
        foreign key (run_id) references runs(run_id) on delete cascade,
    constraint fk_application_automation_reference_run_leases_generation
        foreign key (app_id, repository_id, generation)
        references application_automation_reference_generations(app_id, repository_id, generation)
);

create index idx_application_automation_reference_replicas_claim
    on application_automation_reference_replicas(linux_server_id, status, next_retry_at, lease_until, updated_at);
create index idx_application_automation_reference_generations_recover
    on application_automation_reference_generations(status, updated_at, app_id, repository_id, generation);
create index idx_application_automation_reference_run_leases_generation
    on application_automation_reference_run_leases(app_id, repository_id, generation);

comment on table application_automation_references is '每个应用、每个自动化版本库唯一的当前引用状态';
comment on column application_automation_references.active_generation is '当前已生效的共享只读配置代次';
comment on column application_automation_references.pending_generation is '正在同步且尚未整体激活的配置代次';
comment on column application_automation_references.lock_version is '保存和终止操作的乐观锁版本';
comment on table application_automation_reference_generations is '应用自动化引用的不可变分支、目录、描述和目标提交代次';
comment on column application_automation_reference_generations.directory_path is '共享仓库副本内的逻辑相对目录，根目录为空字符串';
comment on column application_automation_reference_generations.description is '应用成员共享的自动化引用用途描述';
comment on column application_automation_reference_generations.merge_enabled is '固定为否，自动化引用不参与引用合并';
comment on table application_automation_reference_replicas is '每个自动化引用代次、每台服务器唯一的共享只读仓库副本任务';
comment on table application_automation_reference_run_leases is '运行中任务对实际自动化引用代次的生命周期租约，不进入提示词或消息';
comment on column application_automation_reference_run_leases.linux_server_id is '本次 Run 实际使用共享副本所在服务器';

-- 从已执行旧模型中为每个应用、每个自动化版本库挑选最新有效配置。
insert into application_automation_references(
    app_id, repository_id, active_generation, pending_generation, next_generation,
    lock_version, status, operation_type, trace_id, created_at, updated_at
)
with workspace_versions as (
    select workspace.app_id,
           workspace.repository_id,
           workspace.workspace_id,
           workspace.directory_path,
           workspace.enabled,
           version.version_id,
           version.branch,
           version.target_commit_hash,
           version.created_by_user_id,
           coalesce(active_version.updated_at, version.updated_at, workspace.updated_at) as effective_at,
           row_number() over (
               partition by workspace.workspace_id
               order by case when active_version.version_id = version.version_id then 0 else 1 end,
                        version.created_at desc,
                        version.version_id desc
           ) as version_position
    from application_workspaces workspace
    join code_repositories repository on repository.repository_id = workspace.repository_id
    join application_workspace_versions version
      on version.application_workspace_id = workspace.workspace_id
     and version.status = 'ACTIVE'
     and version.target_commit_hash is not null
    left join automation_workspace_active_versions active_version
      on active_version.application_workspace_id = workspace.workspace_id
    where repository.repository_type = 'AUTOMATION_CODE_REPOSITORY'
), repository_configs as (
    select workspace_versions.*,
           row_number() over (
               partition by app_id, repository_id
               order by case when enabled then 0 else 1 end,
                        effective_at desc,
                        workspace_id desc
           ) as repository_position
    from workspace_versions
    where version_position = 1
), selected as (
    select config.*, repository.name as repository_name
    from repository_configs config
    join code_repositories repository on repository.repository_id = config.repository_id
    where config.repository_position = 1
)
select app_id, repository_id, 1, null, 2,
       0, 'SYNCHRONIZING', 'SYNCHRONIZE', 'migration-automation-reference', current_timestamp, current_timestamp
from selected;

insert into application_automation_reference_generations(
    app_id, repository_id, generation, branch, directory_path, description, merge_enabled,
    target_commit_hash, status, operation_type, operated_by_user_id, operation_id, trace_id,
    created_at, updated_at
)
with workspace_versions as (
    select workspace.app_id,
           workspace.repository_id,
           workspace.workspace_id,
           workspace.directory_path,
           workspace.enabled,
           version.version_id,
           version.branch,
           version.target_commit_hash,
           version.created_by_user_id,
           coalesce(active_version.updated_at, version.updated_at, workspace.updated_at) as effective_at,
           row_number() over (
               partition by workspace.workspace_id
               order by case when active_version.version_id = version.version_id then 0 else 1 end,
                        version.created_at desc,
                        version.version_id desc
           ) as version_position
    from application_workspaces workspace
    join code_repositories repository on repository.repository_id = workspace.repository_id
    join application_workspace_versions version
      on version.application_workspace_id = workspace.workspace_id
     and version.status = 'ACTIVE'
     and version.target_commit_hash is not null
    left join automation_workspace_active_versions active_version
      on active_version.application_workspace_id = workspace.workspace_id
    where repository.repository_type = 'AUTOMATION_CODE_REPOSITORY'
), repository_configs as (
    select workspace_versions.*,
           row_number() over (
               partition by app_id, repository_id
               order by case when enabled then 0 else 1 end,
                        effective_at desc,
                        workspace_id desc
           ) as repository_position
    from workspace_versions
    where version_position = 1
), selected as (
    select config.*, repository.name as repository_name
    from repository_configs config
    join code_repositories repository on repository.repository_id = config.repository_id
    where config.repository_position = 1
)
select app_id,
       repository_id,
       1,
       branch,
       case when trim(directory_path) in ('', '.') then '' else directory_path end,
       repository_name || ' / ' || branch || ' / ' || case when directory_path = '' then '.' else directory_path end
           || '，只读自动化引用',
       false,
       target_commit_hash,
       'SYNCHRONIZING',
       'SYNCHRONIZE',
       created_by_user_id,
       'migration',
       'migration-automation-reference',
       current_timestamp,
       current_timestamp
from selected;

insert into application_automation_reference_replicas(
    app_id, repository_id, generation, linux_server_id, status, retry_count, created_at, updated_at
)
with workspace_versions as (
    select workspace.app_id,
           workspace.repository_id,
           workspace.workspace_id,
           workspace.enabled,
           version.version_id,
           coalesce(active_version.updated_at, version.updated_at, workspace.updated_at) as effective_at,
           row_number() over (
               partition by workspace.workspace_id
               order by case when active_version.version_id = version.version_id then 0 else 1 end,
                        version.created_at desc,
                        version.version_id desc
           ) as version_position
    from application_workspaces workspace
    join code_repositories repository on repository.repository_id = workspace.repository_id
    join application_workspace_versions version
      on version.application_workspace_id = workspace.workspace_id
     and version.status = 'ACTIVE'
     and version.target_commit_hash is not null
    left join automation_workspace_active_versions active_version
      on active_version.application_workspace_id = workspace.workspace_id
    where repository.repository_type = 'AUTOMATION_CODE_REPOSITORY'
), repository_configs as (
    select workspace_versions.*,
           row_number() over (
               partition by app_id, repository_id
               order by case when enabled then 0 else 1 end,
                        effective_at desc,
                        workspace_id desc
           ) as repository_position
    from workspace_versions
    where version_position = 1
), selected as (
    select * from repository_configs where repository_position = 1
)
select selected.app_id,
       selected.repository_id,
       1,
       replica.linux_server_id,
       'PENDING',
       0,
       current_timestamp,
       current_timestamp
from selected
join application_workspace_version_replicas replica on replica.version_id = selected.version_id;

-- 旧模板、版本、副本和个人 worktree 均保留追溯，但不再进入正常工作空间入口。
update application_workspaces workspace
set enabled = false,
    updated_at = current_timestamp
where exists (
    select 1
    from code_repositories repository
    where repository.repository_id = workspace.repository_id
      and repository.repository_type = 'AUTOMATION_CODE_REPOSITORY'
);
