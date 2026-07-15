-- 为每日清理定时任务运行记录提供 ended_at 查询索引。
create index if not exists idx_scheduled_task_runs_ended_at
    on scheduled_task_runs(ended_at);
