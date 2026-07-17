create table public_agent_config_rollouts (
    rollout_id varchar(64) primary key,
    branch varchar(255) not null,
    commit_hash varchar(128) not null,
    status varchar(32) not null,
    failure_reason varchar(1000),
    trace_id varchar(128) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create unique index uk_public_agent_config_rollouts_active
    on public_agent_config_rollouts ((1))
    where status = 'DRAINING';

create table public_agent_config_rollout_servers (
    rollout_id varchar(64) not null references public_agent_config_rollouts(rollout_id) on delete cascade,
    linux_server_id varchar(128) not null,
    status varchar(32) not null,
    synced_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (rollout_id, linux_server_id)
);

create table public_agent_config_rollout_targets (
    target_id varchar(64) primary key,
    rollout_id varchar(64) not null references public_agent_config_rollouts(rollout_id) on delete cascade,
    linux_server_id varchar(128) not null,
    container_id varchar(128) not null,
    port integer not null,
    base_url varchar(1000) not null,
    status varchar(32) not null,
    retry_count integer not null default 0,
    next_retry_at timestamptz not null,
    lease_until timestamptz,
    last_error varchar(1000),
    disposed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (rollout_id, linux_server_id, container_id, port)
);

create index idx_public_agent_config_rollout_targets_claim
    on public_agent_config_rollout_targets (status, next_retry_at, lease_until);
