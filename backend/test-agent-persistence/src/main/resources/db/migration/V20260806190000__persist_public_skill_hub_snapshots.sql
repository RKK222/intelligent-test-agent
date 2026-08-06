-- 公共 Agent/Skill 由定时对账任务按精确 Git commit 固化；Hub 查询不再逐请求扫描 Git。
create table agent_skill_hub_builtin_revisions (
    revision_id varchar(512) primary key,
    asset_id varchar(256) not null,
    asset_type varchar(16) not null,
    technical_id varchar(128) not null,
    source_commit_hash varchar(128) not null,
    artifact_sha256 varchar(64) not null,
    content_sha256 varchar(64) not null,
    display_name varchar(1024),
    display_name_en varchar(1024),
    description varchar(1024),
    pushed_at timestamp not null,
    constraint fk_hub_builtin_revision_artifact foreign key (artifact_sha256)
        references agent_skill_hub_artifacts(artifact_sha256),
    constraint ck_hub_builtin_revision_type check (asset_type in ('AGENT', 'SKILL')),
    constraint uk_hub_builtin_revision_commit unique (asset_id, source_commit_hash)
);

create table agent_skill_hub_builtin_state (
    source_key varchar(32) primary key,
    source_commit_hash varchar(128),
    indexed_at timestamp not null,
    constraint ck_hub_builtin_state_source check (source_key = 'PUBLIC')
);

create index idx_hub_builtin_current_lookup
    on agent_skill_hub_builtin_revisions(source_commit_hash, asset_type, technical_id);

comment on table agent_skill_hub_builtin_revisions is '公共配置 Git 精确提交对应的只读 Agent/Skill 修订';
comment on table agent_skill_hub_builtin_state is '公共 Skill Hub 当前已完成对账的 Git 提交';
