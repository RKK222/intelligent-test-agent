-- 双模式任务共用到期扫描；清零 next time 让 XXL Admin 按新 Cron 立即重算下一触发点。
UPDATE `xxl_job_info`
SET `schedule_conf` = '0 0/1 * * * ? *',
    `trigger_next_time` = 0,
    `update_time` = NOW()
WHERE `platform_task_key` = 'opencode-runtime.night-execution-dispatch';
