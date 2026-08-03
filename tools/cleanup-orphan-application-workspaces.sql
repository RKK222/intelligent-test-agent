-- 应用工作空间孤立模板审计与受控清理脚本（PostgreSQL / psql）
--
-- 孤立模板定义：application_workspaces 存在，但没有任何 application_workspace_versions。
-- 默认仅审计，不修改数据。禁止用 SQL 补造版本，因为版本还依赖真实 Git 目录、运行态
-- workspaces、服务器副本和目标 commit；清理后应从设置页按正确分支/目录重新创建。
--
-- 审计：
--   psql ... -v ON_ERROR_STOP=1 \
--     -f tools/cleanup-orphan-application-workspaces.sql
--
-- 执行清理（先停止全部后端并备份数据库，再把 confirm_candidate_count 替换为审计数量）：
--   psql ... -v ON_ERROR_STOP=1 \
--     -v apply_cleanup=true \
--     -v confirm_backend_stopped=true \
--     -v confirm_candidate_count=2 \
--     -v older_than_hours=24 \
--     -f tools/cleanup-orphan-application-workspaces.sql

\set ON_ERROR_STOP on

\if :{?apply_cleanup}
\else
    \set apply_cleanup false
\endif

\if :{?confirm_backend_stopped}
\else
    \set confirm_backend_stopped false
\endif

\if :{?older_than_hours}
\else
    \set older_than_hours 24
\endif

\if :{?running_grace_minutes}
\else
    \set running_grace_minutes 60
\endif

SELECT :'older_than_hours' ~ '^[0-9]+$'
       AND :'older_than_hours'::integer > 0 AS older_than_hours_valid \gset
\if :older_than_hours_valid
\else
    \echo 'older_than_hours 必须是正整数'
    SELECT 1 / 0;
\endif

SELECT :'running_grace_minutes' ~ '^[0-9]+$'
       AND :'running_grace_minutes'::integer > 0 AS running_grace_minutes_valid \gset
\if :running_grace_minutes_valid
\else
    \echo 'running_grace_minutes 必须是正整数'
    SELECT 1 / 0;
\endif

-- 1. 全库审计：blocked_reason 为空且 cleanup_candidate=true 才允许进入删除候选。
WITH orphan_audit AS (
    SELECT
        aw.workspace_id,
        aw.app_id,
        a.app_name,
        aw.workspace_name,
        aw.repository_id,
        aw.branch,
        aw.directory_path,
        aw.enabled,
        aw.created_at,
        (SELECT COUNT(*)
         FROM personal_workspaces pw
         WHERE pw.application_workspace_id = aw.workspace_id) AS personal_workspace_count,
        (SELECT COUNT(*)
         FROM agent_skill_hub_assets ha
         WHERE ha.source_application_workspace_id = aw.workspace_id) AS hub_asset_count,
        (SELECT COUNT(*)
         FROM agent_skill_hub_references hr
         WHERE hr.target_application_workspace_id = aw.workspace_id) AS hub_reference_count,
        EXISTS (
            SELECT 1
            FROM workspace_create_operations op
            WHERE op.app_id = aw.app_id
              AND op.status = 'RUNNING'
              AND op.updated_at >= current_timestamp
                    - make_interval(mins => :'running_grace_minutes'::integer)
        ) AS app_has_recent_running_operation
    FROM application_workspaces aw
    JOIN applications a ON a.app_id = aw.app_id
    WHERE NOT EXISTS (
        SELECT 1
        FROM application_workspace_versions v
        WHERE v.application_workspace_id = aw.workspace_id
    )
), classified AS (
    SELECT
        audit.*,
        audit.created_at < current_timestamp
                - make_interval(hours => :'older_than_hours'::integer)
            AND audit.personal_workspace_count = 0
            AND audit.hub_asset_count = 0
            AND audit.hub_reference_count = 0
            AND NOT audit.app_has_recent_running_operation AS cleanup_candidate,
        concat_ws(', ',
            CASE WHEN audit.created_at >= current_timestamp
                    - make_interval(hours => :'older_than_hours'::integer)
                 THEN 'TOO_RECENT' END,
            CASE WHEN audit.personal_workspace_count > 0 THEN 'HAS_PERSONAL_WORKSPACE' END,
            CASE WHEN audit.hub_asset_count > 0 THEN 'HAS_HUB_ASSET' END,
            CASE WHEN audit.hub_reference_count > 0 THEN 'HAS_HUB_REFERENCE' END,
            CASE WHEN audit.app_has_recent_running_operation THEN 'APP_HAS_RECENT_RUNNING_OPERATION' END
        ) AS blocked_reason
    FROM orphan_audit audit
)
SELECT
    workspace_id,
    app_id,
    app_name,
    workspace_name,
    repository_id,
    branch,
    directory_path,
    enabled,
    created_at,
    personal_workspace_count,
    hub_asset_count,
    hub_reference_count,
    app_has_recent_running_operation,
    cleanup_candidate,
    blocked_reason
FROM classified
ORDER BY app_name, app_id, created_at, workspace_id;

