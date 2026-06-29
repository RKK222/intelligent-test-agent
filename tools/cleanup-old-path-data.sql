-- 本地旧路径数据审计脚本
--
-- 默认只执行 SELECT，不自动删除业务数据。原因是 workspaces 可能仍被 Session、Run、
-- 用户偏好和同步记录引用，直接按路径 DELETE 会触发外键错误或丢失历史会话。
-- 使用方式：
--   psql ... -v ON_ERROR_STOP=1 -f tools/cleanup-old-path-data.sql
--   psql ... -v ON_ERROR_STOP=1 -v apply_cleanup=true \
--     -v test_agent_root=/absolute/project/root -f tools/cleanup-old-path-data.sql
--
-- apply_cleanup 默认为 false。执行迁移前必须停止本地后端和 opencode-manager，并备份数据库。

\set ON_ERROR_STOP on
\if :{?apply_cleanup}
\else
    \set apply_cleanup false
\endif

-- ============================================
-- 1. 通用参数与重复行检查
-- ============================================

SELECT parameter_id, parameter_english, parameter_value, platform, updated_at
FROM common_parameters
WHERE parameter_value LIKE '%/tmp/test-agent%'
   OR parameter_value LIKE '%$HOME/tmp%'
   OR parameter_value LIKE '%$HOME/test-agent%'
ORDER BY parameter_english, platform;

SELECT parameter_english, platform, COUNT(*) AS duplicate_count
FROM common_parameters
GROUP BY parameter_english, platform
HAVING COUNT(*) > 1;

-- macOS 目标值应通过“系统管理 → 通用参数管理”更新，以便保留修改历史并触发缓存/manager 刷新：
-- OPENCODE_SESSION_DIR=$TEST_AGENT_ROOT/temp/opencode-session
-- OPENCODE_APP_WORKSPACE_ROOT=$TEST_AGENT_ROOT/temp/workspace/appworkspace
-- OPENCODE_PERSONAL_WORKTREE_ROOT=$TEST_AGENT_ROOT/temp/workspace/personalworktree
-- OPENCODE_PUBLIC_CONFIG_GIT_ROOT=$TEST_AGENT_ROOT/temp/opencode-config
-- OPENCODE_PUBLIC_CONFIG_DIR=$TEST_AGENT_ROOT/temp/opencode-config/opencode
-- OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT=$TEST_AGENT_ROOT/temp/opencode-configdev
-- OPENCODE_PUBLIC_AGENT_GIT_URL=git@gitee.com:huangzhenren/opencodeconfig.git

-- ============================================
-- 2. 旧运行进程与 Agent worktree
-- ============================================

SELECT process_id, user_id, linux_server_id, port, session_path, config_path, status
FROM opencode_server_processes
WHERE session_path LIKE '/tmp/test-agent/%'
   OR config_path LIKE '/tmp/test-agent/%'
   OR session_path LIKE '%/tmp/test-agent/%'
   OR config_path LIKE '%/tmp/test-agent/%'
ORDER BY process_id;

SELECT worktree_id, scope, workspace_id, worktree_name, root_path, linux_server_id, status
FROM agent_config_worktrees
WHERE root_path LIKE '/tmp/test-agent/%'
   OR root_path LIKE '%/tmp/test-agent/%'
   OR root_path LIKE '%/test-agent/opencode-configdev%'
ORDER BY worktree_id;

-- ============================================
-- 3. 旧物理工作区及引用计数
-- ============================================

