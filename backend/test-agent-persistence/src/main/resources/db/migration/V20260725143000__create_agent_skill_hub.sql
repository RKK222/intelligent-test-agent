-- Agent & Skill Hub：远端 push 后的不可变制品、发布依赖和应用级引用状态。
create table agent_skill_hub_artifacts (
    artifact_sha256 varchar(64) primary key,
    encoding varchar(32) not null,
    content bytea not null,
    manifest_json text not null,
    uncompressed_size bigint not null,
    compressed_size bigint not null,
    file_count integer not null,
    created_at timestamp not null,
    constraint ck_hub_artifact_sizes check (uncompressed_size >= 0 and compressed_size >= 0 and file_count > 0)
);

create table agent_skill_hub_assets (
    asset_id varchar(128) primary key,
    source_app_id varchar(128) not null,
    source_application_workspace_id varchar(128) not null,
    asset_type varchar(16) not null,
    technical_id varchar(128) not null,
    latest_pushed_revision_id varchar(128),
    latest_published_revision_id varchar(128),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_hub_asset_app foreign key (source_app_id) references applications(app_id),
    constraint fk_hub_asset_workspace foreign key (source_application_workspace_id) references application_workspaces(workspace_id),
    constraint ck_hub_asset_type check (asset_type in ('AGENT', 'SKILL')),
    constraint uk_hub_asset_identity unique (source_app_id, source_application_workspace_id, asset_type, technical_id)
);

create table agent_skill_hub_revisions (
    revision_id varchar(128) primary key,
    asset_id varchar(128) not null,
    source_version_id varchar(128) not null,
    source_commit_hash varchar(128) not null,
    artifact_sha256 varchar(64),
    content_sha256 varchar(64) not null,
    display_name varchar(1024),
    display_name_en varchar(1024),
    description varchar(1024),
    deleted boolean not null default false,
    pushed_at timestamp not null,
    published_at timestamp,
    published_by_user_id varchar(128),
    constraint fk_hub_revision_asset foreign key (asset_id) references agent_skill_hub_assets(asset_id),
    constraint fk_hub_revision_version foreign key (source_version_id) references application_workspace_versions(version_id),
    constraint fk_hub_revision_artifact foreign key (artifact_sha256) references agent_skill_hub_artifacts(artifact_sha256),
    constraint fk_hub_revision_publisher foreign key (published_by_user_id) references users(user_id),
    constraint ck_hub_revision_artifact check ((deleted and artifact_sha256 is null) or (not deleted and artifact_sha256 is not null)),
    constraint uk_hub_revision_commit unique (asset_id, source_commit_hash)
);

alter table agent_skill_hub_assets
    add constraint fk_hub_asset_latest_pushed foreign key (latest_pushed_revision_id) references agent_skill_hub_revisions(revision_id);
alter table agent_skill_hub_assets
    add constraint fk_hub_asset_latest_published foreign key (latest_published_revision_id) references agent_skill_hub_revisions(revision_id);

create table agent_skill_hub_dependencies (
    revision_id varchar(128) not null,
    dependency_asset_id varchar(128) not null,
    dependency_revision_id varchar(128) not null,
    created_at timestamp not null,
    primary key (revision_id, dependency_asset_id),
    constraint fk_hub_dependency_revision foreign key (revision_id) references agent_skill_hub_revisions(revision_id) on delete cascade,
    constraint fk_hub_dependency_asset foreign key (dependency_asset_id) references agent_skill_hub_assets(asset_id),
    constraint fk_hub_dependency_exact_revision foreign key (dependency_revision_id) references agent_skill_hub_revisions(revision_id),
    constraint ck_hub_dependency_self check (revision_id <> dependency_revision_id)
);

create table agent_skill_hub_references (
    reference_id varchar(128) primary key,
    asset_id varchar(128) not null,
    target_app_id varchar(128) not null,
    target_application_workspace_id varchar(128) not null,
    target_path varchar(512) not null,
    alias_technical_id varchar(128) not null,
    active_revision_id varchar(128),
    pending_revision_id varchar(128),
    pending_content_sha256 varchar(64),
    status varchar(32) not null,
    created_by_user_id varchar(128) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_hub_reference_asset foreign key (asset_id) references agent_skill_hub_assets(asset_id),
    constraint fk_hub_reference_app foreign key (target_app_id) references applications(app_id),
    constraint fk_hub_reference_workspace foreign key (target_application_workspace_id) references application_workspaces(workspace_id),
    constraint fk_hub_reference_active_revision foreign key (active_revision_id) references agent_skill_hub_revisions(revision_id),
    constraint fk_hub_reference_pending_revision foreign key (pending_revision_id) references agent_skill_hub_revisions(revision_id),
    constraint fk_hub_reference_creator foreign key (created_by_user_id) references users(user_id),
    constraint ck_hub_reference_status check (status in ('ACTIVE', 'PENDING_PUSH', 'UPDATE_CONFLICT')),
    constraint uk_hub_reference_target unique (target_application_workspace_id, target_path)
);

create table agent_skill_hub_update_operations (
    operation_id varchar(128) primary key,
    reference_id varchar(128) not null,
    target_personal_workspace_id varchar(128) not null,
    from_revision_id varchar(128),
    to_revision_id varchar(128) not null,
    status varchar(32) not null,
    conflicts_json text not null,
    created_by_user_id varchar(128) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_hub_update_reference foreign key (reference_id) references agent_skill_hub_references(reference_id),
    constraint fk_hub_update_personal foreign key (target_personal_workspace_id) references personal_workspaces(personal_workspace_id),
    constraint fk_hub_update_from_revision foreign key (from_revision_id) references agent_skill_hub_revisions(revision_id),
    constraint fk_hub_update_to_revision foreign key (to_revision_id) references agent_skill_hub_revisions(revision_id),
    constraint fk_hub_update_user foreign key (created_by_user_id) references users(user_id),
    constraint ck_hub_update_status check (status in ('CONFLICT', 'RESOLVED', 'COMPLETED', 'ABORTED'))
);

create index idx_hub_assets_browse on agent_skill_hub_assets(asset_type, updated_at desc);
create index idx_hub_revisions_asset_pushed on agent_skill_hub_revisions(asset_id, pushed_at desc);
create index idx_hub_references_target_updates on agent_skill_hub_references(target_app_id, status, updated_at desc);
create index idx_hub_update_operations_reference on agent_skill_hub_update_operations(reference_id, updated_at desc);

comment on table agent_skill_hub_artifacts is 'Agent/Skill Hub 内容寻址不可变压缩制品';
comment on table agent_skill_hub_assets is '按来源应用、工作空间模板、类型和英文技术ID识别的逻辑资产';
comment on table agent_skill_hub_revisions is '应用远端 push 成功后固化的不可变资产修订';
comment on table agent_skill_hub_dependencies is '发布修订声明的精确 Agent/Skill 依赖';
comment on table agent_skill_hub_references is '目标应用工作空间对 Hub 资产的应用级引用';
comment on table agent_skill_hub_update_operations is '引用更新三方合并的持久化冲突操作';
