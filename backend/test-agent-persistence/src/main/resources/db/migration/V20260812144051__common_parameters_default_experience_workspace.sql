-- 仅把历史未配置值切换到每台后端节点的本地体验目录；管理员已自定义的路径必须保留。
-- Flyway 会解析美元符紧跟大括号的文本，沿用既有迁移的拼接方式保存字面量。
update common_parameters
set parameter_value = '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/workspace/experience',
    updated_at = current_timestamp
where parameter_english = 'OPENCODE_EXPERIENCE_WORKSPACE_DIR'
  and platform = 'all'
  and upper(trim(parameter_value)) = 'UNCONFIGURED';
