drop index if exists uk_public_agent_config_rollouts_active;

create unique index uk_public_agent_config_rollouts_public_active
    on public_agent_config_rollouts ((1))
    where config_scope = 'PUBLIC' and status in ('PREPARING', 'DRAINING');

create unique index uk_public_agent_config_rollouts_application_active
    on public_agent_config_rollouts (scope_key)
    where config_scope = 'APPLICATION' and status in ('PREPARING', 'DRAINING');

create table public_agent_config_rollout_worktrees (
    rollout_id varchar(64) not null references public_agent_config_rollouts(rollout_id) on delete cascade,
    personal_workspace_id varchar(128) not null references personal_workspaces(personal_workspace_id) on delete cascade,
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
    primary key (rollout_id, personal_workspace_id),
    constraint ck_public_agent_config_rollout_worktrees_status
        check (status in ('PENDING', 'PROCESSING', 'AWAITING_USER', 'SYNCED', 'ABANDONED'))
);

create index idx_public_agent_config_rollout_worktrees_claim
    on public_agent_config_rollout_worktrees (linux_server_id, status, next_retry_at, lease_until);

create index idx_public_agent_config_rollout_worktrees_user
    on public_agent_config_rollout_worktrees (rollout_id, linux_server_id, user_id, status);

comment on index uk_public_agent_config_rollouts_public_active is
    '公共 Agent/Skill 配置同一时刻只允许一个活动发布；不再被应用发布占用';
comment on index uk_public_agent_config_rollouts_application_active is
    '应用 Agent/Skill 配置按应用版本隔离活动发布；不同版本可独立收敛';
comment on table public_agent_config_rollout_worktrees is
    '应用配置发布中因本地修改或合并冲突暂未收敛的个人 worktree 补偿任务';
comment on column public_agent_config_rollout_worktrees.status is
    'PENDING/PROCESSING 为后台处理，AWAITING_USER 表示保留本地内容等待用户处理，SYNCED/ABANDONED 为终态';
comment on column public_agent_config_rollout_worktrees.reason is
    '稳定原因码，例如 LOCAL_CHANGES、MERGE_CONFLICT、MERGE_AWAITING_COMPLETION';
