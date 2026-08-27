-- SkillMarket /list 的 createTime/createdAt 是用户提交申请时间，与平台首次同步时间分开保存。
alter table agent_skill_hub_assets
    add column external_created_at timestamp;

comment on column agent_skill_hub_assets.external_created_at
    is 'SkillMarket /list createTime/createdAt，用户提交申请时间；不表示审批完成时间';