WITH old_workspaces AS (
    SELECT workspace_id, name, root_path, status
    FROM workspaces
    WHERE root_path LIKE '/tmp/test-agent/%'
       OR root_path LIKE '%/tmp/test-agent/%'
)
SELECT
    w.workspace_id,
    w.name,
    w.root_path,
    w.status,
    (SELECT COUNT(*) FROM sessions s WHERE s.workspace_id = w.workspace_id) AS session_count,
    (SELECT COUNT(*) FROM runs r WHERE r.workspace_id = w.workspace_id) AS run_count,
    (SELECT COUNT(*) FROM personal_workspaces p WHERE p.runtime_workspace_id = w.workspace_id) AS personal_workspace_count,
    (SELECT COUNT(*) FROM application_workspace_versions v WHERE v.runtime_workspace_id = w.workspace_id) AS version_count,
    (SELECT COUNT(*) FROM application_workspace_version_replicas r WHERE r.runtime_workspace_id = w.workspace_id) AS replica_count
FROM old_workspaces w
ORDER BY w.workspace_id;

SELECT version_id, version, runtime_workspace_id, repo_root_path, workspace_root_path, status
FROM application_workspace_versions
WHERE repo_root_path LIKE '/tmp/test-agent/%'
   OR workspace_root_path LIKE '/tmp/test-agent/%'
   OR repo_root_path LIKE '%/tmp/test-agent/%'
   OR workspace_root_path LIKE '%/tmp/test-agent/%'
ORDER BY version_id;

SELECT replica_id, version_id, runtime_workspace_id, linux_server_id,
       repo_root_path, workspace_root_path, sync_status
FROM application_workspace_version_replicas
WHERE repo_root_path LIKE '/tmp/test-agent/%'
   OR workspace_root_path LIKE '/tmp/test-agent/%'
   OR repo_root_path LIKE '%/tmp/test-agent/%'
   OR workspace_root_path LIKE '%/tmp/test-agent/%'
ORDER BY replica_id;

SELECT personal_workspace_id, user_id, runtime_workspace_id,
       repo_root_path, workspace_root_path, status
FROM personal_workspaces
WHERE repo_root_path LIKE '/tmp/test-agent/%'
   OR workspace_root_path LIKE '/tmp/test-agent/%'
   OR repo_root_path LIKE '%/tmp/test-agent/%'
   OR workspace_root_path LIKE '%/tmp/test-agent/%'
ORDER BY personal_workspace_id;

-- ============================================
-- 4. 可选：把仍需保留的本地工作区迁移到项目 temp
-- ============================================

