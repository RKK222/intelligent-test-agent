-- Skill Hub 双来源：平台推送资产与外部 SkillHub 目录共用引用模型，外部正文按需物化。
alter table agent_skill_hub_assets
    alter column source_app_id drop not null;
alter table agent_skill_hub_assets
    alter column source_application_workspace_id drop not null;

alter table agent_skill_hub_assets
    add column source_kind varchar(16) not null default 'PLATFORM';
alter table agent_skill_hub_assets
    add column source_available boolean not null default true;
alter table agent_skill_hub_assets
    add column external_identity_key varchar(128);
alter table agent_skill_hub_assets
    add column external_skill_id bigint;
alter table agent_skill_hub_assets
    add column external_version varchar(128);
alter table agent_skill_hub_assets
    add column external_source varchar(256);
alter table agent_skill_hub_assets
    add column external_tag varchar(256);
alter table agent_skill_hub_assets
    add column external_phase varchar(128);
alter table agent_skill_hub_assets
    add column external_phase_name varchar(256);
alter table agent_skill_hub_assets
    add column external_contributor varchar(256);
alter table agent_skill_hub_assets
    add column external_download_count bigint;
alter table agent_skill_hub_assets
    add column catalog_display_name varchar(1024);
alter table agent_skill_hub_assets
    add column catalog_description varchar(1024);
alter table agent_skill_hub_assets
    add column forked_from_asset_id varchar(128);
alter table agent_skill_hub_assets
    add column forked_from_revision_id varchar(128);

alter table agent_skill_hub_assets
    add constraint uk_hub_asset_external_identity unique (external_identity_key);
alter table agent_skill_hub_assets
    add constraint fk_hub_asset_fork_asset foreign key (forked_from_asset_id)
        references agent_skill_hub_assets(asset_id);
alter table agent_skill_hub_assets
    add constraint fk_hub_asset_fork_revision foreign key (forked_from_revision_id)
        references agent_skill_hub_revisions(revision_id);
alter table agent_skill_hub_assets
    add constraint ck_hub_asset_source check (
        (source_kind = 'PLATFORM' and source_app_id is not null
            and source_application_workspace_id is not null and external_identity_key is null)
        or
        (source_kind = 'SKILLHUB' and asset_type = 'SKILL' and source_app_id is null
            and source_application_workspace_id is null and external_identity_key is not null
            and external_skill_id is not null and external_version is not null)
    );

alter table agent_skill_hub_revisions
    alter column source_version_id drop not null;
alter table agent_skill_hub_revisions
    add column external_skill_id bigint;
alter table agent_skill_hub_revisions
    add column external_version varchar(128);
alter table agent_skill_hub_revisions
    add constraint uk_hub_revision_external unique (asset_id, external_skill_id, external_version);

create index idx_hub_assets_source_browse
    on agent_skill_hub_assets(source_kind, source_available, asset_type, updated_at desc);

comment on column agent_skill_hub_assets.source_kind is '来源：PLATFORM/SKILLHUB';
comment on column agent_skill_hub_assets.source_available is '来源当前是否仍可发现和新建引用';
comment on column agent_skill_hub_assets.external_identity_key is 'SkillHub 稳定英文 name，仅外部来源非空';
comment on column agent_skill_hub_assets.forked_from_asset_id is '外部 Skill 被修改后推送形成的平台派生来源资产';
comment on column agent_skill_hub_revisions.external_skill_id is 'SkillHub 下载接口版本 ID';
comment on column agent_skill_hub_revisions.external_version is 'SkillHub 外部版本号';
