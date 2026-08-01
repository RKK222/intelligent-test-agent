-- 将早期集成名称 UITEST6_BASE_URL 收敛为不带项目版本号的 UITEST_BASE_URL。
insert into common_parameters(
    parameter_id,
    parameter_english,
    parameter_chinese,
    parameter_value,
    platform,
    editable,
    created_at,
    updated_at
)
select
    'param_uitest_base_url_all',
    'UITEST_BASE_URL',
    parameter_chinese,
    parameter_value,
    platform,
    editable,
    created_at,
    current_timestamp
from common_parameters
where parameter_id = 'param_uitest6_base_url_all'
  and not exists (
      select 1 from common_parameters where parameter_id = 'param_uitest_base_url_all'
  );

update common_parameter_change_logs
set parameter_id = 'param_uitest_base_url_all'
where parameter_id = 'param_uitest6_base_url_all'
  and exists (
      select 1 from common_parameters where parameter_id = 'param_uitest_base_url_all'
  );

delete from common_parameters
where parameter_id = 'param_uitest6_base_url_all'
  and exists (
      select 1 from common_parameters where parameter_id = 'param_uitest_base_url_all'
  );

update common_parameters
set parameter_english = 'UITEST_BASE_URL',
    updated_at = current_timestamp
where parameter_id = 'param_uitest_base_url_all';
