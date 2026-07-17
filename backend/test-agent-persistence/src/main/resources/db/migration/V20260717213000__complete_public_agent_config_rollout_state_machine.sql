drop index if exists uk_public_agent_config_rollouts_active;

alter table public_agent_config_rollouts
    alter column commit_hash drop not null,
    add column initiated_linux_server_id varchar(128),
    add column previous_commit_hash varchar(128);

create unique index uk_public_agent_config_rollouts_active
    on public_agent_config_rollouts ((1))
    where status in ('PREPARING', 'DRAINING');

create table public_agent_config_rollout_memberships (
    linux_server_id varchar(128) primary key,
    status varchar(32) not null,
    registered_at timestamp with time zone not null,
    decommissioned_at timestamp with time zone,
    updated_at timestamp with time zone not null
);

alter table public_agent_config_rollout_servers
    add column retry_count integer not null default 0,
    add column next_retry_at timestamp with time zone,
    add column lease_until timestamp with time zone,
    add column lease_token varchar(64),
    add column last_error varchar(1000);

update public_agent_config_rollout_servers
set next_retry_at = created_at
where next_retry_at is null;

alter table public_agent_config_rollout_servers
    alter column next_retry_at set not null;

create index idx_public_agent_config_rollout_servers_claim
    on public_agent_config_rollout_servers (linux_server_id, status, next_retry_at, lease_until);

alter table public_agent_config_rollout_targets
    add column process_pid bigint,
    add column process_started_at timestamp with time zone;

comment on table public_agent_config_rollout_memberships is
    '公共配置发布成员清单；与linux_servers历史运行拓扑分离，只有实际运行过新版协调器的服务器自动加入';
comment on column public_agent_config_rollouts.status is
    'PREPARING先建立消息门禁，DRAINING表示远端提交已确认，COMPLETED/ABORTED为终态';
comment on column public_agent_config_rollout_targets.process_pid is
    '登记时manager快照中的PID；dispose前必须与当前manager快照精确匹配';
comment on column public_agent_config_rollout_targets.process_started_at is
    '登记时manager快照中的启动时间；与PID共同防止端口复用后误dispose新进程';
