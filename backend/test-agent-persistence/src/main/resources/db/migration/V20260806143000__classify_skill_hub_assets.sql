-- Skill Hub 按具体事项分类；历史与新推送 Skill 默认进入“其他”，仅超级管理员可重新分类。
alter table agent_skill_hub_assets
    add column skill_category varchar(16) not null default 'OTHER';
alter table agent_skill_hub_assets
    add column skill_subcategory varchar(32);
alter table agent_skill_hub_assets
    add column classified_by_user_id varchar(128);
alter table agent_skill_hub_assets
    add column classified_at timestamp;

alter table agent_skill_hub_assets
    add constraint fk_hub_asset_classifier foreign key (classified_by_user_id) references users(user_id);
alter table agent_skill_hub_assets
    add constraint ck_hub_asset_skill_classification check (
        (asset_type = 'AGENT' and skill_category = 'OTHER' and skill_subcategory is null)
        or (asset_type = 'SKILL' and (
            (skill_category in ('WORKER', 'OTHER') and skill_subcategory is null)
            or (skill_category = 'TEST' and skill_subcategory in (
                'TEST_DESIGN', 'TEST_DATA_CONSTRUCTION', 'TEST_EXECUTION', 'TEST_ANALYSIS'))
            or (skill_category = 'CODE' and skill_subcategory = 'WHITE_BOX_ANALYSIS')
        ))
    );

create index idx_hub_assets_skill_classification
    on agent_skill_hub_assets(asset_type, skill_category, skill_subcategory);

comment on column agent_skill_hub_assets.skill_category is 'Skill一级事项分类：WORKER/TEST/CODE/OTHER';
comment on column agent_skill_hub_assets.skill_subcategory is 'Skill二级具体事项；WORKER和OTHER为空';
comment on column agent_skill_hub_assets.classified_by_user_id is '最近一次人工分类的超级管理员';
comment on column agent_skill_hub_assets.classified_at is '最近一次人工分类时间';
