alter table scheduled_tasks alter column cron_expression drop not null;

alter table scheduled_task_runs add column execution_affinity varchar(128);

create index idx_scheduled_task_runs_user_plan_due
    on scheduled_task_runs(trigger_type, status, execution_affinity, scheduled_fire_at);

comment on column scheduled_tasks.cron_expression is 'Cron表达式；USER_PLAN专用代码任务可为空';
comment on column scheduled_task_runs.execution_affinity is '用户计划执行亲和标识，当前保存目标Linux服务器ID';
