-- V9 已在存量 XXL MySQL 中执行且不可改写；只更新平台初始化的旧任务名称，保留管理员自定义名称。
UPDATE `xxl_job_info`
SET `job_desc` = '十天未使用用户 OpenCode 进程关闭',
    `update_time` = NOW()
WHERE `platform_task_key` = 'opencode-runtime.inactive-user-process-cleanup'
  AND `job_desc` = '十五天未使用用户 OpenCode 进程关闭';
