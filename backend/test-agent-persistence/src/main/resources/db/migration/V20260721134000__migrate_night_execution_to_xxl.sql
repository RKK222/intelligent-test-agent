-- 夜间任务从旧 USER_PLAN 扫描迁移到 XXL-JOB；短暂停机发布保证旧非终态运行可统一收敛为历史跳过记录。
alter table night_execution_tasks add column if not exists dispatch_attempt_id varchar(128);
alter table night_execution_tasks add column if not exists dispatch_owner_backend_process_id varchar(128);
alter table night_execution_tasks add column if not exists dispatch_lease_until timestamp with time zone;
alter table night_execution_tasks add column if not exists state_version bigint not null default 0;
alter table runs add column if not exists scheduled_dispatch_attempt_id varchar(128);
alter table runs add column if not exists scheduled_dispatch_lease_until timestamp with time zone;
alter table runs add column if not exists scheduled_dispatch_accepted_at timestamp with time zone;

drop index if exists idx_night_execution_scheduled_due;
drop index if exists idx_night_execution_dispatch_started;
create index if not exists idx_night_execution_scheduled_due
    on night_execution_tasks(status, slot_start, window_end, created_at);
create index if not exists idx_night_execution_dispatch_lease
    on night_execution_tasks(status, dispatch_lease_until, created_at);
create index if not exists idx_night_execution_dispatch_owner
    on night_execution_tasks(dispatch_owner_backend_process_id, status, dispatch_lease_until);

update scheduled_task_runs
set status='SKIPPED',
    ended_at=coalesce(ended_at, current_timestamp),
    skip_reason='夜间执行已迁移至 XXL-JOB',
    updated_at=current_timestamp
where task_key='opencode-runtime.night-execution'
  and trigger_type='USER_PLAN'
  and status in ('PENDING','RUNNING','STOPPING');

-- 新扫描不再依赖旧 scheduler run；旧字段保持 nullable 供历史查询。
update night_execution_tasks
set scheduled_task_run_id=null, state_version=state_version+1, updated_at=current_timestamp
where status='SCHEDULED' and scheduled_task_run_id is not null;

-- 为停机升级前遗留认领补齐一个已过期的迁移租约，交由锚点优先的补偿任务收敛。
update night_execution_tasks
set dispatch_attempt_id=concat('nda_migrated_', task_id),
    dispatch_owner_backend_process_id='bjp_migration_orphan',
    dispatch_lease_until=coalesce(dispatch_started_at, updated_at),
    state_version=state_version+1,
    updated_at=current_timestamp
where status='DISPATCHING' and dispatch_attempt_id is null;

comment on column night_execution_tasks.dispatch_attempt_id is '当前分发尝试ID，完成和回退SQL的fencing令牌';
comment on column night_execution_tasks.dispatch_owner_backend_process_id is '当前认领任务的精确Java后端进程ID';
comment on column night_execution_tasks.dispatch_lease_until is '当前分发认领租约截止时间';
comment on column night_execution_tasks.state_version is '任务聚合状态CAS版本号';
comment on column runs.scheduled_dispatch_attempt_id is 'legacy Scheduled Run启动认领attempt fencing令牌';
comment on column runs.scheduled_dispatch_lease_until is 'legacy Scheduled Run未完成handoff时的恢复租约';
comment on column runs.scheduled_dispatch_accepted_at is 'legacy Scheduled Run完成事件订阅与异步prompt提交的时间';
