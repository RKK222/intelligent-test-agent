-- 每两小时统一触发一次：执行节点检查服务器工作空间，并广播给各 Java 节点检查其绑定的本地客户端工作空间。
INSERT INTO `xxl_job_info` (
    `platform_task_key`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`,
    `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`,
    `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT
    'workspace-management.git-access-inspection', `id`, '工作空间 Git 权限巡检',
    NOW(), NOW(), 'platform', '',
    'CRON', '0 0 0/2 * * ? *', 'DO_NOTHING', 'ROUND', 'testAgentScheduledTaskHandler',
    '{"taskKey":"workspace-management.git-access-inspection","concurrencyPolicy":"GLOBAL_MUTEX","payload":{}}',
    'DISCARD_LATER', 0, 0,
    'BEAN', '', '平台初始化', NOW(), '',
    1, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'test-agent-backend'
  AND NOT EXISTS (
      SELECT 1
      FROM `xxl_job_info`
      WHERE `platform_task_key` = 'workspace-management.git-access-inspection'
  );
