-- RTK 命令输出压缩默认关闭；仅超级管理员可通过通用参数管理页面开启。
-- 当前版本只保存全局开关，服务端与具备 MANAGED_RTK_CONFIG_V1 能力的本地客户端共同读取。
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
    'param_rtk_command_rewrite_enabled_all',
    'RTK_COMMAND_REWRITE_ENABLED',
    'RTK命令改写开关',
    'false',
    'all',
    true,
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from common_parameters
    where parameter_english = 'RTK_COMMAND_REWRITE_ENABLED'
      and platform = 'all'
);
