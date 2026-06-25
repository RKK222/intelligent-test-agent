-- 扩展 scheduler 管理能力：记录管理员协作式停止请求，并为前端展示预置状态字典。

alter table scheduled_task_runs add column stop_requested_at timestamp;
alter table scheduled_task_runs add column stop_requested_by_user_id varchar(128);
alter table scheduled_task_runs add column stop_reason varchar(255);

alter table scheduled_task_runs
    add constraint fk_scheduled_task_runs_stop_user foreign key (stop_requested_by_user_id) references users(user_id);

create index idx_scheduled_task_runs_stop_user on scheduled_task_runs(stop_requested_by_user_id, stop_requested_at);

comment on column scheduled_task_runs.stop_requested_at is '管理员请求停止定时任务的时间，空表示未请求停止。';
comment on column scheduled_task_runs.stop_requested_by_user_id is '请求停止定时任务的超级管理员用户 ID。';
comment on column scheduled_task_runs.stop_reason is '管理员请求停止定时任务的原因摘要。';

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_run_status_pending', '定时任务运行状态', 'SCHEDULER_RUN_STATUS', 'PENDING', '待执行', 1, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_RUN_STATUS' and dict_value = 'PENDING');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_run_status_running', '定时任务运行状态', 'SCHEDULER_RUN_STATUS', 'RUNNING', '运行中', 2, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_RUN_STATUS' and dict_value = 'RUNNING');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_run_status_stopping', '定时任务运行状态', 'SCHEDULER_RUN_STATUS', 'STOPPING', '停止中', 3, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_RUN_STATUS' and dict_value = 'STOPPING');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_run_status_succeeded', '定时任务运行状态', 'SCHEDULER_RUN_STATUS', 'SUCCEEDED', '成功', 4, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_RUN_STATUS' and dict_value = 'SUCCEEDED');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_run_status_failed', '定时任务运行状态', 'SCHEDULER_RUN_STATUS', 'FAILED', '失败', 5, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_RUN_STATUS' and dict_value = 'FAILED');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_run_status_skipped', '定时任务运行状态', 'SCHEDULER_RUN_STATUS', 'SKIPPED', '已跳过', 6, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_RUN_STATUS' and dict_value = 'SKIPPED');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_run_status_manually_stopped', '定时任务运行状态', 'SCHEDULER_RUN_STATUS', 'MANUALLY_STOPPED', '人工停止', 7, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_RUN_STATUS' and dict_value = 'MANUALLY_STOPPED');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_trigger_type_cron', '定时任务触发类型', 'SCHEDULER_TRIGGER_TYPE', 'CRON', '定时触发', 1, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_TRIGGER_TYPE' and dict_value = 'CRON');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_trigger_type_manual', '定时任务触发类型', 'SCHEDULER_TRIGGER_TYPE', 'MANUAL', '手工触发', 2, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_TRIGGER_TYPE' and dict_value = 'MANUAL');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_trigger_type_user_plan', '定时任务触发类型', 'SCHEDULER_TRIGGER_TYPE', 'USER_PLAN', '用户计划', 3, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_TRIGGER_TYPE' and dict_value = 'USER_PLAN');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_registration_registered', '定时任务注册状态', 'SCHEDULER_TASK_REGISTRATION_STATUS', 'REGISTERED', '已注册', 1, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_TASK_REGISTRATION_STATUS' and dict_value = 'REGISTERED');

insert into dictionaries (dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
select 'dict_scheduler_registration_missing_handler', '定时任务注册状态', 'SCHEDULER_TASK_REGISTRATION_STATUS', 'MISSING_HANDLER', '缺少处理器', 2, now(), now()
where not exists (select 1 from dictionaries where dict_key = 'SCHEDULER_TASK_REGISTRATION_STATUS' and dict_value = 'MISSING_HANDLER');
