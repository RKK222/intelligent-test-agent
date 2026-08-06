-- 公共 Git Skill 的分类独立于具体修订保存，后续 Git commit 对账不能覆盖超级管理员分类。
create table agent_skill_hub_builtin_classifications (
    asset_id varchar(256) primary key,
    skill_category varchar(16) not null default 'OTHER',
    skill_subcategory varchar(32),
    classified_by_user_id varchar(128),
    classified_at timestamp not null,
    constraint fk_hub_builtin_classifier foreign key (classified_by_user_id) references users(user_id),
    constraint ck_hub_builtin_skill_classification check (
        (skill_category in ('WORKER', 'OTHER') and skill_subcategory is null)
        or (skill_category = 'TEST' and skill_subcategory in (
            'TEST_DESIGN', 'TEST_DATA_CONSTRUCTION', 'TEST_EXECUTION', 'TEST_ANALYSIS'))
        or (skill_category = 'CODE' and skill_subcategory = 'WHITE_BOX_ANALYSIS')
    )
);

insert into agent_skill_hub_builtin_classifications(
    asset_id, skill_category, skill_subcategory, classified_by_user_id, classified_at)
select distinct asset_id, 'OTHER', null, null, current_timestamp
from agent_skill_hub_builtin_revisions
where asset_type = 'SKILL';

create index idx_hub_builtin_skill_classification
    on agent_skill_hub_builtin_classifications(skill_category, skill_subcategory);

comment on table agent_skill_hub_builtin_classifications is '公共 Git Skill 跨修订保留的事项分类';
comment on column agent_skill_hub_builtin_classifications.classified_by_user_id is '最近一次人工分类的超级管理员';
comment on column agent_skill_hub_builtin_classifications.classified_at is '默认归类或最近一次人工分类时间';
