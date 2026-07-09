-- 回滚短暂引入的内部公共 Git 参数；公共 Agent Git 统一由 OPENCODE_PUBLIC_AGENT_GIT_URL 按部署模式解释。
delete from common_parameter_change_logs
where parameter_id in (
    select parameter_id
    from common_parameters
    where parameter_english = 'OPENCODE_PUBLIC_AGENT_GIT_URL_INTERNAL'
);

delete from common_parameters
where parameter_english = 'OPENCODE_PUBLIC_AGENT_GIT_URL_INTERNAL';

update common_parameters
set parameter_chinese = '公共agent配置Git库地址',
    editable = true,
    updated_at = current_timestamp
where parameter_english = 'OPENCODE_PUBLIC_AGENT_GIT_URL'
  and platform = 'all';
