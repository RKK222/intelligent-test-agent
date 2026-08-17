-- 每日单实例串行扫描本机应用仓库的远端跟踪历史，补偿已配置 SSH Key 用户的 SCM Git 姓名。
INSERT INTO `xxl_job_info` (
    `platform_task_key`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`,
    `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`,
    `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT
    'configuration-management.scm-git-name-sync', `id`, 'SCM Git 姓名补偿校验',
    NOW(), NOW(), 'platform', '',
    'CRON', '0 10 4 * * ? *', 'DO_NOTHING', 'ROUND', 'testAgentScheduledTaskHandler',
    '{"taskKey":"configuration-management.scm-git-name-sync","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}',
    'DISCARD_LATER', 0, 0,
    'BEAN', '', '平台 SQL 初始化', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend';
