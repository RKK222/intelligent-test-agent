-- 全局任务通过 Redis 广播唤醒全部 Java；每台 Java 只认领源目录属于本服务器的错配工作区。
INSERT INTO `xxl_job_info` (
    `platform_task_key`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`,
    `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`,
    `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT
    'workspace-management.personal-workspace-relocation', `id`, '个人工作区跨服务器自动搬迁',
    NOW(), NOW(), 'platform', '',
    'CRON', '0 0/1 * * * ? *', 'DO_NOTHING', 'ROUND', 'testAgentScheduledTaskHandler',
    '{"taskKey":"workspace-management.personal-workspace-relocation","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}',
    'DISCARD_LATER', 0, 0,
    'BEAN', '', '平台 SQL 初始化', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend';
