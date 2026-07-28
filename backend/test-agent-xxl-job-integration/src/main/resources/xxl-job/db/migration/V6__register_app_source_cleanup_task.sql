-- 全局调度只负责广播唤醒；各 Java 实例按 linux_server_id 认领自己的数据库清理任务。
INSERT INTO `xxl_job_info` (
    `platform_task_key`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`,
    `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`,
    `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT
    'workspace-management.app-source-cleanup', `id`, '应用源码到期清理', NOW(), NOW(), 'platform', '',
    'CRON', '0 0/1 * * * ? *', 'DO_NOTHING', 'ROUND', 'testAgentScheduledTaskHandler',
    '{"taskKey":"workspace-management.app-source-cleanup","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}',
    'DISCARD_LATER', 0, 0,
    'BEAN', '', '平台 SQL 初始化', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend';
