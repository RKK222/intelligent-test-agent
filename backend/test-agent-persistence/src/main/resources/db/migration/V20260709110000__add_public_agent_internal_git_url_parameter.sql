-- 公共 Agent Git 地址按部署模式拆分：既有参数继续作为外部部署地址，新增内部部署地址片段。
update common_parameters
set parameter_chinese = '公共agent配置Git库地址（外部部署）',
    editable = true,
    updated_at = current_timestamp
where parameter_english = 'OPENCODE_PUBLIC_AGENT_GIT_URL'
  and platform = 'all';

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
    'param_opencode_public_agent_git_url_internal_all',
    'OPENCODE_PUBLIC_AGENT_GIT_URL_INTERNAL',
    '公共agent配置Git库地址（内部部署）',
    'UNCONFIGURED',
    'all',
    true,
    current_timestamp,
    current_timestamp
where not exists (
    select 1
    from common_parameters
    where parameter_english = 'OPENCODE_PUBLIC_AGENT_GIT_URL_INTERNAL'
      and platform = 'all'
);
