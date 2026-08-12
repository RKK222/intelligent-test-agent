-- 自动化代码库复用非标准库工作空间流程，旧 standard 兼容字段保持 false。
insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_repository_type_automation_code', '版本库类型', 'REPOSITORY_TYPE', 'AUTOMATION_CODE_REPOSITORY', '自动化代码库', 2, current_timestamp, current_timestamp
where not exists (
    select 1 from dictionaries
    where dict_key = 'REPOSITORY_TYPE'
      and dict_value = 'AUTOMATION_CODE_REPOSITORY'
);

-- 保持系统内置类型顺序稳定：测试工作库、自动化代码库、应用代码库、应用资产库。
update dictionaries
set sort_order = 1, updated_at = current_timestamp
where dict_key = 'REPOSITORY_TYPE'
  and dict_value = 'TEST_WORK_REPOSITORY';

update dictionaries
set dict_label = '自动化代码库', sort_order = 2, updated_at = current_timestamp
where dict_key = 'REPOSITORY_TYPE'
  and dict_value = 'AUTOMATION_CODE_REPOSITORY';

update dictionaries
set sort_order = 3, updated_at = current_timestamp
where dict_key = 'REPOSITORY_TYPE'
  and dict_value = 'APPLICATION_CODE_REPOSITORY';

update dictionaries
set sort_order = 4, updated_at = current_timestamp
where dict_key = 'REPOSITORY_TYPE'
  and dict_value = 'APPLICATION_ASSET_REPOSITORY';
