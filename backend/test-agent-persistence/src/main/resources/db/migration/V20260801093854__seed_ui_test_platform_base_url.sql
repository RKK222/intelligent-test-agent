-- 独立 UI 自动化平台地址；超级管理员在线维护，UI Tool 每次执行前从平台实时读取。
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
values (
    'param_uitest6_base_url_all',
    'UITEST6_BASE_URL',
    'UI测试执行平台地址',
    'UNCONFIGURED',
    'all',
    true,
    current_timestamp,
    current_timestamp
);
