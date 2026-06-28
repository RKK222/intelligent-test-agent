-- 添加 macOS 平台支持
-- 1. 修改 common_parameters 表的约束，添加 macos 平台
ALTER TABLE common_parameters DROP CONSTRAINT IF EXISTS ck_common_parameters_platform;
ALTER TABLE common_parameters ADD CONSTRAINT ck_common_parameters_platform
    CHECK (platform::text = ANY (ARRAY['windows'::character varying, 'linux'::character varying, 'macos'::character varying, 'all'::character varying]::text[]));

-- 2. 添加 macOS 平台的通用参数配置
-- 注意：macOS 本地开发路径需要使用绝对路径。
-- 请根据实际项目路径修改 parameter_value。
INSERT INTO common_parameters (parameter_id, parameter_english, parameter_chinese, parameter_value, platform, created_at, updated_at) VALUES
    ('opencode_session_dir_macos', 'OPENCODE_SESSION_DIR', 'OpenCode会话目录', '/tmp/test-agent/opencode-session', 'macos', NOW(), NOW()),
    ('opencode_app_workspace_root_macos', 'OPENCODE_APP_WORKSPACE_ROOT', '应用工作空间根目录', '/tmp/test-agent/workspace/appworkspace', 'macos', NOW(), NOW()),
    ('opencode_personal_worktree_root_macos', 'OPENCODE_PERSONAL_WORKTREE_ROOT', '个人工作树根目录', '/tmp/test-agent/workspace/personalworktree', 'macos', NOW(), NOW()),
    ('opencode_public_config_dir_macos', 'OPENCODE_PUBLIC_CONFIG_DIR', '公共配置目录', '/tmp/test-agent/opencode-config', 'macos', NOW(), NOW()),
    ('opencode_public_config_git_root_macos', 'OPENCODE_PUBLIC_CONFIG_GIT_ROOT', '公共配置Git根目录', '/tmp/test-agent/opencode-config', 'macos', NOW(), NOW()),
    ('opencode_public_config_worktree_root_macos', 'OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT', '公共配置工作树根目录', '/tmp/test-agent/opencode-configdev', 'macos', NOW(), NOW())
ON CONFLICT (parameter_id) DO UPDATE SET
    parameter_value = EXCLUDED.parameter_value,
    updated_at = NOW();
