-- 使用只读 MySQL 账号执行；交互输入请使用 mysql --password，或由受管客户端安全配置凭据，避免把凭据写入命令历史。

SELECT VERSION() AS mysql_version, DATABASE() AS current_database, @@global.time_zone AS global_time_zone, @@session.time_zone AS session_time_zone;

SELECT installed_rank, version, description, type, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT COUNT(*) AS jit_user_count,
       SUM(platform_user_id IS NULL) AS missing_platform_user_id_count,
       SUM(CHAR_LENGTH(platform_session_digest) = 64) AS active_digest_shape_count,
       SUM(platform_session_expires_at > NOW(3)) AS unexpired_session_count
FROM xxl_job_user;

SELECT id, platform_user_id, username, role,
       CHAR_LENGTH(platform_session_digest) AS session_digest_length,
       platform_session_expires_at
FROM xxl_job_user
ORDER BY id;

SELECT id, app_name, title, address_type,
       CASE WHEN address_list IS NULL OR address_list = '' THEN 0
            ELSE 1 + CHAR_LENGTH(address_list) - CHAR_LENGTH(REPLACE(address_list, ',', '')) END AS configured_address_count,
       update_time
FROM xxl_job_group
WHERE app_name = 'test-agent-backend';

SELECT platform_task_key, job_desc, schedule_type, schedule_conf, trigger_status,
       misfire_strategy, executor_route_strategy, executor_handler,
       executor_block_strategy, executor_timeout, executor_fail_retry_count,
       update_time
FROM xxl_job_info
WHERE platform_task_key IS NOT NULL
ORDER BY platform_task_key;

SELECT registry_group, registry_key, registry_value, update_time,
       TIMESTAMPDIFF(SECOND, update_time, NOW()) AS age_seconds
FROM xxl_job_registry
WHERE registry_group = 'EXECUTOR' AND registry_key = 'test-agent-backend'
ORDER BY registry_value;

SELECT DATE(trigger_time) AS trigger_day,
       COUNT(*) AS total_count,
       SUM(trigger_code = 200) AS trigger_success_count,
       SUM(handle_code = 200) AS handle_success_count,
       SUM(handle_code <> 0 AND handle_code <> 200) AS handle_failure_count,
       SUM(handle_code = 0) AS unfinished_count
FROM xxl_job_log
WHERE trigger_time >= NOW() - INTERVAL 7 DAY
GROUP BY DATE(trigger_time)
ORDER BY trigger_day DESC;

SHOW INDEX FROM xxl_job_user;
SHOW INDEX FROM xxl_job_info;
