create table application_asset_references (
    app_id varchar(128) not null references applications(app_id),
    repository_id varchar(128) not null references code_repositories(repository_id),
    directory_path varchar(1000) not null,
    alias varchar(128) not null,
    merge_enabled boolean not null,
    sdd_folder_name varchar(255) not null,
    description varchar(2000) not null,
    version bigint not null default 1,
    updated_at timestamp not null,
    primary key (app_id, repository_id, directory_path),
    constraint uk_application_asset_references_alias unique (app_id, alias),
    constraint chk_application_asset_references_version check (version >= 1)
);

create table application_asset_reference_import_sources (
    workspace_id varchar(128) primary key references workspaces(workspace_id),
    app_id varchar(128) not null references applications(app_id),
    linux_server_id varchar(128),
    status varchar(16) not null default 'PENDING',
    lease_until timestamp,
    next_retry_at timestamp,
    safe_error varchar(255),
    scanned_at timestamp,
    constraint chk_asset_reference_import_source_status check (status in ('PENDING', 'SCANNING', 'DONE'))
);
create index idx_asset_reference_import_sources_claim
    on application_asset_reference_import_sources(linux_server_id, status, next_retry_at, lease_until);
create index idx_asset_reference_import_sources_app
    on application_asset_reference_import_sources(app_id, status);

create table application_asset_reference_import_candidates (
    workspace_id varchar(128) not null references application_asset_reference_import_sources(workspace_id) on delete cascade,
    app_id varchar(128) not null references applications(app_id),
    repository_id varchar(128) not null references code_repositories(repository_id),
    directory_path varchar(1000) not null,
    alias varchar(128) not null,
    merge_enabled boolean not null,
    sdd_folder_name varchar(255) not null,
    description varchar(2000) not null,
    primary key (workspace_id, alias)
);
create index idx_asset_reference_import_candidates_app
    on application_asset_reference_import_candidates(app_id, alias);

create table application_asset_reference_import_results (
    app_id varchar(128) primary key references applications(app_id),
    completed_at timestamp not null
);
create table application_asset_reference_import_conflicts (
    app_id varchar(128) not null references applications(app_id),
    alias varchar(128) not null,
    reason varchar(255) not null,
    detected_at timestamp not null,
    primary key (app_id, alias)
);

-- 只冻结升级时已经存在的管理员个人工作区身份；文件正文由所属服务器后续只读扫描。
insert into application_asset_reference_import_sources(workspace_id, app_id, linux_server_id)
select distinct pw.runtime_workspace_id, pw.app_id, w.linux_server_id
from personal_workspaces pw
join workspaces w on w.workspace_id = pw.runtime_workspace_id
join users u on u.user_id = pw.user_id and u.status = 'ACTIVE'
join application_members am on am.app_id = pw.app_id and am.user_id = pw.user_id and am.deleted_at is null
where pw.status = 'ACTIVE'
  and exists (
    select 1 from user_roles ur
    join dictionaries d on d.dict_id = ur.dict_id
    where ur.user_id = pw.user_id and d.dict_key = 'ROLE'
      and d.dict_value in ('APP_ADMIN', 'SYSTEM_ADMIN', 'SUPER_ADMIN')
  );

comment on table application_asset_references is '应用成员共享的资产目录引用配置；资产内容仍由本机 READY 副本只读提供';
comment on table application_asset_reference_import_sources is '升级时冻结的管理员个人工作区迁移扫描任务';
comment on table application_asset_reference_import_candidates is '旧个人 JSONC 中经校验的资产引用候选，不保存文件正文或凭据';
comment on table application_asset_reference_import_results is '旧配置迁移按应用完成标记，阻止后续个人配置重复导入';
comment on table application_asset_reference_import_conflicts is '同一应用旧配置不一致时需管理员处理的安全摘要';
