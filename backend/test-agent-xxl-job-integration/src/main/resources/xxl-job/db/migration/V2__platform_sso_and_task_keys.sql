-- 平台扩展字段与上游字段分离，方便后续整体替换上游源码并重放小范围适配。
ALTER TABLE `xxl_job_user`
    MODIFY COLUMN `username` varchar(128) NOT NULL COMMENT '平台显示账号',
    ADD COLUMN `platform_user_id` varchar(128) DEFAULT NULL COMMENT '平台稳定用户ID' AFTER `id`,
    ADD COLUMN `platform_session_digest` char(64) DEFAULT NULL COMMENT '平台会话SHA-256摘要' AFTER `token`,
    ADD COLUMN `platform_session_expires_at` datetime(3) DEFAULT NULL COMMENT '平台会话过期时间' AFTER `platform_session_digest`,
    ADD UNIQUE KEY `uk_xxl_job_user_platform_user_id` (`platform_user_id`);

ALTER TABLE `xxl_job_info`
    ADD COLUMN `platform_task_key` varchar(128) DEFAULT NULL COMMENT '平台稳定任务key' AFTER `id`,
    ADD UNIQUE KEY `uk_xxl_job_info_platform_task_key` (`platform_task_key`);