\if :apply_cleanup
    \if :{?test_agent_root}
    \else
        \echo 'apply_cleanup=true 时必须通过 -v test_agent_root=/absolute/project/root 指定项目根目录'
        SELECT 1 / 0;
    \endif
    SELECT :'test_agent_root' ~ '^/' AS test_agent_root_is_absolute \gset
    \if :test_agent_root_is_absolute
    \else
        \echo 'test_agent_root 必须是绝对路径'
        SELECT 1 / 0;
    \endif

    BEGIN;

    -- 仅迁移路径字段，不删除 Session、Run、版本和工作区历史记录。
    UPDATE workspaces
    SET root_path = replace(replace(root_path,
                                    '/private/tmp/test-agent',
                                    :'test_agent_root' || '/temp'),
                            '/tmp/test-agent',
                            :'test_agent_root' || '/temp'),
        updated_at = NOW()
    WHERE root_path LIKE '%/tmp/test-agent/%';

    UPDATE application_workspace_versions
    SET repo_root_path = replace(replace(repo_root_path,
                                         '/private/tmp/test-agent',
                                         :'test_agent_root' || '/temp'),
                                 '/tmp/test-agent',
                                 :'test_agent_root' || '/temp'),
        workspace_root_path = replace(replace(workspace_root_path,
                                              '/private/tmp/test-agent',
                                              :'test_agent_root' || '/temp'),
                                      '/tmp/test-agent',
                                      :'test_agent_root' || '/temp'),
        updated_at = NOW()
    WHERE repo_root_path LIKE '%/tmp/test-agent/%'
       OR workspace_root_path LIKE '%/tmp/test-agent/%';

    UPDATE application_workspace_version_replicas
    SET repo_root_path = replace(replace(repo_root_path,
                                         '/private/tmp/test-agent',
                                         :'test_agent_root' || '/temp'),
                                 '/tmp/test-agent',
                                 :'test_agent_root' || '/temp'),
        workspace_root_path = replace(replace(workspace_root_path,
                                              '/private/tmp/test-agent',
                                              :'test_agent_root' || '/temp'),
                                      '/tmp/test-agent',
                                      :'test_agent_root' || '/temp'),
        updated_at = NOW()
    WHERE repo_root_path LIKE '%/tmp/test-agent/%'
       OR workspace_root_path LIKE '%/tmp/test-agent/%';

    UPDATE personal_workspaces
    SET repo_root_path = replace(replace(repo_root_path,
                                         '/private/tmp/test-agent',
                                         :'test_agent_root' || '/temp'),
                                 '/tmp/test-agent',
                                 :'test_agent_root' || '/temp'),
        workspace_root_path = replace(replace(workspace_root_path,
                                              '/private/tmp/test-agent',
                                              :'test_agent_root' || '/temp'),
                                      '/tmp/test-agent',
                                      :'test_agent_root' || '/temp'),
        updated_at = NOW()
    WHERE repo_root_path LIKE '%/tmp/test-agent/%'
       OR workspace_root_path LIKE '%/tmp/test-agent/%';

    UPDATE application_workspaces
    SET directory_path = replace(replace(directory_path,
                                         '/private/tmp/test-agent',
                                         :'test_agent_root' || '/temp'),
                                 '/tmp/test-agent',
                                 :'test_agent_root' || '/temp'),
        updated_at = NOW()
    WHERE directory_path LIKE '%/tmp/test-agent/%';

    UPDATE agent_config_worktrees
    SET root_path = replace(replace(root_path,
                                    '/private/tmp/test-agent',
                                    :'test_agent_root' || '/temp'),
                            '/tmp/test-agent',
                            :'test_agent_root' || '/temp'),
        updated_at = NOW()
    WHERE root_path LIKE '%/tmp/test-agent/%';

    -- RUNNING 进程不能改写为新目录；停止服务后遗留的非运行记录才允许迁移。
    UPDATE opencode_server_processes
    SET session_path = replace(replace(session_path,
                                       '/private/tmp/test-agent',
                                       :'test_agent_root' || '/temp'),
                               '/tmp/test-agent',
                               :'test_agent_root' || '/temp'),
        config_path = replace(replace(config_path,
                                      '/private/tmp/test-agent',
                                      :'test_agent_root' || '/temp'),
                              '/tmp/test-agent',
                              :'test_agent_root' || '/temp'),
        updated_at = NOW()
    WHERE status <> 'RUNNING'
      AND (session_path LIKE '%/tmp/test-agent/%'
       OR config_path LIKE '%/tmp/test-agent/%');

    COMMIT;
    SELECT '旧工作区路径已迁移到 ' || :'test_agent_root' || '/temp；通用参数仍需通过管理页面更新。' AS result;
\else
    SELECT '审计完成：未执行 UPDATE/DELETE。传入 apply_cleanup=true 和 test_agent_root 可迁移工作区路径。' AS result;
\endif

-- 1) 通过 manager stop/restart 停止旧 opencode 进程。
-- 2) 对确认废弃的 process_id，先删除 user_opencode_process_bindings，再删除 opencode_server_processes。
-- 3) 对确认废弃的 Agent worktree，先删除磁盘 worktree，再删除 agent_config_worktrees。
-- 4) 工作区存在 session_count/run_count 时不要直接删除；先决定保留历史还是整体清除。
-- 5) 只清理可重建且无历史保留要求的 personal workspace、replica、version 和 runtime workspace。
-- 6) 保留 common_parameter_change_logs、agent_config_operations 等审计记录。
-- 7) 数据库不再引用旧目录后，才删除 /tmp/test-agent、$HOME/tmp/test-agent
--    和 $HOME/test-agent/opencode-configdev。
