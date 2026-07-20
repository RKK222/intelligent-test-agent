-- 首批周期任务与后续新增任务使用同一模式：每次通过新的版本 SQL 新增，不在应用启动时覆盖页面调整。
INSERT INTO `xxl_job_group` (`app_name`, `title`, `address_type`, `address_list`, `update_time`)
VALUES ('test-agent-backend', 'Test Agent Java 执行器', 0, NULL, NOW());

INSERT INTO `xxl_job_info` (
    `platform_task_key`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`,
    `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`,
    `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT
    task_key, group_id, job_desc, NOW(), NOW(), 'platform', '',
    'CRON', cron_expression, 'DO_NOTHING', 'ROUND', 'testAgentScheduledTaskHandler',
    executor_param, 'DISCARD_LATER', 0, 0,
    'BEAN', '', '平台 SQL 初始化', NOW(), '',
    1, 0, 0
FROM (
    SELECT 'opencode-runtime.analytics-rollup' AS task_key,
           '运营分析汇总' AS job_desc,
           '0 0/5 * * * ? *' AS cron_expression,
           '{"taskKey":"opencode-runtime.analytics-rollup","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}' AS executor_param
    UNION ALL
    SELECT 'opencode-runtime.night-execution-reconcile', '夜间执行补偿', '0 0/5 * * * ? *',
           '{"taskKey":"opencode-runtime.night-execution-reconcile","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}'
    UNION ALL
    SELECT 'opencode-runtime.stale-active-run-reconcile', '陈旧活动运行收敛', '0 0/5 * * * ? *',
           '{"taskKey":"opencode-runtime.stale-active-run-reconcile","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}'
    UNION ALL
    SELECT 'opencode-runtime.terminal-projection-retry', '终态投影重试', '0/5 * * * * ? *',
           '{"taskKey":"opencode-runtime.terminal-projection-retry","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}'
    UNION ALL
    SELECT 'opencode-runtime.side-question-orphan-cleanup', '旁路问答孤儿清理', '0 0/5 * * * ? *',
           '{"taskKey":"opencode-runtime.side-question-orphan-cleanup","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}'
    UNION ALL
    SELECT 'scheduler.run-retention-cleanup', '调度运行记录清理', '0 0 8 * * ? *',
           '{"taskKey":"scheduler.run-retention-cleanup","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}'
) task_seed
CROSS JOIN (
    SELECT `id` AS group_id FROM `xxl_job_group` WHERE `app_name` = 'test-agent-backend'
) executor_group;
