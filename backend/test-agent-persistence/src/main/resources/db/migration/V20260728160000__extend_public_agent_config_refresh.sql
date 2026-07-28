alter table public_agent_config_rollouts
    add column discard_shared_runtime_changes boolean not null default false;

create table public_agent_config_rollout_public_worktrees (
    rollout_id varchar(64) not null references public_agent_config_rollouts(rollout_id) on delete cascade,
    worktree_id varchar(128) not null references agent_config_worktrees(worktree_id) on delete cascade,
    user_id varchar(128) not null,
    linux_server_id varchar(128) not null,
    target_commit varchar(128) not null,
    status varchar(32) not null,
    reason varchar(1000),
    retry_count integer not null default 0,
    next_retry_at timestamp with time zone not null,
    lease_until timestamp with time zone,
    lease_token varchar(64),
    trace_id varchar(128) not null,
    synced_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (rollout_id, worktree_id),
    constraint ck_public_agent_config_rollout_public_worktrees_status
        check (status in ('AWAITING_USER', 'PROCESSING', 'SYNCED', 'ABANDONED'))
);

create index idx_public_agent_config_rollout_public_worktrees_claim
    on public_agent_config_rollout_public_worktrees
        (linux_server_id, status, next_retry_at, lease_until);

comment on column public_agent_config_rollouts.discard_shared_runtime_changes is
    '超级管理员是否已明确确认恢复全服务器共享运行副本的本地修改';
comment on table public_agent_config_rollout_public_worktrees is
    '公共 Agent Git 刷新中暂时无法合入目标提交的公共个人 worktree 补偿任务';
