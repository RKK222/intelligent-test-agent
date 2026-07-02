-- 版本库类型字典：用于新增版本库时选择测试工作库、应用代码库或应用资产库
insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_repository_type_test_work', '版本库类型', 'REPOSITORY_TYPE', 'TEST_WORK_REPOSITORY', '测试工作库', 1, current_timestamp, current_timestamp
where not exists (
    select 1 from dictionaries where dict_key = 'REPOSITORY_TYPE' and dict_value = 'TEST_WORK_REPOSITORY'
);

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_repository_type_application_code', '版本库类型', 'REPOSITORY_TYPE', 'APPLICATION_CODE_REPOSITORY', '应用代码库', 2, current_timestamp, current_timestamp
where not exists (
    select 1 from dictionaries where dict_key = 'REPOSITORY_TYPE' and dict_value = 'APPLICATION_CODE_REPOSITORY'
);

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_repository_type_application_asset', '版本库类型', 'REPOSITORY_TYPE', 'APPLICATION_ASSET_REPOSITORY', '应用资产库', 3, current_timestamp, current_timestamp
where not exists (
    select 1 from dictionaries where dict_key = 'REPOSITORY_TYPE' and dict_value = 'APPLICATION_ASSET_REPOSITORY'
);

alter table code_repositories
    add column repository_type varchar(128) not null default 'APPLICATION_CODE_REPOSITORY';

update code_repositories
set repository_type = 'TEST_WORK_REPOSITORY'
where standard = true;

update code_repositories
set repository_type = 'APPLICATION_CODE_REPOSITORY'
where standard = false;

comment on column code_repositories.repository_type is '版本库类型，取自通用字典REPOSITORY_TYPE；测试工作库兼容旧standard=true，其它类型兼容standard=false';
