-- 既有任务保持标准夜间窗口；超级管理员测试定时复用同一任务表和分发链路。
alter table night_execution_tasks
    add column schedule_mode varchar(32) not null default 'NIGHT_WINDOW';

alter table night_execution_tasks
    add constraint ck_night_execution_schedule_mode
    check (schedule_mode in ('NIGHT_WINDOW', 'ADMIN_CUSTOM'));

comment on column night_execution_tasks.schedule_mode is
    '计划模式：NIGHT_WINDOW标准夜间容量时段，ADMIN_CUSTOM超级管理员测试定时';
