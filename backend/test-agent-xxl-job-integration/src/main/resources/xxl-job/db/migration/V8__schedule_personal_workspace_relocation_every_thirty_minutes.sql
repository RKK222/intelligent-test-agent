-- 保留 V7 已登记任务和现有启停/执行策略，只调整默认频率并让 Admin 重算下一触发时间。
UPDATE `xxl_job_info`
SET `schedule_conf` = '0 0/30 * * * ? *',
    `trigger_next_time` = 0,
    `update_time` = NOW()
WHERE `platform_task_key` = 'workspace-management.personal-workspace-relocation';
