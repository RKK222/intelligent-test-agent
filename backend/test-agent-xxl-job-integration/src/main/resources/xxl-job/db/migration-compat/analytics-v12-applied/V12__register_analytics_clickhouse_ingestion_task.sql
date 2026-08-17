-- ClickHouse 运营事实每分钟入库，开关由 Java 侧 test-agent.analytics.clickhouse.enabled 控制。
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
    'BEAN', '', '平台 SQL 初始化', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend';
