-- V12 在 dev/release 已形成两套持久化历史；高版本前向迁移只补齐当前历史缺少的任务。
INSERT INTO `xxl_job_info` (
    `platform_task_key`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`,
    `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`,
    `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT
    'opencode-runtime.analytics-ingestion', `id`, 'ClickHouse 运营事实入库',
    NOW(), NOW(), 'platform', '',
    'CRON', '0 * * * * ? *', 'DO_NOTHING', 'ROUND', 'testAgentScheduledTaskHandler',
    '{"taskKey":"opencode-runtime.analytics-ingestion","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}',
    'DISCARD_LATER', 0, 0,
    'BEAN', '', '平台 SQL 前向补偿', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend'
  AND NOT EXISTS (
      SELECT 1
      FROM `xxl_job_info`
      WHERE `platform_task_key` = 'opencode-runtime.analytics-ingestion'
  );

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
    'BEAN', '', '平台 SQL 前向补偿', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend'
  AND NOT EXISTS (
      SELECT 1
      FROM `xxl_job_info`
      WHERE `platform_task_key` = 'configuration-management.scm-git-name-sync'
  );
