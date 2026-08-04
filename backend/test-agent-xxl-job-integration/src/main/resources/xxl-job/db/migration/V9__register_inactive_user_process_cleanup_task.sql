-- 每日全局任务广播唤醒全部 Java；每个实例只关闭自己实际持有 manager 连接的闲置用户进程。
INSERT INTO `xxl_job_info` (
    `platform_task_key`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`,
    `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`,
    `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT
    'opencode-runtime.inactive-user-process-cleanup', `id`, '十五天未使用用户 OpenCode 进程关闭',
    NOW(), NOW(), 'platform', '',
    'CRON', '0 0 2 * * ? *', 'DO_NOTHING', 'ROUND', 'testAgentScheduledTaskHandler',
    '{"taskKey":"opencode-runtime.inactive-user-process-cleanup","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}',
    'DISCARD_LATER', 0, 0,
    'BEAN', '', '平台 SQL 初始化', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend';