WITH candidates AS (
    SELECT aw.workspace_id, aw.app_id
    FROM application_workspaces aw
    WHERE aw.created_at < current_timestamp
            - make_interval(hours => :'older_than_hours'::integer)
      AND NOT EXISTS (
          SELECT 1 FROM application_workspace_versions v
          WHERE v.application_workspace_id = aw.workspace_id
      )
      AND NOT EXISTS (
          SELECT 1 FROM personal_workspaces pw
          WHERE pw.application_workspace_id = aw.workspace_id
      )
      AND NOT EXISTS (
          SELECT 1 FROM agent_skill_hub_assets ha
          WHERE ha.source_application_workspace_id = aw.workspace_id
      )
      AND NOT EXISTS (
          SELECT 1 FROM agent_skill_hub_references hr
          WHERE hr.target_application_workspace_id = aw.workspace_id
      )
      AND NOT EXISTS (
          SELECT 1
          FROM workspace_create_operations op
          WHERE op.app_id = aw.app_id
            AND op.status = 'RUNNING'
            AND op.updated_at >= current_timestamp
                  - make_interval(mins => :'running_grace_minutes'::integer)
      )
)
SELECT COUNT(*) AS cleanup_candidate_count
FROM candidates;

-- 2. 默认到此结束；显式执行时还需要停机确认和候选数量二次确认。
\if :apply_cleanup
    \if :confirm_backend_stopped
    \else
        \echo 'apply_cleanup=true 时必须先停止全部后端，再传入 confirm_backend_stopped=true'
        SELECT 1 / 0;
    \endif

    \if :{?confirm_candidate_count}
    \else
        \echo 'apply_cleanup=true 时必须传入审计确认的 confirm_candidate_count'
        SELECT 1 / 0;
    \endif

    SELECT :'confirm_candidate_count' ~ '^[0-9]+$' AS confirm_candidate_count_valid \gset
    \if :confirm_candidate_count_valid
    \else
        \echo 'confirm_candidate_count 必须是非负整数'
        SELECT 1 / 0;
    \endif

    BEGIN;
    LOCK TABLE application_workspaces IN SHARE ROW EXCLUSIVE MODE;

    SELECT NOT EXISTS (
        SELECT 1
        FROM workspace_create_operations op
        WHERE op.status = 'RUNNING'
          AND op.updated_at >= current_timestamp
                - make_interval(mins => :'running_grace_minutes'::integer)
    ) AS no_recent_running_operations \gset
    \if :no_recent_running_operations
    \else
        \echo '仍存在近期 RUNNING 工作空间创建任务，终止清理'
        SELECT 1 / 0;
    \endif

    CREATE TEMP TABLE orphan_application_workspace_cleanup_candidates
    ON COMMIT DROP
    AS
    SELECT
        aw.workspace_id,
        aw.app_id,
        aw.workspace_name,
        aw.repository_id,
        aw.branch,
        aw.directory_path,
        aw.created_at
    FROM application_workspaces aw
    WHERE aw.created_at < current_timestamp
            - make_interval(hours => :'older_than_hours'::integer)
      AND NOT EXISTS (
          SELECT 1 FROM application_workspace_versions v
          WHERE v.application_workspace_id = aw.workspace_id
      )
      AND NOT EXISTS (
          SELECT 1 FROM personal_workspaces pw
          WHERE pw.application_workspace_id = aw.workspace_id
      )
      AND NOT EXISTS (
          SELECT 1 FROM agent_skill_hub_assets ha
          WHERE ha.source_application_workspace_id = aw.workspace_id
      )
      AND NOT EXISTS (
          SELECT 1 FROM agent_skill_hub_references hr
          WHERE hr.target_application_workspace_id = aw.workspace_id
      );

    SELECT COUNT(*) = :'confirm_candidate_count'::bigint AS candidate_count_matches
    FROM orphan_application_workspace_cleanup_candidates \gset
    \if :candidate_count_matches
    \else
        \echo '候选数量与 confirm_candidate_count 不一致，数据库状态已变化，终止清理'
        SELECT 1 / 0;
    \endif

    TABLE orphan_application_workspace_cleanup_candidates;

    WITH deleted AS (
        DELETE FROM application_workspaces aw
        USING orphan_application_workspace_cleanup_candidates candidate
        WHERE aw.workspace_id = candidate.workspace_id
          AND NOT EXISTS (
              SELECT 1 FROM application_workspace_versions v
              WHERE v.application_workspace_id = aw.workspace_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM personal_workspaces pw
              WHERE pw.application_workspace_id = aw.workspace_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM agent_skill_hub_assets ha
              WHERE ha.source_application_workspace_id = aw.workspace_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM agent_skill_hub_references hr
              WHERE hr.target_application_workspace_id = aw.workspace_id
          )
        RETURNING aw.workspace_id, aw.app_id, aw.workspace_name,
                  aw.repository_id, aw.branch, aw.directory_path
    )
    SELECT * FROM deleted ORDER BY app_id, workspace_id;

    COMMIT;
    SELECT '孤立应用工作空间模板清理完成；失败 operation 审计记录、物理 Git 目录和运行态 Workspace 未删除。' AS result;
\else
    SELECT '审计完成：未执行 DELETE。确认候选后，在维护窗口按脚本头部命令显式开启清理。' AS result;
\endif
