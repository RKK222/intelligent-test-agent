-- 将 OPENCODE 路径类通用参数收敛为 all 平台单行，值统一引用 SYS_DATA_ROOT_DIR。
-- 依赖 CommonParameterReferenceResolver 支持 all 参数按解析上下文平台引用平台参数
-- （SYS_DATA_ROOT_DIR 仅有 linux/windows/macos 行，无 all 行）。
-- 注意：Flyway 默认把“美元符加大括号”的占位符语法当作变量替换，会因找不到值而解析失败；
-- 因此这里用 '$' || '{SYS_DATA_ROOT_DIR}/...' 拼接，使 SQL 文本中不出现该占位符序列，
-- 而 DB 实际存储美元符加大括号包裹的 SYS_DATA_ROOT_DIR 字面量，由通用参数解析器在运行态按当前平台展开。
-- 先删除既有 linux/windows/macos 平台行，再写入 all 行。

DELETE FROM common_parameters
WHERE parameter_english IN (
    'OPENCODE_APP_WORKSPACE_ROOT',
    'OPENCODE_PERSONAL_WORKTREE_ROOT',
    'OPENCODE_PUBLIC_CONFIG_DIR',
    'OPENCODE_PUBLIC_CONFIG_GIT_ROOT',
    'OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT',
    'OPENCODE_SESSION_DIR'
);

INSERT INTO common_parameters (parameter_id, parameter_english, parameter_chinese, parameter_value, platform, created_at, updated_at)
VALUES
    ('param_opencode_app_workspace_root_all',         'OPENCODE_APP_WORKSPACE_ROOT',         'opencode应用工作区根目录',       '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appworkspace/',     'all', current_timestamp, current_timestamp),
    ('param_opencode_personal_worktree_root_all',     'OPENCODE_PERSONAL_WORKTREE_ROOT',    'opencode应用个人区根目录',       '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/workspace/personalworktree/', 'all', current_timestamp, current_timestamp),
    ('param_opencode_public_config_dir_all',          'OPENCODE_PUBLIC_CONFIG_DIR',         'opencode公共配置目录',           '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/.config/opencode/',           'all', current_timestamp, current_timestamp),
    ('param_opencode_public_config_git_root_all',     'OPENCODE_PUBLIC_CONFIG_GIT_ROOT',    'opencode公共配置Git下载目录',    '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/.config/',                    'all', current_timestamp, current_timestamp),
    ('param_opencode_public_config_worktree_root_all','OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT','opencode公共配置Git worktree目录','$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/.configdev/',               'all', current_timestamp, current_timestamp),
    ('param_opencode_session_dir_all',                'OPENCODE_SESSION_DIR',               'opencodesession目录',            '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/.session/',                   'all', current_timestamp, current_timestamp)
ON CONFLICT (parameter_english, platform) DO UPDATE SET
    parameter_value = EXCLUDED.parameter_value,
    parameter_chinese = EXCLUDED.parameter_chinese,
    updated_at = current_timestamp;
